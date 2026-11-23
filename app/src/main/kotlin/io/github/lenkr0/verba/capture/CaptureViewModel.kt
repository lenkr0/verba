package io.github.lenkr0.verba.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lenkr0.verba.data.Capture
import io.github.lenkr0.verba.data.ExistingEntry
import io.github.lenkr0.verba.data.SaveResult
import io.github.lenkr0.verba.data.VocabRepository
import io.github.lenkr0.verba.dictionary.DictionaryEntry
import io.github.lenkr0.verba.dictionary.DictionaryLookup
import io.github.lenkr0.verba.dictionary.DictionarySense
import io.github.lenkr0.verba.dictionary.LookupResult
import io.github.lenkr0.verba.language.Lemmatizer
import io.github.lenkr0.verba.language.ScriptDetector
import io.github.lenkr0.verba.language.Sentence
import io.github.lenkr0.verba.language.WordTokenizer
import io.github.lenkr0.verba.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which word of which sentence is chosen; [PHRASE] means the whole selection. */
data class WordChoice(val sentence: Int, val token: Int) {
    companion object {
        val PHRASE = WordChoice(-1, -1)
    }
}

/** What the dictionary says about the chosen word. */
sealed interface DictionaryState {
    data object Idle : DictionaryState
    data class Found(val result: LookupResult) : DictionaryState
    data object NotFound : DictionaryState

    /** No pack for the language pair (nor one into English). */
    data object Unavailable : DictionaryState
}

data class CaptureUiState(
    val text: String,
    val language: String,
    val targetLanguage: String,
    /** The selection split into tappable words; empty when a single word was selected. */
    val sentences: List<Sentence> = emptyList(),
    val choice: WordChoice? = null,
    /** A short multi-word selection can be saved as one phrase. */
    val phraseAllowed: Boolean = false,
    val wordForm: String = "",
    val lemma: String = "",
    val translation: String = "",
    val definition: String = "",
    val sentence: String = "",
    val wordStart: Int = -1,
    val wordEnd: Int = -1,
    val sourceTitle: String = "",
    val titleSuggestions: List<String> = emptyList(),
    val sourceAppLabel: String? = null,
    val existing: ExistingEntry? = null,
    val dictionary: DictionaryState = DictionaryState.Idle,
    val saving: Boolean = false,
) {
    val isSentenceMode: Boolean get() = sentences.isNotEmpty()
    val hasWord: Boolean get() = wordForm.isNotBlank()
    val canSave: Boolean get() = hasWord && lemma.isNotBlank() && !saving
}

