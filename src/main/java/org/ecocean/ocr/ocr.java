package org.ecocean.ocr;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import net.sourceforge.tess4j.*;

import org.ecocean.media.MediaAsset;
import org.ecocean.media.YouTubeAssetStore;
import org.ecocean.YouTube;

public class ocr {
  
  public static ArrayList<File> makeFilesFrames(ArrayList<MediaAsset> frames){
    
    
    ArrayList<File> filesFrames = new ArrayList<File>();
    
    for (MediaAsset frame : frames) {
        // this gives the file:   frame.localPath().toFile()
      filesFrames.add(frame.localPath().toFile());
    }
    return filesFrames;
    
  }
  public static String getTextFrames(ArrayList<File> filesFrames) {
    
    
    ArrayList<String> framesTexts = new ArrayList<String>();

    for (File fileFrame : filesFrames) {
//      File imageFile = new File("image"); //pass a file name or path to file
          ITesseract instance = new Tesseract();  // JNA Interface Mapping
          try {
              String frameText = instance.doOCR(fileFrame); //or do I skip creating file and pass image here?
              System.out.println(frameText);
              framesTexts.add(frameText);
          } catch (TesseractException e) {
              System.err.println(e.getMessage());
          }
  }
     System.out.println(framesTexts.size());
     System.out.println(framesTexts);
     
//     String[] arrayTexts = new String[framesTexts.size()];
//     arrayTexts = framesTexts.toArray(arrayTexts);
//     for (int i = 0; i < arrayTexts.length; i++) {
//       for (int j = i+1; j < arrayTexts.length; j++) {
//         if (arrayTexts[i].equals(arrayTexts[j])) {
//           arrayTexts = ArrayUtils.removeElement(arrayTexts,//key );
//     }
     List<String> al = new ArrayList<>();
  // add elements to al, including duplicates
     Set<String> hs = new HashSet<>();
     hs.addAll(framesTexts);
     framesTexts.clear();
     framesTexts.addAll(hs);

     
     String ocrRemarks = String.join(",", framesTexts);
     

     return ocrRemarks; 

  }

}
