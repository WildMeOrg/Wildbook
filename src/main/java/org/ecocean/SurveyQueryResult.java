package org.ecocean;

import java.util.Vector;

public class SurveyQueryResult {

  private Vector<Survey> result;
  private String jdoqlRepresentation;
  private String queryPrettyPrint;

  public SurveyQueryResult(Vector<Survey> result, String jdoqlRepresentation, String queryPrettyPrint) {
    System.out.println("New SurveyQueryResult called. (jdoql,prettyPrint)= ("+jdoqlRepresentation+" ,"+queryPrettyPrint+")");
    this.result = result;
    this.jdoqlRepresentation = jdoqlRepresentation;
    this.queryPrettyPrint = queryPrettyPrint;
  }

  public Vector<Survey> getResult() {
    return result;
  }

  public String getJDOQLRepresentation() {
    return jdoqlRepresentation;
  }

  public String getQueryPrettyPrint() {
    return queryPrettyPrint;
  }

}