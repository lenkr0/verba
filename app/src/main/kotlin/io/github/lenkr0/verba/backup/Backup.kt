package io.github.lenkr0.verba.backup

import androidx.room.withTransaction
import io.github.lenkr0.verba.data.ContextEntity
import io.github.lenkr0.verba.data.EntryEntity
import io.github.lenkr0.verba.data.ReviewCardEntity
import io.github.lenkr0.verba.data.ReviewLogEntity
import io.github.lenkr0.verba.data.SourceEntity
import io.github.lenkr0.verba.data.VerbaDatabase
import io.github.lenkr0.verba.data.VocabRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** Everything Verba stores, in one JSON file (docs/data-format.md). */
@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    @SerialName("exported_at") val exportedAt: Long,
    @SerialName("app_version") val appVersion: String,
    val entries: List<EntryEntity> = emptyList(),
    val contexts: List<ContextEntity> = emptyList(),
    val sources: List<SourceEntity> = emptyList(),
    @SerialName("review_cards") val reviewCards: List<ReviewCardEntity> = emptyList(),
    @SerialName("review_logs") val reviewLogs: List<ReviewLogEntity> = emptyList(),
) {
    companion object {
        const val FORMAT = "verba-backup"
        const val VERSION = 1
    }
}

class BackupException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NotABackup, TooNew }
}

/** What a restore changed: words that are new on this device, and words a newer version replaced. */
data class RestoreResult(val added: Int, val updated: Int)

/**
 * Writes and reads backups. Restoring merges instead of replacing: per record
 * the newer change wins (updated_at), as a sync between devices would do, so
 * restoring an old backup never undoes newer work. Words or books that now
 * exist twice (saved on two devices) are merged.
 */
class BackupRepository(
    private val db: VerbaDatabase,
    private val vocabulary: VocabRepository,
    private val appVersion: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.backupDao()

    /** Writes the backup; returns the number of words in it (not counting deleted ones). */
    suspend fun export(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val backup = db.withTransaction {
            Backup(
                exportedAt = now(),
                appVersion = appVersion,
                entries = dao.entries(),
                contexts = dao.contexts(),
                sources = dao.sources(),
                reviewCards = dao.reviewCards(),
                reviewLogs = dao.reviewLogs(),
            )
        }
        out.write(json.encodeToString(Backup.serializer(), backup).toByteArray())
        out.flush()
        backup.entries.count { it.deletedAt == null }
    }

    suspend fun restore(input: InputStream): RestoreResult = withContext(Dispatchers.IO) {
        val backup = parse(input.readBytes().decodeToString())
        db.withTransaction {
            val local = dao.entries()
            val wasLive = local.filter { it.deletedAt == null }.map { it.id }.toSet()
            val entries = newer(backup.entries, local, { it.id }, { it.updatedAt })
            dao.upsertEntries(entries)
            dao.upsertSources(newer(backup.sources, dao.sources(), { it.id }, { it.updatedAt }))
            dao.upsertContexts(newer(backup.contexts, dao.contexts(), { it.id }, { it.updatedAt }))
            dao.upsertReviewCards(newer(backup.reviewCards, dao.reviewCards(), { it.entryId }, { it.updatedAt }))
            dao.upsertReviewLogs(newer(backup.reviewLogs, dao.reviewLogs(), { it.id }, { it.updatedAt }))
            mergeDuplicates()
            val live = dao.entries().filter { it.deletedAt == null }.map { it.id }.toSet()
            RestoreResult(
                added = live.count { it !in wasLive },
                updated = entries.count { it.id in wasLive && it.id in live },
            )
        }
    }

    private fun parse(text: String): Backup {
        val backup = try {
            json.decodeFromString(Backup.serializer(), text)
        } catch (_: SerializationException) {
            throw BackupException(BackupException.Reason.NotABackup)
        } catch (_: IllegalArgumentException) {
            throw BackupException(BackupException.Reason.NotABackup)
        }
        if (backup.format != Backup.FORMAT) throw BackupException(BackupException.Reason.NotABackup)
        if (backup.version > Backup.VERSION) throw BackupException(BackupException.Reason.TooNew)
        return backup
    }

    /** The rows of [incoming] that are new here or changed later than the local copy. */
    private fun <T> newer(incoming: List<T>, local: List<T>, key: (T) -> String, updatedAt: (T) -> Long): List<T> {
        val byKey = local.associateBy(key)
        return incoming.filter { row -> byKey[key(row)]?.let { updatedAt(row) > updatedAt(it) } ?: true }
    }

    /** The same book or word saved on two devices: keep the older one, move everything to it. */
    private suspend fun mergeDuplicates() {
        val time = now()
        dao.sources().filter { it.deletedAt == null }.groupBy { it.titleKey }.values
            .filter { it.size > 1 }
            .forEach { group ->
                val keep = group.minWith(compareBy({ it.createdAt }, { it.id }))
                for (other in group - keep) {
                    dao.moveSource(other.id, keep.id, time)
                    dao.markSourceDeleted(other.id, time)
                }
            }
        dao.entries().filter { it.deletedAt == null }.groupBy { it.language to it.lemmaKey }.values
            .filter { it.size > 1 }
            .forEach { group ->
                val keep = group.minWith(compareBy({ it.createdAt }, { it.id }))
                for (other in group - keep) vocabulary.merge(other.id, keep.id)
            }
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true // backups from newer versions with extra fields still restore
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
