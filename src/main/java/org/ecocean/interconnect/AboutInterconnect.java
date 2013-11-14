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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class AboutInterconnect extends JPanel implements ActionListener {
  static JFrame frame = null;
  static Image img = null;

  public AboutInterconnect() {
    if (frame != null)
      frame.dispose();
    frame = new JFrame("About Wild Me Interconnect");
    frame.setContentPane(this);
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        frame.dispose();
        frame = null;
      }
    });
    frame.setSize(new Dimension(600, 425));
    frame.setResizable(false);
    frame.setLocation(200, 200);

    ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("/images/interconnect.gif"));
    frame.setIconImage(imageIcon.getImage());
    frame.setVisible(true);
  }

  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    Color bg = new Color(37, 0, 134);
    setBackground(bg);
    Graphics2D graphics2D = (Graphics2D) g;
    graphics2D.setColor(new Color(255, 255, 192));

    if (img == null) {
      ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("/images/interconnect.gif"));
      img = imageIcon.getImage();
    }

    g.drawImage(img, 0, 0, 600, 400, bg, this);
  }

  public void actionPerformed(ActionEvent e) {
    frame.dispose();
    frame = null;
  }
}
