/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.servlet.importer;

import com.oreilly.servlet.multipart.*;

import org.ecocean.*;
import org.ecocean.servlet.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;

import au.com.bytecode.opencsv.CSVReader;

import java.util.List;

import org.joda.time.*;
import org.joda.time.format.*;

import java.lang.IllegalArgumentException;

import org.ecocean.genetics.*;

import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Uploads an SRGD CSV file for data import
 *
 * @author jholmber
 */
public class ImportSRGD extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("ImportSRGD");
    System.out.println("\n\nStarting ImportSRGD servlet...");
    
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File tempSubdir = new File(webappsDir, "temp");
    if(!tempSubdir.exists()){tempSubdir.mkdirs();}
    System.out.println("\n\n     Finished directory creation...");
    
    
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String fileName = "None";
    
    StringBuffer messages=new StringBuffer();
    
    boolean successfullyWroteFile=false;

    File finalFile=new File(tempSubdir,"temp.csv");
    
    try {
      MultipartParser mp = new MultipartParser(request, (CommonConfiguration.getMaxMediaSizeInMegabytes(context) * 1048576));
      Part part;
      while ((part = mp.readNextPart()) != null) {
        String name = part.getName();
        if (part.isParam()) {


          // it's a parameter part
          ParamPart paramPart = (ParamPart) part;
          String value = paramPart.getStringValue();


        }


        if (part.isFile()) {
          FilePart filePart = (FilePart) part;
          fileName = ServletUtilities.cleanFileName(filePart.getFileName());
          if (fileName != null) {
            System.out.println("     Trying to upload file: "+fileName);
            //File thisSharkDir = new File(encountersDir.getAbsolutePath() +"/"+ encounterNumber);
            //if(!thisSharkDir.exists()){thisSharkDir.mkdirs();}
            finalFile=new File(tempSubdir, fileName);
            filePart.writeTo(finalFile);
            successfullyWroteFile=true;
            System.out.println("\n\n     I successfully uploaded the file!");
          }
        }
      }


      

        try {
          if(successfullyWroteFile){
            
            System.out.println("\n\n     Starting CSV content import...");
            
            //OK, we have our CSV file
            //let's import
            CSVReader reader = new CSVReader(new FileReader(finalFile));
            List<String[]> allLines = reader.readAll();
            System.out.println("\n\n     Read in the CSV file!");
            
            //let's detect the size of this array by reading the number of header columns in row 0
            String[] headerNames=allLines.get(0);
            int numColumns = headerNames.length;
            int numRows = allLines.size();
           
            //determine the Occurrence_ID column as it is at the end
            int occurrenceIDColumnNumber=-1;
            for(int g=0;g<numColumns;g++){
              if(headerNames[g].equals("Occurrence_ID")){
                occurrenceIDColumnNumber=g;
              }
            }
            
            for(int i=1;i<numRows;i++){
              
              System.out.println("\n\n     Processing row "+i);
              boolean newEncounter=true;
              boolean newShark=true;
              String[] line=allLines.get(i);
              
              boolean ok2import=true;
              
              Encounter enc=new Encounter();
              
              myShepherd.beginDBTransaction();
              
              //line[0] is the sample_ID
              String encNumber=line[0].trim();
              if((encNumber!=null)&&(!encNumber.equals(""))){
                if(myShepherd.isEncounter(encNumber)){
                  enc=myShepherd.getEncounter(encNumber);
                  newEncounter=false;
                }
                else{
                  enc.setCatalogNumber(encNumber);
                  enc.setState("approved");
                }
              }
              else{
                ok2import=false;
                messages.append("<li>Row "+i+": could not find sample/encounter ID in the first column of row "+i+".</li>");
                System.out.println("          Could not find sample/encounter ID in the first column of row "+i+".");
              }
              
              //line[1] is the IndividualID
              String individualID=line[1].trim();
              if(individualID!=null){
                
               
                  enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Import SRGD process set marked individual to " + individualID + ".</p>");
             
                
                enc.setIndividualID(individualID);
                System.out.println("          Setting Individual ID for row "+i+". Value: "+individualID);
                
                
                
              }
              
              
              //line[2] is the latitude
              String latitude=line[2].trim();
              if((latitude!=null)&&(!latitude.equals(""))){
                try{
                  
                 
                    enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Import SRGD process set latitude to " + latitude + ".</p>");
                  
                  
                  Double lat=new Double(latitude);
                  enc.setDecimalLatitude(lat);
                  System.out.println("          Setting latitude for row "+i+". Value: "+latitude);
                  
                }
                catch(NumberFormatException nfe){
                  messages.append("<li>Row "+i+" for sample ID "+enc.getCatalogNumber()+": Latitude hit a NumberFormatException in row "+i+" and could not be imported. The listed value was: "+latitude+"</li>");
                }
              }
              
              //line[3] is the latitude
              String longitude=line[3].trim();
              if((longitude!=null)&&(!longitude.equals(""))){
                try{
                  
                  enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Import SRGD process set longitude to " + longitude + ".</p>");
                  
                  Double longie=new Double(longitude);
                  enc.setDecimalLongitude(longie);
                  System.out.println("          Setting longitude for row "+i+". Value: "+longitude);
                  
                }
                catch(NumberFormatException nfe){
                  nfe.printStackTrace();
                  messages.append("<li>Row "+i+" for sample ID "+enc.getCatalogNumber()+": Longitude hit a NumberFormatException in row "+i+" and could not be imported. The listed value was: "+longitude+"</li>");
                }
              }
              
              //line[4] is the date_time
              String isoDate=line[4].trim();
              if((isoDate!=null)&&(!isoDate.equals(""))){
                
                StringTokenizer tks=new StringTokenizer(isoDate,"-");
                int numTokens=tks.countTokens();
                DateTimeFormatter parser2 = ISODateTimeFormat.dateTimeParser();
                
                enc.setMonth(-1);
                enc.setDay(-1);
                enc.setYear(-1);
                enc.setHour(-1);
                enc.setMinutes("00");
                
                try{
                  DateTime time = parser2.parseDateTime(isoDate);
                  enc.setYear(time.getYear());
                  
                  if(numTokens>=2){
                    enc.setMonth(time.getMonthOfYear());
                  }
                  if(numTokens>=3){
                    enc.setDay(time.getDayOfMonth());
                  }
                  

                  if(isoDate.indexOf("T")!=-1){
                    int minutes=time.getMinuteOfHour();
                    String minutes2=(new Integer(minutes)).toString();
                    if((time.getHourOfDay()!=0)&&(minutes!=0)){
                      enc.setHour(time.getHourOfDay());
                      if(isoDate.indexOf(":")!=-1){
                        enc.setMinutes(minutes2);
                      }
                    }
                  }
                  
                  enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Import SRGD process set date to " + enc.getDate() + ".</p>");
                  
                  System.out.println("          Set date for encounter: "+enc.getDate());
                  
                }
                catch(IllegalArgumentException iae){
                  iae.printStackTrace();
                  messages.append("<li>Row "+i+": could not import the date and time for row: "+i+". Cancelling the import for this row.</li>");
                  ok2import=false;
                  
                }
              }
              
              //line[5] get locationID
              String locationID=line[5].trim();
              if(line.length>=6){
              if((locationID!=null)&&(!locationID.equals(""))){
                enc.setLocationID(locationID);
                enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Import SRGD process set location ID to " + locationID + ".</p>");
                
                System.out.println("          Setting location ID for row "+i+". Value: "+locationID);
              }
            }
              
              //line[6] get sex
              String sex=line[6].trim();
              if(line.length>=7){
              if((sex!=null)&&(!sex.equals(""))){
                
                if(sex.equals("M")){enc.setSex("male");}
                else if(sex.equals("F")){enc.setSex("female");}
                else{enc.setSex("unknown");}
                
                System.out.println("          Setting sex for row "+i+". Value: "+sex);
                enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Import SRGD process set sex to " + enc.getSex() + ".</p>");
                
              }
              
              //line[occurrenceIDColumnNumber] get Occurrence_ID
              Occurrence occur=new Occurrence();
              if(occurrenceIDColumnNumber!=-1){
                String occurID=line[occurrenceIDColumnNumber];
                
                if(myShepherd.isOccurrence(occurID)){
                  occur=myShepherd.getOccurrence(occurID);
                  boolean isNew=occur.addEncounter(enc);
                  if(isNew){
                    occur.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Import SRGD process added encounter " + enc.getCatalogNumber() + ".</p>");
                  }
                
                }
                else{
                  occur=new Occurrence(occurID,enc);
                  occur.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Import SRGD process added encounter " + enc.getCatalogNumber() + ".</p>");
                  
                  myShepherd.getPM().makePersistent(occur);

                 }
              }
              
            }
              

              
              
              if(ok2import){
                
                System.out.println("          ok2import");
                
                
                myShepherd.commitDBTransaction();
                if(newEncounter){myShepherd.storeNewEncounter(enc, enc.getCatalogNumber());}
                
                //before proceeding with haplotype and loci importing, we need to create the tissue sample
                myShepherd.beginDBTransaction();
                Encounter enc3=myShepherd.getEncounter(encNumber);
                TissueSample ts=new TissueSample(encNumber, ("sample_"+encNumber)) ;
                
                if(myShepherd.isTissueSample(("sample_"+encNumber), encNumber)){
                  ts=myShepherd.getTissueSample(("sample_"+encNumber), encNumber);
                }
                else{
                  myShepherd.getPM().makePersistent(ts);
                  enc3.addTissueSample(ts);
                }
                System.out.println("          Added TissueSample.");
                
                
                //let's set genetic Sex
                if((sex!=null)&&(!sex.equals(""))){  
                  SexAnalysis sexDNA=new SexAnalysis(("analysis_"+enc3.getCatalogNumber()+"_sex"), sex, enc3.getCatalogNumber(), ("sample_"+enc3.getCatalogNumber()));
                  if(myShepherd.isGeneticAnalysis(ts.getSampleID(), encNumber, ("analysis_"+enc3.getCatalogNumber()+"_sex"), "SexAnalysis")){
                    sexDNA=myShepherd.getSexAnalysis(ts.getSampleID(), encNumber, ("analysis_"+enc3.getCatalogNumber()+"_sex"));
                    sexDNA.setSex(sex);
                  }
                  else{
                    ts.addGeneticAnalysis(sexDNA);
                    myShepherd.getPM().makePersistent(sexDNA);
                  }
                  enc3.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br />" + "Import SRGD process added or updated genetic sex analysis "+sexDNA.getAnalysisID()+" for tissue sample "+ts.getSampleID()+".<br />"+sexDNA.getHTMLString());
                } 
                System.out.println("          Added genetic sex.");
                
                
                
                //line[7] get haplotype
                if(line.length>=8){
                String haplo=line[7].trim();
                if((haplo!=null)&&(!haplo.equals(""))){
                  //TBD check id this analysis already exists
                  System.out.println("          Starting haplotype.");
                  
                  MitochondrialDNAAnalysis mtDNA=new MitochondrialDNAAnalysis(("analysis_"+enc3.getCatalogNumber()), haplo, enc3.getCatalogNumber(), ("sample_"+enc3.getCatalogNumber()));
                  if(myShepherd.isGeneticAnalysis(ts.getSampleID(), encNumber, ("analysis_"+enc3.getCatalogNumber()), "MitochondrialDNA")){
                    mtDNA=myShepherd.getMitochondrialDNAAnalysis(ts.getSampleID(), encNumber, ("analysis_"+enc3.getCatalogNumber()));
                    mtDNA.setHaplotype(haplo);
                    System.out.println("                  Haplotype reset.");
                    
                    
                  }
                  else{
                    ts.addGeneticAnalysis(mtDNA);
                    myShepherd.getPM().makePersistent(mtDNA);
                    System.out.println("          Added new haplotype.");
                    
                  }
                  enc3.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br />" + "Import SRGD process added or updated mitochondrial DNA analysis (haplotype) "+mtDNA.getAnalysisID()+" for tissue sample "+ts.getSampleID()+".<br />"+mtDNA.getHTMLString());
                  System.out.println("          Added haplotype.");
                }
                else{System.out.println("          Did NOT add haplotype.");}
              }
                
                
                ArrayList<Locus> loci=new ArrayList<Locus>();
                
                //loci value import       
                if(line.length>=9){
                  
                for (int f = 8; f < numColumns; f++) {
                  if(line.length>(f+2)){
                  String l1=line[f].trim();
                  String l2=line[f+1].trim();
                  String locusName=headerNames[f].replaceAll("L_", "");
                  
                  System.out.println("          Loaded loci name.");
                  
                  //verify that we're looking at the right loci and everything matches up nicely
                  if((l1!=null)&&(l2!=null)&&(!l1.equals(""))&&(!l2.equals(""))&&(!locusName.equals(""))&&(headerNames[f].trim().toLowerCase().startsWith("l_"))&&(headerNames[f+1].trim().toLowerCase().startsWith("l_"))&&(headerNames[f].trim().toLowerCase().equals(headerNames[f+1].trim().toLowerCase()))){
                    
                   
                      
                          //get allele values
                          Integer intA=new Integer(l1);
                          Integer intB=new Integer(l2);
                      
                          Locus myLocus=new Locus(locusName, intA, intB);
                          loci.add(myLocus);
                      
                  }
                  
                  f++;
                }
                }
              }
                
                //TBD check if this analysis already exists
               if(loci.size()>0){ 
                
                 System.out.println("          Found msMarkers!!!!!!!!!!!!1");
                 
                 MicrosatelliteMarkersAnalysis microAnalysis=new MicrosatelliteMarkersAnalysis((ts.getSampleID()+"_msMarkerAnalysis"), ts.getSampleID(), enc.getCatalogNumber(), loci); 
                
                
                
                if(myShepherd.isGeneticAnalysis(ts.getSampleID(), encNumber, (ts.getSampleID()+"_msMarkerAnalysis"), "MicrosatelliteMarkers")){
                  microAnalysis=myShepherd.getMicrosatelliteMarkersAnalysis(ts.getSampleID(), encNumber, (ts.getSampleID()+"_msMarkerAnalysis"));
                  microAnalysis.setLoci(loci);
                }
                else{
                  ts.addGeneticAnalysis(microAnalysis);
                  myShepherd.getPM().makePersistent(microAnalysis);
                }
                System.out.println("          Added ms markers.");
                
                
                enc3.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br />" + "Import SRGD process added or updated microsatellite markers of analysis "+microAnalysis.getAnalysisID()+" for tissue sample "+ts.getSampleID()+".<br />"+microAnalysis.getHTMLString());
                
              }
                
                myShepherd.commitDBTransaction();
                

          
                
                
                if(!individualID.equals("")){
                  MarkedIndividual indie=new MarkedIndividual();
                  myShepherd.beginDBTransaction();
                
                  Encounter enc2=myShepherd.getEncounter(encNumber);
                
                  if(myShepherd.isMarkedIndividual(individualID)){
                    indie=myShepherd.getMarkedIndividual(individualID);
                    newShark=false;
                  }
                  else{
                    indie.setIndividualID(individualID);
                  }
                  
                  //OK to generically add it as the addEncounter() method will ignore it if already added to marked individual
                  indie.addEncounter(enc2, context);

                  if((indie.getSex()==null)||((enc2.getSex()!=null)&&(indie.getSex()!=enc2.getSex()))){
                    indie.setSex(enc2.getSex());
                    indie.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Import SRGD process set sex to " + enc2.getSex() + ".</p>");
                    
                  }
                  
                  if((indie.getHaplotype()==null)&&(enc2.getHaplotype()!=null)){
                    indie.doNotSetLocalHaplotypeReflection(enc2.getHaplotype());
                  }
                  
                  indie.refreshDependentProperties(context);
                  indie.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Import SRGD process added encounter " + enc2.getCatalogNumber() + ".</p>");
                  
                  myShepherd.commitDBTransaction();
                  if(newShark){myShepherd.storeNewMarkedIndividual(indie);}
                }
                
            }
            else{myShepherd.rollbackDBTransaction();}
              
              //out.println("Imported row: "+line);
              
            }
            
            
          }
          else{
            locked=true;
            System.out.println("ImportSRGD: For some reason the import failed without exception.");
          }


        } 
        catch (Exception le) {
          locked = true;
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
          le.printStackTrace();
        }


        if (!locked) {
          myShepherd.commitDBTransaction();
          myShepherd.closeDBTransaction();
          out.println(ServletUtilities.getHeader(request));
          out.println("<p><strong>Success!</strong> I have successfully uploaded and imported your SRGD CSV file.</p>");
          
          if(messages.toString().equals("")){messages.append("None");}
          out.println("<p>The following error messages were reported during the import process:<br /><ul>"+messages+"</ul></p>" );

          out.println("<p><a href=\"appadmin/import.jsp\">Return to the import page</a></p>" );

          
          out.println(ServletUtilities.getFooter(context));
          } 
      
    } 
    catch (IOException lEx) {
      lEx.printStackTrace();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to upload your SRGD CSV. Please contact the webmaster about this message.");
      out.println(ServletUtilities.getFooter(context));
    } 
    catch (NullPointerException npe) {
      npe.printStackTrace();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to import SRGD data as no file was specified.");
      out.println(ServletUtilities.getFooter(context));
    }
    out.close();
  }


}


