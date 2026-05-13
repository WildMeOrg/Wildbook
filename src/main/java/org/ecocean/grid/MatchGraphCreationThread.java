package org.ecocean.grid;

import org.ecocean.Encounter;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;

import java.util.List;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServletRequest;

public class MatchGraphCreationThread implements Runnable, ISharkGridThread {
    public Thread threadCreationObject;
    java.util.Properties props2 = new java.util.Properties();
    GridManager gm;
    String context = "context0";
    String jdoql = "SELECT FROM org.ecocean.Encounter";
    boolean finished = false;

    private static final int BATCH_SIZE = 500;

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
        long startTime = System.currentTimeMillis();
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

        GridManager.setMatchGraphReady(false);
        gm.resetMatchGraphWithInitialCapacity(numEncs);

        try {
            int count = 0;
            myShepherd.beginDBTransaction();

            for (int i = 0; i < numEncs; i++) {
                Encounter enc = myShepherd.getEncounter(encNumbers.get(i));
                if (((enc.getRightSpots() != null) && (enc.getRightSpots().size() > 0)) ||
                    ((enc.getSpots() != null) && (enc.getSpots().size() > 0))) {
                    EncounterLite el = new EncounterLite(enc);
                    GridManager.addMatchGraphEntryBulk(enc.getCatalogNumber(), el);
                    count++;
                }

                // Batch: rollback and re-begin every BATCH_SIZE to release JDO cache
                if ((i + 1) % BATCH_SIZE == 0) {
                    myShepherd.rollbackDBTransaction();
                    myShepherd.beginDBTransaction();
                    System.out.println("MatchGraphCreationThread progress: " +
                        (i + 1) + "/" + numEncs + " (" + count + " with spots)");
                }
            }
            myShepherd.rollbackDBTransaction();

            // Single resetPatternCounts at the end instead of N times during load
            GridManager.resetPatternCounts();

            finished = true;
            GridManager.setMatchGraphReady(true);
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("Ending MatchGraphCreationThread! " + count +
                " encounters loaded in " + elapsed + "ms");
        } catch (Exception e) {
            System.out.println(
                "I failed while constructing the EncounterLites in MatchGraphCreationThread.");
            e.printStackTrace();
            myShepherd.rollbackDBTransaction();
        } finally {
            myShepherd.closeDBTransaction();
        }
    }

    public boolean isFinished() {
        return finished;
    }
}
