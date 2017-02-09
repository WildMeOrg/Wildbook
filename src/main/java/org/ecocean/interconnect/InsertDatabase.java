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

class InsertDatabase extends JPanel implements ActionListener {
  static JFrame frame = null;

  JTextField tfDir = null;
  JTextField tfFile = null;
  SharkPanel sp = null;
  String i3sDataDir = null;
  String fpIn = null;
  String fImg = null;
  String ext = null;

  public InsertDatabase(SharkPanel _sp, String _fpIn, String _i3sDataDir) {
    sp = _sp;
    fpIn = _fpIn;
    i3sDataDir = _i3sDataDir;

    GetImageFile gif = new GetImageFile(fpIn);
    fImg = gif.getImageString();
    ext = gif.getImageExtension();

    if (frame != null)
      frame.dispose();

    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    add(Box.createRigidArea(new Dimension(10, 1)));

    JPanel centerPanel = new JPanel();
    centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
    centerPanel.add(Box.createRigidArea(new Dimension(1, 10)));

    JPanel wrapper = new JPanel();
    wrapper.setLayout(new BorderLayout(0, 0));
    JLabel label = new JLabel("Source shark image: " + fImg);
    label.setFont(new Font("Times New Roman", Font.BOLD, 12));
    wrapper.add(label, BorderLayout.LINE_START);
    centerPanel.add(wrapper);

    wrapper = new JPanel();
    wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.X_AXIS));
    label = new JLabel("New database folder: ");
    label.setFont(new Font("Times New Roman", Font.BOLD, 12));
    wrapper.add(label, BorderLayout.LINE_START);
    wrapper.add(Box.createRigidArea(new Dimension(1, 10)));
    tfDir = new JTextField("new shark", 20);
    tfDir.setEnabled(true);
    wrapper.add(tfDir);
    wrapper.add(Box.createRigidArea(new Dimension(100, 1)));
    centerPanel.add(wrapper);

    centerPanel.add(Box.createRigidArea(new Dimension(1, 10)));

    wrapper = new JPanel();
    wrapper.setLayout(new BorderLayout(10, 10));
    JCheckBox renameFile = new JCheckBox("New filename");
    renameFile.addActionListener(new OptionListener());
    renameFile.setSelected(false);
    wrapper.add(renameFile);
    centerPanel.add(wrapper);

    int index = fImg.lastIndexOf('/');
    if (index < fImg.lastIndexOf('\\'))
      index = fImg.lastIndexOf('\\');
    if (index == -1) {
      JOptionPane.showMessageDialog(frame, "Internal error: copy shark image and fingerprint file \nmanually to new directory in the database");
      frame.dispose();
      return;
    }
    wrapper = new JPanel();
    wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.X_AXIS));
    wrapper.add(Box.createRigidArea(new Dimension(20, 1)));
    tfFile = new JTextField(fImg.substring(index + 1, fImg.length()), 20);
    tfFile.setEnabled(false);
    wrapper.add(tfFile);
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

    centerPanel.add(Box.createRigidArea(new Dimension(1, 15)));
    centerPanel.add(wrapper);
    centerPanel.add(Box.createRigidArea(new Dimension(1, 10)));

    add(centerPanel);

    frame = new JFrame("Interconnect: Insert in database");
    ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("/images/icon.gif"));
    frame.setIconImage(imageIcon.getImage());
    frame.setContentPane(this);
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        dispose();
      }
    });
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

    // copy fgp file
    String filename = tfFile.getText();
    int index = filename.lastIndexOf(ext);
    if (!filename.endsWith(ext)) {
      JOptionPane.showMessageDialog(null, "Please, add proper file extension in text field (" + ext + ")");
      return;
    }

    File f = new File(i3sDataDir + "/" + tfDir.getText());
    if (f.exists()) {
      JOptionPane.showMessageDialog(null, "Directory " + tfDir.getText() + " already exists. Select a new name.");
      return;
    }
    f.mkdirs();

    CopyFile cf = new CopyFile(fpIn, i3sDataDir + "/" + tfDir.getText() + "/" + filename.substring(0, index) + ".fgp");
    if (cf.doIt() == false)
      return;

    // copy img file
    cf = new CopyFile(fImg, i3sDataDir + "/" + tfDir.getText() + "/" + filename);
    if (cf.doIt() == false)
      return;

    sp.updateDatabase();
    sp.killShowResultsWindow();
    sp.close();

    dispose();
  }

  /**
   * An ActionListener that listens to the check box.
   */
  class OptionListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      JCheckBox c = (JCheckBox) e.getSource();
      if (c.isSelected())
        tfFile.setEnabled(true);
      else
        tfFile.setEnabled(false);
    }
  }

}

