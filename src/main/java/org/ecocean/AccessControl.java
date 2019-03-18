/*
   note: in currenct incarnation, "anonymous" has username == null
   this should be assigned for "anonymous-owned" objects (as opposed to a null AccessControl value
*/
package org.ecocean;

import org.json.JSONObject;
import javax.servlet.http.HttpServletRequest;
import org.ecocean.security.Collaboration;
import java.util.Properties;

public class AccessControl implements java.io.Serializable {

    private static final long serialVersionUID = -7934293487298429842L;

    protected long id;
    protected String username;

    public AccessControl() {
    }

    public AccessControl(final String username) {
        this.username = username;
    }

    public AccessControl(final int id, final String username) {
        this.id = id;
        this.username = username;
    }

    public AccessControl(final HttpServletRequest request) {
        this.username = simpleUserString(request);
    }

    public boolean isAnonymous() {
        return (username == null);
    }
    //this static version is handy when you have no AccessControl to look at
    public static boolean isAnonymous(final HttpServletRequest request) {
      try{
        return ((request == null) || request.getUserPrincipal() == null);
      }
      catch(Exception e){return true;}
    }

    public static JSONObject userAsJSONObject(final HttpServletRequest request) {
        JSONObject uj = new JSONObject();
        if (request.getUserPrincipal() == null) {
            uj.put("username", (String)null);
            uj.put("anonymous", true);
        } else {
            uj.put("username", request.getUserPrincipal().getName());
        }
        return uj;
    }

    //null when not logged in
    public static String simpleUserString(final HttpServletRequest request) {
        if (request == null) return null;
        return ((request.getUserPrincipal() == null) ? null : request.getUserPrincipal().getName());
    }
    public static User getUser(final HttpServletRequest request, Shepherd myShepherd) {
        if ((request == null) || (request.getUserPrincipal() == null) || (request.getUserPrincipal().getName() == null)) return null;
        return myShepherd.getUser(request.getUserPrincipal().getName());
    }


    //maybe this export stuff can be in its own class... just throwing in here for now
    //   exportType is an arbitrary (unique) string, e.g. "gbif"
    public static boolean canExport(String context, String exportType, Encounter enc) {
        if (enc == null) return false;
        if (!isExportEnabled(context, exportType)) {
            System.out.println("INFO: exporter '" + exportType + "' not enabled; cannot export enc id=" + enc.getCatalogNumber());
            return false;
        }

        //if we do not have collaborationSecurityEnabled, canExport => true, at this point
        //   XXX this will change if/when we decide to support group-level separately?
        if (!Util.booleanNotFalse(getExportProperty(context, exportType, "collaborationSecurityEnabled"))) return true;

        //we must have pairwise collab enabled, thus:
	if (!Collaboration.securityEnabled(context)) {
            System.out.println("WARNING: export collaborationSecurityEnabled is set true, but system collaboration security is NOT enabled; failing to allow export for type " + exportType);
            return false;  //err on side of safety
        }
        String exportUsername = getExportProperty(context, exportType, "collaborationUsername");
	if (exportUsername == null) {
            System.out.println("WARNING: export collaborationSecurityEnabled is set true, but no " + exportType + ".collaborationUsername was set; failing");
            return false;
        }
	return Collaboration.canUserAccessEncounter(enc, context, exportUsername);
    }

    public static boolean isExportEnabled(String context, String exportType) {
        return Util.booleanNotFalse(getExportProperty(context, exportType, "enabled"));
    }
    public static String getExportProperty(String context, String exportType, String label) {
        return getExportProperty(context, exportType, label, null);
    }
    public static String getExportProperty(String context, String exportType, String label, String def) {
        if ((exportType == null) || (label == null)) return null;  //fail
        Properties p = getExportProperties(context);
        if (p == null) {
            System.out.println("getExportProperty(" + label + ") has no properties; export.properties unavailable?");
            return null;
        }
        String fullLable = exportType + "." + label;
        return p.getProperty(label, def);
    }
    private static Properties getExportProperties(String context) {
        try {
            return ShepherdProperties.getProperties("export.properties", "", context);
        } catch (Exception ex) {
            return null;
        }
    }
}
