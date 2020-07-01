package org.ecocean.ai.utilities;

import org.apache.commons.lang3.StringEscapeUtils;

public class AIUtilities {
  
  /*
   * Used to ensure that Intelligent Agent training and prediction strings have identical string operations applied.
   * 
   */
  public static String youtubePredictorPrepareString(String in){
    
    in=in.replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("'","").replaceAll("'","").toLowerCase();
    in=StringEscapeUtils.escapeEcmaScript(in);
    return in;
    
  }
  

}
