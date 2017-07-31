package org.ecocean.ocr;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionScopes;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;

import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
//import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.ecocean.CommonConfiguration;
import org.ecocean.media.MediaAsset;

public class GoogleOcr {
  public static List<byte[]> makeBytesFrames(ArrayList<MediaAsset> frames){
    if (frames == null) throw new RuntimeException("Not media assets for this video?!");
    try {
      List<byte[]> bytesFrames = new ArrayList<byte[]>();
      //return null if nothing comes with try catch block
      for (MediaAsset frame : frames) {
        File fileFrame = frame.localPath().toFile();
        byte[] data = Files.readAllBytes(fileFrame.toPath());
          
        bytesFrames.add(data);
      }
      return bytesFrames;
      
    } catch (Exception e) {
      System.out.println("Yikes, exception while trying to convert frames into jpg bytes array. For more info check 'providin image' in here:https://cloud.google.com/vision/docs/request#providing_the_image");
    }
    return null;
    
  }
  public static String getTextFrames(List<byte[]> bytesFrames, String context) {
    // Instantiates Stella's credentials
    String CLIENT_ID= CommonConfiguration.getProperty("clientIDVision", context);
    String CLIENT_SECRET= CommonConfiguration.getProperty("clientSecretVision", context);
    String refreshToken = CommonConfiguration.getProperty("refreshTokenVision", context);
    System.out.println("Trying Google creds ID: " +CLIENT_ID+" secret:"+CLIENT_SECRET+" refreshoken:"+refreshToken);
    Vision vision = null;

    try {
      HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
      JsonFactory JSON_FACTORY = new JacksonFactory();
      Credential credential = new GoogleCredential.Builder()
               .setTransport(HTTP_TRANSPORT)
               .setJsonFactory(JSON_FACTORY)
               .setClientSecrets(CLIENT_ID, CLIENT_SECRET)
               .build();
           credential.setRefreshToken(refreshToken);
      vision = new Vision.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                   .setApplicationName("wildbook-ocr3")
                   .build();
      
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("oh no, why can't we access credentials!");
    }
    if (vision !=null) {
      try {
        StringBuffer framesTexts= new StringBuffer("");
        
        for (byte[] byteFrame : bytesFrames) {
         // Builds the image annotation request
          ImmutableList.Builder<AnnotateImageRequest> requests = ImmutableList.builder();                 
          requests.add(
                  new AnnotateImageRequest()
                      .setImage(new Image().encodeContent(byteFrame))
                      .setFeatures(ImmutableList.of(
                          new Feature()
                              .setType("TEXT_DETECTION"))));
                           
              Vision.Images.Annotate annotate =
                vision.images()
                    .annotate(new BatchAnnotateImagesRequest().setRequests(requests.build()));
              BatchAnnotateImagesResponse batchResponse = annotate.execute();
              
              if((batchResponse==null)||(batchResponse.getResponses()==null)||(batchResponse.getResponses().get(0).getTextAnnotations()==null)||(batchResponse.getResponses().get(0).getTextAnnotations().get(0).getDescription() == null)){
                //System.out.println("wait what, no text found in image?");
              }
              else {
                System.out.println(batchResponse.getResponses().get(0).getTextAnnotations().get(0).getDescription());
                String frameText= batchResponse.getResponses().get(0).getTextAnnotations().get(0).getDescription();
                if (!(framesTexts.toString()).contains(frameText)) {         
                  framesTexts.append(frameText+ " ");
                }
              }
            //alternative logic to iterate through images texts:                  
//            List<AnnotateImageResponse> responses = batchResponse.getResponses();
//            String frameText = "";
//            for (AnnotateImageResponse res : responses) {
//              if (res.getError() != null) {
//                System.out.printf("Error: %s\n", res.getError().getMessage());
//                return;
//              }
//              for (EntityAnnotation text : res.getTextAnnotations()) {
//                frameText += text.getDescription();
//              }
//              if (frameText.equals("")) {
//                System.out.printf("%s had no discernible text.\n");
//              }
//            }
                
        }              
        String ocrRemarks= framesTexts.toString();
        return ocrRemarks;
        
      } catch (Exception e) {
        e.printStackTrace();
        System.out.println("Exception while trying to convert fileFrames into text.");
      }
    }    
   return null; 
}

  

}
