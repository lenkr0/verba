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
) : ViewModel() {
    var state by mutableStateOf(initialState())
        private set

    val recentLanguages: List<String> get() = settings.settings.value.recentLanguages

    // Fields the user typed into are not overwritten by values of a saved entry
    private var translationEdited = false
    private var definitionEdited = false
    private var lookup: Job? = null

    init {
        viewModelScope.launch {
            val titles = vocabulary.recentTitles(limit = 8)
            val last = vocabulary.lastTitleForApp(request.sourceApp) ?: titles.firstOrNull()
            state = state.copy(
                titleSuggestions = titles,
                sourceTitle = state.sourceTitle.ifEmpty { last.orEmpty() },
            )
        }
        refreshExisting(debounce = false)
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
        state = state.copy(
            choice = WordChoice(sentenceIndex, tokenIndex),
            wordForm = token.text,
            lemma = lemmaOf(token.text, state.language),
            translation = "",
            definition = "",
            sentence = sentence.text,
            wordStart = token.start,
            wordEnd = token.end,
        )
        refreshExisting(debounce = false)
    }

    fun choosePhrase() {
        val phrase = request.text.trim()
        resetEdits()
        state = state.copy(
            choice = WordChoice.PHRASE, wordForm = phrase, lemma = phrase, translation = "",
            definition = "", sentence = "", wordStart = -1, wordEnd = -1,
        )
        refreshExisting(debounce = false)
    }

    fun setLanguage(language: String) {
        if (language == state.language) return
        resetEdits()
        state = analyze(state, language)
        refreshExisting(debounce = false)
    }

    fun setTargetLanguage(language: String) {
        settings.setTargetLanguage(language)
        state = state.copy(targetLanguage = language)
    }

    fun setLemma(value: String) {
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
        translationEdited = false
        definitionEdited = false
    }

    /** Looks up whether the base form is already saved and fills in its translation. */
    private fun refreshExisting(debounce: Boolean) {
        lookup?.cancel()
        lookup = viewModelScope.launch {
            if (debounce) delay(LOOKUP_DELAY_MS)
            val existing = vocabulary.existing(state.language, state.lemma)
            state = state.copy(
                existing = existing,
                translation = if (existing != null && !translationEdited) existing.translation else state.translation,
                definition = if (existing != null && !definitionEdited) existing.definition else state.definition,
            )
        }
    }

    private companion object {
        const val MAX_PHRASE_WORDS = 5
        const val LOOKUP_DELAY_MS = 300L
    }
}
