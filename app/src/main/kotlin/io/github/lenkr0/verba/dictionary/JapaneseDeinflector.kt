package io.github.lenkr0.verba.dictionary

/**
 * Turns conjugated Japanese verbs and adjectives back into dictionary forms:
 * 食べた → 食べる, 行きました → 行く, 高くなかった → 高い.
 *
 * Every rule replaces an ending with a shorter or base ending; rules chain
 * (ました → ます → る), so the result is a list of candidates. Most are not
 * real words – the dictionary decides which one exists.
 */
object JapaneseDeinflector {
    private class Rule(val from: String, val to: String)

    private fun godan(vararg forms: Pair<String, String>) = forms.map { (from, to) -> Rule(from, to) }

    // Godan verbs by their final kana: masu-stem, a-row (negative), e-row
    // (potential, imperative), o-row (volitional), ta and te forms
    private data class Row(val u: String, val i: String, val a: String, val e: String, val o: String, val ta: String, val te: String)

    private val godanRows = listOf(
        Row("う", "い", "わ", "え", "お", "った", "って"),
        Row("く", "き", "か", "け", "こ", "いた", "いて"),
        Row("ぐ", "ぎ", "が", "げ", "ご", "いだ", "いで"),
        Row("す", "し", "さ", "せ", "そ", "した", "して"),
        Row("つ", "ち", "た", "て", "と", "った", "って"),
        Row("ぬ", "に", "な", "ね", "の", "んだ", "んで"),
        Row("ぶ", "び", "ば", "べ", "ぼ", "んだ", "んで"),
        Row("む", "み", "ま", "め", "も", "んだ", "んで"),
        Row("る", "り", "ら", "れ", "ろ", "った", "って"),
    )

    private val rules: List<Rule> = buildList {
        // Endings that reduce to simpler endings first
        addAll(
            listOf(
                Rule("ませんでした", "ます"), Rule("ました", "ます"), Rule("ません", "ます"), Rule("ましょう", "ます"),
                Rule("なかった", "ない"), Rule("なくて", "ない"), Rule("なければ", "ない"),
                Rule("ていた", "ている"), Rule("ています", "ている"), Rule("ていない", "ている"),
                Rule("ている", "て"), Rule("てる", "て"), Rule("でいる", "で"), Rule("でる", "で"),
                Rule("てしまう", "て"), Rule("ちゃう", "て"), Rule("ちゃった", "て"),
                Rule("たかった", "たい"), Rule("たくない", "たい"),
                // i-adjectives
                Rule("かった", "い"), Rule("くない", "い"), Rule("くて", "い"), Rule("ければ", "い"),
                Rule("く", "い"), Rule("さ", "い"), Rule("そう", "い"),
            ),
        )
        // Ichidan verbs (食べる, 見る): the stem stays, る goes
        for (ending in listOf(
            "ます", "ない", "た", "て", "たい", "よう", "られる", "させる", "れば", "ろ", "よ", "ず",
        )) add(Rule(ending, "る"))
        // Godan verbs
        for (row in godanRows) {
            addAll(
                godan(
                    row.i + "ます" to row.u, row.a + "ない" to row.u, row.ta to row.u, row.te to row.u,
                    row.i + "たい" to row.u, row.o + "う" to row.u, row.e + "ば" to row.u,
                    row.e + "る" to row.u, // potential: 行ける → 行く
                    row.a + "れる" to row.u, row.a + "せる" to row.u, // passive, causative
                    row.a + "ず" to row.u,
                    row.e to row.u, // imperative: 行け → 行く
                    row.i to row.u, // masu-stem as it appears in compounds
                ),
            )
        }
        // Irregular verbs
        addAll(
            listOf(
                Rule("した", "する"), Rule("して", "する"), Rule("しない", "する"), Rule("します", "する"),
                Rule("しよう", "する"), Rule("される", "する"), Rule("させる", "する"), Rule("できる", "する"),
                Rule("きた", "くる"), Rule("きて", "くる"), Rule("こない", "くる"), Rule("きます", "くる"),
                Rule("来た", "来る"), Rule("来て", "来る"), Rule("来ない", "来る"), Rule("来ます", "来る"),
                Rule("行った", "行く"), Rule("行って", "行く"), Rule("いった", "いく"), Rule("いって", "いく"),
            ),
        )
    }

    private const val MAX_STEPS = 4

    /** Possible dictionary forms of [text], nearest first; [text] itself is not included. */
    fun candidates(text: String): List<String> {
        val seen = linkedSetOf<String>()
        var frontier = listOf(text)
        repeat(MAX_STEPS) {
            val next = mutableListOf<String>()
            for (word in frontier) {
                for (rule in rules) {
                    // ">=": irregular rules cover whole words (行った → 行く)
                    if (word.length >= rule.from.length && word.endsWith(rule.from)) {
                        val base = word.dropLast(rule.from.length) + rule.to
                        if (base != text && seen.add(base)) next += base
                    }
                }
            }
            if (next.isEmpty()) return seen.toList()
            frontier = next
        }
        return seen.toList()
    }
}
