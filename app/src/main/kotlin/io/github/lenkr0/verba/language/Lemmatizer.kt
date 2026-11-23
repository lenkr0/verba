package io.github.lenkr0.verba.language

import java.text.Normalizer
import java.util.Locale

/** Suggests the base form of a word ("running" → "run"). The user can always edit it. */
fun interface Lemmatizer {
    fun lemma(word: String): String

    companion object {
        /**
         * Rules exist for English only; other languages keep the word until the
         * dictionaries (milestone 2) provide real base forms.
         */
        fun forLanguage(language: String): Lemmatizer = when (language) {
            "en" -> EnglishLemmatizer
            else -> Lemmatizer { it.trim() }
        }
    }
}

/** Key that decides whether two base forms are the same entry: "Run" and "run" are. */
object LemmaKey {
    fun of(lemma: String): String =
        Normalizer.normalize(lemma.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
}

/**
 * Conservative English rules: only forms whose base form is unambiguous
 * without a dictionary are changed. "stopped" → "stop" and "studies" →
 * "study" are safe; "hoped" could be "hope" or "hop", so it stays as it is
 * unless it's in the table of common words.
 */
object EnglishLemmatizer : Lemmatizer {
    private val irregular: Map<String, String> = buildMap {
        fun forms(lemma: String, vararg forms: String) = forms.forEach { put(it, lemma) }
        forms("be", "am", "is", "are", "was", "were", "been", "being")
        forms("have", "has", "had", "having")
        forms("do", "does", "did", "done", "doing")
        forms("go", "goes", "went", "gone", "going")
        forms("say", "said")
        forms("make", "made", "making", "makes")
        forms("take", "took", "taken", "taking")
        forms("come", "came", "coming")
        forms("give", "gave", "given", "giving")
        forms("see", "saw", "seen")
        forms("get", "got", "gotten", "getting")
        forms("know", "knew", "known")
        forms("think", "thought")
        forms("find", "found")
        forms("tell", "told")
        forms("become", "became", "becoming")
        forms("leave", "left", "leaving")
        forms("feel", "felt")
        forms("bring", "brought")
        forms("begin", "began", "begun", "beginning")
        forms("keep", "kept")
        forms("hold", "held")
        forms("write", "wrote", "written", "writing")
        forms("stand", "stood")
        forms("hear", "heard")
        forms("mean", "meant")
        forms("meet", "met")
        forms("run", "ran")
        forms("pay", "paid")
        forms("sit", "sat")
        forms("speak", "spoke", "spoken")
        forms("lie", "lay", "lain", "lying")
        forms("lead", "led")
        forms("read")
        forms("grow", "grew", "grown")
        forms("lose", "lost", "losing")
        forms("fall", "fell", "fallen")
        forms("send", "sent")
        forms("build", "built")
        forms("understand", "understood")
        forms("draw", "drew", "drawn")
        forms("break", "broke", "broken")
        forms("spend", "spent")
        forms("rise", "rose", "risen", "rising")
        forms("drive", "drove", "driven", "driving")
        forms("buy", "bought")
        forms("wear", "wore", "worn")
        forms("choose", "chose", "chosen", "choosing")
        forms("seek", "sought")
        forms("throw", "threw", "thrown")
        forms("catch", "caught")
        forms("teach", "taught")
        forms("fight", "fought")
        forms("fly", "flew", "flown", "flies")
        forms("sleep", "slept")
        forms("eat", "ate", "eaten")
        forms("drink", "drank", "drunk")
        forms("sing", "sang", "sung")
        forms("swim", "swam", "swum")
        forms("win", "won")
        forms("forget", "forgot", "forgotten")
        forms("hide", "hid", "hidden", "hiding")
        forms("bite", "bit", "bitten")
        forms("shake", "shook", "shaken", "shaking")
        forms("steal", "stole", "stolen")
        forms("freeze", "froze", "frozen", "freezing")
        forms("wake", "woke", "woken", "waking")
        forms("die", "died", "dying")
        // Common verbs ending in a silent "e", whose -ed/-ing forms are ambiguous
        for (verb in listOf(
            "use", "live", "like", "love", "hope", "move", "close", "smile", "change", "arrive",
            "decide", "believe", "hate", "dance", "notice", "place", "face", "save", "share", "care",
            "declare", "prepare", "promise", "realize", "recognize", "agree", "breathe", "chase",
            "continue", "create", "escape", "excuse", "force", "guide", "imagine", "include",
            "introduce", "judge", "manage", "measure", "name", "pause", "please", "produce",
            "raise", "receive", "refuse", "remove", "replace", "serve", "shine", "suppose",
            "surprise", "trace", "wave", "whisper", "struggle", "settle", "rescue", "sense",
        )) {
            val stem = verb.dropLast(1)
            forms(verb, verb + "s", verb + "d", stem + "ing")
        }
        forms("child", "children")
        forms("man", "men")
        forms("woman", "women")
        forms("person", "people")
        forms("mouse", "mice")
        forms("foot", "feet")
        forms("tooth", "teeth")
        forms("goose", "geese")
        forms("knife", "knives")
        forms("life", "lives")
        forms("wife", "wives")
        forms("wolf", "wolves")
        forms("half", "halves")
        forms("shelf", "shelves")
        forms("leaf", "leaves")
        forms("thief", "thieves")
        // Plurals of words ending in -ie (not -y)
        for (noun in listOf("movie", "cookie", "zombie", "calorie", "brownie", "rookie", "pie", "tie", "genie")) {
            forms(noun, noun + "s")
        }
    }

