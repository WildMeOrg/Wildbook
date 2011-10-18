package org.ecocean;

import java.io.File;
import java.io.IOException;

public class SinglePhotoVideo extends DataCollectionEvent {


  private static final long serialVersionUID = 7999349137348568641L;
  private String filename;
  private String fullFileSystemPath;
  private static String type="SinglePhotoVideo";
  private String copyrightOwner;
  private String copyrightStatement;

  /**
   * Empty constructor required for JDO persistence
   */
  public SinglePhotoVideo(){}
  
  /*
   * Required constructor for instance creation
   */
  public SinglePhotoVideo(String correspondingEncounterNumber, String filename, String fullFileSystemPath) {
    super(correspondingEncounterNumber, type);
    this.filename=filename;
    this.fullFileSystemPath=fullFileSystemPath;
  }
  
  /**
   * Returns the photo or video represented by this object as a java.io.File
   * This is a convenience method.
   * @return java.io.File
   */
  public File getFile(){
    if(fullFileSystemPath!=null){
        return (new File(fullFileSystemPath));
    }
    else{return null;}
  }
  
  public String getFilename(){return filename;}
  public void setFilename(String newName){this.filename=newName;}
  
  public String getFullFileSystemPath(){return fullFileSystemPath;}
  public void setFullFileSystemPath(String newPath){this.fullFileSystemPath=newPath;}
  
  public String getCopyrightOwner(){return copyrightOwner;}
  public void setCopyrightOwner(String owner){copyrightOwner=owner;}
  
  public String getCopyrightStatement(){return copyrightStatement;}
  public void setCopyrightStatement(String statement){copyrightStatement=statement;}
  
}
