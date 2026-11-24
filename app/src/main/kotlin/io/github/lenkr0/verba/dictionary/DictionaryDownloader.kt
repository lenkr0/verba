package io.github.lenkr0.verba.dictionary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.coroutines.coroutineContext

@Serializable
data class Catalog(
    val format: String,
    val version: Int,
    val updatedAt: String = "",
    val packs: List<CatalogPack>,
)

@Serializable
data class CatalogPack(
    val id: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val title: String,
    val version: String,
    val formatVersion: Int,
    val headwords: Int,
    val url: String,
    val size: Long,
    val installedSize: Long,
    val sha256: String,
    val sources: List<PackSource> = emptyList(),
)

class ChecksumException : Exception("checksum mismatch")

/**
 * The only network code in Verba: fetches the catalog and packs from the
 * project's GitHub release when the user asks for it. Sends nothing but
 * the request itself (with a "Verba/<version>" user agent).
 */
class DictionaryDownloader(
    private val store: DictionaryStore,
    private val userAgent: String,
    private val catalogUrl: String = CATALOG_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchCatalog(): Catalog = withContext(Dispatchers.IO) {
        val text = open(catalogUrl).use { it.readBytes().decodeToString() }
        val catalog = json.decodeFromString<Catalog>(text)
        require(catalog.format == "verba-dictionary-catalog") { "not a Verba catalog" }
        catalog.copy(packs = catalog.packs.filter { it.formatVersion <= DictionaryPack.FORMAT_VERSION })
    }

    /** Downloads, checks and installs [pack]; [onProgress] gets compressed bytes read and the total. */
    suspend fun download(pack: CatalogPack, onProgress: (Long, Long) -> Unit): PackInfo =
        withContext(Dispatchers.IO) {
            val temp = store.tempFile()
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                open(pack.url).use { raw ->
                    val counting = CountingInputStream(raw) { read -> onProgress(read, pack.size) }
                    GZIPInputStream(DigestInputStream(counting, digest), BUFFER).use { gz ->
                        temp.outputStream().use { out -> copy(gz, out) }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(pack.sha256, ignoreCase = true)) throw ChecksumException()
                store.install(temp)
            } finally {
                temp.delete()
            }
        }

    /** Installs a pack file the user picked: .sqlite or .sqlite.gz. */
    suspend fun import(input: InputStream): PackInfo = withContext(Dispatchers.IO) {
        val temp = store.tempFile()
        try {
            BufferedInputStream(input, BUFFER).use { buffered ->
                buffered.mark(2)
                val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
                buffered.reset()
                val source = if (gzip) GZIPInputStream(buffered, BUFFER) else buffered
                temp.outputStream().use { out -> copy(source, out) }
            }
            store.install(temp)
        } finally {
            temp.delete()
        }
    }

    private suspend fun copy(input: InputStream, out: java.io.OutputStream) {
        val buffer = ByteArray(BUFFER)
        while (true) {
            coroutineContext.ensureActive() // cancelling stops the download
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
    }

    private fun open(url: String): InputStream {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", userAgent)
        if (connection is HttpURLConnection) {
            connection.instanceFollowRedirects = true
            val code = connection.responseCode
            if (code !in 200..299) {
                connection.disconnect()
                throw java.io.IOException("HTTP $code")
            }
        }
        return connection.getInputStream()
    }

    private class CountingInputStream(
        private val source: InputStream,
        private val onRead: (Long) -> Unit,
    ) : java.io.FilterInputStream(source) {
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
    }

    companion object {
        const val CATALOG_URL = "https://github.com/lenkr0/verba/releases/download/dictionaries/catalog.json"
        private const val BUFFER = 1 shl 16
        private const val REPORT_STEP = 256L * 1024
    }
}
