package io.github.lenkr0.verba.ocr

import io.github.lenkr0.verba.util.ChecksumException
import io.github.lenkr0.verba.util.CountingInputStream
import io.github.lenkr0.verba.util.copyCancellable
import io.github.lenkr0.verba.util.openUrl
import io.github.lenkr0.verba.util.toHex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

enum class OcrModelError { Download, Checksum }

/**
 * The text recognition models (PaddleOCR PP-OCRv5, about 11 MB). They are not
 * part of the APK: downloaded from the project's GitHub release when the user
 * turns on text recognition, checked and kept in [directory].
 */
class OcrModels(
    private val directory: File,
    private val userAgent: String,
    private val scope: CoroutineScope,
) {
    data class Pack(val id: String, val version: Int, val url: String, val sha256: String, val size: Long) {
        val directoryName get() = "$id-$version"
        val fileName get() = "verba-ocr-$id-$version.zip"
    }

    val packDirectory: File get() = File(directory, PACK.directoryName)

    private val installedState = MutableStateFlow(FILES.all { File(packDirectory, it).isFile })
    val installed: StateFlow<Boolean> = installedState.asStateFlow()

    /** Fraction downloaded, or null when no download is running. */
    private val progressState = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = progressState.asStateFlow()

    private val errorState = MutableStateFlow<OcrModelError?>(null)
    val error: StateFlow<OcrModelError?> = errorState.asStateFlow()

    private var job: Job? = null

    /** Runs in the app's scope, so leaving the screen doesn't stop it. */
    fun download() {
        if (job?.isActive == true) return
        progressState.value = 0f
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    openUrl(PACK.url, userAgent).use { raw ->
                        val counting = CountingInputStream(raw) { read ->
                            progressState.value = (read.toFloat() / PACK.size).coerceIn(0f, 1f)
                        }
                        install(counting, PACK.sha256)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChecksumException) {
                errorState.value = OcrModelError.Checksum
            } catch (e: IOException) {
                errorState.value = OcrModelError.Download
            } finally {
                progressState.value = null
            }
        }
    }

    fun cancel() {
        job?.cancel()
        progressState.value = null
    }

    fun delete() {
        scope.launch(Dispatchers.IO) {
            installedState.value = false
            packDirectory.deleteRecursively()
        }
    }

    fun clearError() {
        errorState.value = null
    }

    /** Checks the pack zip read from [input] against [sha256] and unpacks it. */
    suspend fun install(input: InputStream, sha256: String) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val temp = File(directory, "download.part")
        val staging = File(directory, "staging")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(input, digest).use { d -> temp.outputStream().use { copyCancellable(d, it) } }
            if (!digest.digest().toHex().equals(sha256, ignoreCase = true)) throw ChecksumException()
            staging.deleteRecursively()
            staging.mkdirs()
            ZipFile(temp).use { zip ->
                // Only the known files, by name: nothing else from the archive lands on disk
                for (name in FILES) {
                    val entry = zip.getEntry(name) ?: throw IOException("$name missing in pack")
                    zip.getInputStream(entry).use { src -> File(staging, name).outputStream().use { copyCancellable(src, it) } }
                }
            }
            packDirectory.deleteRecursively()
            if (!staging.renameTo(packDirectory)) throw IOException("could not install pack")
            installedState.value = true
        } finally {
            temp.delete()
            staging.deleteRecursively()
        }
    }

    companion object {
        val PACK = Pack(
            id = "ppocrv5-mobile",
            version = 1,
            url = "https://github.com/lenkr0/verba/releases/download/ocr-models/verba-ocr-ppocrv5-mobile-1.zip",
            sha256 = "f7248d6ccef1fc1008632b2412819447f47d0de6191f64fab8a85c7019e2bf55",
            size = 10_717_274,
        )
        val FILES = listOf("pack.json", "det.ncnn.param", "det.ncnn.bin", "rec.ncnn.param", "rec.ncnn.bin", "dict.txt")
    }
}
