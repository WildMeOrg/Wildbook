package org.ecocean.servlet.export;

/**
 * Canonical normalization for exported cell values.
 *
 * CSV and XLSX do not accept the same set of strings: XLSX caps a cell at 32,767 characters and
 * cannot carry text that XML forbids, while CSV would happily pass both through. Normalizing once
 * here - in the shared row-production path, before either sink sees the value - is what keeps the
 * two formats carrying identical data instead of quietly diverging at the edges.
 *
 * The operation is idempotent, so the sinks apply it again defensively without changing the result.
 */
public final class ExportValues {
    /** Excel's hard limit on the number of characters in a single cell. */
    public static final int MAX_CELL_CHARS = 32767;

    private ExportValues() {}

    /** Normalizes every entry of a row in place. */
    public static void normalizeInPlace(String[] row) {
        if (row == null) return;
        for (int i = 0; i < row.length; i++) {
            row[i] = normalize(row[i]);
        }
    }

    /**
     * Returns a value safe for both sinks: never null, with line endings canonicalized, free of
     * text XML cannot carry, and no longer than a single Excel cell can hold.
     */
    public static String normalize(String value) {
        if (value == null) return "";
        String cleaned = canonicalizeLineEndings(value);

        cleaned = stripTextIllegalInXml(cleaned);
        return truncateToCellLimit(cleaned);
    }

    /**
     * Collapses CRLF and lone CR to LF.
     *
     * An embedded CRLF survives an XLSX cell verbatim, but CSV readers - Excel's included -
     * normalize it to LF on the way back in. Canonicalizing here means a value containing a line
     * break reads back the same from both formats instead of differing by a stray CR.
     */
    private static String canonicalizeLineEndings(String value) {
        if (value.indexOf('\r') < 0) return value;
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * Drops anything XML cannot represent, walking by code point.
     *
     * Walking by code point rather than by char matters twice over: a valid supplementary character
     * (an emoji, say) must survive as the pair it is, and a lone unpaired surrogate - which is
     * illegal in XML and would make the workbook unreadable - must not be mistaken for one half of
     * a legitimate pair.
     */
    private static String stripTextIllegalInXml(String value) {
        StringBuilder sb = null;
        final int length = value.length();
        int i = 0;

        while (i < length) {
            char c = value.charAt(i);
            int codePoint;
            int width;
            if (Character.isHighSurrogate(c) && (i + 1) < length &&
                Character.isLowSurrogate(value.charAt(i + 1))) {
                codePoint = Character.toCodePoint(c, value.charAt(i + 1));
                width = 2;
            } else {
                codePoint = c;
                width = 1;
            }
            if (isLegalXmlCodePoint(codePoint)) {
                if (sb != null) sb.append(value, i, i + width);
            } else if (sb == null) {
                sb = new StringBuilder(length);
                sb.append(value, 0, i);
            }
            i += width;
        }
        return (sb == null) ? value : sb.toString();
    }

    /** The XML 1.0 Char production. Unpaired surrogates reach here as their own code point. */
    private static boolean isLegalXmlCodePoint(int codePoint) {
        if (codePoint == '\t' || codePoint == '\n' || codePoint == '\r') return true;
        if (codePoint < 0x20) return false;
        if (codePoint >= 0xD800 && codePoint <= 0xDFFF) return false; // unpaired surrogate
        if (codePoint == 0xFFFE || codePoint == 0xFFFF) return false;
        return codePoint <= 0x10FFFF;
    }

    /**
     * Cuts a value down to what one Excel cell holds, never between the halves of a surrogate pair.
     *
     * Truncation is silent to the consumer but not to the operator: a value long enough to hit this
     * is being altered, which is worth a line in the log.
     */
    private static String truncateToCellLimit(String value) {
        if (value.length() <= MAX_CELL_CHARS) return value;
        int end = MAX_CELL_CHARS;
        // Cutting here would leave a high surrogate whose partner is the character being dropped.
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        System.out.println("ExportValues: truncated a " + value.length() +
            "-character value to the " + end + "-character spreadsheet cell limit.");
        return value.substring(0, end);
    }
}
