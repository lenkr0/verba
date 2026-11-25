package io.github.lenkr0.verba.widget

import java.security.MessageDigest
import java.time.LocalDate

/**
 * The word of the day, the same on every device and in every app that has
 * the same words (docs/data-format.md): for each word that isn't deleted,
 * SHA-256 of the UTF-8 text "YYYY-MM-DD:<id>" (the local date); the word
 * with the smallest hash wins. A new day reshuffles; adding a word changes
 * today's pick only if the new one happens to win.
 */
object WordOfTheDay {
    fun pick(ids: Collection<String>, date: LocalDate): String? = ids.minWithOrNull(compareBy(UnsignedBytes) { key(date, it) })

    internal fun key(date: LocalDate, id: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("$date:$id".toByteArray(Charsets.UTF_8))

    /** Byte by byte, unsigned – the same order as comparing the hex strings. */
    private object UnsignedBytes : Comparator<ByteArray> {
        override fun compare(a: ByteArray, b: ByteArray): Int {
            for (i in 0..<minOf(a.size, b.size)) {
                val c = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff)
                if (c != 0) return c
            }
            return a.size - b.size
        }
    }
}
