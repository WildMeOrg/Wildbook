package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import org.ecocean.*;


//returns the results of an image search request in XML
public class ImageSearch extends HttpServlet {
	
	
	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
  	}

	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
    	doPost(request, response);
	}
		

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		//establish a shepherd to manage DB interactions
		Shepherd myShepherd=new Shepherd();
		
		//number of parameters to search by
		int numSearchParams=0;

		//create a vector to hold matches
		Vector matches=new Vector();
		
		//determine if there is a primary image for display
		boolean primaryImage=false, locCodeSearch=false;
		//primaryImageName must be of the format '123456789/imageName.jpg' which includes the encounter number
		String primaryImageName="";
		String queryString=request.getParameter("queryString");
		String[] params=queryString.split("_AMP_");
		if(!params[0].equals("NONE")) {
			primaryImage=true;
			primaryImageName=params[0];
		}
		String locCode="QQQ";
		for(int r=1;r<params.length;r++){
			if(!params[r].equals("None")){
				numSearchParams++;
			}
			if(r==4){
				locCodeSearch=true;
				locCode=params[r];
				numSearchParams--;
			}
		}
		
		//System.out.println("Num search params is "+numSearchParams);

		//if(request.getParameter("locationCode")!=null) {locCodeSearch=true;}
		//System.out.println("Loc code search is: "+locCodeSearch);
		//set up for servlet response
		response.setContentType("text/xml");
		PrintWriter out = response.getWriter();
		
		//open the DB
		myShepherd.beginDBTransaction();
		
		if(numSearchParams==1){
			//only one keyword input
			//in this case just add all entries of the keyword to the matches Vector
			Keyword kw=myShepherd.getKeyword(params[1]);
			//System.out.println("Searching on solo keyword: "+kw.getReadableName());
			matches=kw.getMembers();
			
			
		}
		else if(numSearchParams>1) {
			
			//in this case just iterate through the list of images in the first keyword and only add to the matches Vector
			//those encounters found in every parameter keyword
			String[] searchparams=new String[numSearchParams];
			for(int j=0;j<numSearchParams;j++) {
					searchparams[j]=params[j+1];
					//System.out.println("param is "+searchparams[j]);
			}
			
			//now iterate through first list and see if any of its images are in any other list
			Keyword firstKW=myShepherd.getKeyword(searchparams[1]);
			//System.out.println("Searching on keyword: "+firstKW.getReadableName());
			//System.out.println("Searching on keyword: "+firstKW.getIndexname());
			Vector firstKWImages=firstKW.getMembers();
			for(int k=0;k<firstKWImages.size();k++){
				boolean successfulMatch=true;
				String image=(String)firstKWImages.get(k);
				//System.out.println("Consider image "+image);
				for(int l=1;l<searchparams.length;l++) {
					Keyword tempKW=myShepherd.getKeyword(params[l]);
					if(!tempKW.isMemberOf(image)){
						successfulMatch=false;
					}
				}
				if(successfulMatch){
					matches.add(image);
				}
				
			}
		}

	
		//close the DB txn with rollback -- no persisted data
		myShepherd.rollbackDBTransaction();
		
					
      	//set up the XML to return to the Flash display
      	out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");					
      	out.println("<encounterList>");
      	
		//now printout the needed list
      	if(matches.size()>0) {
      		
      		//open DB again to pull data
      		myShepherd.beginDBTransaction();
      		
      		try{
      			
      			//now spit out that XML for each match!
      			//remember to set primary attribute!
      			for(int i=0;i<matches.size();i++) {
      				String[] thisEncounter=((String)matches.get(i)).split("/");
      				//System.out.println("Tokenizer yielded "+thisEncounter[0]+":"+thisEncounter[1]);
      				Encounter tempEnc=myShepherd.getEncounter(thisEncounter[0]);
      				if(tempEnc!=null){
      					//System.out.println("Testing: "+locCode);
      					//System.out.println("Testing starts with: "+tempEnc.getLocationCode().startsWith(locCode));
      					if((!locCodeSearch)||(tempEnc.getLocationCode().startsWith(locCode))) {
      						String primaryAtt="";
      						if(primaryImage&&(primaryImageName.equals((String)matches.get(i)))){
      							primaryAtt="primary=\"yes\"";
      						}
      					

      						String img_s="";
      						String img_f="";
      					
      						int smallImagePlace=tempEnc.getAdditionalImageNames().indexOf(thisEncounter[1]);
      						if(smallImagePlace>=0) {
      							img_s=(smallImagePlace+1)+".jpg";
      							img_f=thisEncounter[1];
      							out.println("<encounter num=\""+thisEncounter[0]+"\" date=\""+tempEnc.getDate()+"\" sharksize=\""+tempEnc.getSize()+" meters\" img_s=\""+img_s+"\" img_f=\""+img_f+"\" "+primaryAtt+" />");
      						}
      					}
      				}
      					
      					
      			}

      		}
      		catch(Exception e){
      				e.printStackTrace();
      		}
			myShepherd.rollbackDBTransaction();
      		myShepherd.closeDBTransaction();
      			
      	} //end if-matches>0
      		

      	out.println("</encounterList>");
      	out.close();
	}//end doPost

} //end class
	
	
