package io.github.lenkr0.verba.dictionary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The packs on this device, one file per pair in [directory]
 * ("en-de.sqlite"). Separate from your words: deleting a dictionary never
 * touches them, and backups don't include dictionaries.
 */
class DictionaryStore(private val directory: File) {
    private val mutex = Mutex()
    private val open = mutableMapOf<String, DictionaryPack>()
    private val state = MutableStateFlow<List<PackInfo>>(emptyList())

    private var scanned = false

    /** Installed packs; up to date once [refresh] or [installedNow] ran. */
    val installed: StateFlow<List<PackInfo>> = state.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock { scan() }
    }

    /** The installed packs, scanning the folder first if that hasn't happened yet. */
    suspend fun installedNow(): List<PackInfo> = withContext(Dispatchers.IO) {
        mutex.withLock { if (!scanned) scan() }
        state.value
    }

    /** The pack for a language pair, opened on first use. */
    suspend fun pack(sourceLanguage: String, targetLanguage: String): DictionaryPack? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id = "$sourceLanguage-$targetLanguage"
            open[id] ?: fileFor(id).takeIf { it.exists() }?.let { file ->
                runCatching { DictionaryPack.open(file) }.getOrNull()?.also { open[id] = it }
            }
        }
    }

    /**
     * Moves a downloaded or imported pack into place, replacing an older
     * version of the same pair. Throws [InvalidPackException] for files that
     * aren't packs this app can read.
     */
    suspend fun install(candidate: File): PackInfo = withContext(Dispatchers.IO) {
        val info = DictionaryPack.open(candidate).use { it.info }
        mutex.withLock {
            open.remove(info.id)?.close()
            directory.mkdirs()
            val target = fileFor(info.id)
            if (!candidate.renameTo(target)) {
                candidate.copyTo(target, overwrite = true)
                candidate.delete()
            }
            scan()
        }
        info
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            open.remove(id)?.close()
            fileFor(id).delete()
            scan()
        }
    }

    /** A temporary file next to the packs, so installing is a cheap rename. */
    fun tempFile(): File {
        directory.mkdirs()
        return File.createTempFile("incoming-", ".part", directory)
    }

    private fun fileFor(id: String) = File(directory, "$id.sqlite")

    private fun scan() {
        // Leftovers of downloads the app didn't finish; only at the first scan,
        // later ones would hit downloads that are running
        if (!scanned) directory.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
        state.value = directory.listFiles { f -> f.name.endsWith(".sqlite") }.orEmpty()
            .mapNotNull { file ->
                open[file.nameWithoutExtension]?.info
                    ?: runCatching { DictionaryPack.open(file).use { it.info } }.getOrNull()
            }
            .sortedBy { it.id }
        scanned = true
    }
}
