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

package org.ecocean.interconnect;
/*******************************************************************************
 *   Changes since 1.0:                                                         *
 *   In 1.1 a slightly different approach is chosen. Instead of a standard     *
 *   comparison space each known individual is now mapped onto the space of    *
 *   the unknown individual. As a consequence it was necessary to give the c++ *
 *   code information about the reference points and to exchange the original  *
 *   spots of the unknown individual instead of the transformed spots.         *
 *                                                                             *
 *   Changes since 1.1:                                                        *
 *   Considerable changes in the GUI (zoom functionality, toolbar). Relevant   *
 *   controls and information for the user are now displayed in the toolbar.   *
 *   First step made towards multi-threaded searching using the SwingWorker    *
 *   class. This was also required for display of the progress bar.            * 
 *******************************************************************************/

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.print.Printable;
import java.io.*;


class FingerPrint {
  private Point2D spots[];
  private Point2D normspots[];
  private Point2D refPoint1;
  private Point2D refPoint2;
  private Point2D refPoint3;
  private StringBuffer comment = null;
  private String filename;
  private int spotcnt;

  public FingerPrint() {
    spotcnt = 0;
    spots = new Point2D[SharkPanel.MAX_SPOT_CNT * 2];
    normspots = new Point2D[SharkPanel.MAX_SPOT_CNT * 2];

    for (int i = 0; i < SharkPanel.MAX_SPOT_CNT * 2; i++) {
      spots[i] = new Point2D();
      normspots[i] = new Point2D();
    }
    comment = new StringBuffer();
    refPoint1 = new Point2D();
    refPoint2 = new Point2D();
    refPoint3 = new Point2D();

    filename = null;
  }

  public boolean read(String fname, StringBuffer mess) {
    try {
      filename = fname;
      DataInputStream in = new DataInputStream(new FileInputStream(filename));
      byte id[] = new byte[4];

      in.read(id, 0, 4);
      int version = versionOk(id);

      if (version != 0 && version != 1) {
        mess.append("Unknown type of fingerprint file " + filename);
        return false;
      }

      refPoint1.x = in.readDouble();
      refPoint1.y = in.readDouble();
      refPoint2.x = in.readDouble();
      refPoint2.y = in.readDouble();
      refPoint3.x = in.readDouble();
      refPoint3.y = in.readDouble();

      spotcnt = in.readInt();

      for (int i = 0; i < spotcnt; i++) {
        spots[i].x = in.readDouble();
        spots[i].y = in.readDouble();
      }
      for (int i = 0; i < spotcnt; i++) {
        normspots[i].x = in.readDouble();
        normspots[i].y = in.readDouble();
      }
      if (version > 0) {
        int strlen = in.readInt();
        byte[] buf = new byte[strlen];
        in.read(buf, 0, strlen);
        comment = new StringBuffer(new String(buf));
      } else
        comment = new StringBuffer();

      in.close();
    } catch (FileNotFoundException fnf) {
      reset();
    } catch (IOException e) {
      reset();
      mess.append("Error while reading from " + filename);
      return false;
    }

    return true;
  }

  public boolean write(StringBuffer mess) {
    if (filename == null) {
      mess.append("Invalid file name.\nNo data has been written.");
      return false;
    }
    if (!refPointsOk()) {
      mess.append("Point out the control points first.\nNo data has been written.");
      return false;
    }

    if (spotcnt < SharkPanel.MIN_SPOT_CNT) {
      mess.append("Insufficient input. At least " + SharkPanel.MIN_SPOT_CNT + " spots are required.\nNo data has been written.");
      return false;
    }

    // doAffine();

    try {
      DataOutputStream out = new DataOutputStream(new FileOutputStream(filename));

      byte id[] = new byte[4];
      id[0] = 'I';
      id[1] = 'f';
      id[2] = '2';
      id[3] = '0';
      out.write(id, 0, 4);

      out.writeDouble(refPoint1.x);
      out.writeDouble(refPoint1.y);
      out.writeDouble(refPoint2.x);
      out.writeDouble(refPoint2.y);
      out.writeDouble(refPoint3.x);
      out.writeDouble(refPoint3.y);

      out.writeInt(spotcnt);

      for (int i = 0; i < spotcnt; i++) {
        out.writeDouble(spots[i].x);
        out.writeDouble(spots[i].y);
      }
      // for backwards compatibility only
      for (int i = 0; i < spotcnt; i++) {
        out.writeDouble(normspots[i].x);
        out.writeDouble(normspots[i].y);
      }

      out.writeInt(comment.length());
      out.write(comment.toString().getBytes(), 0, comment.length());
      out.close();
    } catch (FileNotFoundException fnf) {
      mess.append("Could not open " + filename + " for writing.");
      return false;
    } catch (IOException e) {
      mess.append("Error while writing to " + filename);
      return false;
    }

    return true;
  }

