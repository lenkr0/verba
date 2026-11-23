package io.github.lenkr0.verba.dictionary

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A dictionary a pack is built from; shown with its license on the attribution screen. */
@Serializable
data class PackSource(
    val id: String,
    val name: String,
    val url: String,
    val license: String,
    @SerialName("license_url") val licenseUrl: String,
    val attribution: String = "",
)

/** What an installed pack says about itself (its meta table). */
data class PackInfo(
    val id: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val title: String,
    val version: String,
    val formatVersion: Int,
    val headwords: Int,
    val sources: List<PackSource>,
    val sizeBytes: Long,
)

data class DictionarySense(
    val translations: List<String>,
    val definition: String,
    val definitionLanguage: String,
    val source: String,
)

data class DictionaryEntry(
    val lemma: String,
    val reading: String?,
    val partOfSpeech: String?,
    val senses: List<DictionarySense>,
)

/**
 * The outcome of looking up a word: [matched] is the text in the sentence
 * that was found (the tapped word, a longer phrase like "gave up", or a
 * shorter or longer piece for languages without spaces). [fallback] is true when the entries come from a pack
 * into English because no pack for the reader's own language exists.
 */
data class LookupResult(
    val matched: String,
    val entries: List<DictionaryEntry>,
    val pack: PackInfo,
    val fallback: Boolean,
)
