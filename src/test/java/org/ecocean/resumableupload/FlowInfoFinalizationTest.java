package org.ecocean.resumableupload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        Files.write(new File(info.flowFilePath).toPath(), new byte[(int)totalSize]);
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
        Files.write(new File(info.flowFilePath).toPath(), new byte[5]);
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

    @Test void finalizationDropsBytesLeftBehindByAnAbandonedLongerUpload() throws IOException {
        // staging files are opened without truncation, so if a shorter upload lands on a .temp
        // left by an abandoned longer one, the old file's tail would survive into the new image
        FlowInfo info = staged(tmp.toFile(), "photo.jpg", 5, 5L);
        Files.writeString(new File(info.flowFilePath).toPath(), "NEWBYOLDTAIL");

        info.uploadedChunks.add(new FlowInfo.flowChunkNumber(1));
        String finalPath = info.checkIfUploadFinished();

        assertNotNull(finalPath);
        assertEquals("NEWBY", Files.readString(new File(finalPath).toPath()),
            "the finished file must be exactly the declared size");
    }

    @Test void onlyOneCallerFinalizesAnUpload() throws IOException {
        // concurrent final chunks both see a complete set; exactly one may finalize and report it
        FlowInfo info = staged(tmp.toFile(), "photo.jpg", 5, 1L);

        info.uploadedChunks.add(new FlowInfo.flowChunkNumber(1));
        assertNotNull(info.checkIfUploadFinished());
        assertNull(info.checkIfUploadFinished(), "a second finalization must report nothing");
    }

    @Test void anUndersizedStagingFileIsNotFinalized() throws IOException {
        // every chunk being marked is not proof the bytes are all there: if the staging file is
        // shorter than declared, finalizing it would hand a truncated image to the importer
        FlowInfo info = staged(tmp.toFile(), "photo.jpg", 5, 5L);
        Files.write(new File(info.flowFilePath).toPath(), new byte[3]);

        info.uploadedChunks.add(new FlowInfo.flowChunkNumber(1));
        assertNull(info.checkIfUploadFinished(), "a short staging file must not be finalized");
        assertTrue(new File(info.flowFilePath).exists(), "and must be left where it is");
    }

    @Test void finalizedStateIsVisibleToLaterChunks() throws IOException {
        // lets a duplicate chunk that arrives after completion be acknowledged without recreating
        // a staging file that nothing will ever finalize
        FlowInfo info = staged(tmp.toFile(), "photo.jpg", 5, 1L);

        assertFalse(info.isFinalized());
        info.uploadedChunks.add(new FlowInfo.flowChunkNumber(1));
        info.checkIfUploadFinished();
        assertTrue(info.isFinalized());
    }

    @Test void destinationIsComparedAcrossRequests() {
        // two requests can share a staging key yet want different final names, e.g. through an
        // existing symlinked .temp; the second must not silently inherit the first's destination
        FlowInfo a = new FlowInfo();
        FlowInfo b = new FlowInfo();

        a.finalFilePath = "/up/x/a.jpg";
        b.finalFilePath = "/up/x/b.jpg";
        assertFalse(a.sameDestination(b));
        b.finalFilePath = "/up/x/a.jpg";
        assertTrue(a.sameDestination(b));
    }

    @Test void aWriteAfterFinalizationIsSkippedAndDoesNotRun() throws IOException {
        FlowInfo info = staged(tmp.toFile(), "photo.jpg", 5, 1L);
        info.uploadedChunks.add(new FlowInfo.flowChunkNumber(1));
        info.checkIfUploadFinished();

        boolean[] ran = { false };
        assertFalse(info.writeUnlessFinalized(() -> ran[0] = true),
            "a chunk arriving after finalization must be told so");
        assertFalse(ran[0], "and must not touch the filesystem at all");
    }

    @Test void finalizationWaitsForAChunkWriteAlreadyInProgress() throws Exception {
        // the gap Codex found: a duplicate that checked "not finalized" and was about to open the
        // file could recreate the .temp after finalization renamed it away. Finalization must not
        // be able to run while any chunk write is in flight.
        FlowInfo info = staged(tmp.toFile(), "photo.jpg", 5, 1L);
        info.uploadedChunks.add(new FlowInfo.flowChunkNumber(1));
        java.util.concurrent.CountDownLatch writing = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

        Thread writer = new Thread(() -> {
            try {
                info.writeUnlessFinalized(() -> {
                    writing.countDown();
                    try { release.await(); } catch (InterruptedException ex) {}
                });
            } catch (IOException ex) {}
        });
        writer.start();
        writing.await();

        java.util.concurrent.FutureTask<String> finalize =
            new java.util.concurrent.FutureTask<>(info::checkIfUploadFinished);
        new Thread(finalize).start();
        Thread.sleep(200);
        assertFalse(finalize.isDone(), "finalization must wait while a chunk write is in flight");

        release.countDown();
        assertNotNull(finalize.get(5, java.util.concurrent.TimeUnit.SECONDS));
        writer.join(5000);
    }

    @Test void chunkWritesDoNotBlockEachOther() throws Exception {
        // flow.js sends several chunks of one file at once; the lock must stay shared for writes
        FlowInfo info = staged(tmp.toFile(), "photo.jpg", 5, 10L);
        java.util.concurrent.CountDownLatch bothInside = new java.util.concurrent.CountDownLatch(2);

        Runnable w = () -> {
            try {
                info.writeUnlessFinalized(() -> {
                    bothInside.countDown();
                    try { bothInside.await(); } catch (InterruptedException ex) {}
                });
            } catch (IOException ex) {}
        };
        Thread a = new Thread(w);
        Thread b = new Thread(w);
        a.start();
        b.start();
        a.join(5000);
        b.join(5000);
        assertEquals(0L, bothInside.getCount(), "two chunk writes must be able to run at once");
    }
}
