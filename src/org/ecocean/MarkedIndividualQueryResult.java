package org.ecocean;

import java.util.Vector;

public class MarkedIndividualQueryResult {

  private Vector<MarkedIndividual> result;
  private String jdoqlRepresentation;
  private String queryPrettyPrint;

  public MarkedIndividualQueryResult(Vector<MarkedIndividual> result, String jdoqlRepresentation, String queryPrettyPrint) {
    this.result = result;
    this.jdoqlRepresentation = jdoqlRepresentation;
    this.queryPrettyPrint = queryPrettyPrint;
  }

  public Vector<MarkedIndividual> getResult() {
    return result;
  }

  public String getJDOQLRepresentation() {
    return jdoqlRepresentation;
  }

  public String getQueryPrettyPrint() {
    return queryPrettyPrint;
  }

}
