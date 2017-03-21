package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Measurement;
import org.ecocean.Shepherd;
import org.ecocean.tag.AcousticTag;
import org.ecocean.tag.MetalTag;
import org.ecocean.tag.SatelliteTag;

public class EncounterSetTags extends HttpServlet {

  private static final String METAL_TAG_PARAM_START = "metalTag(";
  private static final String SATELLITE_TAG_NAME = "satelliteTagName";
  private static final String SATELLITE_TAG_SERIAL = "satelliteTagSerial";
  private static final String SATELLITE_TAG_ARGOS_PTT_NUMBER = "satelliteTagArgosPttNumber";
  private static final String ACOUSTIC_TAG_SERIAL = "acousticTagSerial";
  private static final String ACOUSTIC_TAG_ID = "acousticTagId";

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd=new Shepherd(context);
    myShepherd.setAction("EncounterSetTags.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked=false;

    String encNum="None";

    encNum=request.getParameter("encounter");
    myShepherd.beginDBTransaction();
    StringBuilder sb = new StringBuilder();
    if (myShepherd.isEncounter(encNum)) {
      Encounter enc=myShepherd.getEncounter(encNum);
      try {
        String tagType = request.getParameter("tagType");
        if ("metalTags".equals(tagType)) {
          List<String> metalTagParamNames = getMetalTagParamNames(request);
          for (String metalTagParamName : metalTagParamNames) {
            // Param name is of the form "metalTag(<location>)", e.g., metalTag(right). Get the value inside the parens:
            String location = metalTagParamName.substring(METAL_TAG_PARAM_START.length(), metalTagParamName.length() - 1);
            MetalTag metalTag = enc.findMetalTagForLocation(location);
            if (metalTag == null) {
              metalTag = new MetalTag();
              metalTag.setLocation(location);
              enc.addMetalTag(metalTag);
            }
            metalTag.setTagNumber(getParam(request, metalTagParamName));
            sb.append(MessageFormat.format("<br/>Metal Tag {0} set to {1}", location, getParam(request, metalTagParamName)));
          }
        }
        else if ("acousticTag".equals(tagType)) {
          AcousticTag acousticTag = enc.getAcousticTag();
          if (acousticTag == null) {
            acousticTag = new AcousticTag();
            enc.setAcousticTag(acousticTag);
          }
          acousticTag.setIdNumber(getParam(request, ACOUSTIC_TAG_ID));
          acousticTag.setSerialNumber(getParam(request, ACOUSTIC_TAG_SERIAL));
          sb.append(MessageFormat.format("<br/>{0} set to {1}", ACOUSTIC_TAG_ID, getParam(request, ACOUSTIC_TAG_ID)));
          sb.append(MessageFormat.format("<br/>{0} set to {1}", ACOUSTIC_TAG_SERIAL, getParam(request, ACOUSTIC_TAG_SERIAL)));
        }
        else if ("satelliteTag".equals(tagType)) {
          SatelliteTag satelliteTag = enc.getSatelliteTag();
          if (satelliteTag == null) {
            satelliteTag = new SatelliteTag();
            enc.setSatelliteTag(satelliteTag);
          }
          satelliteTag.setArgosPttNumber(getParam(request, SATELLITE_TAG_ARGOS_PTT_NUMBER));
          satelliteTag.setSerialNumber(getParam(request, SATELLITE_TAG_SERIAL));
          satelliteTag.setName(getParam(request, SATELLITE_TAG_NAME));
          sb.append(MessageFormat.format("<br/>{0} set to {1}", SATELLITE_TAG_ARGOS_PTT_NUMBER, getParam(request, SATELLITE_TAG_ARGOS_PTT_NUMBER)));
          sb.append(MessageFormat.format("<br/>{0} set to {1}", SATELLITE_TAG_SERIAL, getParam(request, SATELLITE_TAG_SERIAL)));
          sb.append(MessageFormat.format("<br/>{0} set to {1}", SATELLITE_TAG_NAME, getParam(request, SATELLITE_TAG_NAME)));
        }
        else {
          
        }
      } catch(Exception ex) {
        ex.printStackTrace();
        locked = true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
      if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<p><strong>Success!</strong> I have successfully set the following tag values:");
        out.println(sb.toString());
        out.println("<p><a href=\""+request.getScheme()+"://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
      }
      else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> This encounter is currently being modified by another user, or an exception occurred. Please wait a few seconds before trying to modify this encounter again.");

        out.println("<p><a href=\""+request.getScheme()+"://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
      }
      
    }
    else {
      myShepherd.rollbackDBTransaction();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to set the tag. I cannot find the encounter that you intended in the database.");
      out.println(ServletUtilities.getFooter(context));

    }
    out.close();
    myShepherd.closeDBTransaction();
  }

  private String getParam(HttpServletRequest request, String paramName) {
    String value = request.getParameter(paramName);
    if (value != null) {
      value = value.trim();
      if (value.length() == 0){
        value = null;
      }
    }
    return value;
  }
  
  private List<String> getMetalTagParamNames(HttpServletRequest request) {
    List<String> list = new ArrayList<String>();
    Enumeration parameterNames = request.getParameterNames();
    while (parameterNames.hasMoreElements()) {
      String paramName = (String) parameterNames.nextElement();
      if (paramName.startsWith(METAL_TAG_PARAM_START)) {
        list.add(paramName);
      }
    }
    return list;
  }

}
