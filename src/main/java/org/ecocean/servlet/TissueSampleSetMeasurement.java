package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.*;
import org.ecocean.genetics.*;

public class TissueSampleSetMeasurement extends HttpServlet {

  private static final Pattern MEASUREMENT_NAME = Pattern.compile("measurement(\\d+)\\(([^)]*)\\)");
  

  public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doPost(req, resp);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);
    Shepherd myShepherd=new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked=false;

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", request.getParameter("encounter"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link)
            .setParams(request.getParameter("encounter"), request.getParameter("sampleID"), request.getParameter("analysisID"));

    myShepherd.beginDBTransaction();
    if((request.getParameter("encounter")!=null)&&(request.getParameter("sampleID")!=null)&&(myShepherd.isTissueSample(request.getParameter("sampleID"), request.getParameter("encounter")))) {
      
      String encNum=request.getParameter("encounter");
      String sampleID=request.getParameter("sampleID");
      String analysisID = request.getParameter("analysisID");
      
      TissueSample enc=myShepherd.getTissueSample(sampleID, encNum);
      Encounter myEnc=myShepherd.getEncounter(encNum);
       try {
    
          BiologicalMeasurement measurement=new BiologicalMeasurement();
          if((request.getParameter("measurementType")!=null)&&(request.getParameter("value")!=null)&&(request.getParameter("samplingProtocol")!=null)){
            
            if ((!myShepherd.isGeneticAnalysis(sampleID, encNum, analysisID, "BiologicalMeasurement"))) {
            
              //let's determine the units
              String units="";
              if((request.getParameter("measurementType")!=null)&&(CommonConfiguration.getIndexNumberForValue("biologicalMeasurementType", request.getParameter("measurementType").trim(),context)!=null)){
                int index=CommonConfiguration.getIndexNumberForValue("biologicalMeasurementType", request.getParameter("measurementType").trim(),context).intValue();
                System.out.println("     TissueSampleSetMeasurement index: "+index);
                if(CommonConfiguration.getProperty(("biologicalMeasurementUnits"+index),context)!=null){
                  System.out.println("Found units!");
                  units=CommonConfiguration.getProperty(("biologicalMeasurementUnits"+index),context);
                }
              }
            
              measurement = new BiologicalMeasurement(sampleID, analysisID, encNum, request.getParameter("measurementType"), (new Double(request.getParameter("value"))), units, request.getParameter("samplingProtocol"));
              if(request.getParameter("measurementType")!=null){measurement.setMeasurementType(request.getParameter("measurementType"));}
              
              if(request.getParameter("processingLabTaskID")!=null){measurement.setProcessingLabTaskID(request.getParameter("processingLabTaskID"));}
              if(request.getParameter("processingLabName")!=null){measurement.setProcessingLabName(request.getParameter("processingLabName"));}
              if(request.getParameter("processingLabContactName")!=null){measurement.setProcessingLabContactName(request.getParameter("processingLabContactName"));}
              if(request.getParameter("processingLabContactDetails")!=null){measurement.setProcessingLabContactDetails(request.getParameter("processingLabContactDetails"));}

              enc.addGeneticAnalysis(measurement);
              //log the new measurement addition
              myEnc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Added tissue sample "+sampleID+" biological\\chemical measurement:<br><i>" + request.getParameter("measurementType") + " "+request.getParameter("value")+" "+units+" ("+request.getParameter("samplingProtocol")+")</i></p>");
            
            }
            else {
            
            
              
              measurement  = myShepherd.findGeneticAnalysis(BiologicalMeasurement.class, request.getParameter("analysisID"));
            
            
              //let's determine the units
              String units="";
              if((request.getParameter("measurementType")!=null)&&(CommonConfiguration.getIndexNumberForValue("biologicalMeasurementType", request.getParameter("measurementType").trim(),context)!=null)){
                int index=CommonConfiguration.getIndexNumberForValue("biologicalMeasurementType", request.getParameter("measurementType").trim(),context).intValue();
                System.out.println("     TissueSampleSetMeasurement index: "+index);
                if(CommonConfiguration.getProperty(("biologicalMeasurementUnits"+index),context)!=null){
                  System.out.println("Found units!");
                  units=CommonConfiguration.getProperty(("biologicalMeasurementUnits"+index),context);
                }
              }
              
              String oldValue="null";
              if(measurement.getValue()!=null){oldValue=measurement.getValue().toString();}
              String oldSamplingProtocol="null";
              if(measurement.getSamplingProtocol()!=null){oldSamplingProtocol=measurement.getSamplingProtocol();}
            
              //now set the new values
              measurement.setValue(new Double(request.getParameter("value")));
              measurement.setSamplingProtocol(request.getParameter("samplingProtocol"));
            
              if(request.getParameter("processingLabTaskID")!=null){measurement.setProcessingLabTaskID(request.getParameter("processingLabTaskID"));}
              if(request.getParameter("processingLabName")!=null){measurement.setProcessingLabName(request.getParameter("processingLabName"));}
              if(request.getParameter("processingLabContactName")!=null){measurement.setProcessingLabContactName(request.getParameter("processingLabContactName"));}
              if(request.getParameter("processingLabContactDetails")!=null){measurement.setProcessingLabContactDetails(request.getParameter("processingLabContactDetails"));}

            
              //log the measurement change -- TBD
              myEnc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Changed tissue sample "+sampleID+" biological\\chemical measurement " + request.getParameter("measurementType") + " from "+oldValue+" "+units+" ("+oldSamplingProtocol+") to "+request.getParameter("value")+" "+units+" ("+request.getParameter("samplingProtocol")+")</i></p>");
            
            }
          }
          else{
            locked=true;
          }

       } 
       catch(Exception ex) {
         ex.printStackTrace();
         locked = true;
         myShepherd.rollbackDBTransaction();
         myShepherd.closeDBTransaction();
      }
      if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        actionResult.setMessageOverrideKey("tissueSampleMeasurement");
      }
      else {
        actionResult.setSucceeded(false).setMessageOverrideKey("locked");
      }
    }
    else {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      actionResult.setSucceeded(false);
    }

    // Reply to user.
    request.getSession().setAttribute(ActionResult.SESSION_KEY, actionResult);
    getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);

    out.close();
  }
}
