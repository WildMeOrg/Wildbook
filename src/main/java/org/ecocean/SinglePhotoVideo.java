package org.ecocean;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ecocean.Util;
import org.ecocean.genetics.TissueSample;
import org.ecocean.Encounter;
import org.ecocean.servlet.ServletUtilities;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.output.*;
import org.apache.commons.io.FilenameUtils;


public class SinglePhotoVideo extends DataCollectionEvent {

  private static final long serialVersionUID = 7999349137348568641L;
  private PatterningPassport patterningPassport;
  private String filename;
  private String fullFileSystemPath;
  
  //use for User objects
  String correspondingUsername;
  
  //Use for Story objects
  String correspondingStoryID;
  
  /*
  private String thumbnailFilename;
  private String thumbnailFullFileSystemPath;
  */
  
  private static String type = "SinglePhotoVideo";
  private String copyrightOwner;
  private String copyrightStatement;
  private List<Keyword> keywords;
  
  /**
   * Empty constructor required for JDO persistence
   */
  public SinglePhotoVideo(){}
  
  /*
   * Required constructor for instance creation
   */
  public SinglePhotoVideo(String correspondingEncounterNumber, String filename, String fullFileSystemPath) {
    super(correspondingEncounterNumber, type);
    this.filename = filename;
    this.fullFileSystemPath = fullFileSystemPath;
  }
  
  public SinglePhotoVideo(String correspondingEncounterNumber, File file) {
    super(correspondingEncounterNumber, type);
    this.filename = file.getName();
    this.fullFileSystemPath = file.getAbsolutePath();
  }

	public SinglePhotoVideo(Encounter enc, FileItem formFile, String context, String dataDir) throws Exception {
//TODO FUTURE: should use context to find out METHOD of storage (e.g. remote, amazon, etc) and switch accordingly?
    super(enc.getEncounterNumber(), type);

		String encID = enc.getEncounterNumber();
		if ((encID == null) || encID.equals("")) {
			throw new Exception("called SinglePhotoVideo(enc) with Encounter missing an ID");
		}

		//TODO generalize this when we encorporate METHOD?
		//File dir = new File(dataDir + File.separator + correspondingEncounterNumber.charAt(0) + File.separator + correspondingEncounterNumber.charAt(1), correspondingEncounterNumber);
		File dir = new File(enc.dir(dataDir));
		if (!dir.exists()) { dir.mkdirs(); }

		//String origFilename = new File(formFile.getName()).getName();
		this.filename = ServletUtilities.cleanFileName(new File(formFile.getName()).getName());

		File file = new File(dir, this.filename);
    this.fullFileSystemPath = file.getAbsolutePath();
		formFile.write(file);  //TODO catch errors and return them, duh
System.out.println("full path??? = " + this.fullFileSystemPath + " WRITTEN!");
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
  

	public String asUrl(Encounter enc, String baseDir) {
		return "/" + enc.dir(baseDir) + "/" + this.filename;
	}

  /*
  public File getThumbnailFile(){
    if(thumbnailFullFileSystemPath!=null){
        return (new File(thumbnailFullFileSystemPath));
    }
    else{return null;}
  }
  */
  
  public String getFilename(){return filename;}
  public void setFilename(String newName){this.filename=newName;}
  
  public String getFullFileSystemPath(){return fullFileSystemPath;}
  public void setFullFileSystemPath(String newPath){this.fullFileSystemPath=newPath;}
  
  public String getCopyrightOwner(){return copyrightOwner;}
  public void setCopyrightOwner(String owner){copyrightOwner=owner;}
  
  public String getCopyrightStatement(){return copyrightStatement;}
  public void setCopyrightStatement(String statement){copyrightStatement=statement;}
  
   //public String getThumbnailFilename(){return (this.getDataCollectionEventID()+".jpg");}
  
  /*
  public void setThumbnailFilename(String newName){this.thumbnailFilename=newName;}
  
  public String getThumbnailFullFileSystemPath(){return thumbnailFullFileSystemPath;}
  public void setThumbnailFullFileSystemPath(String newPath){this.thumbnailFullFileSystemPath=newPath;}
  */
  
  public void addKeyword(Keyword dce){
    if(keywords==null){keywords=new ArrayList<Keyword>();}
    if(!keywords.contains(dce)){keywords.add(dce);}
  }
  public void removeKeyword(int num){keywords.remove(num);}
  public List<Keyword> getKeywords(){return keywords;}
  public void removeKeyword(Keyword num){keywords.remove(num);}

  public PatterningPassport getPatterningPassport() {
    if (patterningPassport == null) {
      patterningPassport = new PatterningPassport();
    }
    return patterningPassport;
  }
  
  public File getPatterningPassportFile() {
    File f = this.getFile();
    String xmlPath;
    String dirPath;
    if (f != null) {
      dirPath = f.getParent();
      xmlPath = dirPath + "/" + this.filename.substring(0,this.filename.indexOf(".")) + "_pp.xml";
    } else {
      return null; // no xml if no image!
    }
    
    File xmlFile = new File(xmlPath);
    if (xmlFile.isFile() == Boolean.FALSE) {
      return null; 
    } 
   
    return xmlFile;
  }

  /**
   * @param patterningPassport the patterningPassport to set
   */
  public void setPatterningPassport(PatterningPassport patterningPassport) {
    this.patterningPassport = patterningPassport;
  }
  
  public String getCorrespondingUsername(){return correspondingUsername;}
  public void setCorrespondingUsername(String username){this.correspondingUsername=username;}

  public String getCorrespondingStoryID(){return correspondingStoryID;}
  public void setCorrespondingStoryID(String userID){this.correspondingStoryID=userID;}

  
  
}
