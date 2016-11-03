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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.*;
//import com.reijns.affine;


/**
 * To do:
 * - Clicking in image convert to image coordinates instead of window coordinates
 * - Keyboard listener for ctrl-s
 * *
 */

class Point2D {
  public double x;
  public double y;

  public Point2D() {
    x = Double.NEGATIVE_INFINITY;
    y = Double.NEGATIVE_INFINITY;
  }

  public Point2D(double _x, double _y) {
    x = _x;
    y = _y;
  }

  public boolean empty() {
    if (x == Double.NEGATIVE_INFINITY && y == Double.NEGATIVE_INFINITY)
      return true;
    return false;
  }

  public void reset() {
    x = Double.NEGATIVE_INFINITY;
    y = Double.NEGATIVE_INFINITY;
  }
}

class SharkPanel extends JPanel {
  final static int MIN_SPOT_CNT = 5;
  final static int MAX_SPOT_CNT = 80;
  final static int NR_OF_MATCHES = 20;

  static ShowResults showRes = null;

  private Point2D spots[];
  private Point2D normspots[];
  private Point2D dorsal1;
  private Point2D dorsal2;
  private Point2D pelvic;

  private double matrix[];

  private int xOffset;
  private int spotcnt;
  private double iw;
  private double ih;
  private double factor;

  private String results[] = null;
  private String i3sDataDir = null;
  //indicates which side of the shark is represented
  //by default, an empty string is a left-side pattern
  //a right-side pattern requires the value "right", all other values default to left
  private String side = "left";


  private boolean sharpen;
  private boolean fileSaved;
  private boolean dotSelection;
  private boolean lastSearchExhaustive;
  private Image img;
  private Image excl;
  private Image cursor;
  private BufferedImage sharpened;
  private StringBuffer fpfile;

  // external C++ function definitions
  //public native void     calcAffine(double d1x, double d1y, double d2x, double d2y, double px, double py, double matrix[]);
  //public native String[] scoreShark(double ref[], double nx[], double ny[], double scores[], int nrpairs[], int dummy);
  //public native boolean  compareTwo(String s,
  //double ref[], double nx[], double ny[],
  //double matchx[], double matchy[], double origx[], double origy[],
  //int pairs[], int exhaustive);
  public void initCompare() {
  }

  ;

  public void closeCompare() {
  }

  ;


  public SharkPanel(String _i3sDataDir) {
    i3sDataDir = _i3sDataDir;
    spotcnt = 0;
    spots = new Point2D[MAX_SPOT_CNT * 2];
    normspots = new Point2D[MAX_SPOT_CNT * 2];

    for (int i = 0; i < MAX_SPOT_CNT * 2; i++) {
      spots[i] = new Point2D();
      normspots[i] = new Point2D();
    }

    dorsal1 = new Point2D();
    dorsal2 = new Point2D();
    pelvic = new Point2D();

    matrix = new double[6];

    sharpen = false;
    fileSaved = true;
    dotSelection = false;
    lastSearchExhaustive = false;
    img = null;
    excl = null;
    cursor = null;
    sharpened = null;
    fpfile = null;

    addMouseListener(new MouseClickListener());
    initCompare();      // read database with all fingerprints
  }

  public void closeOnExit() {
    closeCompare();
  }

  public void updateDatabase() {
    closeCompare();
    initCompare();
  }

  public void toggleSharpen() {
    sharpen = !sharpen;
    repaint();
  }

  public void toggleDotSelection() {
    if (dotSelection)
      dotSelection = false;
    else
      dotSelection = true;
    repaint();
  }

  public void killShowResultsWindow() {
    if (showRes != null) {
      showRes.dispose();
      showRes = null;
    }
  }

  public boolean insertInDatabase(StringBuffer mess) {
    if (img == null) {
      mess.append("Nothing to insert in database. Open an image first.");
      return false;
    }
    if (dorsal1.empty() || dorsal2.empty() || pelvic.empty()) {
      mess.append("Unsufficient data, point out all three fins first.");
      return false;
    }
    if (spotcnt < MIN_SPOT_CNT) {
      mess.append("Unsufficient data, point out at least " + MIN_SPOT_CNT + " spots first.");
      return false;
    }
    if (writeFingerprint(mess) == false)
      return false;
    new InsertDatabase(this, new String(fpfile), i3sDataDir);

    return true;
  }


