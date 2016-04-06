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
        this(-1, username);
    }

    public AccessControl(final int id, final String username) {
        this.id = id;
        this.username = username;
    }

    public static AccessControl fromRequest(HttpServletRequest request) {
        if (request.getUserPrincipal() == null) return new AccessControl();
        return new AccessControl(request.getUserPrincipal().getName());
    }
}
