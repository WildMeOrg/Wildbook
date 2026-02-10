package org.ecocean;

import com.pgvector.PGvector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javax.jdo.Query;
import org.json.JSONObject;
import org.postgresql.jdbc.PgArray;

import org.json.JSONArray;
import org.json.JSONException;

import org.ecocean.ia.IAException;
// import org.ecocean.ia.MatchResult;
import org.ecocean.ia.MLService;
import org.ecocean.ia.Task;
import org.ecocean.shepherd.core.Shepherd;

// https://github.com/pgvector/pgvector
// https://github.com/pgvector/pgvector-java
// https://www.thenile.dev/blog/pgvector_myth_debunking
// CREATE EXTENSION IF NOT EXISTS vector

public class Embedding implements java.io.Serializable {
    private String id;
    private Annotation annotation;
    private PGvector vector;
    private float[] vectorFloatArray;
    private String method;
    private String methodVersion;
    private long created;

    // for trying to query vectors of annots without embeddings
    public static int BACKGROUND_SLICE_SIZE = 10;
    public static int BACKGROUND_MINUTES = 30;

    public Embedding() {}

    public Embedding(Annotation ann, String method, String methodVersion, PGvector vec) {
        this.id = Util.generateUUID();
        this.annotation = ann;
        this.setVector(vec);
        this.method = method;
        this.methodVersion = methodVersion;
        this.created = System.currentTimeMillis();
        this.getVector();
        if (ann != null) ann.addEmbedding(this);
    }

    public Embedding(Annotation ann, String method, String methodVersion, JSONArray vecArr) {
        this(ann, method, methodVersion, (PGvector)null);
        this.setVector(vecArr);
        this.getVector();
    }