  public void add(double x, double y) {
    if (refPoint1.empty()) {
      refPoint1.x = x;
      refPoint1.y = y;
    } else if (refPoint2.empty()) {
      refPoint2.x = x;
      refPoint2.y = y;
    } else if (refPoint3.empty()) {
      refPoint3.x = x;
      refPoint3.y = y;
    } else {
      spots[spotcnt].x = x;
      spots[spotcnt].y = y;
      spotcnt++;
    }
  }

  public void removeNearest(double x, double y) {
    double mindist = Double.POSITIVE_INFINITY;
    int index = -1;

    for (int i = 0; i < spotcnt; i++) {
      double dist = (x - spots[i].x) * (x - spots[i].x) + (y - spots[i].y) * (y - spots[i].y);
      if (dist < mindist) {
        mindist = dist;
        index = i;
      }
    }

    double dist1 = (x - refPoint1.x) * (x - refPoint1.x) + (y - refPoint1.y) * (y - refPoint1.y);
    double dist2 = (x - refPoint2.x) * (x - refPoint2.x) + (y - refPoint2.y) * (y - refPoint2.y);
    double dist3 = (x - refPoint3.x) * (x - refPoint3.x) + (y - refPoint3.y) * (y - refPoint3.y);
    Point2D tmp = refPoint1;
    if (dist2 < dist1) {
      dist1 = dist2;
      tmp = refPoint2;
    }
    if (dist3 < dist1) {
      dist1 = dist3;
      tmp = refPoint3;
    }

    if (dist1 < mindist)
      tmp.reset();
    else {
      if (spotcnt == 0 || index == -1)
        return;
      for (int i = index; i < spotcnt - 1; i++) {
        spots[i].x = spots[i + 1].x;
        spots[i].y = spots[i + 1].y;
      }
      spotcnt--;
    }
  }

  public void paint(Graphics2D g, double factor, int xOffset, int yOffset, boolean dotSelection,
                    String control1, String control2, String control3) {
    g.setColor(Color.red);
    g.setFont(new Font("Arial", Font.PLAIN, 12));
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    if (refPoint1.empty() == false) {
      g.drawString(control1, (int) (refPoint1.x * factor) + 10 + xOffset, (int) (refPoint1.y * factor) - 20 + yOffset);
      g.drawOval((int) (refPoint1.x * factor) - 10 + xOffset, (int) (refPoint1.y * factor) - 10 + yOffset, 20, 20);
      g.fill(new Rectangle2D.Double((refPoint1.x * factor) - 1 + xOffset, (refPoint1.y * factor) - 1 + yOffset, 3, 3));
    } else if (dotSelection) {
      g.drawString("Point out " + control1, 100 + xOffset, 100 + yOffset);
    }
    if (refPoint2.empty() == false) {
      g.drawString(control2, (int) (refPoint2.x * factor) + 10 + xOffset, (int) (refPoint2.y * factor) - 20 + yOffset);
      g.drawOval((int) (refPoint2.x * factor) - 10 + xOffset, (int) (refPoint2.y * factor) - 10 + yOffset, 20, 20);
      g.fill(new Rectangle2D.Double(refPoint2.x * factor - 1 + xOffset, refPoint2.y * factor - 1 + yOffset, 3, 3));
    } else if (!refPoint1.empty() && dotSelection) {
      g.drawString("Point out " + control2, 100 + xOffset, 100 + yOffset);
    }
    if (refPoint3.empty() == false) {
      g.drawString(control3, (int) (refPoint3.x * factor) + 10 + xOffset, (int) (refPoint3.y * factor) + 20 + yOffset);
      g.drawOval((int) (refPoint3.x * factor) - 10 + xOffset, (int) (refPoint3.y * factor) - 10 + yOffset, 20, 20);
      g.fill(new Rectangle2D.Double(refPoint3.x * factor - 1 + xOffset, refPoint3.y * factor - 1 + yOffset, 3, 3));
    } else if (!refPoint1.empty() && !refPoint2.empty() && dotSelection) {
      g.drawString("Point out " + control3, 100 + xOffset, 100 + yOffset);
    }

    for (int i = 0; i < spotcnt; i++)
      g.fill(new Rectangle2D.Double(spots[i].x * factor - 1 + xOffset, spots[i].y * factor - 1 + yOffset, 3, 3));
  }

