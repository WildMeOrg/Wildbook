package org.ecocean.export;

import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.security.HiddenEncReporter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Vector;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EncounterImageExportFileTest {
    @Test
    void writeTo_skipsEncountersFlaggedByHiddenEncReporter() throws Exception {
        Encounter hidden = mock(Encounter.class);
        when(hidden.getCatalogNumber()).thenReturn("hidden-enc");

        Encounter visible = mock(Encounter.class);
        when(visible.getCatalogNumber()).thenReturn("visible-enc");
        when(visible.getAnnotations()).thenReturn(new java.util.ArrayList<>());

        Vector<Encounter> encs = new Vector<>();
        encs.add(hidden);
        encs.add(visible);

        HiddenEncReporter reporter = mock(HiddenEncReporter.class);
        when(reporter.contains(hidden)).thenReturn(true);
        when(reporter.contains(visible)).thenReturn(false);

        EncounterImageExportFile sut = new EncounterImageExportFile(
            encs,
            new HashMap<String, MarkedIndividual>(),
            -1,
            EnumSet.noneOf(EncounterImageExportFile.ExportOptions.class),
            reporter);

        try (ZipOutputStream zos = new ZipOutputStream(new ByteArrayOutputStream())) {
            assertDoesNotThrow(() -> sut.writeTo(zos));
        }

        // Hidden encounter must short-circuit before any annotation/media access.
        verify(hidden, never()).getAnnotations();
        // Visible encounter is examined for annotations (none here, so the inner loop is empty).
        verify(visible, times(1)).getAnnotations();
    }
}
