package org.ecocean.interconnect;
import java.io.File;
import javax.swing.*;
import javax.swing.filechooser.*;


/* ImageFileView.java is a 1.4 example used by FileChooserDemo2.java. */
public class ImageFileView extends FileView {

    ImageIcon jpgIcon = new ImageIcon(this.getClass().getResource("/images/jpgicon.gif")); //Utils.createImageIcon("images/jpgicon.gif");
    ImageIcon gifIcon = new ImageIcon(this.getClass().getResource("/images/gificon.gif")); //Utils.createImageIcon("images/gificon.gif");
    ImageIcon i3sIcon = new ImageIcon(this.getClass().getResource("/images/sharkicon.gif")); //Utils.createImageIcon("images/sharkicon.gif");

    public String getName(File f) {
        return null; //let the L&F FileView figure this out
    }

    public String getDescription(File f) {
        return getTypeDescription(f); //let the L&F FileView figure this out
    }

    public Boolean isTraversable(File f) {
        return null; //let the L&F FileView figure this out
    }

    public String getTypeDescription(File f) {
        String extension = Utils.getExtension(f);
        String type = null;

        if (extension != null) {
            if (extension.equals(Utils.jpeg) ||
                extension.equals(Utils.jpg)) {
                type = "JPEG Image";
            } else if (extension.equals(Utils.gif)){
                type = "GIF Image";
            } else if (extension.equals(Utils.tiff) ||
                       extension.equals(Utils.tif)) {
                type = "TIFF Image";
            } else if (extension.equals(Utils.png)){
                type = "PNG Image";
            } else if (extension.equals(Utils.bmp)){
                type = "BMP Image";
            }
        }
        return type;
    }

    public Icon getIcon(File f) {
        String extension = Utils.getExtension(f);

        if (extension == null)
            return null;

        if(extension.equals(Utils.jpeg) || extension.equals(Utils.jpg) || extension.equals(Utils.gif) || 
           extension.equals(Utils.tiff) || extension.equals(Utils.tif) || extension.equals(Utils.png) || extension.equals(Utils.bmp)) {
            File ftmp = new File(f.getPath().substring(0, f.getPath().lastIndexOf('.')) + ".fgp");
            if(ftmp.exists())
                return i3sIcon;
        }

        if (extension.equals(Utils.jpeg) || extension.equals(Utils.jpg)) {
                return jpgIcon;
        } else if (extension.equals(Utils.gif)) {
            return gifIcon;
        }

        return null;
    }
}
