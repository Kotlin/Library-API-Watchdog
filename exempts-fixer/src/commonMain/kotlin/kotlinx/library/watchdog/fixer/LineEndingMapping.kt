package kotlinx.library.watchdog.fixer

/**
 * The compiler reads a source file without its byte order mark and with each `\r\n` and `\r` as
 * one `\n`. It records offsets on that text, [normalizedText], and the fixer plans its edits on it.
 * [toRawOffset] maps the offset of an edit to the file text, so the byte order mark and the
 * existing line endings stay. Inserted text uses [newline], the dominant separator.
 */
internal class LineEndingMapping private constructor(
    val normalizedText: String,
    /** Null when the file text is [normalizedText]. */
    private val rawOffsets: IntArray?,
    val newline: String,
) {
    fun toRawOffset(normalizedOffset: Int): Int = rawOffsets?.get(normalizedOffset) ?: normalizedOffset

    companion object {
        private const val BYTE_ORDER_MARK = '\uFEFF'

        fun of(text: String): LineEndingMapping {
            val start = if (text.startsWith(BYTE_ORDER_MARK)) 1 else 0
            if (start == 0 && '\r' !in text) {
                return LineEndingMapping(text, rawOffsets = null, newline = "\n")
            }

            val crlfPairs = generateSequence(text.indexOf("\r\n").takeIf { it >= 0 }) { previous ->
                text.indexOf("\r\n", previous + 2).takeIf { it >= 0 }
            }.count()
            val otherBreaks = text.count { it == '\n' || it == '\r' } - 2 * crlfPairs
            val newline = if (crlfPairs > otherBreaks) "\r\n" else "\n"

            val normalized = StringBuilder(text.length)
            val rawOffsets = IntArray(text.length + 1)
            var rawIndex = start
            while (rawIndex < text.length) {
                val char = text[rawIndex]
                if (char == '\r' && rawIndex + 1 < text.length && text[rawIndex + 1] == '\n') {
                    // The pair maps to one `\n` at the start of the pair, so an insertion can't split it.
                    rawOffsets[normalized.length] = rawIndex
                    normalized.append('\n')
                    rawIndex += 2
                    continue
                }
                rawOffsets[normalized.length] = rawIndex
                normalized.append(if (char == '\r') '\n' else char)
                rawIndex++
            }
            rawOffsets[normalized.length] = text.length
            return LineEndingMapping(normalized.toString(), rawOffsets.copyOf(normalized.length + 1), newline)
        }
    }
}
