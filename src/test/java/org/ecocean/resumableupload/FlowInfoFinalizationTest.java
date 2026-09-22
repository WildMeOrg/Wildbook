package org.ecocean.resumableupload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Finalization renames the staging file to its final name. It used to derive that name by
 * chopping ".temp" off the staging path -- which, once the staging path is canonicalised, can
 * resolve to a name that does not end in ".temp" at all, putting the rename outside the root.
 * The final destination is validated up front and carried explicitly instead.
 */
class FlowInfoFinalizationTest {
    @TempDir Path tmp;

    private FlowInfo staged(File dir, String name, int chunkSize, long totalSize)
    throws IOException {
        FlowInfo info = new FlowInfo();

        info.flowChunkSize = chunkSize;
        info.flowTotalSize = totalSize;
        info.flowIdentifier = "id";
        info.flowFilename = name;
        info.flowRelativePath = name;
        info.flowFilePath = new File(dir, name + ".temp").getPath();
        info.finalFilePath = new File(dir, name).getPath();
        Files.writeString(new File(info.flowFilePath).toPath(), "x");
        return info;
    }

    @Test void finalizationRenamesToTheCarriedDestination() throws IOException {
        FlowInfo info = staged(tmp.toFile(), "photo.jpg", 5, 5L);

        info.uploadedChunks.add(new FlowInfo.flowChunkNumber(1));
        String finalPath = info.checkIfUploadFinished();

        assertNotNull(finalPath);
        assertEquals(new File(tmp.toFile(), "photo.jpg").getPath(), finalPath,
            "the final name must come from the validated destination, not from string surgery");
        assertTrue(new File(finalPath).exists());
    }

    @Test void finalizationDoesNotDeriveTheNameByTrimmingTheStagingPath() throws IOException {
        // staging path deliberately does NOT end in ".temp": trimming five characters would
        // produce a sibling path outside the intended name
        FlowInfo info = new FlowInfo();

        info.flowChunkSize = 5;
        info.flowTotalSize = 5L;
        info.flowIdentifier = "id";
        info.flowFilename = "photo.jpg";
        info.flowRelativePath = "photo.jpg";
        info.flowFilePath = new File(tmp.toFile(), "a").getPath();
        info.finalFilePath = new File(tmp.toFile(), "photo.jpg").getPath();
        Files.writeString(new File(info.flowFilePath).toPath(), "x");
        info.uploadedChunks.add(new FlowInfo.flowChunkNumber(1));

        assertEquals(new File(tmp.toFile(), "photo.jpg").getPath(), info.checkIfUploadFinished());
    }

    @Test void anIncompleteUploadIsNotFinalized() throws IOException {
        FlowInfo info = staged(tmp.toFile(), "photo.jpg", 5, 12L);

        info.uploadedChunks.add(new FlowInfo.flowChunkNumber(1));
        assertNull(info.checkIfUploadFinished(), "12 bytes at 5 per chunk needs three chunks");
    }

    @Test void exactMultipleNeedsEveryChunkAndNoMore() throws IOException {
        FlowInfo info = staged(tmp.toFile(), "photo.jpg", 5, 10L);

        info.uploadedChunks.add(new FlowInfo.flowChunkNumber(1));
        assertNull(info.checkIfUploadFinished());
        info.uploadedChunks.add(new FlowInfo.flowChunkNumber(2));
        assertNotNull(info.checkIfUploadFinished(), "10 bytes at 5 per chunk is exactly two");
    }

    @Test void zeroByteUploadCompletesOnItsSingleChunk() throws IOException {
        FlowInfo info = staged(tmp.toFile(), "empty.jpg", 5, 0L);

        info.uploadedChunks.add(new FlowInfo.flowChunkNumber(1));
        assertNotNull(info.checkIfUploadFinished(), "an empty file still sends one empty chunk");
    }
}