  public int print(Graphics2D g2, double scalex, double scaley, int xOffset, int yOffset, int imgHeight) {
    try {
      g2.setColor(Color.red);
      for (int i = 0; i < spotcnt; i++)
        g2.fill(new Rectangle2D.Double(spots[i].x * scalex - 1 + xOffset, spots[i].y * scalex - 1 + yOffset, 3, 3));

      g2.setColor(Color.black);
      g2.setFont(new Font("Arial", Font.PLAIN, 24));
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      if (filename != null) {
        int index = filename.lastIndexOf('\\');
        g2.drawString(filename.substring(index + 1), xOffset, yOffset - 36);
      }
      g2.setFont(new Font("Arial", Font.PLAIN, 12));
      g2.setColor(Color.black);

      printComment(g2, xOffset, yOffset + 48 + (int) (imgHeight * scaley));
    } catch (Exception exc) {
      JOptionPane.showMessageDialog(null, "An unexpected error occured while printing (" + exc.toString() + ").");
    }

    return Printable.PAGE_EXISTS;
  }

  public void printComment(Graphics2D g, int x, int y) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    String c = comment.toString();
    int lastCut = 0;
    for (int i = 0; i < c.length(); i++) {
      if ((i >= lastCut + 60 && (c.charAt(i) == ' ' || c.charAt(i) == (char) 10 || c.charAt(i) == (char) 13)) || (i >= lastCut + 80)) {
        g.drawString(c.substring(lastCut, i), x, y);
        y += 20;
        lastCut = i + 1;
      }
    }
    if (lastCut < c.length() - 1)
      g.drawString(c.substring(lastCut, c.length() - 1), x, y);
  }

  public StringBuffer getComment() {
    return comment;
  }

  public int getCnt() {
    return spotcnt;
  }

  public String getFileName() {
    return filename;
  }

  public void copyData(double nx[], double ny[], double ref[]) {
    for (int i = 0; i < spotcnt; i++) {
      nx[i] = spots[i].x;
      ny[i] = spots[i].y;
    }

    ref[0] = refPoint1.x;
    ref[1] = refPoint1.y;
    ref[2] = refPoint2.x;
    ref[3] = refPoint2.y;
    ref[4] = refPoint3.x;
    ref[5] = refPoint3.y;
  }

  public void reset() {
    spotcnt = 0;
    refPoint1.reset();
    refPoint2.reset();
    refPoint3.reset();
    comment = new StringBuffer();
  }

  public boolean refPointsOk() {
    return !(refPoint1.empty() || refPoint2.empty() || refPoint3.empty());
  }

  private int versionOk(byte id[]) {
    if (id[0] == 'I' && id[1] == 'f' && id[2] == '0' && id[3] == '1')
      return 0;

    if (id[0] == 'I' && id[1] == 'f' && id[2] == '2' && id[3] == '0')
      return 1;

    return -1;
  }

}

