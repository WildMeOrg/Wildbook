package org.ecocean.servlet.export;

import java.io.Closeable;
import java.io.IOException;

/**
 * A sink for fully-materialized export rows.
 *
 * Exports build each row as a {@code String[]} indexed by {@link ExportColumn#colNum} and then hand
 * the finished array to one of these. Keeping the row-production logic (query, access-control
 * filtering, column construction) in a single code path and varying only the sink is what
 * guarantees that the CSV and XLSX outputs contain identical data: there is no second traversal to
 * keep in sync.
 *
 * Implementations are responsible for flushing and releasing their own resources in
 * {@link #close()}. They do not own the underlying stream and must not close it.
 */
public interface ExportRowWriter extends Closeable {
    /**
     * Writes one row. The header row is written by calling this with the header labels.
     *
     * @param row one value per column, in column order. Entries are expected to be non-null;
     *            {@link ExportColumn#writeLabel} already substitutes "" for null values.
     */
    void writeRow(String[] row)
    throws IOException;

    /**
     * Abandons the export so that {@link #close()} emits nothing further.
     *
     * Without this, a failure part-way through would still produce a complete-looking workbook
     * holding only the rows written so far - silent truncation, which is precisely the kind of
     * quiet data loss this export has already been bitten by. Streaming formats such as CSV have
     * usually put bytes on the wire by this point and cannot take them back, so the default is a
     * no-op.
     */
    default void abort() {}

    /** Flushes any buffered content and releases implementation resources. */
    @Override void close()
    throws IOException;
}
