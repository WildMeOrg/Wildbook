package org.ecocean.api.submission;

import java.io.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionFilesTest {
    @TempDir Path root;
    static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out); return out.toByteArray();
    }
    @Test void storesExactNameChecksDigestAndRejectsTampering() throws Exception {
        SubmissionFiles files = new SubmissionFiles(root);
        JSONObject entry = files.write("image.png", new ByteArrayInputStream(png()), 10000);
        assertEquals("image.png", files.path(entry).getFileName().toString()); files.verify(entry);
        Files.write(files.path(entry), new byte[]{1});
        assertThrows(IOException.class, () -> files.verify(entry));
        files.remove(entry); assertEquals(0, root.toFile().list().length);
    }
    @Test void rejectsUnsafeNamesCorruptImagesAndActualByteOverflowWithoutOrphans() throws Exception {
        SubmissionFiles files = new SubmissionFiles(root);
        for (String name : new String[]{"../a.png", "a/b.png", "a\\b.png", ".", "cat picture.png", "é.png"})
            assertThrows(SubmissionException.class, () -> files.write(name, new ByteArrayInputStream(png()), 10000));
        assertThrows(SubmissionException.class, () -> files.write("a.png", new ByteArrayInputStream(png()), 1));
        assertThrows(SubmissionException.class, () -> files.write("a.png", new ByteArrayInputStream(new byte[]{1,2,3}), 10000));
        assertEquals(0, root.toFile().list().length);
    }
    @Test void rejectsEscapingSymlink() throws Exception {
        SubmissionFiles files = new SubmissionFiles(root); JSONObject entry = files.write("a.png", new ByteArrayInputStream(png()), 10000);
        Path path = files.path(entry); Files.delete(path); Files.createSymbolicLink(path, Path.of("/etc/hosts"));
        assertThrows(IOException.class, () -> files.path(entry));
    }
    @Test void multipartUnknownLengthOversizeAndMalformedImageHaveClientErrors() throws Exception {
        SubmissionFiles files = new SubmissionFiles(root);
        byte[] prefix = "--boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.png\"\r\nContent-Type: image/png\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        ByteArrayOutputStream body = new ByteArrayOutputStream(); body.write(prefix); body.write(png());
        body.write("\r\n--boundary--\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(413, assertThrows(SubmissionException.class, () -> files.receive(request(body.toByteArray()), 8)).status);
        body.reset(); body.write(prefix); body.write(java.util.Arrays.copyOf(png(), 40));
        body.write("\r\n--boundary--\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(422, assertThrows(SubmissionException.class, () -> files.receive(request(body.toByteArray()), 10000)).status);
        assertEquals(0, root.toFile().list().length);
    }
    private javax.servlet.http.HttpServletRequest request(byte[] bytes) throws Exception {
        javax.servlet.http.HttpServletRequest request = org.mockito.Mockito.mock(javax.servlet.http.HttpServletRequest.class);
        org.mockito.Mockito.when(request.getMethod()).thenReturn("POST");
        org.mockito.Mockito.when(request.getContentType()).thenReturn("multipart/form-data; boundary=boundary");
        org.mockito.Mockito.when(request.getContentLength()).thenReturn(-1);
        org.mockito.Mockito.when(request.getContentLengthLong()).thenReturn(-1L);
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        org.mockito.Mockito.when(request.getInputStream()).thenReturn(new javax.servlet.ServletInputStream() {
            public int read() { return input.read(); }
            public boolean isFinished() { return input.available() == 0; }
            public boolean isReady() { return true; }
            public void setReadListener(javax.servlet.ReadListener listener) {}
        });
        return request;
    }
    @Test void stagingRejectsBothDirectionsOfServedDirectoryOverlapAndProcessingIsBounded() {
        for (String served : new String[]{"/srv/webapps", "/srv/import", "/srv/uploads"}) {
            Path base = Path.of(served);
            assertThrows(SubmissionException.class, () -> SubmissionFiles.rejectOverlap(base.resolve("private"), base));
            assertThrows(SubmissionException.class, () -> SubmissionFiles.rejectOverlap(base.getParent(), base));
            SubmissionFiles.rejectOverlap(Path.of("/private-intake"), base);
        }
        try (SubmissionResources slot = SubmissionResources.acquire("one")) {
            assertEquals(429, assertThrows(SubmissionException.class, () -> SubmissionResources.acquire("one")).status);
            try (SubmissionResources second = SubmissionResources.acquire("two")) {
                assertEquals(429, assertThrows(SubmissionException.class, () -> SubmissionResources.acquire("three")).status);
            }
        }
        try (SubmissionResources slot = SubmissionResources.acquire("two")) { assertNotNull(slot); }
    }

}
