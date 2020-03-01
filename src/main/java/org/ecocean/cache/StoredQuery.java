package org.ecocean.cache;

import java.lang.String;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.Util;
import org.joda.time.DateTime;

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

    public long created;
    public long modified;

    //FUTURE: lastExecuted - last execution time of the query in milliseconds, non-persistent, Long
    //FUTURE: invalidateAfter - number of milliseconds after the lastExecuted time after which the stored results are invalidated and the query must be re-run, non-persistent, Long
    //FUTURE: cachedResult - holds the results from the last time the query was run, non-persistent, List<Object>

    public StoredQuery() {
        this(null, null);
    }
    
    public StoredQuery(String name, String queryString){
      this.name=name;
      this.uuid=Util.generateUUID();
      this.queryString=queryString;
        this.created = System.currentTimeMillis();
        this.updateModified();
    }
    
    public StoredQuery(String name){
      this(name, null);
    }
    

    public void updateModified() {
        this.modified = System.currentTimeMillis();
    }
    public long getCreated() {
        return created;
    }
    public long getModified() {
        return modified;
    }

    public String getName(){return name;}
    public void setName(String newName){
      if(newName==null){this.name=null;}
      else{this.name=newName;}
        this.updateModified();
    }
    
    public String getUUID(){return uuid;}
    public void setUUID(String newUUID){
      if(newUUID==null){this.uuid=null;}
      else{this.uuid=newUUID;}
        this.updateModified();
    }
    
    
    public String getQueryString(){return queryString;}
    public void setQueryString(String newQS){
      if(newQS==null){this.queryString=null;}
      else{this.queryString=newQS;}
        this.updateModified();
    }
    
    public String getCorrespondingIACacheName(){return correspondingIACacheName;}
    public void setCorrespondingIACacheName(String cacheName){
      if(cacheName==null){this.correspondingIACacheName=null;}
      else{this.correspondingIACacheName=cacheName;}
        this.updateModified();
    }
    
    public long getExpirationTimeoutDuration(){return expirationTimeoutDuration;}
    public void setExpirationTimeoutDuration(long timeout){
        expirationTimeoutDuration=timeout;
        this.updateModified();
    }

    public long getNextExpirationTimeoutDuration(){return nextExpirationTimeout;}
    public void setNextExpirationTimeoutDuration(long timeout){
        nextExpirationTimeout=timeout;
        this.updateModified();
    }
    
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


    public String toString() {
        return new ToStringBuilder(this)
                .append("id", uuid)
                .append("name", name)
                .append("created", new DateTime(created))
                .append("modified", new DateTime(modified))
                .toString();
    }
}