  //send data points to the Wildbook library
  public boolean send2library(StringBuffer mess, String transmitToURL) {
    if (img == null) {
      mess.append("Nothing to transmit. You must open an image first.");
      return false;
    }
    if (dorsal1.empty() || dorsal2.empty() || pelvic.empty()) {
      mess.append("Insufficient data. You must point out the three reference points first.");
      return false;
    }
    if (spotcnt < MIN_SPOT_CNT) {
      mess.append("Insufficient data. You must point out at least " + MIN_SPOT_CNT + " spots first.");
      return false;
    }

    //get the encounterNumber
    new GetEncounterNumber(this, mess, side, spotcnt, transmitToURL);
    return true;
  }


  //first step in an I3S scan
  public boolean compareWithDatabase(StringBuffer mess, boolean exhaustive) {
    if (img == null) {
      mess.append("No data to compare. Open an image first.");
      return false;
    }
    if (dorsal1.empty() || dorsal2.empty() || pelvic.empty()) {
      mess.append("Point out the reference points first.\nWithout these points a comparison is not possible.");
      return false;
    }

    if (spotcnt < MIN_SPOT_CNT) {
      mess.append("Insufficient input. At least " + MIN_SPOT_CNT + " spots are required.\nNo comparison has been made.");
      return false;
    }

    doAffine();

    double nx[] = new double[spotcnt];
    double ny[] = new double[spotcnt];
    double ref[] = new double[6];
    double scores[] = new double[NR_OF_MATCHES];
    double origx[] = new double[100];
    double origy[] = new double[100];
    double matchx[] = new double[100];
    double matchy[] = new double[100];
    //double scores[];

    int pairs[] = new int[100];
    int nrpairs[] = new int[NR_OF_MATCHES];
    int currentScoreNum = 0;

    for (int i = 0; i < spotcnt; i++) {
      nx[i] = spots[i].x;
      ny[i] = spots[i].y;
    }

    ref[0] = dorsal1.x;
    ref[1] = dorsal1.y;
    ref[2] = dorsal2.x;
    ref[3] = dorsal2.y;
    ref[4] = pelvic.x;
    ref[5] = pelvic.y;


/*
        //new way
        //step 1 iterate through FGP files based on starting location of I3S_DATA
        Vector<String> res=new Vector();
        Vector<double> scrs=new Vector();
        File startDir=new File(i3sDataDir);
        for() {
       
          if(fileSide.equals(side)){
            String s;
            if(exhaustive) {
              scrs.add(compareTwo(s, ref, nx, ny, matchx, matchy, origx, origy, pairs, 1));
            }
            else {
           scrs.add(compareTwo(s, ref, nx, ny, matchx, matchy, origx, origy, pairs, 0));
         }
         res.add(s);
            currentScoreNum++;
          }
        }

    //convert results to an array
    results=res.toArray();

    //populate scores[]
    scores=scrs.toArray();


        
*/

    //old way
    //if(exhaustive)
    //results = scoreShark(ref, nx, ny, scores, nrpairs, 1);
    //else
    //results = scoreShark(ref, nx, ny, scores, nrpairs, 0);


    lastSearchExhaustive = exhaustive;


    //results=vResults.toArray(results);
    showRes = new ShowResults(this, results, scores, nrpairs);
    return true;
  }

  public void doVisualComparison(int index, double score) {
    double nx[] = new double[spotcnt];
    double ny[] = new double[spotcnt];

    for (int i = 0; i < spotcnt; i++) {
      nx[i] = spots[i].x;
      ny[i] = spots[i].y;
    }

    double matchx[] = new double[100];
    double matchy[] = new double[100];
    double origx[] = new double[100];
    double origy[] = new double[100];
    double ref[] = new double[6];
    int pairs[] = new int[100];

    int dummy;
    if (lastSearchExhaustive)
      dummy = 1;
    else
      dummy = 0;

    ref[0] = dorsal1.x;
    ref[1] = dorsal1.y;
    ref[2] = dorsal2.x;
    ref[3] = dorsal2.y;
    ref[4] = pelvic.x;
    ref[5] = pelvic.y;

    //if(compareTwo(results[index], ref, nx, ny, matchx, matchy, origx, origy, pairs, dummy) == false) {
    //JOptionPane.showMessageDialog(null, "Error while making comparison. Please report this bug to i3s@reijns.com\nSee cmd-prompt for details on the error.");
    //return;
    //}

    Point2D match[] = new Point2D[100];
    Point2D orig[] = new Point2D[100];
    for (int i = 0; i < 100; i++) {
      if (origx[i] < -1000000)
        orig[i] = new Point2D();
      else
        orig[i] = new Point2D(origx[i], origy[i]);

      if (matchx[i] < -1000000)
        match[i] = new Point2D();
      else
        match[i] = new Point2D(matchx[i], matchy[i]);
    }

    CompareResults cr = new CompareResults(this, new String(fpfile), results[index], spots, orig, normspots, match, pairs, score);
  }

