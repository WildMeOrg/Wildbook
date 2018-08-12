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

package org.ecocean.mmutil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Giles Winstanley
 */
public class FileUtilities {
  /** SLF4J logger instance for writing log entries. */
  private static final Logger log = LoggerFactory.getLogger(FileUtilities.class);

  private FileUtilities() {}

  /**
   * Loads the contents of a specified file.
   * @param f File to load
   * @return A byte array containing the contents of the specified {@code File}.
   */
  public static byte[] loadFile(File f) throws IOException {
    if (!f.exists()) {
      throw new FileNotFoundException();
    }
    FileInputStream fis = null;
    try {
      ByteArrayOutputStream bao = new ByteArrayOutputStream();
      fis = new FileInputStream(f);
      byte[] b = new byte[4096];
      int n;
      while ((n = fis.read(b)) != -1) {
        bao.write(b, 0, n);
      }
      return bao.toByteArray();
    } finally {
      if (fis != null) {
        try {
          fis.close();
        } catch (IOException iox) {
          log.warn(iox.getMessage(), iox);
        }
      }
    }
  }

  /**
   * Copies a file to another location.
   * @param src source file
   * @param dst destination file
   * @throws IOException if there is a problem copying the file
   */
  public static void copyFile(File src, File dst) throws IOException {
    if (src == null)
      throw new NullPointerException("Invalid source file specified: null");
    if (dst == null)
      throw new NullPointerException("Invalid destination file specified: null");
    if (!src.exists())
      throw new IOException("Invalid source file specified: " + src.getAbsolutePath());
    if (dst.exists())
      throw new IOException("Destination file already exists: " + dst.getAbsolutePath());
    BufferedInputStream in = null;
    BufferedOutputStream out = null;
    try {
      in = new BufferedInputStream(new FileInputStream(src));
      out = new BufferedOutputStream(new FileOutputStream(dst));
      byte[] b = new byte[4096];
      int len = 0;
      while ((len = in.read(b)) != -1)
        out.write(b, 0, len);
      out.flush();
    } finally {
      if (out != null) {
        try { out.close(); }
        catch (IOException ex) { log.warn(ex.getMessage(), ex); }
      }
      if (in != null) {
        try { in.close(); }
        catch (IOException ex) { log.warn(ex.getMessage(), ex); }
      }
    }
  }

  /**
   * Downloads the byte contents of a URL to a specified file.
   * @param url URL to download
   * @param file File to which to write downloaded data
   * @throws IOException
   */
  public static void downloadUrlToFile(URL url, File file) throws IOException {
    BufferedInputStream is = null;
    BufferedOutputStream os = null;
    try {
      is = new BufferedInputStream(url.openStream());
      os = new BufferedOutputStream(new FileOutputStream(file));
      byte[] b = new byte[4096];
      int len = -1;
      while ((len = is.read(b)) != -1)
        os.write(b, 0, len);
      os.flush();
    } finally {
      if (os != null)
        os.close();
      if (is != null)
        is.close();
    }
  }
}
