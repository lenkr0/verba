package io.github.lenkr0.verba.util

import kotlinx.coroutines.ensureActive
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

// Verba's only network code: downloads of dictionaries and recognition models
// from the project's GitHub releases, when the user asks for them. Nothing is
// sent but the request itself (with a "Verba/<version>" user agent).

class ChecksumException : Exception("checksum mismatch")

internal const val DOWNLOAD_BUFFER = 1 shl 16

/** Opens [url] following redirects; throws for anything but a 2xx answer. */
internal fun openUrl(url: String, userAgent: String): InputStream {
    val connection = URL(url).openConnection()
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.setRequestProperty("User-Agent", userAgent)
    if (connection is HttpURLConnection) {
        connection.instanceFollowRedirects = true
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP $code")
        }
    }
    return connection.getInputStream()
}

/** Copies [input] to [out]; cancelling the coroutine stops the copy. */
internal suspend fun copyCancellable(input: InputStream, out: OutputStream) {
    val buffer = ByteArray(DOWNLOAD_BUFFER)
    while (true) {
        coroutineContext.ensureActive()
        val n = input.read(buffer)
        if (n < 0) break
        out.write(buffer, 0, n)
    }
}

/** Reports the number of bytes read so far, every 256 KiB. */
internal class CountingInputStream(source: InputStream, private val onRead: (Long) -> Unit) : FilterInputStream(source) {
    private var count = 0L
    private var reported = 0L

    override fun read(): Int = super.read().also { if (it >= 0) advance(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) advance(it.toLong()) }

    private fun advance(n: Long) {
        count += n
        if (count - reported >= REPORT_STEP) {
            reported = count
            onRead(count)
        }
    }

    private companion object {
        const val REPORT_STEP = 256L * 1024
    }
}

/** Hex SHA-256 of a finished digest. */
internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