  public void close() {
    img = null;
    spotcnt = 0;
    fileSaved = true;
    dorsal1.reset();
    dorsal2.reset();
    pelvic.reset();
    killShowResultsWindow();
    repaint();
  }

  public boolean updateImage(String filename, StringBuffer mess) {
    ImageIcon imageIcon = new ImageIcon(filename);

    File ftest = new File(filename);
    if (ftest.exists() == false) {
      mess.append("File " + filename + " does not exist");
      return false;
    }
    String test = filename.toLowerCase();
    if (test.endsWith(".jpg") || test.endsWith(".gif") || test.endsWith(".jpeg"))
      fpfile = new StringBuffer(filename.substring(0, filename.length() - 4)).append(".fgp");
    else {
      mess.append("Only files with extension '.jpg' or '.gif' are supported");
      return false;
    }

    img = imageIcon.getImage();
    spotcnt = 0;
    fileSaved = true;

    if (readFingerprint(mess) == false)
      return false;

    calcDisplayFactor();

    repaint();

    return true;
  }

  public boolean readFingerprint(StringBuffer mess) {
    try {
      DataInputStream in = new DataInputStream(new FileInputStream(fpfile.toString()));
      byte id[] = new byte[4];
      int version = versionOk(id);

      in.read(id, 0, 4);
      //if(version != 0 && version != 1)
      //{
      //   mess.append("Unknown type of fingerprint file " + fpfile.toString());
      // return false;
      //}

      dorsal1.x = in.readDouble();
      dorsal1.y = in.readDouble();
      dorsal2.x = in.readDouble();
      dorsal2.y = in.readDouble();
      pelvic.x = in.readDouble();
      pelvic.y = in.readDouble();

      if (dorsal1.x > pelvic.x) {
        side = "right";
      } else {
        side = "left";
      }

      spotcnt = in.readInt();

      for (int i = 0; i < spotcnt; i++) {
        spots[i].x = in.readDouble();
        spots[i].y = in.readDouble();
        if (i > 0 && spots[i].x == spots[i - 1].x && spots[i].y == spots[i - 1].y)
          fileSaved = false;
      }
      for (int i = 0; i < spotcnt; i++) {
        normspots[i].x = in.readDouble();
        normspots[i].y = in.readDouble();
      }


      in.close();
    } catch (FileNotFoundException fnf) {
      spotcnt = 0;
      dorsal1.reset();
      dorsal2.reset();
      pelvic.reset();
    } catch (IOException e) {
      mess.append("Error while reading from " + fpfile.toString());
      return false;
    }

    return true;
  }

  private int versionOk(byte id[]) {
    if (id[0] == 'I' && id[1] == 'f' && id[2] == '0' && id[3] == '1')
      return 0;

    if (id[0] == 'I' && id[1] == 'f' && id[2] == '2' && id[3] == '0')
      return 1;

    return -1;
  }

  public boolean writeFingerprint(StringBuffer mess) {
    if (img == null) {
      mess.append("No data to save. Open an image first.");
      return false;
    }
    if (dorsal1.empty() || dorsal2.empty() || pelvic.empty()) {
      mess.append("Indicate the reference points first.\nNo data has been written.");
      return false;
    }

    if (spotcnt < MIN_SPOT_CNT) {
      mess.append("Insufficient input. At least " + MIN_SPOT_CNT + " spots are required.\nNo data has been written.");
      return false;
    }

    doAffine();

    try {
      DataOutputStream out = new DataOutputStream(new FileOutputStream(fpfile.toString()));

      byte id[] = new byte[4];
      id[0] = 'I';
      id[1] = 'f';
      id[2] = '0';
      id[3] = '1';
      out.write(id, 0, 4);

      out.writeDouble(dorsal1.x);
      out.writeDouble(dorsal1.y);
      out.writeDouble(dorsal2.x);
      out.writeDouble(dorsal2.y);
      out.writeDouble(pelvic.x);
      out.writeDouble(pelvic.y);

      out.writeInt(spotcnt);

      for (int i = 0; i < spotcnt; i++) {
        out.writeDouble(spots[i].x);
        out.writeDouble(spots[i].y);
      }
      for (int i = 0; i < spotcnt; i++) {
        out.writeDouble(normspots[i].x);
        out.writeDouble(normspots[i].y);
      }
      //out.writeUTF(side);
      out.close();
      fileSaved = true;
      repaint();
    } catch (FileNotFoundException fnf) {
      mess.append("Could not open " + fpfile.toString() + " for writing.");
      return false;
    } catch (IOException e) {
      mess.append("Error while writing to " + fpfile.toString());
      return false;
    }

    return true;
  }

