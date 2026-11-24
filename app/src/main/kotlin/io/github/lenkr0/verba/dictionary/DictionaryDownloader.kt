package io.github.lenkr0.verba.dictionary

import io.github.lenkr0.verba.util.ChecksumException
import io.github.lenkr0.verba.util.CountingInputStream
import io.github.lenkr0.verba.util.DOWNLOAD_BUFFER
import io.github.lenkr0.verba.util.copyCancellable
import io.github.lenkr0.verba.util.openUrl
import io.github.lenkr0.verba.util.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

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

/** Fetches the dictionary catalog and packs from the project's GitHub release when the user asks for it. */
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
                    GZIPInputStream(DigestInputStream(counting, digest), DOWNLOAD_BUFFER).use { gz ->
                        temp.outputStream().use { out -> copyCancellable(gz, out) }
                    }
                }
                val actual = digest.digest().toHex()
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
            BufferedInputStream(input, DOWNLOAD_BUFFER).use { buffered ->
                buffered.mark(2)
                val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
                buffered.reset()
                val source = if (gzip) GZIPInputStream(buffered, DOWNLOAD_BUFFER) else buffered
                temp.outputStream().use { out -> copyCancellable(source, out) }
            }
            store.install(temp)
        } finally {
            temp.delete()
        }
    }

    private fun open(url: String): InputStream = openUrl(url, userAgent)

    companion object {
        const val CATALOG_URL = "https://github.com/lenkr0/verba/releases/download/dictionaries/catalog.json"
    }
}
