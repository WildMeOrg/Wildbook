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
import java.util.Properties;

class GetEncounterNumber extends JPanel implements ActionListener {
  JFrame frame = null;


  JTextField tfDir = null;
  SharkPanel sp = null;
  private String side = "left";
  String sideURLAddition = "";
  StringBuffer mess = new StringBuffer();
  private int spotcnt = 0;
  String transmitToURL = "";

  public GetEncounterNumber(SharkPanel _sp, StringBuffer mess, String side, int spotcnt, String transmitToURL) {
    sp = _sp;
    this.mess = mess;
    this.side = side;
    this.spotcnt = spotcnt;
    this.transmitToURL = transmitToURL;
    if (side.equals("right")) {
      sideURLAddition = "&rightSide=true";
    }

    if (frame != null)
      frame.dispose();

    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    add(Box.createRigidArea(new Dimension(10, 1)));

    JPanel centerPanel = new JPanel();
    centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
    centerPanel.add(Box.createRigidArea(new Dimension(1, 10)));

    JPanel wrapper = new JPanel();
    wrapper.setLayout(new BorderLayout(0, 0));
    centerPanel.add(wrapper);

    wrapper = new JPanel();
    wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.X_AXIS));
    JLabel label = new JLabel("Whale Shark Photo-identification Library encounter number: ");
    label.setFont(new Font("Times New Roman", Font.BOLD, 12));
    wrapper.add(label, BorderLayout.LINE_START);
    wrapper.add(Box.createRigidArea(new Dimension(1, 10)));
    tfDir = new JTextField("", 20);
    tfDir.setEnabled(true);
    wrapper.add(tfDir);
    wrapper.add(Box.createRigidArea(new Dimension(100, 1)));
    centerPanel.add(wrapper);

    centerPanel.add(Box.createRigidArea(new Dimension(1, 10)));


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

    frame = new JFrame("Send a " + side + "-side pattern to the Wildbook library");
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
    if (frame != null) frame.dispose();
    frame = null;
  }

  public void actionPerformed(ActionEvent e) {
    JButton source = (JButton) e.getSource();
    if (source.getText() == "Cancel") {
      if (frame != null) dispose();
    } else {

      String enc_number = tfDir.getText();

      Properties props = new Properties();
      //String transmitToURL="unknown";
      System.out.println("Transmitting the pattern to: " + transmitToURL);

      /**
       try{
       props.load(GetEncounterNumber.class.getResourceAsStream("/bundles/en/interconnect.properties"));
       transmitToURL=props.getProperty("transmitToURL").trim();
       System.out.println("I will be transmitting this spot pattern to: "+transmitToURL);
       }
       catch(IOException ioe){ioe.printStackTrace();}
       */

      String libraryURL = transmitToURL + "?number=" + enc_number + sideURLAddition;

      StringBuffer spotURL = new StringBuffer();
      int spot_length = sp.getSpots().length;
      Point2D[] spots = sp.getSpots();
      for (int i = 0; i < spotcnt; i++) {
        if (spots[i].x >= 0) {
          //note the casting as integer to reduce HTTP header size
          //this must be undone to generate match values that exactly match those of the I3S client
          String spotString = "&spotx" + i + "=" + (((int) spots[i].x)) + "&spoty" + i + "=" + (((int) spots[i].y));
          spotURL.append(spotString);
        }
      }
      //add spotURL to libraryURL
      libraryURL = libraryURL + spotURL.toString();

      //add the three reference points
      String ref1x = "&ref1x=" + (new Double(sp.getDorsal1().x)).toString();
      String ref2x = "&ref2x=" + (new Double(sp.getDorsal2().x)).toString();
      String ref3x = "&ref3x=" + (new Double(sp.getPelvic().x)).toString();
      String ref1y = "&ref1y=" + (new Double(sp.getDorsal1().y)).toString();
      String ref2y = "&ref2y=" + (new Double(sp.getDorsal2().y)).toString();
      String ref3y = "&ref3y=" + (new Double(sp.getPelvic().y)).toString();
      //String ref1x="&ref1x="+((int)(sp.getDorsal1().x));
      //String ref2x="&ref2x="+((int)(sp.getDorsal2().x));
      //String ref3x="&ref3x="+((int)(sp.getPelvic().x));
      //String ref1y="&ref1y="+((int)(sp.getDorsal1().y));
      //String ref2y="&ref2y="+((int)(sp.getDorsal2().y));
      //String ref3y="&ref3y="+((int)(sp.getPelvic().y));
      libraryURL = libraryURL + ref1x + ref2x + ref3x + ref1y + ref2y + ref3y;

      //System.out.println(libraryURL);

      BareBonesBrowserLaunch.openURL(libraryURL);


      //sp.killShowResultsWindow();
      //sp.close();

      dispose();
    }
  }

}

