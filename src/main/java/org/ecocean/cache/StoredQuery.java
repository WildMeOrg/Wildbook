package org.ecocean.cache;

import java.lang.String;

import org.ecocean.Util;

//A persistent object representing a single query. 
public class StoredQuery {
  
  
    //primary key, persistent, String, not null
    public String uuid;
    
    //The JDOQL representation of the query, persistent, String, not null
    public String queryString;
    
    //a human-readable name for the query, persistent, String, not null, unique
    public String name;
    
    //if this query matches an IA cache this field in the name of the cache, String, persistent
    public String correspondingIACacheName;
    
    //The time duration (diff) between create time and this queries expiration time in milliseconds, requiring a refresh of cached items.
    public long expirationTimeoutDuration = -1;
    
    //the next time this cache expires
    public long nextExpirationTimeout  = -1;
    
    //FUTURE: lastExecuted - last execution time of the query in milliseconds, non-persistent, Long
    //FUTURE: invalidateAfter - number of milliseconds after the lastExecuted time after which the stored results are invalidated and the query must be re-run, non-persistent, Long
    //FUTURE: cachedResult - holds the results from the last time the query was run, non-persistent, List<Object>

    public StoredQuery(){}
    
    public StoredQuery(String name, String queryString){
      this.name=name;
      this.uuid=Util.generateUUID();
      this.queryString=queryString;
    }
    

    public String getName(){return name;}
    public void setName(String newName){
      if(newName==null){this.name=null;}
      else{this.name=newName;}
    }
    
    public String getUUID(){return uuid;}
    public void setUUID(String newUUID){
      if(newUUID==null){this.uuid=null;}
      else{this.uuid=newUUID;}
    }
    
    
    public String getQueryString(){return queryString;}
    public void setQueryString(String newQS){
      if(newQS==null){this.queryString=null;}
      else{this.queryString=newQS;}
    }
    
    public String getCorrespondingIACacheName(){return correspondingIACacheName;}
    public void setCorrespondingIACacheName(String cacheName){
      if(cacheName==null){this.correspondingIACacheName=null;}
      else{this.correspondingIACacheName=cacheName;}
    }
    
    public long getExpirationTimeoutDuration(){return expirationTimeoutDuration;}
    public void setExpirationTimeoutDuration(long timeout){expirationTimeoutDuration=timeout;}
    
    public long getNextExpirationTimeoutDuration(){return nextExpirationTimeout;}
    public void setNextExpirationTimeoutDuration(long timeout){nextExpirationTimeout=timeout;}
    
    /*
    FUTURE: setCachedResult
    FUTURE: getCachedResult
    FUTURE: invalidateCachedResult - immediately invalidates cachedResult
    FUTURE: setInvalidateAfter
    Value of -1 never invalidates cachedResult if not null
    FUTURE: executeQuery
    */

  
    public CachedQuery getCachedQueryCopy(){
      return new CachedQuery(this);
    }

}
