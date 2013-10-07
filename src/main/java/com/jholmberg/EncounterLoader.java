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






/**
 * @author jholmber
 *
 */
public class EncounterLoader {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		
		String urlToThumbnailJSPPage="http://localhost:8080/wildbook-4.0.3-EXPERIMENTAL/";
		
		System.out.println("\n\n");
		
		int numThumbnailsToGenerate=17712;
		String IDKey="";
		for(int q=1;q<numThumbnailsToGenerate;q++){
			System.out.println(q);
			//ping a URL to thumbnail generator - Tomcat must be up and running
		    try 
		    {
		        
		    	//System.out.println("Trying to render a thumbnail for: "+IDKey+ "as "+thumbnailTheseImages.get(q));
		    	String urlString=urlToThumbnailJSPPage+"resetThumbnail.jsp?number="+q+"&imageNum=1";
		    	String urlString2=urlToThumbnailJSPPage+"encounters/encounter.jsp?number="+q+"&imageNum=1";
		    	URL url = new URL(urlString);
		    	URL url2 = new URL(urlString2);
		        BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
		        in.close();
		        
		        BufferedReader in2 = new BufferedReader(new InputStreamReader(url2.openStream()));
            in2.close();
		    } 
		    catch (MalformedURLException e) {
		    	
		    	System.out.println("Error trying to render the thumbnail for "+IDKey+".");
		    	e.printStackTrace();
		    	
		    }
		    catch (IOException ioe) {
		    	
		    	System.out.println("Error trying to render the thumbnail for "+IDKey+".");
		    	ioe.printStackTrace();
		    	
		    } 
		    
			
			
		}
		

	}
	
	

}