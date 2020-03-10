/*
    this is a Shepherd-compatible object, which only can be used for reading from db.
    it will use (when available) "jdoconfigReadOnly.properties" to access a separate
    db instance.  this is very handy for (read-only) secondary postgres replication, for example.
*/
package org.ecocean;

import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServletRequest;
import org.ecocean.servlet.ServletUtilities;

public class ShepherdRO extends Shepherd {
    public ShepherdRO(String context) {
        super(context, true);
    }
    public ShepherdRO(HttpServletRequest req) {
        super(ServletUtilities.getContext(req), true);
    }
}
