package com.bal.reminders.parser

/**
 * A normalized view over Arabic input that supports consuming matched spans and
 * recovering the untouched remainder *from the original string*, so titles
 * keep their original spelling (ة، همزات، تشكيل) even though matching runs on
 * normalized text.
 *
 * Normalization: Arabic-Indic digits → ASCII, hamza forms → ا, ة → ه, ى → ي,
 * diacritics/tatweel removed, separators unified, whitespace collapsed.
 */
internal class ConsumableText(private val original: String) {

    val text: String
    private val map: IntArray // normalized index → original index
    private val consumed: BooleanArray

    init {
        val sb = StringBuilder(original.length)
        val indices = ArrayList<Int>(original.length)
        var pendingSpace = false
        original.forEachIndexed { i, raw ->
            val ch = normalizeChar(raw) ?: return@forEachIndexed
            if (ch == ' ') {
                pendingSpace = sb.isNotEmpty()
            } else {
                if (pendingSpace) {
                    sb.append(' ')
                    // A space maps to the original position of the char after it.
                    indices.add(i)
                    pendingSpace = false
                }
                sb.append(ch)
                indices.add(i)
            }
        }
        text = sb.toString()
        map = indices.toIntArray()
        consumed = BooleanArray(text.length)
    }

    /** Finds the first match of [regex] that doesn't overlap a consumed span, consumes it. */
    fun consumeFirst(regex: Regex): MatchResult? {
        for (match in regex.findAll(text)) {
            if (match.value.isEmpty()) continue
            if (match.range.any { consumed[it] }) continue
            consume(match.range)
            return match
        }
        return null
    }

    /** Like [consumeFirst] but only consumes when [accept] approves the match. */
    fun consumeFirstIf(regex: Regex, accept: (MatchResult) -> Boolean): MatchResult? {
        for (match in regex.findAll(text)) {
            if (match.value.isEmpty()) continue
            if (match.range.any { consumed[it] }) continue
            if (!accept(match)) continue
            consume(match.range)
            return match
        }
        return null
    }

    private fun consume(range: IntRange) {
        for (i in range) consumed[i] = true
    }

    /** The unconsumed parts, recovered from the original string. */
    fun remainder(): String {
        val parts = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            if (consumed[i] || text[i] == ' ') {
                i++
                continue
            }
            var j = i
            while (j < text.length && !consumed[j]) j++
            var end = j - 1
            while (end >= i && text[end] == ' ') end--
            if (end >= i) parts += original.substring(map[i], map[end] + 1)
            i = j
        }
        return parts.joinToString(" ").trim()
    }

    private fun normalizeChar(ch: Char): Char? = when (ch) {
        in '٠'..'٩' -> '0' + (ch - '٠') // ٠..٩
        in '۰'..'۹' -> '0' + (ch - '۰') // ۰..۹
        'ـ' -> null                                // tatweel
        in 'ً'..'ٟ', 'ٰ' -> null         // diacritics
        'أ', 'إ', 'آ', 'ٱ' -> 'ا'
        'ة' -> 'ه'
        'ى' -> 'ي'
        'ؤ' -> 'و'
        'ئ' -> 'ي'
        '،', ',', '؛', ';', '!', '؟', '?' -> ' '
        else -> if (ch.isWhitespace()) ' ' else ch
    }
}
