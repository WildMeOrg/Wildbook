package org.ecocean.ai.ocr.google;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Feature.Type;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.nio.file.Files;

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
  
  /*
  public static String getTextFrames(List<byte[]> bytesFrames, String context) {
    // Instantiates Stella's credentials
    Properties googleProps = ShepherdProperties.getProperties("googleKeys.properties","");
    
    
    String CLIENT_ID= googleProps.getProperty("clientIDVision");
    String CLIENT_SECRET= googleProps.getProperty("clientSecretVision");
    String refreshToken = googleProps.getProperty("refreshTokenVision");
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
*/
  
  //Depends on the system variable GOOGLE_APPLICATION_CREDENTIALS to get JSON key for service account authentication
  public static String detectText(List<byte[]> byteFrames) throws Exception, IOException {
    List<AnnotateImageRequest> requests = new ArrayList<AnnotateImageRequest>();
    StringBuffer framesTexts= new StringBuffer("");
    for (byte[] byteFrame : byteFrames) {
      ByteString imgBytes = ByteString.copyFrom(byteFrame);
  
      Image img = Image.newBuilder().setContent(imgBytes).build();
      Feature feat = Feature.newBuilder().setType(Type.TEXT_DETECTION).build();
      AnnotateImageRequest request = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
      requests.add(request);
    }
    
    //with requests now populated, send batches of 5 to ensure we don't send massive packets.
    int numRequests = requests.size();
  
    List<AnnotateImageRequest> requestBatch = new ArrayList<AnnotateImageRequest>();
    for(int i=0;i<numRequests;i++) {
      
      if((requestBatch.size()==5)||(i==(numRequests-1))) {
        framesTexts.append(getTextForBatch(requestBatch));
        requestBatch = new ArrayList<>();
      }
      else {
        requestBatch.add(requests.get(i));
      }
    }
    //wrap it up
    String returnText=framesTexts.toString();
    System.out.println("Google OCR output: "+returnText);
    return returnText;
  }
  
  private static String getTextForBatch(List<AnnotateImageRequest> requestBatch) {
    StringBuffer framesTexts= new StringBuffer("");
    try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
      BatchAnnotateImagesResponse response = client.batchAnnotateImages(requestBatch);
      List<AnnotateImageResponse> responses = response.getResponsesList();

      for (AnnotateImageResponse res : responses) {
        if (res.hasError()) {
          System.out.println("Error: %s\n"+ res.getError().getMessage());
        }
        else {
          
          for (EntityAnnotation annotation : res.getTextAnnotationsList()) {
            
            if (!(framesTexts.toString()).contains(annotation.getDescription())) { 
              //System.out.println(annotation.getDescription());
              framesTexts.append(annotation.getDescription()+ " ");
            }
          }
        }
        
      }
   }
   catch(Exception e) {
     System.out.println("GoogleOcr: couldn't instantiate a client!");
     e.printStackTrace();
   }
    
    //wrap it up
    String returnText=framesTexts.toString();
    //System.out.println("Google OCR output: "+returnText);
    return returnText;
  }
  

}
