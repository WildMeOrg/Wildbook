package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;

import org.ecocean.*;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;



public class EncounterSetField extends HttpServlet {

  private static Class DEFAULT_FIELD_TYPE = String.class;

  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd=new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked=false;

    String encNum="None";
    encNum=request.getParameter("encounter");

    String fieldName=request.getParameter("fieldName");
    // optional fieldSetterName arg
    String fieldSetterName=request.getParameter("fieldSetterName");
    // if necessary and possible derive fieldSetterName from fieldName
    if (fieldSetterName==null && fieldName!=null) {
      fieldSetterName = "set"+Util.capitolizeFirstLetter(fieldName);
    }

    Class fieldType=null;
    String fieldTypeName=request.getParameter("fieldType");
    if (fieldTypeName==null) fieldType = DEFAULT_FIELD_TYPE;
    else if (fieldTypeName.equals("String"))  fieldType = String.class;
    else if (fieldTypeName.equals("Double"))  fieldType = Double.class;
    else if (fieldTypeName.equals("Integer")) fieldType = Integer.class;
    else if (fieldTypeName.equals("Boolean")) fieldType = Boolean.class;
    else {
      throw new IOException("EncounterSetField: incompatible with fieldType received from request: "+fieldTypeName);
    }

    Method setField=null;
    try {
      setField = Encounter.class.getMethod(fieldSetterName, fieldType);
    } catch (Exception nsme) {
      nsme.printStackTrace();
      throw new IOException("EncounterSetField: there is no Encounter method called "+fieldSetterName+" with a single "+fieldType.getName()+" argument.");
    }

    String value=request.getParameter("value");
    if (value==null) {
      throw new IOException("EncounterSetField: Could not get value from request. Recieved value="+value+" with field setter "+fieldSetterName);
    }

    System.out.println("EncounterSetField: proceeding towards calling Encounter."+fieldSetterName+"("+value+") for encounter "+encNum);

    myShepherd.beginDBTransaction();
    if (myShepherd.isEncounter(encNum)) {
      Encounter enc=myShepherd.getEncounter(encNum);
      if (value!=null) value = value.trim();
      try{

        // if empty string, and the fieldType is NOT String, set the field to null
        if      (fieldType == String.class)  setField.invoke(enc, value);
        else if (fieldType == Double.class) {
          if (value.equals("")) setField.invoke(enc, (Double) null);
          else setField.invoke(enc, Double.valueOf(value));
        }
        else if (fieldType == Integer.class) {
          if (value.equals("")) setField.invoke(enc, (Integer) null);
          else setField.invoke(enc, Integer.valueOf(value));
        }
        else if (fieldType == Boolean.class) {
          if (value.equals("")) setField.invoke(enc, (Boolean) null);
          else setField.invoke(enc, Boolean.valueOf(value));
        }

        enc.addComments("<p><em>" +request.getRemoteUser()+ " on " +(new java.util.Date()).toString()+ "</em><br>Changed "+fieldName+" to " +value+ " using EncounterSetField servlet.</p>");


      }
      catch(Exception le){
        locked=true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        System.out.println("Exception submitting EncounterSetField("+fieldName+", "+value+"):");
        le.printStackTrace();
      }

      if(!locked){
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success!</strong> I have successfully changed the "+fieldName+" for encounter "+encNum+" to "+value+".</p>");

        out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
        String message="The "+fieldName+" for encounter "+encNum+" was set to "+value+".";


      }
      else{

        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> This encounter is currently being modified by another user, or an exception occurred. Please wait a few seconds before trying to modify this encounter again.");

        out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        out.println(ServletUtilities.getFooter(context));

      }
    }
    else {
      myShepherd.rollbackDBTransaction();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to set the encounter field. I cannot find the encounter that you intended in the database.");
      out.println(ServletUtilities.getFooter(context));
    }
    out.close();
    myShepherd.closeDBTransaction();
  }
}
