package org.ecocean;

import java.util.Vector;

public class OccurrenceQueryResult {
    private Vector<Occurrence> result;
    private String jdoqlRepresentation;
    private String queryPrettyPrint;

    public OccurrenceQueryResult(Vector<Occurrence> result, String jdoqlRepresentation,
        String queryPrettyPrint) {
        System.out.println("New OccurrenceQueryResult called. (jdoql,prettyPrint)= (" +
            jdoqlRepresentation + " ," + queryPrettyPrint + ")");
        this.result = result;
        this.jdoqlRepresentation = jdoqlRepresentation;
        this.queryPrettyPrint = queryPrettyPrint;
    }

    public Vector<Occurrence> getResult() {
        return result;
    }

    public String getJDOQLRepresentation() {
        return jdoqlRepresentation;
    }

    public String getQueryPrettyPrint() {
        return queryPrettyPrint;
    }
}
