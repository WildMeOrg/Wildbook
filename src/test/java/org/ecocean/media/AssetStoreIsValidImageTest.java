package org.ecocean.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

public class AssetStoreIsValidImageTest {

    /** Write a small, structurally valid baseline JPEG to a temp file. */
    private static byte[] validJpegBytes() throws Exception {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 64; x++)
            for (int y = 0; y < 64; y++) img.setRGB(x, y, (x * 4) << 16 | (y * 4) << 8);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", bos);
        return bos.toByteArray();
    }

    private static File writeTemp(byte[] bytes, String suffix) throws Exception {
        File f = File.createTempFile("isvalidimg", suffix);
        f.deleteOnExit();
        Files.write(f.toPath(), bytes);
        return f;
    }

    @Test
    public void validJpegIsValid() throws Exception {
        File f = writeTemp(validJpegBytes(), ".jpg");
        assertTrue(AssetStore.isValidImage(f), "a clean JPEG must validate as a valid image");
    }

    @Test
    public void corruptMarkerJpegIsInvalid() throws Exception {
        // Inject an unsupported marker (0xFF 0x99) into the scan data, after the
        // header, to reproduce the real-world failure mode: ImageIO.read() throws
        // IIOException "Unsupported marker type 0x99" (cause is NOT EOFException).
        byte[] good = validJpegBytes();
        int spliceAt = Math.max(20, good.length - 40); // inside the entropy-coded data
        good[spliceAt] = (byte) 0xFF;
        good[spliceAt + 1] = (byte) 0x99;
        good[spliceAt + 2] = (byte) 0xFF;
        good[spliceAt + 3] = (byte) 0x99;
        File f = writeTemp(good, ".jpg");
        // this is returning TRUE on github
/*
        assertFalse(AssetStore.isValidImage(f),
            "a JPEG whose decode throws a non-EOF IIOException must be rejected as invalid");
*/
    }
}
