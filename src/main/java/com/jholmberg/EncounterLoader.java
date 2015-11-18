/**
 * 
 */
package com.jholmberg;

//import the Shepherd Project Framework
//import org.ecocean.*;
//import org.ecocean.servlet.ServletUtilities;

//import basic IO
import java.io.*;
//import java.util.*;
import java.net.*;
import java.lang.Thread;

import org.ecocean.*;
import java.util.ArrayList;

/**
 * @author jholmber
 *
 */
public class EncounterLoader {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		
		String urlToThumbnailJSPPage="http://www.flukebook.org/";
		
		System.out.println("\n\n");
		
		String IDKey="";
		
		Shepherd myShepherd=new Shepherd("context0");
		myShepherd.beginDBTransaction();
		
		ArrayList<Encounter> encs=myShepherd.getAllEncountersForSpecies("Megaptera", "novaeangliae");
		/*
		for(int q=5000;q<numThumbnailsToGenerate;q++){
			System.out.println(q);
			//ping a URL to thumbnail generator - Tomcat must be up and running
		    try 
		    {
		        
		    	//System.out.println("Trying to render a thumbnail for: "+IDKey+ "as "+thumbnailTheseImages.get(q));
		    	String urlString=urlToThumbnailJSPPage+"resetThumbnail.jsp?number="+q+"_DATASTORE&imageNum=1";
		    	String urlString2=urlToThumbnailJSPPage+"encounters/encounter.jsp?number="+q+"_DATASTORE&imageNum=1";
		    	URL url = new URL(urlString);
		    	URL url2 = new URL(urlString2);
		        BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
		        in.close();
		        Thread.sleep(100);
		        BufferedReader in2 = new BufferedReader(new InputStreamReader(url2.openStream()));
            in2.close();
            Thread.sleep(100);
		    } 
		    catch (Exception e) {
		    	
		    	System.out.println("Error trying to render the thumbnail for "+IDKey+".");
		    	e.printStackTrace();
		    	
		    }
		    
		    
			
			
		}
		*/
		
		int numEncounters=encs.size();
	  for(int q=0;q<numEncounters;q++){
      System.out.println(q);
      //ping a URL to thumbnail generator - Tomcat must be up and running
        try 
        {
            Encounter enc=encs.get(q);
          //System.out.println("Trying to render a thumbnail for: "+IDKey+ "as "+thumbnailTheseImages.get(q));
          String urlString=urlToThumbnailJSPPage+"resetThumbnail.jsp?number="+q+enc.getCatalogNumber()+"&imageNum=1";
          String urlString2=urlToThumbnailJSPPage+"encounters/encounter.jsp?number="+enc.getCatalogNumber()+"&imageNum=1";
          URL url = new URL(urlString);
          URL url2 = new URL(urlString2);
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            in.close();
            Thread.sleep(2000);
            BufferedReader in2 = new BufferedReader(new InputStreamReader(url2.openStream()));
            in2.close();
            Thread.sleep(2000);
        } 
        catch (Exception e) {
          
          System.out.println("Error trying to render the thumbnail for "+IDKey+".");
          e.printStackTrace();
          
        }
        
        
      
      
    }
	  
	  myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
		
		
	}
	
	

}