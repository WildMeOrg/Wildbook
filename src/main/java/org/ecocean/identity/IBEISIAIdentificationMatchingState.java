package org.ecocean.identity;

import org.ecocean.Shepherd;
import javax.jdo.Query;
import javax.jdo.Extent;
import java.util.Collection;
import org.json.JSONArray;

public class IBEISIAIdentificationMatchingState implements java.io.Serializable {
    protected String annId1;
    protected String annId2;
    protected String state;

    public IBEISIAIdentificationMatchingState() {}

    public IBEISIAIdentificationMatchingState(String a1, String a2, String s) {
        this.annId1 = a1;
        this.annId2 = a2;
        this.state = s;
    }

    public String getState() {
        return state;
    }

    public static IBEISIAIdentificationMatchingState set(String a1, String a2, String state, Shepherd myShepherd) {
        if ((a1 == null) || (a2 == null)) return null;
        IBEISIAIdentificationMatchingState m = load(a1, a2, myShepherd);
        if ((m != null) && m.state.equals(state)) return m;
        if (m == null) {
            m = new IBEISIAIdentificationMatchingState(a1, a2, state);
        } else {
            m.state = state;
        }
        myShepherd.getPM().makePersistent(m);
        return m;
    }

    public static IBEISIAIdentificationMatchingState load(String a1, String a2, Shepherd myShepherd) {
        if ((a1 == null) || (a2 == null)) return null;
        Query query = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.identity.IBEISIAIdentificationMatchingState WHERE annId1 == \"" + a1 + "\" && annId2 == \"" + a2 + "\"");
        query.setUnique(true);
        IBEISIAIdentificationMatchingState s = (IBEISIAIdentificationMatchingState)(query.execute());
        query.closeAll();
        return s;
    }

    public static JSONArray allAsJSONArray(Shepherd myShepherd) {
        Extent all = myShepherd.getPM().getExtent(IBEISIAIdentificationMatchingState.class, true);
        Query q = myShepherd.getPM().newQuery(all);
        Collection allC = (Collection)(q.execute());
        JSONArray jarr = new JSONArray();
        for (Object o : allC) {
            IBEISIAIdentificationMatchingState ms = (IBEISIAIdentificationMatchingState)o;
            jarr.put(new JSONArray(new String[]{ms.annId1, ms.annId2, ms.state}));
        }
        return jarr;
    }

}

