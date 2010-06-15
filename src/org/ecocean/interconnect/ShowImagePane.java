package org.ecocean.interconnect;

import java.awt.*;
import javax.swing.*;
import java.awt.image.*;

class ShowImagePane extends JPanel {
    private Image img = null;
    private String fim= null;
    public ShowImagePane(String f) {
        GetImageFile gif = new GetImageFile(f);
        fim = gif.getImageString();
        ImageIcon imageIcon = new ImageIcon(fim);
        img = imageIcon.getImage();
    }
    
    private void paintImage(Graphics2D g) {
        double iw = (double) img.getWidth(this);
        double ih = (double) img.getHeight(this);
        double factor = 600 / ih;
         System.out.println("About to draw!");
        g.drawImage(img, 0, 0, (int)(iw*factor), (int)(ih*factor), new Color(37, 0, 134), this);
    }

  	public void paintComponent(Graphics g) {
        super.paintComponent(g);
     	Graphics2D graphics2D = (Graphics2D) g;
       	graphics2D.setColor(Color.red);
        
        if(img != null)
            paintImage(graphics2D);
        graphics2D.drawString(fim, 400, 30);
    }
}

