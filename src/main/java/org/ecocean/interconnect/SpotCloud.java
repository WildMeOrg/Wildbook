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

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.text.NumberFormat;

class SpotCloud extends JPanel {
  private Point2D s1[] = null;
  private Point2D s2[] = null;
  private int pairs[] = null;
  private String orig = null;
  private String found = null;
  private double score;

  public SpotCloud(String forig, String ffound, Point2D _s1[], Point2D _s2[], int _pairs[], double _score) {
    s1 = new Point2D[_s1.length];
    s2 = new Point2D[_s2.length];

    for (int i = 0; i < _s1.length; i++)
      s1[i] = new Point2D(_s1[i].x, _s1[i].y);
    for (int i = 0; i < _s2.length; i++)
      s2[i] = new Point2D(_s2[i].x, _s2[i].y);

    pairs = _pairs;
    score = _score;

    GetImageFile gif = new GetImageFile(forig);
    orig = gif.getImageString();
    gif = new GetImageFile(ffound);
    found = gif.getImageString();

    scale();
  }

  void scale() {
    double minx = Double.POSITIVE_INFINITY;
    double miny = Double.POSITIVE_INFINITY;
    double maxx = Double.NEGATIVE_INFINITY;
    double maxy = Double.NEGATIVE_INFINITY;

    for (int i = 0; i < s1.length; i++) {
      if (s1[i].empty())
        continue;
      if (s1[i].x < minx) minx = s1[i].x;
      if (s1[i].y < miny) miny = s1[i].y;
      if (s1[i].x > maxx) maxx = s1[i].x;
      if (s1[i].y > maxy) maxy = s1[i].y;
    }
    for (int i = 0; i < s2.length; i++) {
      if (s2[i].empty())
        continue;
      if (s2[i].x < minx) minx = s2[i].x;
      if (s2[i].y < miny) miny = s2[i].y;
      if (s2[i].x > maxx) maxx = s2[i].x;
      if (s2[i].y > maxy) maxy = s2[i].y;
    }

    double scalex = 700.0 / (maxx - minx);
    double scaley = 500.0 / (maxy - miny);
    double offsetx = 0;
    double offsety = 0;

    if (scalex < scaley) {
      offsety = (500.0 - 500.0 * (scalex / scaley)) / 2.0;
      scaley = scalex;
    } else {
      offsetx = (700.0 - 700.0 * (scaley / scalex)) / 2.0;
      scalex = scaley;
    }

    for (int i = 0; i < s1.length; i++) {
      if (s1[i].empty())
        continue;
      s1[i].x = (s1[i].x - minx) * scalex + offsetx + 50;
      s1[i].y = (s1[i].y - miny) * scaley + offsety + 50;
    }

    for (int i = 0; i < s2.length; i++) {
      if (s2[i].empty())
        continue;
      s2[i].x = (s2[i].x - minx) * scalex + offsetx + 50;
      s2[i].y = (s2[i].y - miny) * scaley + offsety + 50;
    }
  }

  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    setBackground(Color.white);
    Graphics2D graphics2D = (Graphics2D) g;

    NumberFormat nf = NumberFormat.getInstance();
    nf.setMaximumFractionDigits(2);

    int nrpairs = 0;
    for (nrpairs = 0; nrpairs < pairs.length && pairs[nrpairs] != -1; nrpairs++) ;

    graphics2D.setColor(Color.red);
    graphics2D.drawString("Unknown shark: " + orig, 50, 30);
    graphics2D.setColor(Color.blue);
    graphics2D.drawString("Found shark: " + found, 50, 48);
    graphics2D.setColor(Color.black);
    graphics2D.drawString("Nr of spot pairs: " + nrpairs, 50, 66);
    graphics2D.drawString("Score: " + nf.format(score), 50, 84);

    graphics2D.setColor(Color.green);
    for (int i = 0; i < pairs.length; i++) {
      if (pairs[i] == -1)
        break;
      int index1 = pairs[i] % 1000;
      int index2 = pairs[i] / 1000;

      graphics2D.draw(new Line2D.Double(s1[index1].x, s1[index1].y, s2[index2].x, s2[index2].y));
    }

    graphics2D.setColor(Color.red);
    for (int i = 0; i < s1.length; i++)
      if (s1[i].empty() == false)
        graphics2D.fill(new Ellipse2D.Double(s1[i].x - 1, s1[i].y - 1, 3, 3));

    graphics2D.setColor(Color.blue);
    for (int i = 0; i < s2.length; i++)
      if (s2[i].empty() == false)
        graphics2D.drawOval((int) s2[i].x - 3, (int) s2[i].y - 3, 6, 6);
  }
}

