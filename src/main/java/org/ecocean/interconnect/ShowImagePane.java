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

class ShowImagePane extends JPanel {
  private Image img = null;
  private String fim = null;

  public ShowImagePane(String f) {
    GetImageFile gif = new GetImageFile(f);
    fim = gif.getImageString();
    ImageIcon imageIcon = new ImageIcon(fim);
    img = imageIcon.getImage();
  }

  private void paintImage(Graphics2D g) {
    double iw = (double) img.getWidth(this);
    double ih = (double) img.getHeight(this);
    double factor = 600 / ih;
    System.out.println("About to draw!");
    g.drawImage(img, 0, 0, (int) (iw * factor), (int) (ih * factor), new Color(37, 0, 134), this);
  }

  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D graphics2D = (Graphics2D) g;
    graphics2D.setColor(Color.red);

    if (img != null)
      paintImage(graphics2D);
    graphics2D.drawString(fim, 400, 30);
  }
}

