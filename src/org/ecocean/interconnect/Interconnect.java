package org.ecocean.interconnect;

import javax.swing.*;
import javax.swing.filechooser.*;


import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.util.*;
import java.io.File;

public class Interconnect extends JPanel {
    static JFrame frame;
    static SharkPanel sp;
    static JFileChooser chooser;
    static String transmitToURL="";

    Dimension preferredSize = new Dimension(1024, 756);
    ImageFileFilter imfilter;
    
    public Interconnect() {
        // Create Filter
        imfilter = new ImageFileFilter(new String[] {"jpg", "gif"}, "JPEG or GIF Image Files");
        imfilter.setExtensionListInDescription(true);
        
        String topdir = System.getenv("I3S_DATA");
        if(topdir == null || topdir == "") {
            //JOptionPane.showMessageDialog(null, "Variable I3S_DATA has not been set. Please see the Interconnect manual for instructions.");
            //exit();
        }

        //File f = new File(topdir);
        //if(!f.exists() || !f.isDirectory()) {
            //JOptionPane.showMessageDialog(null, "Variable I3S_DATA does not refer to an existing directory. Please see the Interconnect manual for instructions.");
            //exit();
        //}
                
        chooser = new JFileChooser(topdir);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setMultiSelectionEnabled(false);
        chooser.addChoosableFileFilter(imfilter);
        chooser.setFileView(new ImageFileView());
        chooser.setAccessory(new ImagePreview(chooser));
    }
    
    static public String selectImage()
    {
        chooser.setSelectedFile(null);

        int retval = chooser.showDialog(frame, null);
        if (retval == JFileChooser.APPROVE_OPTION) {
            File theFile = chooser.getSelectedFile();
            if (theFile != null) {
                return theFile.getPath();
            }
        }
        
        return null;
    }

    public void buildUI(Container container) {
        sp = new SharkPanel(System.getenv("I3S_DATA"));
        container.add(sp, BorderLayout.CENTER);
    }

    public Dimension getPreferredSize() {
        return preferredSize;
    }

    static public class SharkMenu implements ActionListener, ItemListener {
        static String filename = null;
        static String previous = null;
    
        final String Item1 = "File";
        final String Item1_1 = "Open left-side shark image";
        final String Item1_7 = "Open right-side shark image";
        final String Item1_2 = "Open previous image";
        final String Item1_3 = "Save fingerprint";
        final String Item1_4 = "Close shark image";
        final String Item1_5 = "Spot selection";
        final String Item1_6 = "Exit";
        final String Item2 = "Search";
        //final String Item2_1 = "I3S Quick search";
        final String Item2_2 = "I3S Exhaustive search";
        final String Item3 = "Database";
        final String Item3_3 = "Submit online to ECOCEAN Library";
        final String Item3_1 = "Insert in local database";
        final String Item3_2 = "Update local database";
        final String Item4 = "ImageOps";
        final String Item4_1 = "Sharpen";
        final String Item5 = "Help";
        final String Item5_1 = "About Interconnect...";
        
        public JMenuBar createMenuBar() {
            JMenuBar menuBar = new JMenuBar();
            // Menu #1
            JMenu menu = new JMenu(Item1);
            menu.setMnemonic(KeyEvent.VK_F);
            menuBar.add(menu);
    
            // SubItem 1.1
            JMenuItem menuItem = new JMenuItem(Item1_1, KeyEvent.VK_O);
            menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));            
            menuItem.addActionListener(this);
            menu.add(menuItem);
            
