package org.ecocean.interconnect;
import java.util.*;
import java.io.*;

class GetImageFile {
    String s = null;
    String ext = null;
    
    public GetImageFile(String buf) {
    	
       s = buf.replaceAll(".fgp", ".jpg");
       ext = new String(".jpg");
       File f = new File(s);
       if(f.exists())
            return;
            
       s = buf.replaceAll(".fgp", ".JPG");
       ext = new String(".JPG");
       f = new File(s);
       if(f.exists())
           return;
           
       s = buf.replaceAll(".fgp", ".gif");
       ext = new String(".gif");
       f = new File(s);
       if(f.exists())
           return;
           
       s = buf.replaceAll(".fgp", ".GIF");
       ext = new String(".GIF");       
       f = new File(s);
       if(f.exists())
           return;
           
       s = buf.replaceAll(".fgp", ".bmp");
       ext = new String(".bmp");
       f = new File(s);
       if(f.exists())
           return;
           
       s = buf.replaceAll(".fgp", ".BMP");
       ext = new String(".BMP");
       f = new File(s);
       if(f.exists())
           return;
           
       s = buf.replaceAll(".fgp", ".png");
       ext = new String(".png");
       f = new File(s);
       if(f.exists())
           return;
       s = buf.replaceAll(".fgp", ".PNG");
       ext = new String(".PNG");
       f = new File(s);
       if(f.exists())
           return;
       s = buf.replaceAll(".fgp", ".tif");
       ext = new String(".tif");       
       f = new File(s);
       if(f.exists())
           return;
       s = buf.replaceAll(".fgp", ".TIF");
       ext = new String(".TIF");
       f = new File(s);
       if(f.exists())
           return;
       s = new String("");
       return;
    }
        
    public String getImageString() {
        return s;
    }
    public String getImageExtension() {
        return ext;
    }
}
