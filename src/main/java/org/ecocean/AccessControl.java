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
        this.username = ((request.getUserPrincipal() == null) ? null : request.getUserPrincipal().getName());
    }

    public boolean isAnonymous() {
        return (username == null);
    }
}
