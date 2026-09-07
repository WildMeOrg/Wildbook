package org.ecocean.servlet.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * Writes export rows as CSV.
 */
public class CsvExportRowWriter implements ExportRowWriter {
    public static final String CONTENT_TYPE = "text/csv; charset=UTF-8";
    public static final String EXTENSION = ".csv";

    private final CSVPrinter printer;

    public CsvExportRowWriter(OutputStream out)
    throws IOException {
        // Explicit UTF-8. The JVM default charset is not guaranteed to be UTF-8 on Java 11, and
        // the XLSX sink is always UTF-8; pinning it keeps non-ASCII values identical across the
        // two formats.
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

        this.printer = new CSVPrinter(writer, CSVFormat.EXCEL);
    }

    @Override public void writeRow(String[] row)
    throws IOException {
        // Normalized here as well as in the shared producer path so that this writer and the XLSX
        // one behave identically when used directly. ExportValues.normalize is idempotent, so the
        // already-normalized rows the exporter sends are unaffected.
        for (int i = 0; i < row.length; i++) {
            printer.print(ExportValues.normalize(row[i]));
        }
        printer.println();
    }

    /**
     * Flushes the encoder and CSV buffer. Does not close the caller's stream.
     *
     * Flushing rather than closing is safe because every value has been through
     * {@link ExportValues#normalize}, which strips unpaired surrogates - the one input that would
     * otherwise leave state inside the encoder that only close() finalizes.
     */
    @Override public void close()
    throws IOException {
        printer.flush();
    }
}
