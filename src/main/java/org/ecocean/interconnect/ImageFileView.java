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
import javax.swing.filechooser.FileView;
import java.io.File;


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
      } else if (extension.equals(Utils.gif)) {
        type = "GIF Image";
      } else if (extension.equals(Utils.tiff) ||
        extension.equals(Utils.tif)) {
        type = "TIFF Image";
      } else if (extension.equals(Utils.png)) {
        type = "PNG Image";
      } else if (extension.equals(Utils.bmp)) {
        type = "BMP Image";
      }
    }
    return type;
  }

  public Icon getIcon(File f) {
    String extension = Utils.getExtension(f);

    if (extension == null)
      return null;

    if (extension.equals(Utils.jpeg) || extension.equals(Utils.jpg) || extension.equals(Utils.gif) ||
      extension.equals(Utils.tiff) || extension.equals(Utils.tif) || extension.equals(Utils.png) || extension.equals(Utils.bmp)) {
      File ftmp = new File(f.getPath().substring(0, f.getPath().lastIndexOf('.')) + ".fgp");
      if (ftmp.exists())
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
