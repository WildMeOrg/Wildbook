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

package com.reijns.I3S;

//

import java.io.*;
import java.util.Collection;


public class FgpFileOpen {

  /**
   * Public method to read in all the available FGP files in the supplied
   * PathName.  If PathName is a folder, it will check all files in the folder
   * for FGP files and all subfolders as well.  Any FGP files will be loaded
   * as FingerPoint objects into the fp Collection.
   *
   * @param PathName String : Path to open or search (file or folder)
   * @param fp       Collection : The collection which to add the FingerPring object
   */
  // Collection should be Collection<FingerPrint> fp for Java 1.5
  public void read(String PathName, Collection fp) {
    if (fp == null) {
      throw new NullPointerException("FingerPrint collection is not initialized.");
    }

    File f = new File(PathName);
    processFile(f, fp);
  }

  /**
   * Checks the path provided for FGP files. If "f" is a file, try to open it
   * as a FGP file. If "f" is a folder, then check for any FGP files in the
   * folder.
   *
   * @param f  File : The File to test
   * @param fp Collection : The collection which to add the FingerPring object
   */
  // Collection should be Collection<FingerPrint> fpa for Java 1.5
  private void processFile(File f, Collection fp) {
    //
    if (f.exists()) {
      if (f.isFile()) {
        OpenFgpFile(f, fp);
      } else if (f.isDirectory()) {
        GetFgpFiles(f, fp);
      }
    }
  }

  /**
   * This method is called when "f" is a folder.  It then checks all the
   * files in "f" and any subfolders in "f" for FGP files by recursive
   * call to processFile.  See also the processFile method.
   *
   * @param f  File : The File to test
   * @param fp Collection : The collection which to add the FingerPring object
   *           that will be created and filled with spots if the file is a FGP file.
   */
  // Collection should be Collection<FingerPrint> fpa for Java 1.5
  private void GetFgpFiles(File f, Collection fp) {
    File[] fa = f.listFiles();
    for (int i = 0; i < fa.length; i++) {
      processFile(fa[i], fp);
    }
  }

  /**
   * Opens a file, checks to see if it is a FGP file, and if it is, loads the
   * spots point pattern and addes it as a FingerPrint object to Collection fp.
   *
   * @param f  File : The File object to open
   * @param fp Collection : The collection which to add the FingerPring object
   *           that will be created and filled with spots if the file is a FGP file.
   */
  // Collection should be Collection<FingerPrint> fp for Java 1.5
  @SuppressWarnings("unchecked")
  private void OpenFgpFile(File f, Collection fp) {
    if (f.canRead()) {
      try {
        FileInputStream fileStream = new FileInputStream(f);
        try {
          DataInputStream data = new DataInputStream(new BufferedInputStream(fileStream));

          try {
            if (checkFileType(data)) {
              getPoints(fp, data, f.getName());
            }
          } catch (IOException e) {
            System.err.println("Caught IOException reading file: " + e.getMessage());
          } finally {
            closeFile(data);
          }
        } finally {
          closeFile(fileStream);
        }
      } catch (FileNotFoundException e) {
        System.err.println("Caught FileNotFoundException: " + e.getMessage());
      } catch (Exception e) {
        System.err.println(e.getLocalizedMessage());
      }
    }
  }

  /**
   * Checks to see if the file is of the FGP file type by checking the file
   * header.
   *
   * @param data DataInputStream : The file stream opened.
   * @return boolean : Returns true if the file header starts with "If01"
   * @throws IOException
   */
  private boolean checkFileType(DataInputStream data) throws IOException {
    byte[] b = new byte[4];
    // read in first 4 bytes, and check file type.
    data.read(b, 0, 4);
    if (((char) b[0] == 'I' && (char) b[1] == 'f' && (char) b[2] == '0' && (char) b[3] == '1') == false) {
      return false;
    }
    return true;
  }

  /**
   * Gets the points from the current FGP file.
   *
   * @param fpa   Collection : A new FingerPrint will be added to this collection
   *              with the loaded points.
   * @param data  DataInputStream : The file to check
   * @param fname String : The name to attach to the FingerPrint file added
   *              to the fpa Collection for identification purposes
   * @throws IOException
   */
  // Collection should be Collection<FingerPrint> fp for Java 1.5
  @SuppressWarnings("unchecked")
  private void getPoints(Collection fpa, DataInputStream data, String fname) throws IOException {
    // TODO do Endian check to all numbers.
    // read in three control points (dorsal and pelvic fins).
    Point2D[] controlPoints = new Point2D[3];
    loadSpots(data, 3, controlPoints);

    int n = data.readInt();

    Point2D[] spots = new Point2D[n];
    loadSpots(data, n, spots);

    Point2D[] normalizedSpots = new Point2D[n];
    loadSpots(data, n, normalizedSpots);

    FingerPrint fp = new FingerPrint(spots, normalizedSpots, controlPoints);
    fp.name = fname;
    fpa.add(fp);
  }

  /**
   * Reads in all the spots (x,y coordinate points) from a file.  This method
   * is called from getPoints.
   *
   * @param data  DataInputStream : The file to check
   * @param n     int : The number of points to retrieve
   * @param spots Point2D[] : The array of Point2D objects to fill with spots
   * @throws IOException
   */
  private void loadSpots(DataInputStream data, int n, Point2D[] spots) throws IOException {
    for (int i = 0; i < n; i++) {
      double x = data.readDouble();
      double y = data.readDouble();
      spots[i] = new Point2D(x, y);
    }
  }

  /**
   * Closes the FGP opened as any InputStream decendent.
   *
   * @param data
   */
  private void closeFile(InputStream data) {
    try {
      if (data != null) {
        data.close();
      }
    } catch (IOException e) {
      System.err.println("Caught IOException closing file: " + e.getMessage());
    }
  }
}
