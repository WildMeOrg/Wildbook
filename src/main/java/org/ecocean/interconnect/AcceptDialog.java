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

class AcceptDialog extends JPanel implements ActionListener {
  static JFrame frame = null;
  static CompareResults cr = null;

  JTextField tf;
  String dir = null;
  String orig = null;
  String forig = null;
  String found = null;
  String ffound = null;
  String ext = null;

  public AcceptDialog(CompareResults _cr, String _forig, String _ffound) {
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

    int index = ffound.lastIndexOf('/');
    if (index < ffound.lastIndexOf('\\'))
      index = ffound.lastIndexOf('\\');
    if (index == -1) {
      JOptionPane.showMessageDialog(null, "Internal error: copy shark image and fingerprint file \nmanually to proper directory in the database");
      frame.dispose();
      return;
    }

    JPanel wrapper = new JPanel();
    wrapper.setLayout(new BorderLayout(0, 0));
    JLabel label = new JLabel("Source shark image: " + reduceString(orig));
    label.setFont(new Font("Times New Roman", Font.BOLD, 12));
    wrapper.add(label, BorderLayout.LINE_START);
    centerPanel.add(wrapper);

    dir = ffound.substring(0, index);
    wrapper = new JPanel();
    wrapper.setLayout(new BorderLayout(0, 0));
    label = new JLabel("Destination database folder:  " + reduceString(dir));
    label.setFont(new Font("Times New Roman", Font.BOLD, 12));
    wrapper.add(label, BorderLayout.LINE_START);
    centerPanel.add(wrapper);

    centerPanel.add(Box.createRigidArea(new Dimension(1, 10)));

    wrapper = new JPanel();
    wrapper.setLayout(new BorderLayout(10, 10));
    JCheckBox renameFile = new JCheckBox("Rename file");
    renameFile.addActionListener(new OptionListener());
    renameFile.setSelected(false);
    wrapper.add(renameFile);
    centerPanel.add(wrapper);

    index = orig.lastIndexOf('/');
    if (index < orig.lastIndexOf('\\'))
      index = orig.lastIndexOf('\\');
    if (index == -1) {
      JOptionPane.showMessageDialog(frame, "Internal error: move shark image and fingerprint file \nmanually to proper directory in the database");
      frame.dispose();
      return;
    }
    wrapper = new JPanel();
    wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.X_AXIS));
    // wrapper.setPreferredSize(new Dimension(100, 100));
    wrapper.add(Box.createRigidArea(new Dimension(20, 1)));
    tf = new JTextField(orig.substring(index + 1, orig.length()), 20);
    tf.setEnabled(false);
    wrapper.add(tf);
    wrapper.add(Box.createRigidArea(new Dimension(200, 1)));
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

    frame = new JFrame("I3S: Include in database");
    ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("/images/i3sicon.gif"));
    frame.setIconImage(imageIcon.getImage());
    frame.setContentPane(this);
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        dispose();
      }
    });
    frame.setSize(new Dimension(420, 170));
    frame.setLocation(200, 100);
    frame.setResizable(false);
    frame.setVisible(true);
  }

  String reduceString(String s) {
    String tmp = null;

    if (s.length() < 50)
      return s;
    int i = 0;
    int cnt = 0;
    while (s.length() - i > 50 && cnt < 10) {
      i = s.indexOf("\\", i + 1);
      cnt++;
    }
    if (cnt == 10)
      i = s.length() - 50;
    else
      tmp = new String("..." + s.substring(i));
    return tmp;
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
      JOptionPane.showMessageDialog(null, "Could not write fingerprint file! Check disk space or write permission. \nFile not copied.");
      return;
    }

    // copy fgp file
    String filename = tf.getText();
    if (!filename.endsWith(ext)) {
      JOptionPane.showMessageDialog(null, "Please, add proper file extension in text field (" + ext + ")");
      return;
    }

    int index = filename.lastIndexOf(ext);
    CopyFile cf = new CopyFile(forig, dir + "/" + filename.substring(0, index) + ".fgp");
    if (cf.doIt() == false)
      return;
    // copy img file
    cf = new CopyFile(orig, dir + "/" + filename);
    if (cf.doIt() == false)
      return;

    cr.getSharkPanel().killShowResultsWindow();
    cr.getSharkPanel().close();
    cr.getSharkPanel().updateDatabase();
    cr.dispose();


    frame.dispose();
    frame = null;
  }

  /**
   * An ActionListener that listens to the check box.
   */
  class OptionListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      JCheckBox c = (JCheckBox) e.getSource();
      if (c.isSelected())
        tf.setEnabled(true);
      else
        tf.setEnabled(false);
    }
  }

}
