package org.ecocean.ocr;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import net.sourceforge.tess4j.*;

import org.ecocean.media.MediaAsset;
import org.ecocean.media.YouTubeAssetStore;
import org.ecocean.CommonConfiguration;
import org.ecocean.YouTube;

import javax.imageio.ImageIO;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.awt.image.BufferedImage;

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
                //String frameText = instance.doOCR(fileFrame);
                BufferedImage bimg = ImageIO.read(fileFrame);
                int width          = bimg.getWidth();
                int height         = bimg.getHeight();
                String frameText = instance.doOCR(width, height, getByteBufferFromFile(fileFrame), null, 8);
                
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

  }
  
  public static ByteBuffer getByteBufferFromFile(File file){
    
    FileInputStream fIn=null;
    FileChannel fChan=null;
    long fSize;
    ByteBuffer mBuf=null;

    try {
      fIn = new FileInputStream("test.txt");
      fChan = fIn.getChannel();
      fSize = fChan.size();
      mBuf = ByteBuffer.allocate((int) fSize);
      fChan.read(mBuf);
      mBuf.rewind();
      for (int i = 0; i < fSize; i++)
        System.out.print((char) mBuf.get());

    } catch (IOException exc) {
      System.out.println(exc);
    }    
    finally{
      try{
        if(fChan!=null)fChan.close(); 
        if(fIn!=null)fIn.close(); 
      }
      catch(Exception e){}
    }
    return mBuf;
  }

}
