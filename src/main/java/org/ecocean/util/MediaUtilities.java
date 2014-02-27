/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class providing centralized image-related services, such as image rescaling,
 * JPEG saving, thumbnail generation, etc.
 *
 * @author Giles Winstanley
 */
public final class MediaUtilities {
  /** SLF4J logger instance for writing log entries. */
  private static Logger log = LoggerFactory.getLogger(MediaUtilities.class);
  /** Regex pattern string suffix for matching image filenames (case-insensitive, capturing group). */
  public static final String REGEX_SUFFIX_FOR_IMAGES = "(?i:(jpe?g?|png|gif|tiff?|bmp))$";
  /** Regex pattern string suffix for matching image filenames (case-insensitive, capturing group). */
  public static final String REGEX_SUFFIX_FOR_WEB_IMAGES = "(?i:(jpe?g?|png|gif))$";
  /** Regex pattern string suffix for matching video filenames (case-insensitive, capturing group). */
  public static final String REGEX_SUFFIX_FOR_MOVIES = "(?i:(mp4|mpg|mov|wmv|avi|flv))$";
  /** Instance for writing JPEG images. */
  private static ImageWriter iwJPEG;
  /** Instance for writing JPEG images. */
  private static ImageWriteParam iwpJPEG;

  static {
    // Obtain ImageWriter instance for JPEG images.
    for (Iterator<ImageWriter> iter = ImageIO.getImageWritersByMIMEType("image/jpeg"); iter.hasNext();)
    {
      ImageWriter iw = iter.next();
      ImageWriteParam iwp = iw.getDefaultWriteParam();
      if (iwJPEG == null ||
          (!iwpJPEG.canWriteCompressed() && iwp.canWriteCompressed()) ||
          (!iwpJPEG.canWriteProgressive() && iwp.canWriteProgressive()))
      {
        iwJPEG = iw;
        iwpJPEG = iwp;
      }
    }
  }

  private MediaUtilities() {}

  /**
   * Checks filename extension for supported image type.
   * This method had been recreated here to allow static and centralized access;
   * original version requires a {@code Shepherd} instance.
   * @param filename filename of file to check
   * @return true if filename is support, false otherwise
   */
  public static boolean isAcceptableImageFile(String filename) {
    return (filename == null) ? false : filename.matches("^.+\\." + REGEX_SUFFIX_FOR_WEB_IMAGES);
  }

  /**
   * Checks filename extension for supported video type.
   * This method had been recreated here to allow static and centralized access;
   * original version requires a {@code Shepherd} instance.
   * @param filename filename of file to check
   * @return true if filename is support, false otherwise
   */
  public static boolean isAcceptableVideoFile(String filename) {
    return (filename == null) ? false : filename.matches("^.+\\." + REGEX_SUFFIX_FOR_MOVIES);
  }

  /**
   * Checks filename extension for supported image type.
   * @param file file to check
   * @return true if filename is support, false otherwise
   */
  public static boolean isAcceptableImageFile(File file) {
    return (file == null) ? false : isAcceptableImageFile(file.getName());
  }

  /**
   * Checks filename extension for supported video type.
   * @param file file to check
   * @return true if filename is support, false otherwise
   */
  public static boolean isAcceptableVideoFile(File file) {
    return (file == null) ? false : isAcceptableVideoFile(file.getName());
  }

  /**
   * Checks filename extension for supported media (photo/video) type.
   * @param filename filename of file to check
   * @return true if filename is support, false otherwise
   */
  public static boolean isAcceptableMediaFile(String filename) {
    return isAcceptableImageFile(filename) || isAcceptableVideoFile(filename);
  }

  /**
   * Checks filename extension for supported media (photo/video) type.
   * @param file file to check
   * @return true if filename is support, false otherwise
   */
  public static boolean isAcceptableMediaFile(File file) {
    return isAcceptableImageFile(file) || isAcceptableVideoFile(file);
  }

  /**
   * Loads the specified image from the specified file.
   * @param f file to which to save image
   */
  public static BufferedImage loadImage(File f) throws IOException {
    if (f == null)
      throw new NullPointerException("Invalid (null) file specified");
    else if (!f.exists())
      throw new FileNotFoundException(String.format("File %s doesn't exist", f.getAbsolutePath()));
    else if (f.isDirectory())
      throw new FileNotFoundException(String.format("%s is a folder", f.getAbsolutePath()));

    return ImageIO.read(f);
  }

