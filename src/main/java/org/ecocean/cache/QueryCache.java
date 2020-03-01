package org.ecocean.cache;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;

import org.ecocean.cache.StoredQuery;
import org.ecocean.Shepherd;
import org.json.JSONObject;

//A singleton responsible for storing, retrieving, and eventually executing queries that are desired for caching. It will have all necessary getters and setters for dealing with the cache.
public class QueryCache {
  
  private Map<String,CachedQuery> cachedQueries;
  private String context = null;

  public QueryCache(){}
  public QueryCache(String context) {
        this.context = context;
  }

  public CachedQuery getQueryByName(String name) {
    if(cachedQueries==null)loadQueries();
    return cachedQueries.get(name);
  }
  
  
  public Map<String,CachedQuery> cachedQueries(){return cachedQueries;}
  
  public void loadQueries() {
    if (context == null) throw new RuntimeException("QueryCache.loadQueries() called with context null");
    cachedQueries=new HashMap<String,CachedQuery>();
    Shepherd myShepherd=new Shepherd(context);
    myShepherd.beginDBTransaction();
    
    List<StoredQuery> queries=myShepherd.getAllStoredQueries();
    int numQueries=queries.size();
    for(int i=0;i<numQueries;i++){
      StoredQuery sq=queries.get(i);
      cachedQueries.put(sq.getName(), sq.getCachedQueryCopy());
    }
    
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
  }
  
  
  public void invalidateByName(String name) throws IOException {
    if(cachedQueries.containsKey(name)){
      cachedQueries.get(name).invalidate();
    }
  }
  
  public void addCachedQuery(JSONObject jsonobj,String name, boolean persistAsStoredQuery, Shepherd myShepherd){
    
    //create CachedQuery object
    CachedQuery cq=new CachedQuery(name,jsonobj, persistAsStoredQuery, myShepherd);
    //put on HashMap
    cachedQueries.put(name, cq);
    
  }
  
  public void addCachedQuery(CachedQuery cq){
    
    cachedQueries.put(cq.getName(), cq);
    
  }


  
}