    public String getId() {
        return id;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public void setAnnotation(Annotation ann) {
        this.annotation = ann;
        if (!ann.hasEmbedding(this)) ann.addEmbedding(this);
    }

    public PGvector getVector() {
        if ((vector == null) && (vectorFloatArray != null))
            vector = new PGvector(vectorFloatArray);
        return vector;
    }

    public void setVector(PGvector vec) {
        if (vec != null) {
            vectorFloatArray = vec.toArray();
        } else {
            vectorFloatArray = null;
        }
        this.vector = vec;
    }

    public void setVector(JSONArray varr) {
        this.setVector(vectorFromJSONArray(varr));
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getMethodVersion() {
        return methodVersion;
    }

    public void setMethodVersion(String methodVersion) {
        this.methodVersion = methodVersion;
    }

    public String getMethodDescription() {
        return ((method == null) ? "(unknown)" : method) + " " + ((methodVersion ==
                   null) ? "(unknown version)" : methodVersion);
    }

    public long getCreated() {
        return created;
    }

    public float[] vectorToFloatArray() {
        getVector();
        if (vector == null) return null;
        return vector.toArray();
    }

    public int vectorLength() {
        getVector();
        if (vector == null) return 0;
        return vector.toArray().length;
    }

    public static PGvector vectorFromJSONArray(JSONArray varr) {
        if (varr == null) return null;
        float[] vecVals = new float[varr.length()];
        for (int i = 0; i < varr.length(); i++) {
            try {
                vecVals[i] = varr.getFloat(i);
            } catch (JSONException ex) {
                System.out.println("[WARNING] Embedding.setVector() could not getFloat at i=" + i +
                    " of " + varr);
                vecVals[i] = varr.getFloat(i);
            }
        }
        return new PGvector(vecVals);
    }

/* note: these have been deprecated but just kept for reference

    // these shenanigans could be avoided if datanucleus supported vectors, but alas
    public PGvector loadVector(Shepherd myShepherd) {
        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
            this.id + "'";
        Query q = null;

        try {
            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
            List results = (List)q.execute();
            Iterator it = results.iterator();
            if (it.hasNext()) {
                PgArray parr = (PgArray)it.next();
                Float[] vf = (Float[])parr.getArray();
                // we need little-f so we gotta skip nulls, which we should never get from a db vector!
                float[] vecVals = new float[vf.length];
                for (int i = 0; i < vf.length; i++) {
                    vecVals[i] = ((vf[i] == null) ? 0f : vf[i]);
                }
                this.vector = new PGvector(vecVals);
            }
        } catch (Exception ex) {
            System.out.println("[ERROR] could not loadVector() on " + this.toString());
            ex.printStackTrace();
        } finally {
            if (q != null) q.closeAll();
        }
        return this.vector;
    }

    public void storeVector(Shepherd myShepherd) {
        if (vector == null) return;
        List<String> vals = new java.util.ArrayList<String>();
        for (float f : vector.toArray()) {
            vals.add(Float.toString(f));
        }
        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
            vals) + "]' WHERE \"ID\"='" + this.id + "'";
        Query q = null;

        try {
            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
            q.execute();
        } catch (Exception ex) {
            System.out.println("[ERROR] could not storeVector() on " + this.toString());
            ex.printStackTrace();
        } finally {
            if (q != null) q.closeAll();
        }
    }
 */

    // TODO: (1) configurable?  (2) exceptions when vector length differs?
    public static int getVectorDimension() {
        return 2152;
    }

    // compare vectors of two Embeddings
    public boolean hasEqualVector(Embedding emb) {
        if (emb == null) return false;
        return Arrays.equals(this.vectorFloatArray, emb.vectorFloatArray);
    }

    // returns final annot id
    public static JSONObject catchUpEmbeddings(Shepherd myShepherd, String startId) {
        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");

        if (embData == null) embData = new JSONObject();
        // this will pick up where last left off, effectively
        // note: passing zero-uuid will effectively override to start over
        // TODO prevent duplicate runs by perhaps locking wity SystemValue like indexing
        if (startId == null) startId = embData.optString("_lastId", null);
        System.out.println("catchUpEmbeddings: beginning at " + startId + "; slice size=" +
            BACKGROUND_SLICE_SIZE);

        String sql =
            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
        if (startId != null) sql += " AND \"ANNOTATION\".\"ID\" > '" + startId + "'";
        sql += " order by \"ANNOTATION\".\"ID\" limit " + BACKGROUND_SLICE_SIZE;
        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
        q.setClass(Annotation.class);
        Collection c = (Collection)q.execute();
        List<Annotation> anns = new ArrayList(c);
        q.closeAll();
        MLService mls = new MLService();
        String lastId = null;
        int ct = 0;
        int ok = 0;
        List<String> runIds = new ArrayList<String>();
        for (Annotation ann : anns) {
            ct++;
            System.out.println("catchUpEmbeddings: [" + ct + "]: " + ann);
            lastId = ann.getId();
            runIds.add(lastId);
            String txStr = ann.getTaxonomyString(myShepherd);
            if (txStr == null) {
                embData.put(ann.getId(), "null taxonomy");
                continue;
            }
            try {
                mls.send(ann, txStr, myShepherd);
                System.out.println("catchUpEmbeddings: completed " + ann);
                ok++; // send() may have found duplicate and not added new, but we count as ok
                /// maybe set on embData when we have *no embeddings* but did not have exception??
            } catch (IAException ex) {
                // certain cases we store in embData, so they *will not be retried later*
                // TODO decide actual cases!!
                embData.put(ann.getId(), ex.toString());
                System.out.println("catchUpEmbeddings: exception " + ann + " -> " + ex);
            }
        }
        System.out.println("catchUpEmbeddings: finished with lastId=" + lastId);
        embData.put("_runCount", ct);
        embData.put("_runOk", ok);
        embData.put("_runIds", runIds);
        embData.put("_lastId", lastId);
        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
        return embData;
    }

    public static boolean findMatchProspects(JSONObject iaConfig, Task task, Shepherd myShepherd) {
        if ((iaConfig == null) || (iaConfig.optString("api_endpoint", null) == null)) return false;
        System.out.println("findMatchProspects() has embedding match: " + iaConfig + ", " + task);
        if ((task == null) || (task.numberAnnotations() < 1)) return true;
        for (Annotation ann : task.getObjectAnnotations()) {
            // first we get matchingSetQuery to find number of candidates
            boolean useClauses = false; // TODO how??
            JSONObject matchingSetQuery = ann.getMatchingSetQuery(myShepherd, task.getParameters(),
                useClauses);
            // then we use matchingSetQuery to get matchQuery (to find prospect matches)
            String[] methodValues = MLService.getMethodValues(iaConfig);
            JSONObject matchQuery = ann.getMatchQuery(methodValues[0], methodValues[1],
                matchingSetQuery);
            if (matchQuery == null) {
                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
                    " due to no suitable embeddings for " + iaConfig);
                return true;
            }
            OpenSearch os = new OpenSearch();
            int numberCandidates = -1;
            try {
                numberCandidates = os.queryCount("annotation", matchingSetQuery);
            } catch (IOException ex) {}
            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
            Task subTask = new Task(task);
            subTask.addObject(ann);
            System.out.println("findMatchProspects() on " + ann + " found " +
                Util.collectionSize(prospects) + " prospects (in " + numberCandidates +
                " candidates) for " + subTask);
/*  FOR FUTURE EXPANSION when merged with MatchResults branch FIXME (also uncomment import at top)
            try {
                // we build this even if empty, cuz that means we got results; just not nice ones
                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates);
                System.out.println("findMatchProspects() created " + mr + " on " + subTask);
                myShepherd.getPM().makePersistent(mr);
            } catch (IOException ex) {
                System.out.println("findMatchProspects() MatchResult creation failed on " + subTask + ": " + ex);
                ex.printStackTrace();
            }
 */
            myShepherd.getPM().makePersistent(subTask);
        }
        return true;
    }

    public String toString() {
        String st = "Embedding " + id;

        st += " (vec len " + this.vectorLength() + ")";
        if (annotation != null) st += " [Annotation " + annotation.getId() + "]";
        st += " " + this.getMethodDescription();
        st += " " + Util.prettyPrintDateTime(this.created);
        return st;
    }
}
