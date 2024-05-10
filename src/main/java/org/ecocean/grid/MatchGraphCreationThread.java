package org.ecocean.grid;

import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
// import org.ecocean.Occurrence;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;

public class MatchGraphCreationThread implements Runnable, ISharkGridThread {
    public Thread threadCreationObject;
    java.util.Properties props2 = new java.util.Properties();
    GridManager gm;
    String context = "context0";
    String jdoql = "SELECT FROM org.ecocean.Encounter";
    boolean finished = false;
    /**
     * Constructor to create a new thread object
     */
    public MatchGraphCreationThread(HttpServletRequest request) {
        gm = GridManagerFactory.getGridManager();
        threadCreationObject = new Thread(this, ("MatchGraphCreationThread.class"));
        this.context = ServletUtilities.getContext(request);
    }

    public MatchGraphCreationThread() {
        gm = GridManagerFactory.getGridManager();
        threadCreationObject = new Thread(this, ("MatchGraphCreationThread.class"));
    }

    /**
     * main method of the shepherd thread
     */
    public void run() {
        createThem();
    }

    public void createThem() {
        System.out.println("Starting MatchGraphCreationThread!");
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MatchGraphCreationThread.class");
        GridManager gm = GridManagerFactory.getGridManager();
        PersistenceManager pm = myShepherd.getPM();
        PersistenceManagerFactory pmf = pm.getPersistenceManagerFactory();

        javax.jdo.FetchGroup grp2 = pmf.getFetchGroup(Encounter.class, "encSearchResults");
        grp2.addMember("sex").addMember("catalogNumber").addMember("year").addMember(
            "hour").addMember("month").addMember("minutes").addMember("day").addMember(
            "spots").addMember("rightSpots").addMember("leftReferenceSpots").addMember(
            "rightReferenceSpots");

        myShepherd.getPM().getFetchPlan().setGroup("encSearchResults");

        myShepherd.beginDBTransaction();

        List<String> encNumbers = myShepherd.getAllEncounterNumbers();
        int numEncs = encNumbers.size();
        System.out.println("MatchGraphCreationThread is exploring this many encounters: " +
            numEncs);
        myShepherd.rollbackDBTransaction();

        // gm.initializeNodes((int)(numEncs*2/3));
        gm.resetMatchGraphWithInitialCapacity(numEncs);

        // Query query=null;
        try {
            // query=myShepherd.getPM().newQuery(jdoql);
            // Collection c = (Collection) (query.execute());
            // System.out.println("Num scans to do: "+c.size());
            // Iterator encounters = c.iterator();

            int count = 0;
            for (int i = 0; i < numEncs; i++) {
                myShepherd.beginDBTransaction();
                Encounter enc = myShepherd.getEncounter(encNumbers.get(i));
                if (((enc.getRightSpots() != null) && (enc.getRightSpots().size() > 0)) ||
                    ((enc.getSpots() != null) && (enc.getSpots().size() > 0))) {
                    EncounterLite el = new EncounterLite(enc);
                    gm.addMatchGraphEntry(enc.getCatalogNumber(), el);
                    count++;
                }
                myShepherd.rollbackDBTransaction();
            }
            // myShepherd.rollbackDBTransaction();
            finished = true;
        } catch (Exception e) {
            System.out.println(
                "I failed while constructing the EncounterLites in MatchGraphCreationThread.");
            e.printStackTrace();
            myShepherd.rollbackDBTransaction();
        } finally {
            // if(query!=null){query.closeAll();}
            myShepherd.closeDBTransaction();
        }
        System.out.println("Ending MatchGraphCreationThread!");
    }

    public boolean isFinished() {
        return finished;
    }
}
