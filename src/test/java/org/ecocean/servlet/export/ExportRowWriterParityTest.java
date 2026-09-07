package org.ecocean.servlet.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The annotation export serves the same rows as either CSV or XLSX. These tests pin that the two
 * serializations actually agree, and that the XLSX one keeps values string-typed.
 *
 * The string-typing matters concretely: the WildEx image-export app keeps an annotation row only
 * when Annotation&lt;n&gt;.MatchAgainst equals the string "true". A boolean-typed cell there makes
 * it silently discard every row and produce an empty download folder.
 */
public class ExportRowWriterParityTest {
    private static final String[] HEADER = new String[] {
        "Encounter.mediaAsset0", "Annotation0.ViewPoint", "Name0.value",
            "Encounter.mediaAsset0.imageUrl", "Annotation0.bbox", "Annotation0.MatchAgainst"
    };

    /** Rows chosen to exercise everywhere the two formats could legitimately disagree. */
    private static List<String[]> trickyRows() {
        List<String[]> rows = new ArrayList<>();

        rows.add(new String[] {
            "plain.jpg", "left", "Indiv_1", "https://flukebook.org/a.jpg", "[250, 250, 500, 500]",
            "true"
        });
        // empty and null values
        rows.add(new String[] { "", null, "", "", "", "false" });
        // separators, quotes and embedded newlines
        rows.add(new String[] {
            "comma,name.jpg", "he said \"left\"", "line1\nline2", "a\r\nb", "[1, 2, 3, 4]", "true"
        });
        // non-ASCII, which is only identical if the CSV sink is pinned to UTF-8
        rows.add(new String[] {
            "çaça.jpg", "左", "Niño — 鯨", "https://x/é.jpg", "[0, 0, 1, 1]", "true"
        });
        // control characters XML cannot represent, and values at/over Excel's cell limit
        rows.add(new String[] {
            "bell\u0007char.jpg", "left", repeat('a', ExportValues.MAX_CELL_CHARS),
            repeat('b', ExportValues.MAX_CELL_CHARS + 1), "[9, 9, 9, 9]", "true"
        });
        return rows;
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);

