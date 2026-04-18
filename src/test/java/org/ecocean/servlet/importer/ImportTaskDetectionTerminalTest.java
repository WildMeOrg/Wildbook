package org.ecocean.servlet.importer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;

import org.ecocean.media.MediaAsset;
import org.junit.jupiter.api.Test;

class ImportTaskDetectionTerminalTest {

    @Test void allCompleteIsTerminal() {
        assertTrue(taskWithAssets(asset("complete"), asset("complete")).isAllDetectionTerminal());
    }

    @Test void allErrorIsTerminal() {
        assertTrue(taskWithAssets(asset("error"), asset("error")).isAllDetectionTerminal());
    }

    @Test void mixedTerminalIsTerminal() {
        assertTrue(taskWithAssets(asset("complete"), asset("error"), asset("pending"))
            .isAllDetectionTerminal());
    }

    @Test void processingIsNotTerminal() {
        assertFalse(taskWithAssets(asset("complete"), asset("processing"))
            .isAllDetectionTerminal());
    }

    @Test void initiatedIsNotTerminal() {
        assertFalse(taskWithAssets(asset("complete"), asset("initiated"))
            .isAllDetectionTerminal());
    }

    @Test void nullStatusIsNotTerminal() {
        assertFalse(taskWithAssets(asset("complete"), asset(null)).isAllDetectionTerminal());
    }

    @Test void noAssetsIsTerminal() {
        ImportTask it = spy(new ImportTask());
        doReturn(Collections.emptyList()).when(it).getMediaAssets();
        assertTrue(it.isAllDetectionTerminal());
    }

    @Test void nullAssetsIsTerminal() {
        ImportTask it = spy(new ImportTask());
        doReturn(null).when(it).getMediaAssets();
        assertTrue(it.isAllDetectionTerminal());
    }

    private static MediaAsset asset(String status) {
        MediaAsset a = mock(MediaAsset.class);
        when(a.getDetectionStatus()).thenReturn(status);
        return a;
    }

    private static ImportTask taskWithAssets(MediaAsset... assets) {
        ImportTask it = spy(new ImportTask());
        doReturn(Arrays.asList(assets)).when(it).getMediaAssets();
        return it;
    }
}
