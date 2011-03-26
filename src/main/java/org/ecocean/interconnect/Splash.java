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

public class Splash extends JPanel {
  Image img = null;
  JFrame frame = null;

  public Splash() {
    ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("/images/splash.gif"));
    img = imageIcon.getImage();

    frame = new JFrame();
    frame.setUndecorated(true);
    frame.getRootPane().setWindowDecorationStyle(JRootPane.FRAME);
    frame.setContentPane(this);
    frame.setSize(new Dimension(836, 478));
    frame.setResizable(false);
    frame.setFocusable(false);
    frame.setLocation(300, 300);
    repaint();
    frame.setVisible(true);

    long t = System.currentTimeMillis();
    while (System.currentTimeMillis() - t < 3000) ;
    dispose();
  }

  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    g.drawImage(img, 0, 0, 836, 478, Color.white, this);
  }

  public void dispose() {
    frame.dispose();
  }
}