        for (int i = 0; i < n; i++)
            sb.append(c);
        return sb.toString();
    }

    /**
     * Mirrors what the exporter does: normalize once, then hand the same array to whichever sink.
     */
    private static List<String[]> normalizedRows() {
        List<String[]> rows = new ArrayList<>();

        rows.add(HEADER.clone());
        for (String[] row : trickyRows())
            rows.add(row.clone());
        for (String[] row : rows)
            ExportValues.normalizeInPlace(row);
        return rows;
    }

    private static byte[] writeCsv(List<String[]> rows)
    throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (ExportRowWriter writer = new CsvExportRowWriter(out)) {
            for (String[] row : rows)
                writer.writeRow(row);
        }
        return out.toByteArray();
    }

    private static byte[] writeXlsx(List<String[]> rows)
    throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (ExportRowWriter writer = new XlsxExportRowWriter(out)) {
            for (String[] row : rows)
                writer.writeRow(row);
        }
        return out.toByteArray();
    }

    private static List<String[]> readCsv(byte[] bytes)
    throws Exception {
        List<String[]> rows = new ArrayList<>();

        try (CSVParser parser = CSVParser.parse(new InputStreamReader(new ByteArrayInputStream(
                bytes), StandardCharsets.UTF_8), CSVFormat.EXCEL)) {
            for (CSVRecord record : parser) {
                String[] row = new String[record.size()];
                for (int i = 0; i < record.size(); i++)
                    row[i] = record.get(i);
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<String[]> readXlsx(byte[] bytes)
    throws Exception {
        List<String[]> rows = new ArrayList<>();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                String[] values = new String[row.getLastCellNum()];
                for (int c = 0; c < values.length; c++) {
                    Cell cell = row.getCell(c);
                    values[c] = (cell == null) ? "" : cell.getStringCellValue();
                }
                rows.add(values);
            }
        }
        return rows;
    }

    @Test public void csvAndXlsxSerializeIdenticalCells()
    throws Exception {
        List<String[]> rows = normalizedRows();
        List<String[]> fromCsv = readCsv(writeCsv(rows));
        List<String[]> fromXlsx = readXlsx(writeXlsx(rows));

        assertEquals(rows.size(), fromCsv.size(), "CSV row count");
        assertEquals(rows.size(), fromXlsx.size(), "XLSX row count");
        for (int r = 0; r < rows.size(); r++) {
            assertArrayEquals(fromCsv.get(r), fromXlsx.get(r),
                "row " + r + " differs between CSV and XLSX");
            assertArrayEquals(rows.get(r), fromCsv.get(r),
                "row " + r + " does not round-trip through CSV");
        }
    }

    /**
     * The actual WildEx regression: the MatchAgainst cell has to come back as the string "true",
     * not as a boolean.
     */
    @Test public void xlsxWritesMatchAgainstAsAStringNotABoolean()
    throws Exception {
        List<String[]> rows = normalizedRows();
        byte[] xlsx = writeXlsx(rows);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            int matchAgainstCol = HEADER.length - 1;
            Cell cell = sheet.getRow(1).getCell(matchAgainstCol);

            assertEquals(Cell.CELL_TYPE_STRING, cell.getCellType(),
                "MatchAgainst must be a string cell; a boolean cell makes WildEx drop every row");
            assertEquals("true", cell.getStringCellValue(), "MatchAgainst value");
        }
    }

    @Test public void xlsxUsesTheSheetNameTheOldExcelExportUsed()
    throws Exception {
        byte[] xlsx = writeXlsx(normalizedRows());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertEquals(XlsxExportRowWriter.SHEET_NAME, workbook.getSheetName(0), "sheet name");
            assertEquals(1, workbook.getNumberOfSheets(),
                "a second sheet would put records in the XLSX that the CSV does not have");
        }
    }

    @Test public void csvIsWrittenAsUtf8()
    throws Exception {
        List<String[]> rows = new ArrayList<>();

        rows.add(new String[] { "Niño 鯨" });
        byte[] csv = writeCsv(rows);

        assertEquals("Niño 鯨", new String(csv, StandardCharsets.UTF_8).trim(),
            "CSV must be UTF-8 regardless of the JVM default charset");
    }

    @Test public void normalizeTruncatesAtExcelCellLimit() {
        String tooLong = repeat('x', ExportValues.MAX_CELL_CHARS + 10);

        assertEquals(ExportValues.MAX_CELL_CHARS, ExportValues.normalize(tooLong).length(),
            "over-long values are truncated identically for both formats");
    }

    @Test public void normalizeStripsCharactersXmlCannotCarry() {
        assertEquals("ab", ExportValues.normalize("a\u0007b"), "BEL is stripped");
        assertEquals("ab", ExportValues.normalize("a\u0000b"), "NUL is stripped");
        assertEquals("a\tb", ExportValues.normalize("a\tb"), "tab is preserved");
        assertEquals("", ExportValues.normalize(null), "null becomes empty string");
    }

    @Test public void normalizeCanonicalizesLineEndings() {
        assertEquals("a\nb", ExportValues.normalize("a\r\nb"), "CRLF collapses to LF");
        assertEquals("a\nb", ExportValues.normalize("a\rb"), "lone CR becomes LF");
        assertEquals("a\nb", ExportValues.normalize("a\nb"), "LF is left alone");
    }

    /**
     * These two defaults deliberately differ. The download endpoint serves .xlsx so WildEx works,
     * but callers that embed this export under a fixed filename - the bulk-export ZIP writes it as
     * "metadata.csv" - must keep getting CSV.
     */
    @Test public void downloadEndpointDefaultsToXlsxWhileEmbeddedCallersKeepCsv() {
        assertEquals(ExportFileFormat.XLSX, EncounterAnnotationExportExcelFile.DEFAULT_FORMAT,
            "the WildEx download endpoint must serve .xlsx by default");
        assertEquals(ExportFileFormat.CSV,
            org.ecocean.export.EncounterAnnotationExportFile.DEFAULT_FORMAT,
            "embedded callers such as the bulk-export ZIP's metadata.csv must keep getting CSV");
    }

    @Test public void normalizeKeepsSupplementaryCharactersIntact() {
        String whale = new String(Character.toChars(0x1F40B));

        assertEquals("a" + whale + "b", ExportValues.normalize("a" + whale + "b"),
            "a valid surrogate pair must survive as the character it encodes");
    }

    @Test public void normalizeStripsUnpairedSurrogates() {
        assertEquals("ab", ExportValues.normalize("a\uD83Db"),
            "an unpaired high surrogate is illegal in XML and must go");
        assertEquals("ab", ExportValues.normalize("a\uDC0Bb"),
            "an unpaired low surrogate is illegal in XML and must go");
    }

    @Test public void truncationNeverSplitsASurrogatePair() {
        String whale = new String(Character.toChars(0x1F40B));
        String value = repeat('a', ExportValues.MAX_CELL_CHARS - 1) + whale;
        String normalized = ExportValues.normalize(value);

        assertEquals(ExportValues.MAX_CELL_CHARS - 1, normalized.length(),
            "the pair straddling the limit is dropped whole, not cut in half");
        assertFalse(Character.isHighSurrogate(normalized.charAt(normalized.length() - 1)),
            "the result must not end on a dangling high surrogate");
    }

    /**
     * The exporter normalizes before handing rows to a sink, but the sinks must also agree when
     * used directly - otherwise the parity guarantee depends on every caller remembering to
     * normalize first.
     */
    @Test public void writersAgreeEvenWhenHandedRawUnnormalizedValues()
    throws Exception {
        List<String[]> raw = new ArrayList<>();

        raw.add(HEADER.clone());
        for (String[] row : trickyRows())
            raw.add(row.clone());
        List<String[]> fromCsv = readCsv(writeCsv(raw));
        List<String[]> fromXlsx = readXlsx(writeXlsx(raw));

        assertEquals(fromCsv.size(), fromXlsx.size(), "row count with raw input");
        for (int r = 0; r < fromCsv.size(); r++) {
            assertArrayEquals(fromCsv.get(r), fromXlsx.get(r),
                "row " + r + " differs when raw values go straight to the writers");
        }
    }

    /**
     * Degenerate but worth pinning: a row with no columns must not read back as absent from one
     * format and present in the other.
     */
    @Test public void aZeroColumnRowIsRepresentedTheSameWayInBothFormats()
    throws Exception {
        List<String[]> rows = new ArrayList<>();

        rows.add(new String[] {});

        List<String[]> fromCsv = readCsv(writeCsv(rows));
        List<String[]> fromXlsx = readXlsx(writeXlsx(rows));

        assertEquals(fromCsv.size(), fromXlsx.size(), "row count for a zero-column row");
        assertArrayEquals(fromCsv.get(0), fromXlsx.get(0),
            "a zero-column row must decode the same from CSV and XLSX");
    }

    @Test public void abortedXlsxEmitsNothingRatherThanAPartialWorkbook()
    throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (ExportRowWriter writer = new XlsxExportRowWriter(out)) {
            writer.writeRow(HEADER.clone());
            writer.abort();
        }

        assertEquals(0, out.size(),
            "an aborted export must not hand back a complete-looking workbook of partial rows");
    }

    @Test public void eachFormatCarriesItsOwnExtensionAndContentType() {
        assertEquals(".xlsx", ExportFileFormat.XLSX.getExtension(), "xlsx extension");
        assertEquals(".csv", ExportFileFormat.CSV.getExtension(), "csv extension");
        assertTrue(ExportFileFormat.XLSX.getContentType().contains("spreadsheetml"),
            "xlsx must not go out as the old application/msexcel type");
        assertTrue(ExportFileFormat.CSV.getContentType().startsWith("text/csv"),
            "csv content type");
    }

    @Test public void unknownFormatParameterIsRejected() {
        assertEquals(ExportFileFormat.XLSX,
            ExportFileFormat.fromRequestParam(null, ExportFileFormat.XLSX), "absent uses default");
        assertEquals(ExportFileFormat.CSV,
            ExportFileFormat.fromRequestParam("csv", ExportFileFormat.XLSX), "exact csv");
        assertEquals(ExportFileFormat.XLSX,
            ExportFileFormat.fromRequestParam("XLSX", ExportFileFormat.CSV), "case-insensitive");
        assertNull(ExportFileFormat.fromRequestParam("xls", ExportFileFormat.XLSX),
            "near-misses are rejected rather than silently defaulted");
        assertNull(ExportFileFormat.fromRequestParam("../etc/passwd", ExportFileFormat.XLSX),
            "junk is rejected");
    }
}
