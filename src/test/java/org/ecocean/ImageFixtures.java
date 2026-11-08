package org.ecocean;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/** Test-only image fixtures for the bulk-import box tests: generated, deterministic, above 1000 bytes. */
public final class ImageFixtures {
    private ImageFixtures() {}

    /** A w x h RGB image of seeded random noise (compresses poorly, so every encoding is large). */
    public static BufferedImage noise(int w, int h, long seed) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(seed);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, rnd.nextInt(0xffffff));
        return img;
    }

    /** Writes {@code img} in {@code format} ("jpeg", "png", "bmp", "gif"), progressive JPEG when asked. */
    public static File write(BufferedImage img, String format, File f, boolean progressive) throws IOException {
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

    /**
     * A copy of a baseline JPEG with an APP1 Exif segment (little-endian TIFF, one IFD0 entry:
     * Orientation = {@code orientation}) inserted right after the SOI marker.
     */
    public static File withExifOrientation(File jpeg, File f, int orientation) throws IOException {
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
        Files.write(f.toPath(), out.toByteArray());
        return f;
    }

    /** The first {@code keep} fraction of {@code whole}'s bytes: header intact, pixel data cut. */
    public static File truncate(File whole, File f, double keep) throws IOException {
        byte[] src = Files.readAllBytes(whole.toPath());
        Files.write(f.toPath(), Arrays.copyOf(src, (int)(src.length * keep)));
        return f;
    }

    /** JPEG magic bytes followed by zeros: Tika calls it image/jpeg, ImageIO cannot read a header. */
    public static File jpegMagic(File f) throws IOException {
        byte[] magic = new byte[1504];
        magic[0] = (byte)0xFF; magic[1] = (byte)0xD8; magic[2] = (byte)0xFF; magic[3] = (byte)0xE0;
        Files.write(f.toPath(), magic);
        return f;
    }
}
