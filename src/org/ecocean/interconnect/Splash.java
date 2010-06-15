package org.ecocean.interconnect;
import java.awt.*;
import javax.swing.*;

public class Splash extends JPanel {
    Image img = null;
    JFrame frame = null;
    
    public Splash() {
        ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("/images/splash.gif"));
        img = imageIcon.getImage();
        
        frame = new JFrame();
        frame.setUndecorated(true);
        frame.getRootPane().setWindowDecorationStyle(JRootPane.FRAME);        
        frame.setContentPane(this);
        frame.setSize(new Dimension(836, 478));
        frame.setResizable(false);
        frame.setFocusable(false);
        frame.setLocation(300, 300);
        repaint();
        frame.setVisible(true);
        
        long t = System.currentTimeMillis();
        while(System.currentTimeMillis() - t < 3000);
		dispose();
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(img, 0, 0, 836, 478, Color.white, this);
    }
    
    public void dispose() {
        frame.dispose();
    }
}
