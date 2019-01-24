package org.ecocean.cache;

import org.ecocean.cache.QueryCache;

public class QueryCacheFactory {

  private static QueryCache qc;

  public synchronized static QueryCache getQueryCache(String context) {

    try {
      if (qc == null) {

        qc = new QueryCache();
        qc.loadQueries(context);

      }
      
      return qc;
    } catch (Exception jdo) {
      jdo.printStackTrace();
      System.out.println("I couldn't instantiate a QueryCache.");
      return null;
    }
  }

}
