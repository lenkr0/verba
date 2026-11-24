package io.github.lenkr0.verba.dictionary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Looks a word up in the installed packs: the pack into the reader's own
 * language first, else the one into English. Tries the candidates from
 * [LookupCandidates] and returns the first that matches.
 */
class DictionaryLookup(private val store: DictionaryStore) {

    /** True when some pack can look up words of [language] (into [target] or English). */
    suspend fun canLookUp(language: String, target: String): Boolean =
        store.installedNow().any { it.sourceLanguage == language && (it.targetLanguage == target || it.targetLanguage == "en") }

    /** Installed packs as last scanned; for suggesting languages. */
    fun installed(): List<PackInfo> = store.installed.value

    suspend fun lookup(language: String, target: String, word: String, following: String): LookupResult? =
        withContext(Dispatchers.IO) {
            val primary = store.pack(language, target)
            val fallback = if (target != "en") store.pack(language, "en") else null
            val candidates = LookupCandidates.of(language, word, following)
            for (pack in listOfNotNull(primary, fallback)) {
                for (candidate in candidates) {
                    val entries = pack.lookup(candidate.text)
                    if (entries.isNotEmpty()) {
                        return@withContext LookupResult(candidate.surface, entries, pack.info, fallback = pack === fallback)
                    }
                }
            }
            null
        }
}