class CaptureViewModel(
    private val request: CaptureRequest,
    private val settings: SettingsRepository,
    private val vocabulary: VocabRepository,
    private val dictionary: DictionaryLookup,
) : ViewModel() {
    var state by mutableStateOf(initialState())
        private set

    val recentLanguages: List<String> get() = settings.settings.value.recentLanguages

    // Fields the user typed into are not overwritten by the dictionary or a saved entry
    private var lemmaEdited = false
    private var translationEdited = false
    private var definitionEdited = false
    private var existingJob: Job? = null
    private var lookupJob: Job? = null

    /** The text from the chosen word to the end of its sentence, for phrases and longest matches. */
    private var following: String = ""

    init {
        viewModelScope.launch {
            val titles = vocabulary.recentTitles(limit = 8)
            val last = vocabulary.lastTitleForApp(request.sourceApp) ?: titles.firstOrNull()
            state = state.copy(
                titleSuggestions = titles,
                sourceTitle = state.sourceTitle.ifEmpty { last.orEmpty() },
            )
        }
        if (state.hasWord) {
            following = state.wordForm
            lookUp()
        }
    }

    private fun initialState(): CaptureUiState {
        val current = settings.settings.value
        val language = ScriptDetector.guess(request.text, current.recentLanguages)
        val base = CaptureUiState(
            text = request.text,
            language = language,
            targetLanguage = current.targetLanguage,
            sourceAppLabel = request.sourceAppLabel,
        )
        return analyze(base, language)
    }

    /** Splits the selection for [language]; a single word skips the sentence view. */
    private fun analyze(base: CaptureUiState, language: String): CaptureUiState {
        val sentences = WordTokenizer.sentences(request.text, language)
        val words = sentences.flatMap { it.tokens }.filter { it.isWord }
        val cleared = base.copy(
            language = language, choice = null, wordForm = "", lemma = "", translation = "",
            definition = "", sentence = "", wordStart = -1, wordEnd = -1, existing = null,
            dictionary = DictionaryState.Idle,
        )
        return if (words.size <= 1) {
            val word = words.firstOrNull()?.text ?: request.text
            cleared.copy(sentences = emptyList(), phraseAllowed = false, wordForm = word, lemma = lemmaOf(word, language))
        } else {
            cleared.copy(
                sentences = sentences,
                phraseAllowed = sentences.size == 1 && words.size <= MAX_PHRASE_WORDS,
            )
        }
    }

    private fun lemmaOf(word: String, language: String) = Lemmatizer.forLanguage(language).lemma(word)

    fun chooseWord(sentenceIndex: Int, tokenIndex: Int) {
        val sentence = state.sentences.getOrNull(sentenceIndex) ?: return
        val token = sentence.tokens.getOrNull(tokenIndex)?.takeIf { it.isWord } ?: return
        resetEdits()
        following = sentence.text.substring(token.start)
        state = state.copy(
            choice = WordChoice(sentenceIndex, tokenIndex),
            wordForm = token.text,
            lemma = lemmaOf(token.text, state.language),
            translation = "",
            definition = "",
            sentence = sentence.text,
            wordStart = token.start,
            wordEnd = token.end,
            existing = null,
            dictionary = DictionaryState.Idle,
        )
        lookUp()
    }

    fun choosePhrase() {
        val phrase = request.text.trim()
        resetEdits()
        following = phrase
        state = state.copy(
            choice = WordChoice.PHRASE, wordForm = phrase, lemma = phrase, translation = "",
            definition = "", sentence = "", wordStart = -1, wordEnd = -1, existing = null,
            dictionary = DictionaryState.Idle,
        )
        lookUp()
    }

    fun setLanguage(language: String) {
        if (language == state.language) return
        resetEdits()
        state = analyze(state, language)
        if (state.hasWord) {
            following = state.wordForm
            lookUp()
        }
    }

    fun setTargetLanguage(language: String) {
        settings.setTargetLanguage(language)
        state = state.copy(targetLanguage = language)
        if (state.hasWord) lookUp()
    }

    fun setLemma(value: String) {
        lemmaEdited = true
        state = state.copy(lemma = value)
        refreshExisting(debounce = true)
    }

    fun setTranslation(value: String) {
        translationEdited = true
        state = state.copy(translation = value)
    }

    fun setDefinition(value: String) {
        definitionEdited = true
        state = state.copy(definition = value)
    }

    /** The user picked one of the dictionary's senses. */
    fun useSense(entry: DictionaryEntry, sense: DictionarySense) {
        translationEdited = true
        definitionEdited = true
        if (!lemmaEdited) state = state.copy(lemma = entry.lemma)
        state = state.copy(translation = sense.text(), definition = sense.extraDefinition())
        refreshExisting(debounce = false)
    }

    /** Only for a single selected word: the sentence the user adds by hand. */
    fun setSentence(value: String) {
        val start = value.indexOf(state.wordForm, ignoreCase = true)
        state = state.copy(
            sentence = value,
            wordStart = start,
            wordEnd = if (start >= 0) start + state.wordForm.length else -1,
        )
    }

    fun setSourceTitle(value: String) {
        state = state.copy(sourceTitle = value)
    }

    fun save(onSaved: (SaveResult, String) -> Unit) {
        val current = state
        if (!current.canSave) return
        state = current.copy(saving = true)
        viewModelScope.launch {
            val result = vocabulary.save(
                Capture(
                    language = current.language,
                    targetLanguage = current.targetLanguage,
                    wordForm = current.wordForm,
                    lemma = current.lemma,
                    translation = current.translation,
                    definition = current.definition,
                    sentence = current.sentence,
                    wordStart = current.wordStart,
                    wordEnd = current.wordEnd,
                    sourceTitle = current.sourceTitle,
                    sourceApp = request.sourceApp,
                    sourceAppLabel = request.sourceAppLabel,
                ),
            )
            settings.noteLanguageUsed(current.language)
            onSaved(result, current.lemma.trim())
        }
    }

    private fun resetEdits() {
        lemmaEdited = false
        translationEdited = false
        definitionEdited = false
    }

    /**
     * Looks the chosen word up and fills base form, translation and definition
     * from the best match; then checks whether that base form is already saved.
     */
    private fun lookUp() {
        lookupJob?.cancel()
        val language = state.language
        val target = state.targetLanguage
        val word = state.wordForm
        lookupJob = viewModelScope.launch {
            val result = dictionary.lookup(language, target, word, following)
            state = when {
                result != null -> applyResult(result)
                dictionary.canLookUp(language, target) -> state.copy(dictionary = DictionaryState.NotFound)
                else -> state.copy(dictionary = DictionaryState.Unavailable)
            }
            refreshExisting(debounce = false)
        }
    }

    private fun applyResult(result: LookupResult): CaptureUiState {
        val entry = result.entries.first()
        val sense = entry.senses.firstOrNull { it.translations.isNotEmpty() } ?: entry.senses.first()
        var next = state.copy(dictionary = DictionaryState.Found(result))
        // Languages without spaces: the match may be longer or shorter than the
        // tapped piece (食べ|た → 食べた); the saved word and its position follow it
        val matched = result.matched
        if (matched != next.wordForm && next.wordStart >= 0 && next.sentence.startsWith(matched, next.wordStart)) {
            next = next.copy(wordForm = matched, wordEnd = next.wordStart + matched.length)
        }
        if (!lemmaEdited) next = next.copy(lemma = entry.lemma)
        if (!translationEdited) next = next.copy(translation = sense.text())
        if (!definitionEdited) next = next.copy(definition = sense.extraDefinition())
        return next
    }

    /** Looks up whether the base form is already saved; your earlier translation wins. */
    private fun refreshExisting(debounce: Boolean) {
        existingJob?.cancel()
        existingJob = viewModelScope.launch {
            if (debounce) delay(LOOKUP_DELAY_MS)
            val existing = vocabulary.existing(state.language, state.lemma)
            state = state.copy(
                existing = existing,
                translation = existing?.translation?.takeIf { it.isNotBlank() && !translationEdited } ?: state.translation,
                definition = existing?.definition?.takeIf { it.isNotBlank() && !definitionEdited } ?: state.definition,
            )
        }
    }

    private companion object {
        const val MAX_PHRASE_WORDS = 5
        const val LOOKUP_DELAY_MS = 300L
    }
}

/** A sense as it goes into the translation field. */
internal fun DictionarySense.text(): String =
    translations.joinToString("; ").ifEmpty { definition }

/** The definition, unless it already went into the translation field. */
internal fun DictionarySense.extraDefinition(): String = if (translations.isEmpty()) "" else definition
