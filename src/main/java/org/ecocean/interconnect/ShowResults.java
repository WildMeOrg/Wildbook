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
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;


public class ShowResults extends JPanel implements ActionListener {
  static JFrame frame = null;

  JList list = null;
  JButton showButton = null;

  SharkPanel sp = null;
  String files[] = null;
  double scores[] = null;
  int nrpairs[] = null;

  public ShowResults(SharkPanel _sp, String _files[], double _scores[], int _nrpairs[]) {
    sp = _sp;
    files = _files;
    scores = _scores;
    nrpairs = _nrpairs;
    LogFile lf = new LogFile("SearchResults_");
    DefaultListModel listModel = new DefaultListModel();
    for (int i = 0; i < files.length; i++) {
      GetImageFile gif = new GetImageFile(files[i]);
      Integer ii = new Integer(_nrpairs[i]);
      int tmpval = (int) (1000.0 * _scores[i]);
      Double dd = new Double((double) tmpval / 1000.0);
      String tmp = gif.getImageString();
      String s = tmp.substring(tmp.lastIndexOf('\\') + 1, tmp.length()) + "\t" + ii.toString() + "\t" + dd.toString();
      String ddval = dd.toString();
      while (ddval.length() < 5) ddval += "0";
      if (i < 9)
        listModel.addElement("  " + (i + 1) + ".  " + ddval + "   " + tmp.substring(tmp.lastIndexOf('\\') + 1, tmp.length()));
      else
        listModel.addElement((i + 1) + ".  " + ddval + "   " + tmp.substring(tmp.lastIndexOf('\\') + 1, tmp.length()));
      lf.write(s);
    }
    lf.close();

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    list = new JList(listModel);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setDragEnabled(false);
    list.addMouseListener(new MouseClickListener());

    JScrollPane listView = new JScrollPane(list);
    listView.setPreferredSize(new Dimension(250, 250));

    showButton = new JButton("Visual comparison");
    showButton.addActionListener(this);
    showButton.setMnemonic('V');
    showButton.setEnabled(false);

    JButton closeButton = new JButton("Close");
    closeButton.addActionListener(this);
    closeButton.setMnemonic('C');

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    panel.add(listView, BorderLayout.CENTER);
    panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    JPanel panel2 = new JPanel();
    panel2.setLayout(new BoxLayout(panel2, BoxLayout.X_AXIS));
    panel2.add(showButton);
    panel2.add(Box.createRigidArea(new Dimension(10, 1)));
    panel2.add(closeButton);

    add(panel);
    add(Box.createRigidArea(new Dimension(1, 10)));
    add(panel2);

    if (frame != null)
      frame.dispose();
    frame = new JFrame("I3S: Search results");
    frame.setContentPane(this);
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        frame.dispose();
        frame = null;
      }
    });
    frame.setSize(new Dimension(400, 300));
    frame.setLocation(200, 200);

    ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("/Simages/icon.gif"));
    frame.setIconImage(imageIcon.getImage());

    frame.pack();
    frame.setVisible(true);
  }

  public void dispose() {
    if (frame != null)
      frame.dispose();
    frame = null;
  }

  public void actionPerformed(ActionEvent e) {
    JButton source = (JButton) e.getSource();
    if (source == showButton)
      sp.doVisualComparison(list.getSelectedIndex(), scores[list.getSelectedIndex()]);
    else
      dispose();
  }

  private class MouseClickListener extends MouseAdapter {
    Popup popup;

    public void mouseClicked(MouseEvent e) {
      if (e.getButton() == e.BUTTON1) {
        showButton.setEnabled(list.getSelectedIndex() != -1 ? true : false);

        if (e.getClickCount() == 2) {
          int index = list.locationToIndex(e.getPoint());
          sp.doVisualComparison(index, scores[index]);
        }
      }
    }

    public void mousePressed(MouseEvent e) {
      if (e.getButton() == e.BUTTON3) {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);
        int index = list.locationToIndex(e.getPoint());
        GetImageFile gif = new GetImageFile(files[index]);

        JTextArea area = new JTextArea("File: " + gif.getImageString() + "\n" +
          "Score: " + nf.format(scores[index]) + "\n" +
          "Pairs: " + nrpairs[index]);
        area.setEditable(false);
        area.setBorder(BorderFactory.createLineBorder(Color.black));
        area.setFont(new Font("times", Font.PLAIN, 12));
        PopupFactory factory = PopupFactory.getSharedInstance();
        popup = factory.getPopup(null, area,
          (int) e.getComponent().getLocationOnScreen().getX() + e.getX() + 25,
          (int) e.getComponent().getLocationOnScreen().getY() + e.getY());
        popup.show();
      }
    }

    public void mouseReleased(MouseEvent e) {
      if (e.getButton() == e.BUTTON3) {
        popup.hide();
      }
    }
  }
}


class LogFile {
  static private FileWriter fw;
  static private String fname;

  public LogFile(String fn) {
    try {
      String ext = extension();
      fname = fn + ext + ".log";
      fw = new FileWriter(fname);
    } catch (IOException ioe) {
      System.err.println("Logfile::Logfile. Cannot open logfile  " + fname + "\n");
      JOptionPane.showMessageDialog(null, "Logfile::Logfile. Cannot open logfile  " + fname);
    }
  }

  // write message to the file
  public void write(String mess) {
    try {
      fw.write(mess + "\n");
      fw.flush();
    } catch (IOException ioe) {
      System.err.println("LogFile::write. Cannot write to logfile " + fname + "\nCause: " + ioe.toString());
      JOptionPane.showMessageDialog(null, "LogFile::write. Cannot write to logfile " + fname + "\nCause: " + ioe.toString());
    }
  }

  public void close() {
    try {
      fw.close();
    } catch (IOException ioe) {
      System.err.println("Logfile::close. Cannot close logfile " + fname + ".\n");
      JOptionPane.showMessageDialog(null, "Logfile::close. Cannot close logfile " + fname);
    }
  }

  private String extension() {
    Date date = new Date();    // current time and date
    GregorianCalendar cal = new GregorianCalendar();
    cal.setTime(date);
    String ext = new String("_");
    ext = ext + cal.get(Calendar.YEAR);
    if (cal.get(Calendar.MONTH) < 9)
      ext += "0";
    ext = ext + (cal.get(Calendar.MONTH) + 1);
    if (cal.get(Calendar.DAY_OF_MONTH) < 10)
      ext += "0";
    ext += cal.get(Calendar.DAY_OF_MONTH) + "_";
    if (cal.get(Calendar.HOUR_OF_DAY) < 10)
      ext += "0";
    ext += cal.get(Calendar.HOUR_OF_DAY);
    if (cal.get(Calendar.MINUTE) < 10)
      ext += "0";
    ext += cal.get(Calendar.MINUTE);
    if (cal.get(Calendar.SECOND) < 10)
      ext += "0";
    ext += cal.get(Calendar.SECOND);
    return ext;
  }
}