  public void addClick(double x, double y) {
    if (spotcnt == MAX_SPOT_CNT)
      return;
    spots[spotcnt].x = x;
    spots[spotcnt].y = y;
    spotcnt++;
  }

  public void removeNearestClick(double x, double y) {
    double mindist = Double.POSITIVE_INFINITY;
    int index = -1;

    for (int i = 0; i < spotcnt; i++) {
      double dist = (x - spots[i].x) * (x - spots[i].x) + (y - spots[i].y) * (y - spots[i].y);
      if (dist < mindist) {
        mindist = dist;
        index = i;
      }
    }

    double dist1 = (x - dorsal1.x) * (x - dorsal1.x) + (y - dorsal1.y) * (y - dorsal1.y);
    double dist2 = (x - dorsal2.x) * (x - dorsal2.x) + (y - dorsal2.y) * (y - dorsal2.y);
    double dist3 = (x - pelvic.x) * (x - pelvic.x) + (y - pelvic.y) * (y - pelvic.y);
    Point2D tmp = dorsal1;
    if (dist2 < dist1) {
      dist1 = dist2;
      tmp = dorsal2;
    }
    if (dist3 < dist1) {
      dist1 = dist3;
      tmp = pelvic;
    }

    if (dist1 < mindist)
      tmp.reset();
    else {
      for (int i = index; i < spotcnt - 1; i++) {
        spots[i].x = spots[i + 1].x;
        spots[i].y = spots[i + 1].y;
      }
      spotcnt--;
    }
  }

  private class MouseClickListener extends MouseAdapter {
    public void mouseClicked(MouseEvent e) {
      if (img == null)
        return;
      if (dotSelection == false) {
        JOptionPane.showMessageDialog(null, "Please select 'Spot selection' via the File menu first");
        return;
      }

      fileSaved = false;

      double x = (e.getX() - xOffset) / factor;
      double y = e.getY() / factor;

      if (e.getButton() == e.BUTTON1) {
        if (dorsal1.empty()) {
          dorsal1.x = x;
          dorsal1.y = y;
        } else if (dorsal2.empty()) {

          //check to see if the spots are in the correct locations for a side-based system
          if ((side.equals("right")) && (x >= dorsal1.x)) {
            JOptionPane.showMessageDialog(null, "Error! Your choice for the posterior fin point is incorrect for a right-side image! Are you using the correct side option from the file menu?");
            return;
          } else if ((side.equals("left")) && (x <= dorsal1.x)) {
            JOptionPane.showMessageDialog(null, "Error! Your choice for the posterior fin point is incorrect for a left-side image! Are you using the correct side option from the file menu?");
            return;
          }
          dorsal2.x = x;
          dorsal2.y = y;
        } else if (pelvic.empty()) {
          pelvic.x = x;
          pelvic.y = y;
        } else
          addClick(x, y);
      } else if (e.getButton() == e.BUTTON3)
        removeNearestClick(x, y);
      else if (e.getButton() == e.BUTTON2)
        removeNearestClick(x, y);

      repaint();
    }
  }

  private Image imageOp() {
    float SHARPEN3x3[] = {0.f, -1.f, 0.f,
      -1.f, 5.f, -1.f,
      0.f, -1.f, 0.f};

    if (sharpen == false)
      return img;

    if (sharpened != null && spotcnt > 0)
      return sharpened;

    int iw = img.getWidth(this);
    int ih = img.getHeight(this);

    BufferedImage bi = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
    sharpened = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
    Graphics2D big = bi.createGraphics();
    big.drawImage(img, 0, 0, this);

    Kernel kernel = new Kernel(3, 3, SHARPEN3x3);
    ConvolveOp cop = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
    cop.filter(bi, sharpened);

    return sharpened;
  }

  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D graphics2D = (Graphics2D) g;

