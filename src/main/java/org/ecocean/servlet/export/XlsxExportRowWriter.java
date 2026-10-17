package org.ecocean.servlet.export;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * Writes export rows as a streaming .xlsx workbook.
 *
 * Every cell is written as an explicit string cell. That is not incidental: the WildEx image-export
 * app (WildMeOrg/WildbookExport) keeps an annotation row only when Annotation&lt;n&gt;.MatchAgainst is
 * exactly the string "true". If the value reaches it as a boolean instead, every row is filtered
 * out and the user gets an empty download folder with no error. Writing string-typed cells here is
 * what the pre-CSV export did, and is what keeps that tool working.
 */
public class XlsxExportRowWriter implements ExportRowWriter {
    public static final String CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String EXTENSION = ".xlsx";

    /** Sheet name used by the pre-CSV export; WildEx reads sheet index 0 but expects this name. */
    public static final String SHEET_NAME = "Search Results";

    /** Rows held in memory before SXSSF spills them to a temp file. */
    private static final int ROW_ACCESS_WINDOW = 100;

    private final OutputStream out;
    private final SXSSFWorkbook workbook;
    private final Sheet sheet;

    private int rowNum = 0;
    private boolean aborted = false;

    public XlsxExportRowWriter(OutputStream out) {
        this.out = out;
        // Streaming workbook: only ROW_ACCESS_WINDOW rows are held in memory at a time, so the
        // workbook itself does not have to be materialized all at once. (The query result the rows
        // are built from is still a fully materialized list; this bounds the workbook, not the
        // whole export.)
        this.workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW);
        // SXSSF spills rows to temp XML that is several times larger than the finished workbook.
        // Must be set before the sheet is created.
        this.workbook.setCompressTempFiles(true);
        this.sheet = workbook.createSheet(SHEET_NAME);
    }

    @Override public void writeRow(String[] row)
    throws IOException {
        Row sheetRow = sheet.createRow(rowNum++);

        if (row.length == 0) {
            // A row with no cells reads back as absent (getLastCellNum() == -1), whereas CSV emits
            // an empty record that parses as a single empty field. One empty cell keeps the two
            // formats agreeing even on this degenerate row.
            sheetRow.createCell(0, Cell.CELL_TYPE_STRING).setCellValue("");
            return;
        }
        for (int i = 0; i < row.length; i++) {
            Cell cell = sheetRow.createCell(i, Cell.CELL_TYPE_STRING);
            cell.setCellValue(clampToCellLimit(row[i]));
        }
    }

    /**
     * Defence in depth. Callers are expected to have run values through
     * {@link ExportValues#normalize} already - that is what keeps CSV and XLSX identical - but a
     * direct user of this writer should still not be able to blow up on Excel's cell limit.
     */
    static String clampToCellLimit(String value) {
        return ExportValues.normalize(value);
    }

    /** Nothing is written on close after this; the temp files are still cleaned up. */
    @Override public void abort() {
        this.aborted = true;
    }

    /** Serializes the workbook to the stream, then releases both the package and the temp files. */
    @Override public void close()
    throws IOException {
        try {
            if (!aborted) {
                workbook.write(out);
                out.flush();
            }
        } finally {
            try {
                // Releases the underlying OOXML package; dispose() alone only clears the SXSSF
                // sheet temp files.
                workbook.close();
            } finally {
                workbook.dispose();
            }
        }
    }
}
