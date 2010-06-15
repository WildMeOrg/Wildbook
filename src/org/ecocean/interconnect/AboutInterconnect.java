package org.ecocean.interconnect;


import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class AboutInterconnect extends JPanel implements ActionListener {
    static JFrame frame = null;
    static Image img = null;
    
    public AboutInterconnect() {
        if(frame != null)
            frame.dispose();
        frame = new JFrame("About ECOCEAN Interconnect");
        frame.setContentPane(this);
        frame.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { frame.dispose(); frame = null; } });
        frame.setSize(new Dimension(600, 425));
        frame.setResizable(false);                                            
        frame.setLocation(200, 200);
        
        ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("/images/interconnect.gif"));
        frame.setIconImage(imageIcon.getImage());      
        frame.setVisible(true);
    }

  	public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Color bg = new Color(37, 0, 134);
        setBackground(bg);
     	Graphics2D graphics2D = (Graphics2D) g;
       	graphics2D.setColor(new Color(255, 255, 192));

        if(img == null)
        {
           ImageIcon imageIcon = new ImageIcon(this.getClass().getResource("/images/interconnect.gif"));
           img = imageIcon.getImage();
        }
               
        g.drawImage(img, 0, 0, 600, 400, bg, this);
}
    
    public void actionPerformed(ActionEvent e) {
        frame.dispose();
        frame = null;
    }
}