    /** Words that only look inflected: nouns in -ing, adjectives in -ed, words in -s. */
    private val keep = setOf(
        "morning", "evening", "nothing", "something", "anything", "everything", "during", "ceiling",
        "darling", "sibling", "pudding", "wedding", "string", "spring", "thing", "king", "ring",
        "wing", "sing", "bring", "ding", "sting", "swing", "cling", "fling",
        "hundred", "sacred", "hatred", "kindred", "wicked", "naked", "rugged", "ragged",
        "crooked", "beloved", "aged",
        "news", "always", "perhaps", "series", "species", "lens", "physics", "mathematics",
        "politics", "economics", "sometimes", "whereas", "thus", "yes", "this", "his", "hers",
        "ours", "yours", "theirs", "besides", "unless", "chaos", "canvas", "atlas", "bias", "alias",
        "christmas", "afterwards", "towards", "upstairs", "downstairs", "overseas", "nowadays",
    )

    private val vowels = setOf('a', 'e', 'i', 'o', 'u')
    private val keepDouble = setOf('l', 's', 'z', 'f')
    // A stem ending in one of these often lost a silent "e" (danced, changed, solved)
    private val silentEHint = setOf('c', 'g', 'v', 'z', 's', 'u')

    override fun lemma(word: String): String {
        val w = word.trim().lowercase(Locale.ROOT)
        if (w.length < 3 || w.any { !it.isLetter() && it != '\'' && it != '-' }) return w
        irregular[w]?.let { return it }
        if (w in keep) return w

        return when {
            w.endsWith("ies") && w.length > 4 -> w.dropLast(3) + "y"
            w.endsWith("ied") && w.length > 4 -> w.dropLast(3) + "y"
            w.endsWith("ing") && w.length > 5 -> stem(w.dropLast(3)) ?: w
            w.endsWith("ed") && w.length > 4 -> stem(w.dropLast(2)) ?: w
            w.endsWith("sses") || w.endsWith("ches") || w.endsWith("shes") || w.endsWith("xes") ||
                w.endsWith("zzes") -> w.dropLast(2)
            w.endsWith("s") && w.length > 3 && !w.endsWith("ss") && !w.endsWith("us") &&
                !w.endsWith("is") -> w.dropLast(1)
            else -> w
        }
    }

    /** The stem of an -ing/-ed form if it is unambiguous, else null. */
    private fun stem(s: String): String? {
        // "string" → "str" is no word: a stem needs a vowel
        if (s.length < 2 || s.none { it in vowels || it == 'y' }) return null
        val last = s.last()
        val prev = s[s.length - 2]
        return when {
            // stopped → stop, running → run; but falling → fall, passed → pass
            last == prev && last !in vowels -> if (last in keepDouble) s else s.dropLast(1)
            // played → play, showing → show
            last in setOf('y', 'w') && prev in vowels -> s
            // trembling → tremble, handled → handle (but curled → curl, howled → howl)
            last == 'l' && prev !in vowels && prev !in setOf('r', 'l', 'w', 'y') -> s + "e"
            // walked → walk, watched → watch
            last !in vowels && prev !in vowels && last !in silentEHint -> s
            // looked → look, rained → rain
            last !in vowels && last !in silentEHint && s.length >= 3 &&
                prev in vowels && s[s.length - 3] in vowels -> s
            else -> null
        }
    }
}
