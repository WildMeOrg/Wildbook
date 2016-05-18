package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.*;
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
    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);
    Properties encProps = ShepherdProperties.getProperties("encounter.properties", langCode, context);
    Map<String, String> mapI18nTagLocs = CommonConfiguration.getI18nPropertiesMap("metalTagLocation", langCode, context, false);

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
            .setParams(request.getParameter("encounter"));

    String encNum = request.getParameter("encounter");
    myShepherd.beginDBTransaction();
    StringBuilder sb = new StringBuilder().append("<ul>");
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
            sb.append(String.format("<li>%s = %s</li>", MessageFormat.format(encProps.getProperty("metalTag_loc"), mapI18nTagLocs.get(location)), getParam(request, metalTagParamName)));
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
          sb.append(String.format("<li>%s = %s</li>", encProps.getProperty("acousticTag_serial"), getParam(request, ACOUSTIC_TAG_SERIAL)));
          sb.append(String.format("<li>%s = %s</li>", encProps.getProperty("acousticTag_id"), getParam(request, ACOUSTIC_TAG_ID)));
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
          sb.append(String.format("<li>%s = %s</li>", encProps.getProperty("satelliteTag_name"), getParam(request, SATELLITE_TAG_NAME)));
          sb.append(String.format("<li>%s = %s</li>", encProps.getProperty("satelliteTag_serial"), getParam(request, SATELLITE_TAG_SERIAL)));
          sb.append(String.format("<li>%s = %s</li>", encProps.getProperty("satelliteTag_argos"), getParam(request, SATELLITE_TAG_ARGOS_PTT_NUMBER)));
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
        sb.append("</ul>");
        actionResult.setMessageOverrideKey("tags").addParams(sb.toString());
      }
      else {
        actionResult.setSucceeded(false).setMessageOverrideKey("locked");
      }
      
    }
    else {
      myShepherd.rollbackDBTransaction();
      actionResult.setSucceeded(false);
    }

    // Reply to user.
    request.getSession().setAttribute(ActionResult.SESSION_KEY, actionResult);
    getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);

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
