package org.ecocean;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.*;
import java.util.ArrayList;
import java.util.Properties;
import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspWriter;
/**
 * Comment
 *
 * @author mfisher
 */
public class FormUtilities {

  public static void printStringFieldSearchRow(String fieldName, javax.servlet.jsp.JspWriter out, Properties nameLookup) throws IOException, IllegalAccessException {
    // note how fieldName is variously manipulated in this method to make element ids and contents
    String displayName = getDisplayName(fieldName, nameLookup);
    out.println("<tr id=\""+fieldName+"Row\">");
    out.println("  <td id=\""+fieldName+"Title\"><br /><strong>"+displayName+"</strong>");
    out.println("  <input name=\""+fieldName+"\" type=\"text\" size=\"60\"/> <br> </td>");
    out.println("</tr>");
  }

  public static void printStringFieldSearchRow(String fieldName, List<String> valueOptions,  javax.servlet.jsp.JspWriter out, Properties nameLookup) throws IOException, IllegalAccessException {
    // note how fieldName is variously manipulated in this method to make element ids and contents
    String displayName = getDisplayName(fieldName, nameLookup);
    out.println("<tr id=\""+fieldName+"Row\">");
    out.println("  <td id=\""+fieldName+"Title\">"+displayName+"</td>");
    out.println("  <td> <select multiple name=\""+fieldName+"\" id=\""+fieldName+"\"/>");
    out.println("    <option value=\"None\" selected=\"selected\"></option>");
    for (String val: valueOptions) {
      out.println("    <option value=\""+val+"\">"+val+"</option>");
    }
    out.println("  </select></td>");
    out.println("</tr>");
  }

  public static void printStringFieldSearchRowBoldTitle(int colLen, Boolean isInFlexbox, Boolean isForIndividualOrOccurrenceSearch, String fieldDisplayName, String fieldName, List<String> displayOptions, List<String> valueOptions, javax.servlet.jsp.JspWriter out, Properties nameLookup) throws IOException, IllegalAccessException {
    // note how fieldName is variously manipulated in this method to make element ids and contents
    String displayName = getDisplayName(fieldDisplayName, nameLookup);
    if(!isInFlexbox){
      out.println("<div class=\"col-xs-12 col-sm-12 col-md-"+ colLen +" col-lg-" + colLen + "\">");
    } else{
      out.println("<div class=\"flex-left-justify-no-margin\">");
    }
    if(isForIndividualOrOccurrenceSearch == true){
      out.println("<br/><strong>"+displayName+"</strong><br/>"); //<td id=\""+fieldName+"Title\"><br/>
    } else{
      out.println("<tr id=\""+fieldName+"Row\">");
      out.println("<td id=\""+fieldName+"Title\"><br/><strong>"+displayName+"</strong><br/>");
    }
    out.println("<select multiple name=\""+fieldName+"\" id=\""+fieldName+"\"/>");
    out.println("<option value=\"None\" selected=\"selected\"></option>");
    for (int i=0; i<displayOptions.size(); i++) {
      out.println("<option value=\""+valueOptions.get(i)+"\">"+displayOptions.get(i)+"</option>");
    }
    if(isForIndividualOrOccurrenceSearch == true){
      out.println("</select>");
    } else{
      out.println("</select></td>");
      out.println("</tr>");
    }
    out.println("</div>");
  }


  public static void setUpProjectDropdown(int colLen, String fieldDisplayName, String fieldName, Properties encprops, JspWriter out, HttpServletRequest request, Shepherd myShepherd){
    User usr = AccessControl.getUser(request, myShepherd);
    if(usr != null){
      List<Project> projects = myShepherd.getProjectsForUserId(usr.getUUID());
      ArrayList<String> projOptions = new ArrayList<String>();
      ArrayList<String> projIds = new ArrayList<String>();
      for (int i = 0; i < projects.size(); i++) { //TODO DRY up
        Project currentProj = projects.get(i);
        String currentProjName = currentProj.getResearchProjectName();
        String currentProjId = currentProj.getResearchProjectId();
        projOptions.add(currentProjName);
        projIds.add(currentProjId);
      }
      try {
        printStringFieldSearchRowBoldTitle(colLen,true, false, fieldDisplayName, fieldName, projOptions, projIds, out, encprops);
      }
      catch(IOException e) {
        System.out.println("IOException: " + e);
      }
      catch(IllegalAccessException e){
        System.out.println("IllegalAccessException: " + e);
      }
    }
  }

  public static void setUpOrgDropdown(String fieldName, Boolean isForIndividualOrOccurrenceSearch, Properties encprops, JspWriter out, HttpServletRequest request, Shepherd myShepherd){
    User usr = AccessControl.getUser(request, myShepherd);
    if(usr != null){
      List<Organization> orgsUserBelongsTo = usr.getOrganizations();
      ArrayList<String> orgOptions = new ArrayList<String>();
      ArrayList<String> orgIds = new ArrayList<String>();
      for (int i = 0; i < orgsUserBelongsTo.size(); i++) { //TODO DRY up
        Organization currentOrg = orgsUserBelongsTo.get(i);
        String currentOrgName = currentOrg.getName();
        String currentOrgId = currentOrg.getId();
        orgOptions.add(currentOrgName);
        orgIds.add(currentOrgId);
      }
      try {
        if(isForIndividualOrOccurrenceSearch == true){
          printStringFieldSearchRowBoldTitle(12, false, true, fieldName, fieldName, orgOptions, orgIds, out, encprops);
        } else{
          printStringFieldSearchRowBoldTitle(12, false, false, fieldName, fieldName, orgOptions, orgIds, out, encprops);
        }
      }
      catch(IOException e) {
        System.out.println("IOException: " + e);
      }
      catch(IllegalAccessException e){
        System.out.println("IllegalAccessException: " + e);
      }
    }
  }

  public static String getDisplayName(String fieldName, Properties nameLookup) throws IOException, IllegalAccessException {
    // Tries to lookup a translation and defaults to some string manipulation
    String defaultName = ClassEditTemplate.prettyFieldName(fieldName);
    String ans = nameLookup.getProperty(fieldName, ClassEditTemplate.capitalizedPrettyFieldName(fieldName));
    if (Util.stringExists(ans)) return ans;
    System.out.println("getDisplayName found no property for "+fieldName+" in "+nameLookup+". Falling back on fieldName");
    return fieldName;
  }
}
