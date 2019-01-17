package org.ecocean.cache;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ecocean.cache.StoredQuery;
import org.ecocean.Shepherd;

//A singleton responsible for storing, retrieving, and eventually executing queries that are desired for caching. It will have all necessary getters and setters for dealing with the cache.
public class QueryCache {
  
  private Map<String,CachedQuery> cachedQueries;

  public QueryCache(){}
  
  
  public CachedQuery getQueryByName(String name, String context){
    if(cachedQueries==null)loadQueries(context);
    return cachedQueries.get(name);
  }
  
  
  public Map<String,CachedQuery> cachedQueries(){return cachedQueries;}
  
  public void loadQueries(String context){
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
  
  
  public void invalidateByName(String name){
    if(cachedQueries.containsKey(name)){
      CachedQuery cq=cachedQueries.get(name).invalidate();
    }
  }


  
}
