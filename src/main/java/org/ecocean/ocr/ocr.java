package org.ecocean.ocr;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

//import net.sourceforge.tess4j.*;

import org.ecocean.media.MediaAsset;
import org.ecocean.media.YouTubeAssetStore;
import org.ecocean.CommonConfiguration;
import org.ecocean.YouTube;

import javax.imageio.ImageIO;

public class ocr {

  public static ArrayList<File> makeFilesFrames(ArrayList<MediaAsset> frames){
    if (frames == null) throw new RuntimeException("Not media assets for this video?");
    try {
      ArrayList<File> filesFrames = new ArrayList<File>();
      //return null if nothing comes with try catch block
      for (MediaAsset frame : frames) {
          // this gives the file:   frame.localPath().toFile()
        filesFrames.add(frame.localPath().toFile());
      }
      return filesFrames;

    } catch (Exception e) {
      System.out.println("caught exception while trying to convert frames into jpg files.");
    }
    return null;

  }
  public static String getTextFrames(ArrayList<File> filesFrames, String context) {
    return null;  //disabling the below because we dont want to use tess4j right now
/*

//    ArrayList<String> framesTexts = new ArrayList<String>();
    try {

      StringBuffer framesTexts= new StringBuffer("");
      for (File fileFrame : filesFrames) {
//        File imageFile = new File("image"); //pass a file name or path to file
            ITesseract instance = new Tesseract();  // JNA Interface Mapping
            instance.setDatapath(CommonConfiguration.getProperty("tesseractDataPath", context));// JNA Interface Mapping
            //use cube and tesseract - high quality
            instance.setOcrEngineMode(2);
            try {
                String frameText = instance.doOCR(fileFrame);
                //System.out.println(frameText);
//                framesTexts.add(frameText);
                if (!(framesTexts.toString()).contains(frameText)) {
                  framesTexts.append(frameText+ " ");
                }
            } catch (TesseractException e) {
                System.err.println(e.getMessage());
            }
    }
      String ocrRemarks= framesTexts.toString();
      return ocrRemarks;

    } catch (Exception e) {
      System.out.println("Exception while trying to convert fileFrames into text.");
    }
     return null;
*/

  }

}