  /**
   * Saves the specified image to the specified file.
   * @param img image to save in JPEG format
   * @param f file to which to save image
   * @param overwrite whether to overwrite file if it already exists
   * @param quality JPEG quality (0-1)
   * @param progressive whether to save as a progressive JPEG
   */
  public static void saveImageJPEG(BufferedImage img, File f, boolean overwrite, float quality, boolean progressive) throws IOException {
    if (img == null)
      throw new NullPointerException("Invalid (null) image specified");
    if (f == null)
      throw new NullPointerException("Invalid (null) file specified");
    else if (f.exists())
      throw new IllegalArgumentException(String.format("File %s already exists", f.getAbsolutePath()));
    if (quality < 0f || quality > 1f) {
      throw new IllegalArgumentException("Invalid JPEG quality specified (0 <= quality <= 1)");
    }
    if (f.exists()) {
      if (overwrite) {
        if (!f.delete()) {
          throw new IOException("Unable to delete existing file");
        }
      } else {
        throw new IOException("File already exists");
      }
    }
    if (iwJPEG == null) {
      throw new IOException("No JPEG ImageWriter available");
    }
    synchronized(iwJPEG) {
      iwpJPEG.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      iwpJPEG.setCompressionQuality(quality);
      iwpJPEG.setProgressiveMode(progressive ? ImageWriteParam.MODE_DEFAULT : ImageWriteParam.MODE_DISABLED);

      ImageOutputStream ios = null;
      try {
        ios = ImageIO.createImageOutputStream(f);
        iwJPEG.setOutput(ios);
        iwJPEG.write(null, new IIOImage(img, null, null), iwpJPEG);
      } finally {
        try {
          if (ios != null) {
            ios.flush();
            ios.close();
          }
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    }
  }

  /**
   * Convenience method that returns a scaled instance of the
   * provided {@code BufferedImage}.
   *
   * @param img the original image to be scaled
   * @param targetWidth the desired width of the scaled instance, in pixels
   * @param targetHeight the desired height of the scaled instance, in pixels
   * @param hint one of the rendering hints that corresponds to
   *    {@link java.awt.RenderingHints.KEY_INTERPOLATION} (e.g.
   *    {@link java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR},
   *    {@link java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR},
   *    {@link java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC})
   * @return a scaled version of the original {@code BufferedImage}
   */
  public static BufferedImage rescaleImage(BufferedImage img, int targetWidth, int targetHeight, Object hint) {
    if (img == null)
      throw new NullPointerException("Invalid (null) image specified");
    int type = (img.getColorModel().getTransparency() == Transparency.OPAQUE)
            ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
    BufferedImage tmp = new BufferedImage(targetWidth, targetHeight, type);
    Graphics2D g2 = tmp.createGraphics();
    if (hint != null)
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
    g2.drawImage(img, 0, 0, targetWidth, targetHeight, null);
    g2.dispose();
    return tmp;
  }

  /**
   * Convenience method that returns a scaled instance of the
   * provided {@code BufferedImage} with a text overlay.
   * This is a simple implementation which was created solely for the purpose
   * of reproducing thumbnail images with a copyright message overlay,
   * as done by the deprecated Sunwest Technologies Dynamic Images library.
   */
  public static BufferedImage rescaleImageWithTextOverlay(BufferedImage img, int tw, int th, String text) {
    int type = (img.getColorModel().getTransparency() == Transparency.OPAQUE)
            ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
    BufferedImage tmp = new BufferedImage(tw, th, type);
    Graphics2D g2 = tmp.createGraphics();
    int yPos = th / 3;
    // RenderingHints for determining image quality.
    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

    // White translucent overlay over whole image.
    Rectangle2D rectWash = new Rectangle2D.Float(0, 0, tw, th);
    Paint paintWash = new Color(0xFF, 0xFF, 0xFF, 0x4D);

    // Text overlay.
    Font font = new Font("Arial", Font.BOLD, 11);
    FontMetrics fm = g2.getFontMetrics(font);
    Paint paintText = Color.black;
    // Strip behind text.
    Paint paintStrip = new Color(0x99, 0xCC, 0xFF, 0x4D);
    Rectangle2D rectStrip = new Rectangle2D.Float(0, yPos, tw, 13);
    Rectangle2D textBounds = fm.getStringBounds(text, g2);
    if (textBounds.getWidth() > tw)
      log.warn("Text overlay too long for image width.");

    g2.drawImage(img, 0, 0, tw, th, null);
    g2.setPaint(paintWash);
    g2.fill(rectWash);
    g2.setPaint(paintStrip);
    g2.fill(rectStrip);
    g2.setPaint(paintText);
    g2.setFont(font);
    g2.drawString(text, 4, yPos + fm.getHeight() - fm.getMaxDescent());
    g2.dispose();
    return tmp;
  }
}
