package org.ecocean.servlet.export;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Output formats an export endpoint can serve, and the sink each one uses.
 *
 * Both formats are fed by the same row-production code path, so the data they contain is identical;
 * only the serialization differs.
 */
public enum ExportFileFormat {
    CSV(CsvExportRowWriter.EXTENSION, CsvExportRowWriter.CONTENT_TYPE),
        XLSX(XlsxExportRowWriter.EXTENSION, XlsxExportRowWriter.CONTENT_TYPE);

    private final String extension;
    private final String contentType;

    ExportFileFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String getExtension() { return extension; }
    public String getContentType() { return contentType; }

    public ExportRowWriter newWriter(OutputStream out)
    throws IOException {
        switch (this) {
        case XLSX:
            return new XlsxExportRowWriter(out);

        default:
            return new CsvExportRowWriter(out);
        }
    }

    /**
     * Resolves a user-supplied "format" request parameter against a strict whitelist.
     *
     * Absent or blank means "use the default". Anything other than an exact, known format name is
     * rejected with null so the caller can fail the request before it starts writing bytes, rather
     * than silently handing back a format the user did not ask for. The parameter value is never
     * echoed into the response, so it cannot reach a header.
     *
     * @return the resolved format, or null if the supplied value is not a recognized format
     */
    public static ExportFileFormat fromRequestParam(String value, ExportFileFormat fallback) {
        if (value == null)
            return fallback;
        String normalized = value.trim().toLowerCase();
        if (normalized.isEmpty())
            return fallback;
        for (ExportFileFormat format : values()) {
            if (format.name().toLowerCase().equals(normalized))
                return format;
        }
        return null;
    }
}
