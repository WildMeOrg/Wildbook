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





/**
 * @author jholmber
 *
 */
public class EncounterLoader {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		
		String urlToThumbnailJSPPage="http://www.oceansmart.org/";
		
		System.out.println("\n\n");
		
		int numThumbnailsToGenerate=6195;
		String IDKey="";
		
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
		
		int numEncounters=6190;
	  for(int q=5000;q<numEncounters;q++){
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
            Thread.sleep(500);
            BufferedReader in2 = new BufferedReader(new InputStreamReader(url2.openStream()));
            in2.close();
            Thread.sleep(500);
        } 
        catch (Exception e) {
          
          System.out.println("Error trying to render the thumbnail for "+IDKey+".");
          e.printStackTrace();
          
        }
        
        
      
      
    }
		
		
		
	}
	
	

}