package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;


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
        BufferedImage img = ImageFixtures.noise(W, H, 1787);
        valid.put("baseline.jpg", ImageFixtures.write(img, "jpeg", file("baseline.jpg"), false));
        valid.put("progressive.jpg", ImageFixtures.write(img, "jpeg", file("progressive.jpg"), true));
        valid.put("noise.png", ImageFixtures.write(img, "png", file("noise.png"), false));
        valid.put("noise.bmp", ImageFixtures.write(img, "bmp", file("noise.bmp"), false));
        valid.put("noise.gif", ImageFixtures.write(img, "gif", file("noise.gif"), false));
        valid.put("exif.jpg", ImageFixtures.withExifOrientation(valid.get("baseline.jpg"), file("exif.jpg"), 6));
        truncatedPng = ImageFixtures.truncate(valid.get("noise.png"), file("trunc.png"), 0.6);
        truncatedGif = ImageFixtures.truncate(valid.get("noise.gif"), file("trunc.gif"), 0.6);
        jpegMagic = ImageFixtures.jpegMagic(file("magic.jpg"));
        text = file("text.txt");
        Files.write(text.toPath(), "not an image ".repeat(200).getBytes("UTF-8"));
        for (File f : valid.values()) assertTrue(f.length() > 1000, f.getName() + " must exceed fastFileValidation's 1000-byte floor");
    }

    static File file(String name) {
        return dir.resolve(name).toFile();
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
