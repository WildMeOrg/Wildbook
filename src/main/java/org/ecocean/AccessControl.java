/*
   note: in currenct incarnation, "anonymous" has username == null
   this should be assigned for "anonymous-owned" objects (as opposed to a null AccessControl value
*/
package org.ecocean;

import org.json.JSONObject;
import javax.servlet.http.HttpServletRequest;

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
}
