package org.ecocean;

import com.pgvector.PGvector;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.jdo.Query;
import org.postgresql.jdbc.PgArray;

import org.json.JSONArray;
import org.json.JSONException;

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

    public Embedding() {}

    public Embedding(Annotation ann, String method, String methodVersion, PGvector vec) {
        this.id = Util.generateUUID();
        this.annotation = ann;
        this.setVector(vec);
        this.method = method;
        this.methodVersion = methodVersion;
        this.created = System.currentTimeMillis();
        this.getVector();
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
        ann.addEmbedding(this);
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

    public String toString() {
        String st = "Embedding " + id;

        st += " (vec len " + this.vectorLength() + ")";
        if (annotation != null) st += " [Annotation " + annotation.getId() + "]";
        st += " " + this.getMethodDescription();
        st += " " + Util.prettyPrintDateTime(this.created);
        return st;
    }
}
