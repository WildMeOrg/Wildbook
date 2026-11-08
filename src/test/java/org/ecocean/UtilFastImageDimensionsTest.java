package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

import org.ecocean.media.AssetStore;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Bulk-import boxes PR B, task 4 (spec 4.2): Util.fastImageDimensions reads width and height from
 * the image header alone, on the raw pixel grid that AssetStore.extractMetadataAttributes stores
 * (both ignore an EXIF orientation tag), for every format a stored MediaAsset can have; it
 * returns null when the header cannot be read.
 */
class UtilFastImageDimensionsTest {
    static final int W = 200, H = 150;
    @TempDir static Path dir;
    static final Map<String, File> valid = new LinkedHashMap<String, File>();   // the six generated fixtures
    static File truncatedPng, truncatedGif, jpegMagic, text;
    static final File imageOk0 = new File("src/test/bulk-images/image-ok-0.jpg");

    @BeforeAll static void fixtures() throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(1787);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) img.setRGB(x, y, rnd.nextInt(0xffffff));
        valid.put("baseline.jpg", write(img, "jpeg", "baseline.jpg", false));
        valid.put("progressive.jpg", write(img, "jpeg", "progressive.jpg", true));
        valid.put("noise.png", write(img, "png", "noise.png", false));
        valid.put("noise.bmp", write(img, "bmp", "noise.bmp", false));
        valid.put("noise.gif", write(img, "gif", "noise.gif", false));
        valid.put("exif.jpg", withExifOrientation(valid.get("baseline.jpg"), "exif.jpg", 6));
        truncatedPng = truncate(valid.get("noise.png"), "trunc.png", 0.6);
        truncatedGif = truncate(valid.get("noise.gif"), "trunc.gif", 0.6);
        byte[] magic = new byte[1504];
        magic[0] = (byte)0xFF; magic[1] = (byte)0xD8; magic[2] = (byte)0xFF; magic[3] = (byte)0xE0;
        jpegMagic = dir.resolve("magic.jpg").toFile();
        Files.write(jpegMagic.toPath(), magic);
        text = dir.resolve("text.txt").toFile();
        Files.write(text.toPath(), "not an image ".repeat(200).getBytes("UTF-8"));
        for (File f : valid.values()) assertTrue(f.length() > 1000, f.getName() + " must exceed fastFileValidation's 1000-byte floor");
    }

    static File write(BufferedImage img, String format, String name, boolean progressive) throws IOException {
        File f = dir.resolve(name).toFile();
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (progressive) param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(f)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return f;
    }

    // a copy of a baseline JPEG with an APP1 Exif segment (little-endian TIFF, one IFD0 entry:
    // Orientation = value) inserted right after the SOI marker
    static File withExifOrientation(File jpeg, String name, int orientation) throws IOException {
        byte[] src = Files.readAllBytes(jpeg.toPath());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(src, 0, 2);   // SOI
        byte[] app1 = {
            (byte)0xFF, (byte)0xE1, 0x00, 0x22,                 // APP1, length 34 (includes these two bytes)
            'E', 'x', 'i', 'f', 0, 0,                           // Exif header
            'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00,       // TIFF header, little-endian, IFD0 at offset 8
            0x01, 0x00,                                         // one entry
            0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00,     // tag 0x0112 Orientation, SHORT, count 1
            (byte)orientation, 0x00, 0x00, 0x00,                // value
            0x00, 0x00, 0x00, 0x00                              // no next IFD
        };
        out.write(app1);
        out.write(src, 2, src.length - 2);
        File f = dir.resolve(name).toFile();
        Files.write(f.toPath(), out.toByteArray());
        return f;
    }

    static File truncate(File whole, String name, double keep) throws IOException {
        byte[] src = Files.readAllBytes(whole.toPath());
        File f = dir.resolve(name).toFile();
        Files.write(f.toPath(), Arrays.copyOf(src, (int)(src.length * keep)));
        return f;
    }

    @Test void readsWidthAndHeightOfEveryStoredFormatFromTheHeader() {
        for (Map.Entry<String, File> e : valid.entrySet()) {
            assertArrayEquals(new int[] { W, H }, Util.fastImageDimensions(e.getValue()), e.getKey());
            assertTrue(Util.fastFileValidation(e.getValue()), e.getKey());
            assertTrue(AssetStore.isValidImage(e.getValue()), e.getKey());
        }
    }

    @Test void agreesWithTheStoredDimensionsIncludingAnExifRotatedJpeg() throws Exception {
        Map<String, File> seven = new LinkedHashMap<String, File>(valid);
        seven.put("image-ok-0.jpg", imageOk0);
        for (Map.Entry<String, File> e : seven.entrySet()) {
            int[] dims = Util.fastImageDimensions(e.getValue());
            assertNotNull(dims, e.getKey());
            JSONObject md = AssetStore.extractMetadataAttributes(e.getValue());
            assertEquals(dims[0], (int)md.getDouble("width"), e.getKey() + " width");
            assertEquals(dims[1], (int)md.getDouble("height"), e.getKey() + " height");
        }
        assertArrayEquals(new int[] { 4608, 3456 }, Util.fastImageDimensions(imageOk0));
        // the orientation tag is real (the EXIF reader sees it) and ignored by both ImageIO readers
        Metadata meta = ImageMetadataReader.readMetadata(valid.get("exif.jpg"));
        Directory exif = meta.getDirectory(ExifIFD0Directory.class);
        assertNotNull(exif, "the injected APP1 segment is a readable Exif IFD0");
        assertEquals(6, exif.getInt(ExifIFD0Directory.TAG_ORIENTATION));
        assertArrayEquals(new int[] { W, H }, Util.fastImageDimensions(valid.get("exif.jpg")));
    }

    @Test void truncatedFilesStillYieldHeaderDimensionsButFailFullDecode() {
        for (File f : new File[] { truncatedPng, truncatedGif }) {
            assertArrayEquals(new int[] { W, H }, Util.fastImageDimensions(f), f.getName());
            assertTrue(Util.fastFileValidation(f), f.getName());
            assertFalse(AssetStore.isValidImage(f), f.getName() + " must fail the predicate MediaAsset creation uses");
        }
    }

    @Test void unreadableHeadersAndMissingFilesGiveNull() {
        assertTrue(Util.fastFileValidation(jpegMagic), "Tika sniffs image/jpeg from the magic bytes");
        assertNull(Util.fastImageDimensions(jpegMagic), "ImageIO cannot read a header from the magic bytes alone");
        assertNull(Util.fastImageDimensions(new File(dir.toFile(), "nope.jpg")));
        assertNull(Util.fastImageDimensions(text));
        assertNull(Util.fastImageDimensions(null));
    }
}
