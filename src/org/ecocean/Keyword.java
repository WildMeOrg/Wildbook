package org.ecocean;

import java.util.Vector;

public class Keyword{
	
	//the primary key of the keyword
	private String indexname;
	
	//the visible descriptor of the keyword
	private String readableName;
	
	//a Vector of String relative paths to the photo file that the keyword applies to
	public Vector photos;
	
	/**
	 *empty constructor required by JDO Enhancer
	 */
	public Keyword() {}
	
	
	//use this constructor for new keywords
	public Keyword(String indexname, String readableName) {
		this.indexname=indexname;
		this.readableName=readableName;
		photos=new Vector();
		}
		
	public void removeImageName(String imageFile) {
		for(int i=0;i<photos.size();i++){
			String thisName=(String)photos.get(i);
			if(thisName.equals(imageFile)){
				photos.remove(i);
				i--;
				}
			}
		}	
		
	public String getReadableName(){
		return readableName;
	}
	
	public void setReadableName(String name){
		this.readableName=name;
	}
		
	public String getIndexname() {
		return indexname;
	}	
	
	public void addImageName(String photoName) {
		if(!isMemberOf(photoName)){photos.add(photoName);}
	}
	
	public boolean isMemberOf(String photoName){
		//boolean truth=false;
		for(int i=0;i<photos.size();i++){
			String thisName=(String)photos.get(i);
			if(thisName.equals(photoName)){
				return true;
				}
			}
		return false;
	}


	public boolean isMemberOf(Encounter enc){
		//boolean truth=false;
		Vector photos=enc.getAdditionalImageNames();
		int photoSize=photos.size();
		for(int i=0;i<photoSize;i++){
			String thisName=enc.getEncounterNumber()+"/"+(String)photos.get(i);
			if(isMemberOf(thisName)){
				return true;
				}
			}
		return false;
	}
	
	public Vector getMembers() {
		return photos;
	}		
	
}
