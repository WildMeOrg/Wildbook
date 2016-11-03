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
import java.awt.event.*;

class CompareResults extends JPanel implements ActionListener {
  static JFrame frame = null;
  static SharkPanel sp = null;

  JButton insertInDB;
  JButton identifyShark;

  String forig;
  String ffound;

  public CompareResults(SharkPanel _sp, String _forig, String _ffound,
                        Point2D _orig1[], Point2D _orig2[], Point2D _tf1[], Point2D _tf2[], int _pairs[], double score) {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    sp = _sp;
    forig = _forig;
    ffound = _ffound;

    JTabbedPane tabbedPane = new JTabbedPane();
    tabbedPane.addTab("Images", null, new BothSharks(forig, ffound, _orig1, _orig2, _pairs, score),
      "Shows the images of both sharks together with the selected spots");
    tabbedPane.setMnemonicAt(0, KeyEvent.VK_I);

    tabbedPane.addTab("Unknown shark", null, new ShowImagePane(forig), "Shows the image of the unknown shark");
    tabbedPane.setMnemonicAt(1, KeyEvent.VK_U);
    tabbedPane.addTab("Found shark", null, new ShowImagePane(ffound), "Shows the image of the found shark");
    tabbedPane.setMnemonicAt(2, KeyEvent.VK_F);
    tabbedPane.addTab("Spot cloud", null, new SpotCloud(forig, ffound, _tf1, _tf2, _pairs, score),
      "Shows the selected spots of both sharks in a single diagram");
    tabbedPane.setMnemonicAt(3, KeyEvent.VK_S);

    tabbedPane.setPreferredSize(new Dimension(900, 700));

    add(tabbedPane);

    insertInDB = new JButton("Include in database");
    insertInDB.setToolTipText("Accept the current shark as the correct match and include the new shark in the database");
    insertInDB.setMnemonic(KeyEvent.VK_I);
    insertInDB.addActionListener(this);

    identifyShark = new JButton("Only identification");
    identifyShark.setToolTipText("The current shark is renamed but not inserted in the database");
    identifyShark.setMnemonic(KeyEvent.VK_O);
    identifyShark.addActionListener(this);

    JButton close = new JButton("Close");
    close.setToolTipText("Close this window");
    close.setMnemonic(KeyEvent.VK_C);
    close.addActionListener(this);

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    panel.add(insertInDB);
    panel.add(Box.createRigidArea(new Dimension(10, 1)));
    panel.add(identifyShark);
    panel.add(Box.createRigidArea(new Dimension(10, 1)));
    panel.add(close);

    add(Box.createRigidArea(new Dimension(1, 7)));
    add(panel);
    add(Box.createRigidArea(new Dimension(1, 7)));

    if (frame != null)
      frame.dispose();
    frame = new JFrame("I3S: Compare results");

    ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("/images/icon.gif"));
    frame.setIconImage(imageIcon.getImage());

    frame.setContentPane(this);
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        dispose();
      }
    });
    frame.setSize(new Dimension(800, 650));
    frame.setLocation(0, 0);
    frame.setResizable(false);
    frame.setVisible(true);
  }

  SharkPanel getSharkPanel() {
    return sp;
  }

  void dispose() {
    frame.dispose();
    frame = null;
  }

  public void actionPerformed(ActionEvent e) {
    if ((JButton) e.getSource() == insertInDB)
      new AcceptDialog(this, forig, ffound);
    else if ((JButton) e.getSource() == identifyShark)
      new SharkIdentification(this, forig, ffound);
    else
      dispose();
  }
}

