package io.github.lenkr0.verba.backup

import android.content.Context
import android.net.Uri
import android.widget.Toast
import io.github.lenkr0.verba.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Backups and exports to files the user picks in the system file picker – no
 * storage permission. Runs in the app's scope, so leaving the screen doesn't
 * stop it; the result shows as a toast.
 */
class Transfers(
    private val context: Context,
    private val backups: BackupRepository,
    private val csv: CsvExport,
    private val anki: AnkiExport,
    private val scope: CoroutineScope,
) {
    private val busyState = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = busyState.asStateFlow()

    private val resolver get() = context.contentResolver

    fun backup(uri: Uri) = run {
        val words = output(uri) { backups.export(it) }
        context.resources.getQuantityString(R.plurals.backup_saved, words, words)
    }

    fun restore(uri: Uri) = run {
        val result = resolver.openInputStream(uri)?.use { backups.restore(it) } ?: throw IOException("can't read $uri")
        context.getString(R.string.restore_done, result.added, result.updated)
    }

    fun exportCsv(uri: Uri) = run {
        val words = output(uri) { csv.export(it) }
        context.resources.getQuantityString(R.plurals.export_saved, words, words)
    }

    fun exportAnki(uri: Uri) = run {
        val words = output(uri) { anki.export(it) }
        context.resources.getQuantityString(R.plurals.export_saved, words, words)
    }

    private suspend fun output(uri: Uri, write: suspend (java.io.OutputStream) -> Int): Int =
        // "wt": an existing file the user picked is replaced, not partly overwritten
        resolver.openOutputStream(uri, "wt")?.use { write(it) } ?: throw IOException("can't write $uri")

    private fun run(block: suspend () -> String) {
        scope.launch {
            busyState.value = true
            val message = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                context.getString(
                    if (e.reason == BackupException.Reason.TooNew) R.string.restore_too_new else R.string.restore_not_a_backup,
                )
            } catch (_: Exception) {
                context.getString(R.string.transfer_failed)
            } finally {
                busyState.value = false
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
