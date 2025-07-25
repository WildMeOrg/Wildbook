package org.ecocean;

import com.pgvector.PGvector;
import java.util.Iterator;
import java.util.List;
import javax.jdo.Query;
import org.postgresql.jdbc.PgArray;

import org.ecocean.shepherd.core.Shepherd;

// https://github.com/pgvector/pgvector
// https://github.com/pgvector/pgvector-java
// CREATE EXTENSION IF NOT EXISTS vector

public class Embedding implements java.io.Serializable {
    private String id;
    private Annotation annotation;
    private PGvector vector;
    private String method;
    private String methodVersion;
    private long created;

    public Embedding() {}

    public Embedding(Annotation ann, String method, String methodVersion, PGvector vec) {
        this.id = Util.generateUUID();
        this.annotation = ann;
        this.vector = vec;
        this.method = method;
        this.methodVersion = methodVersion;
        this.created = System.currentTimeMillis();
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public void setAnnotation(Annotation ann) {
        this.annotation = ann;
    }

    public PGvector getVector() {
        return vector;
    }

    public void setVector(PGvector vec) {
        this.vector = vec;
    }

    public String getMethod() {
        return method;
    }

    public String getMethodDescription() {
        return ((method == null) ? "(unknown)" : method) + " " + ((methodVersion ==
                   null) ? "(unknown version)" : methodVersion);
    }

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

    public String toString() {
        String st = "Embedding " + id;

        if (annotation != null) st += " [Annotation " + annotation.getId() + "]";
        st += " " + this.getMethodDescription();
        st += " " + Util.prettyPrintDateTime(this.created);
        return st;
    }
}
