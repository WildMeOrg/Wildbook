package org.ecocean;

import java.util.Vector;

public class EncounterQueryResult {

  private Vector<Encounter> result;
  private String jdoqlRepresentation;
  private String queryPrettyPrint;

  public EncounterQueryResult(Vector<Encounter> result, String jdoqlRepresentation, String queryPrettyPrint) {
    this.result = result;
    this.jdoqlRepresentation = jdoqlRepresentation;
    this.queryPrettyPrint = queryPrettyPrint;
  }

  public Vector<Encounter> getResult() {
    return result;
  }

  public String getJDOQLRepresentation() {
    return jdoqlRepresentation;
  }

  public String getQueryPrettyPrint() {
    return queryPrettyPrint;
  }

}
