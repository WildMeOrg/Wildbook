package org.ecocean.interconnect;
import java.awt.*;
import javax.swing.*;


import java.awt.image.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.io.*;
import java.text.NumberFormat;

class BothSharks extends JPanel {
    private Point2D s1[] = null;
    private Point2D s2[] = null;
    private int nrpairs = 0;
    private double score;
    private String orig = null;
    private String found = null;
    private Image origImg = null;
    private Image foundImg = null;
    private Image bgImg = null;
    
    public BothSharks(String forig, String ffound, Point2D _s1[], Point2D _s2[], int _pairs[], double _score) {
        s1 = new Point2D [_s1.length];
        s2 = new Point2D [_s2.length];
        
        for(int i=0; i<_s1.length; i++)
            s1[i] = new Point2D (_s1[i].x, _s1[i].y);
        for(int i=0; i<_s2.length; i++)
            s2[i] = new Point2D (_s2[i].x, _s2[i].y);

        for(nrpairs = 0; nrpairs < _pairs.length && _pairs[nrpairs] != -1; nrpairs++);
        score = _score;
        
        GetImageFile gif = new GetImageFile(forig);
        orig = gif.getImageString();
        gif = new GetImageFile(ffound);
        found = gif.getImageString();
        
        scale();
        
        ImageIcon imageIcon = new ImageIcon(orig);
        origImg = imageIcon.getImage();
        if(origImg == null)
            System.out.println("orig: " + orig);
        imageIcon = new ImageIcon(found);
        foundImg = imageIcon.getImage();        
    }
    
    void scale() {
        double minx = 0.0;
        double miny = 0.0;
        double maxx = Double.NEGATIVE_INFINITY;
        double maxy = Double.NEGATIVE_INFINITY;
        
        for(int i=0; i<s1.length; i++) {
            if(s1[i].empty())
                continue;
        }

    }

    private void paintImage(Graphics2D g, Image im, Point2D s[], int offsety) {
        double iw = (double) im.getWidth(this);
        double ih = (double) im.getHeight(this);
        double factor;
	if(iw/ih < 1020.0/700.0)
            factor = 280 / ih;
        else
            factor = 373.33 / iw;
        
	g.drawImage(im, 0, offsety, (int)(iw*factor), (int)(ih*factor), Color.white, this);
           
        for(int i=0 ; i<s.length; i++)
            if(s[i].empty() == false)
                g.fill(new Ellipse2D.Double(s[i].x*factor-1, s[i].y*factor-1 + offsety, 3, 3));            
    }

  	public void paintComponent(Graphics g) {
        super.paintComponent(g);
     	Graphics2D graphics2D = (Graphics2D) g;
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);

        //if(bgImg == null) {
            //ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("images/bg_LgreyTrans500.gif"));
            //bgImg = imageIcon.getImage();
        //}
        //graphics2D.drawImage(bgImg, 0, 0, 500, 500, new Color(192, 192, 192), this);
        
       	graphics2D.setColor(Color.red);
        graphics2D.drawString("Unknown shark: " + orig, 400, 130);
        graphics2D.drawString("Nr of spot pairs: " + nrpairs, 400, 170);
        graphics2D.drawString("Score: " + nf.format(score), 400, 190);
        
        if(origImg != null)
            paintImage(graphics2D, origImg, s1, 0);

        graphics2D.setColor(Color.blue);
        graphics2D.drawString("Found shark: " + found, 400, 410);
        
        if(foundImg != null)
            paintImage(graphics2D, foundImg, s2, 280);
        
        double factor1 = 270. / origImg.getHeight(this);
        double factor2 = 270. / foundImg.getHeight(this);
    }
}