            // SubItem 1.7
            menuItem = new JMenuItem(Item1_7, KeyEvent.VK_R);
            menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.CTRL_MASK));            
            menuItem.addActionListener(this);
            menu.add(menuItem);
            
            // SubItem 1.2
            //menuItem = new JMenuItem(Item1_2, KeyEvent.VK_R);
            //menuItem.addActionListener(this);
            //menu.add(menuItem);
            
            // SubItem 1.3
            menuItem = new JMenuItem(Item1_3, KeyEvent.VK_S);
            menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));            
            menuItem.addActionListener(this);
            menu.add(menuItem);
            
            // SubItem 1.4
            menuItem = new JMenuItem(Item1_4, KeyEvent.VK_C);
            menuItem.addActionListener(this);
            menu.add(menuItem);
        
            //menu.addSeparator();

            JCheckBoxMenuItem cbMenuItem = new JCheckBoxMenuItem(Item1_5);
            cbMenuItem.setMnemonic(KeyEvent.VK_P);
            cbMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, ActionEvent.CTRL_MASK));            
            cbMenuItem.addItemListener(this);
            //menu.add(cbMenuItem);
            sp.toggleDotSelection();
            
            //menu.addSeparator();

            // SubItem 1.5
            menuItem = new JMenuItem(Item1_6, KeyEvent.VK_X);
            menuItem.addActionListener(this);
            menu.add(menuItem);
            
            // Menu #2
            menu = new JMenu(Item2);
            menu.setMnemonic(KeyEvent.VK_S);
            //menuBar.add(menu);
    
            // SubItem 2.1
            //menuItem = new JMenuItem(Item2_1, KeyEvent.VK_Q);
            // menuItem.addActionListener(this);
            //menu.add(menuItem);

            // SubItem 2.2
            menuItem = new JMenuItem(Item2_2, KeyEvent.VK_E);
            menuItem.addActionListener(this);
            //menu.add(menuItem);

            // Menu #3
            menu = new JMenu(Item3);
            menu.setMnemonic(KeyEvent.VK_D);
            menuBar.add(menu);
            
            // SubItem 3.3
            menuItem = new JMenuItem(Item3_3, KeyEvent.VK_T);
            menuItem.addActionListener(this);
            menu.add(menuItem);
    
            // SubItem 3.1
            menuItem = new JMenuItem(Item3_1, KeyEvent.VK_I);
            menuItem.addActionListener(this);
            //menu.add(menuItem);

            // SubItem 3.2
            menuItem = new JMenuItem(Item3_2, KeyEvent.VK_U);
            menuItem.addActionListener(this);
            //menu.add(menuItem);

            // Menu #4
            menu = new JMenu(Item4);
            menu.setMnemonic(KeyEvent.VK_I);
            //menuBar.add(menu);
    
            // SubItem 4.1
            cbMenuItem = new JCheckBoxMenuItem(Item4_1);
            cbMenuItem.setMnemonic(KeyEvent.VK_S);
            cbMenuItem.addActionListener(this);
            //menu.add(cbMenuItem);            

            // Menu #5
            menu = new JMenu(Item5);
            menu.setMnemonic(KeyEvent.VK_H);
            menuBar.add(menu);
    
            // SubItem 5.1
            menuItem = new JMenuItem(Item5_1, KeyEvent.VK_A);
            menuItem.addActionListener(this);
            menu.add(menuItem);

            return menuBar;
        }
    
        public void actionPerformed(ActionEvent e) {
            StringBuffer mess = new StringBuffer();
            JMenuItem source = (JMenuItem)(e.getSource());
            
            if(source.getText() == Item1_1) {
                previous = filename;
                filename = selectImage();
                if(filename != null)
                {
                    saveResultsPrompt();
                    if(sp.updateImage(filename, mess) == false)
                        JOptionPane.showMessageDialog(frame, mess);
                    else 
                        frame.setTitle("ECOCEAN Interconnect (v1.2)      File: " + filename);
                        sp.setSide("left");
                }
            }
            if(source.getText() == Item1_7) {
                previous = filename;
                filename = selectImage();
                if(filename != null)
                {
                    saveResultsPrompt();
                    if(sp.updateImage(filename, mess) == false)
                        JOptionPane.showMessageDialog(frame, mess);
                    else 
                        frame.setTitle("ECOCEAN Interconnect (v1.2)      File: " + filename);
                        sp.setSide("right");
                }
            }
            if(source.getText() == Item1_2) {
                if(previous == null)
                    JOptionPane.showMessageDialog(frame, "No previous image file available");
                else
                {
                    if(sp.updateImage(previous, mess) == false)
                        JOptionPane.showMessageDialog(frame, mess);
                    String tmp = previous;
                    previous = filename;
                    filename = tmp;
                }
            }
            if(source.getText() == Item1_3) {
                if(sp.writeFingerprint(mess) == false)
                    JOptionPane.showMessageDialog(frame, mess);
            }
            if(source.getText() == Item1_4) {
                saveResultsPrompt();              
                frame.setTitle("ECOCEAN Interconnect (v1.2)");
                sp.close();
            }
            if(source.getText() == Item1_6) {
                exit();
            }
            //if(source.getText() == Item2_1) {
                //if(sp.compareWithDatabase(mess, false) == false)
                    //JOptionPane.showMessageDialog(frame, mess);
            //}
            if(source.getText() == Item2_2) {
                if(sp.compareWithDatabase(mess, true) == false)
                    JOptionPane.showMessageDialog(frame, mess);
            }
            if(source.getText() == Item3_1) {
                if(sp.insertInDatabase(mess) == false)
                    JOptionPane.showMessageDialog(frame, mess);
            }    
            
            //new in Interconnect
            
            //send spot pattern to the ECOCEAN Library
            if(source.getText() == Item3_3) {
                if(sp.send2library(mess, transmitToURL) == false)
                    JOptionPane.showMessageDialog(frame, mess);
            }     
                           
            if(source.getText() == Item3_2)
                sp.updateDatabase();
            if(source.getText() == Item4_1)
                sp.toggleSharpen();
            if(source.getText() == Item5_1)
            {
                new AboutInterconnect();
            }
        }
        public void itemStateChanged(ItemEvent e) {
            JMenuItem source = (JMenuItem)(e.getSource());
    
        }
    }

    private static void saveResultsPrompt() {
        StringBuffer mess = new StringBuffer();

        if(sp.getFileSaved() == false) {
            switch (JOptionPane.showConfirmDialog(frame, "Do you want to save the selected spots?", "Saving results?", JOptionPane.YES_NO_OPTION)) {
                case JOptionPane.YES_OPTION:
                    if(sp.writeFingerprint(mess) == false)
                        JOptionPane.showMessageDialog(null, mess);
                    break;
                case JOptionPane.CANCEL_OPTION:
                    return;
                case JOptionPane.NO_OPTION:
                    break;
            }
        }
    }
    
    public static void exit() {
        sp.closeOnExit();
        saveResultsPrompt();
        System.exit(0);    
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception exc) {
            System.err.println("Warning: could not load system look-and-feel.");
			JOptionPane.showMessageDialog(null, "Warning: could not load system look-and-feel.\n");
        }

        transmitToURL=args[0];
        
        Splash splash = new Splash();

        frame = new JFrame("ECOCEAN Interconnect (v1.2)");
        SharkMenu sharkmenu = new SharkMenu();
        final Interconnect controller = new Interconnect();

        controller.buildUI(frame.getContentPane());

        frame.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { controller.exit(); } });

        frame.setJMenuBar(sharkmenu.createMenuBar());

        frame.setSize(new Dimension(1024, 756));

        ImageIcon imageIcon = new ImageIcon(controller.getClass().getResource("/images/icon.gif"));
        frame.setIconImage(imageIcon.getImage());

        frame.setLocation(0, 0);
        frame.setVisible(true);
    }
}


