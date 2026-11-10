package org.ecocean.auth;

import java.util.Collection;
import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;

/** Uncached loads for authentication decisions, which must never trust a cached copy. */
public final class FreshObjects {
    private FreshObjects() {}

    /**
     * Loads the one object matching a single-parameter filter, bypassing caches, then reloads its
     * state from the datastore. Returns null when {@code value} is null, when no row or more than one
     * row matches, or when the row is removed between the query and the reload.
     */
    @SuppressWarnings("unchecked")
    public static <T> T load(PersistenceManager pm, Class<T> cls, String filter, String value) {
        if (value == null) return null;
        Query<T> query = pm.newQuery(cls, filter);
        try {
            query.setIgnoreCache(true);
            Collection<T> rows = (Collection<T>)query.execute(value);
            if (rows.size() != 1) return null;
            T obj = rows.iterator().next();
            try {
                pm.refresh(obj);
            } catch (JDOObjectNotFoundException deleted) {
                return null;
            }
            return obj;
        } finally { query.closeAll(); }
    }
}
