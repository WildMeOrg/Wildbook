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
import java.io.File;

class SharkIdentification extends JPanel implements ActionListener {
  static JFrame frame = null;
  static CompareResults cr = null;

  JTextField tf;
  String identity = null;
  String origdir = null;
  String orig = null;
  String forig = null;
  String found = null;
  String ffound = null;
  String ext = null;

  public SharkIdentification(CompareResults _cr, String _forig, String _ffound) {
    forig = _forig;
    ffound = _ffound;

    GetImageFile gif = new GetImageFile(forig);
    orig = gif.getImageString();
    ext = gif.getImageExtension();

    gif = new GetImageFile(ffound);
    found = gif.getImageString();

    cr = _cr;
    if (frame != null)
      frame.dispose();

    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    add(Box.createRigidArea(new Dimension(10, 1)));

    JPanel centerPanel = new JPanel();
    centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
    centerPanel.add(Box.createRigidArea(new Dimension(1, 10)));

    int index1 = ffound.lastIndexOf('/');
    if (index1 < ffound.lastIndexOf('\\'))
      index1 = ffound.lastIndexOf('\\');
    if (index1 == -1) {
      JOptionPane.showMessageDialog(null, "Internal error: rename shark image and fingerprint file manually");
      dispose();
      return;
    }
    int index2 = ffound.lastIndexOf('/', index1 - 1);
    if (index2 < ffound.lastIndexOf('\\', index1 - 1))
      index2 = ffound.lastIndexOf('\\', index1 - 1);
    if (index2 == -1) {
      JOptionPane.showMessageDialog(null, "Internal error: rename shark image and fingerprint file manually");
      dispose();
      return;
    }
    identity = ffound.substring(index2 + 1, index1);

    index1 = forig.lastIndexOf('/');
    if (index1 < forig.lastIndexOf('\\'))
      index1 = forig.lastIndexOf('\\');
    if (index1 == -1) {
      JOptionPane.showMessageDialog(null, "Internal error: rename shark image and fingerprint file manually");
      dispose();
      return;
    }
    origdir = forig.substring(0, index1 + 1);
    JPanel wrapper = new JPanel();
    wrapper.setLayout(new BorderLayout(0, 0));
    JLabel label = new JLabel("Current name:  " + orig.substring(index1 + 1, orig.length()));
    label.setFont(new Font("Times New Roman", Font.BOLD, 12));
    wrapper.add(label, BorderLayout.LINE_START);
    centerPanel.add(wrapper);

    wrapper = new JPanel();
    wrapper.setLayout(new BorderLayout(0, 0));
    label = new JLabel("Shark identity:  " + identity);
    label.setFont(new Font("Times New Roman", Font.BOLD, 12));
    wrapper.add(label, BorderLayout.LINE_START);
    centerPanel.add(wrapper);

    centerPanel.add(Box.createRigidArea(new Dimension(1, 10)));

    wrapper = new JPanel();
    wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.X_AXIS));
    label = new JLabel("New name:  ");
    label.setFont(new Font("Times New Roman", Font.BOLD, 12));
    wrapper.add(label, BorderLayout.LINE_START);
    tf = new JTextField("", 20);
    wrapper.add(tf);
    wrapper.add(Box.createRigidArea(new Dimension(50, 1)));
    centerPanel.add(wrapper);

    JButton ok = new JButton("Ok");
    ok.setMnemonic(KeyEvent.VK_O);
    ok.addActionListener(this);
    JButton cancel = new JButton("Cancel");
    cancel.setMnemonic(KeyEvent.VK_C);
    cancel.addActionListener(this);

    wrapper = new JPanel();
    wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.X_AXIS));
    wrapper.add(ok);
    wrapper.add(Box.createRigidArea(new Dimension(10, 1)));
    wrapper.add(cancel);

    centerPanel.add(Box.createRigidArea(new Dimension(1, 10)));
    centerPanel.add(wrapper);
    centerPanel.add(Box.createRigidArea(new Dimension(1, 10)));

    add(centerPanel);

    frame = new JFrame("I3S: Identify shark");
    ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("/images/i3sicon.gif"));
    frame.setIconImage(imageIcon.getImage());
    frame.setContentPane(this);
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        dispose();
      }
    });
    // frame.setSize(new Dimension(420, 170));
    frame.pack();
    frame.setLocation(200, 100);
    frame.setResizable(false);
    frame.setVisible(true);
  }

  void dispose() {
    frame.dispose();
    frame = null;
  }

  public void actionPerformed(ActionEvent e) {
    JButton source = (JButton) e.getSource();
    if (source.getText() == "Cancel") {
      dispose();
      return;
    }
    StringBuffer mess = new StringBuffer();
    if (cr.getSharkPanel().writeFingerprint(mess) == false) {
      JOptionPane.showMessageDialog(null, "Could not write fingerprint file! Check disk space or write permission. \n");
      return;
    }

    String filename = tf.getText();
    if (!filename.endsWith(ext)) {
      JOptionPane.showMessageDialog(null, "Please, add proper file extension in text field (" + ext + ")");
      return;
    }

    File jpg = new File(orig);
    File newjpg = new File(origdir + filename);
    jpg.renameTo(newjpg);

    File fgp = new File(forig);
    File newfgp = new File(origdir + filename.substring(0, filename.length() - ext.length()) + ".fgp");
    fgp.renameTo(newfgp);

    cr.getSharkPanel().killShowResultsWindow();
    cr.getSharkPanel().close();
    cr.dispose();

    frame.dispose();
  }
}