    if (excl == null) {
      ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("/images/excl.gif"));
      excl = imageIcon.getImage();
    }
    if (cursor == null) {
      ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("/images/pointer.gif"));
      cursor = imageIcon.getImage();
    }
    if (img == null) {
      setBackground(new Color(37, 0, 134));
      ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("/images/bg_blueBig.gif"));
      Image bgimg = imageIcon.getImage();
      graphics2D.drawImage(bgimg, 0, 0, 1024, 756, Color.white, this);
      return;
    }
    setBackground(new Color(192, 192, 192));

    Image im = imageOp();
    graphics2D.drawImage(im, xOffset, 0, (int) (iw * factor), (int) (ih * factor), Color.white, this);

    graphics2D.setColor(Color.red);
    graphics2D.setFont(new Font("Arial", Font.PLAIN, 12));

    if (dorsal1.empty() == false) {
      graphics2D.drawString("5th gill top", (int) (dorsal1.x * factor) + 10 + xOffset, (int) (dorsal1.y * factor) - 20);
      graphics2D.drawOval((int) (dorsal1.x * factor) - 10 + xOffset, (int) (dorsal1.y * factor) - 10, 20, 20);
      graphics2D.fill(new Ellipse2D.Double((dorsal1.x * factor) - 1 + xOffset, (dorsal1.y * factor) - 1, 3, 3));
    } else if (dotSelection) {
      graphics2D.drawString("Click the top of the 5th gill.", 100 + xOffset, 100);
    }
    if (dorsal2.empty() == false) {
      graphics2D.drawString(("posterior pectoral " + side), (int) (dorsal2.x * factor) + 10 + xOffset, (int) (dorsal2.y * factor) - 20);
      graphics2D.drawOval((int) (dorsal2.x * factor) - 10 + xOffset, (int) (dorsal2.y * factor) - 10, 20, 20);
      graphics2D.fill(new Ellipse2D.Double(dorsal2.x * factor - 1 + xOffset, dorsal2.y * factor - 1, 3, 3));
    } else if (!dorsal1.empty() && dotSelection) {
      graphics2D.drawString("Click the posterior point where the pectoral fin meets the body.", 100 + xOffset, 100);
    }
    if (pelvic.empty() == false) {
      graphics2D.drawString("5th gill bottom", (int) (pelvic.x * factor) + 10 + xOffset, (int) (pelvic.y * factor) + 20);
      graphics2D.drawOval((int) (pelvic.x * factor) - 10 + xOffset, (int) (pelvic.y * factor) - 10, 20, 20);
      graphics2D.fill(new Ellipse2D.Double(pelvic.x * factor - 1 + xOffset, pelvic.y * factor - 1, 3, 3));
    } else if (!dorsal1.empty() && !dorsal2.empty() && dotSelection) {
      graphics2D.drawString("Click the bottom of the 5th gill.", 100 + xOffset, 100);
    }

    if (spotcnt > 0)
      if (spotcnt < MAX_SPOT_CNT)
        graphics2D.drawString(spotcnt + " points selected", 50 + xOffset, 50);
      else
        graphics2D.drawString(spotcnt + " points selected. Maximum reached", 50 + xOffset, 50);

    if (dotSelection == true)
      graphics2D.drawImage(cursor, 5, 15, 25, 25, null, this);

    if (fileSaved == false)
      graphics2D.drawImage(excl, 5, 40, 25, 25, null, this);

    for (int i = 0; i < spotcnt; i++)
      graphics2D.fill(new Ellipse2D.Double(spots[i].x * factor - 1 + xOffset, spots[i].y * factor - 1, 5, 5));
  }

  public void doAffine() {
    //calcAffine(dorsal1.x, dorsal1.y, dorsal2.x, dorsal2.y, pelvic.x, pelvic.y, matrix);

    //for(int i=0; i<spotcnt; i++) {
    //normspots[i].x = matrix[0]*spots[i].x + matrix[1]*spots[i].y + matrix[2];
    //normspots[i].y = matrix[3]*spots[i].x + matrix[4]*spots[i].y + matrix[5];
    //}
  }

  private void calcDisplayFactor() {
    if (img == null)
      return;

    iw = (double) img.getWidth(this);
    ih = (double) img.getHeight(this);

    if (iw / ih < 1020.0 / 700.0)
      factor = 700 / ih;
    else
      factor = 1020 / iw;

    xOffset = (int) ((1010 - iw * factor) / 2.0);
  }

  public boolean getFileSaved() {
    return fileSaved;
  }


  public Point2D getDorsal1() {
    return dorsal1;
  }

  public Point2D getDorsal2() {
    return dorsal2;
  }

  public Point2D getPelvic() {
    return pelvic;
  }

  public Point2D[] getSpots() {
    return spots;
  }

  public String getSide() {
    return side;
  }

  public void setSide(String side) {
    this.side = side;
  }

}

