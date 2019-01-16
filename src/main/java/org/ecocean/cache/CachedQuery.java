package org.ecocean.cache;

import java.lang.String;
import java.util.ArrayList;
import java.util.Collection;

import javax.jdo.Query;

import org.ecocean.Encounter;
import org.ecocean.Shepherd;

//A non-persistent object representing a single StoredQuery. 
public class CachedQuery {
  
    public CachedQuery(StoredQuery sq){
      this.uuid=sq.getUUID();
      this.queryString=sq.getQueryString();
      this.name=sq.getName();
      this.correspondingIACacheName=sq.getCorrespondingIACacheName();
      this.expirationTimeoutDuration=sq.getExpirationTimeoutDuration();
      this.nextExpirationTimeout=sq.getNextExpirationTimeoutDuration();
    }
  
  
    //primary key, persistent, String, not null
    private String uuid;
    
    //The JDOQL representation of the query, persistent, String, not null
    private String queryString;
    
    //a human-readable name for the query, persistent, String, not null, unique
    private String name;
    
    //if this query matches an IA cache this field in the name of the cache, String, persistent
    private String correspondingIACacheName;
    
    //The time duration (diff) between create time and this queries expiration time in milliseconds, requiring a refresh of cached items.
    public long expirationTimeoutDuration = -1;
    
    //the next time this cache expires
    public long nextExpirationTimeout  = -1;
    
    //public Collection collectionQueryResults;
    public Integer collectionQueryCount;
    


    public String getName(){return name;}

    
    public String getUUID(){return uuid;}

    
    
    public String getQueryString(){return queryString;}

    
    public String getCorrespondingIACacheName(){return correspondingIACacheName;}

    public long getExpirationTimeoutDuration(){return expirationTimeoutDuration;}

    public long getNextExpirationTimeoutDuration(){return nextExpirationTimeout;}

    public void refreshValues(String context){
      Shepherd myShepherd=new Shepherd(context);
      myShepherd.beginDBTransaction();
      StoredQuery sq=myShepherd.getStoredQuery(uuid);
      this.uuid=sq.getUUID();
      this.queryString=sq.getQueryString();
      this.name=sq.getName();
      this.correspondingIACacheName=sq.getCorrespondingIACacheName();
      this.expirationTimeoutDuration=sq.getExpirationTimeoutDuration();
      this.nextExpirationTimeout=sq.getNextExpirationTimeoutDuration();
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
    
    public Collection executeCollectionQuery(Shepherd myShepherd){
      Query query=myShepherd.getPM().newQuery(queryString);
      Collection c = (Collection) (query.execute());
      query.closeAll();
      return c;
    }
    
    public Integer executeCountQuery(Shepherd myShepherd){
      if((collectionQueryCount==null)||((expirationTimeoutDuration>-1)&&(System.currentTimeMillis()>nextExpirationTimeout))){
        try{
          Collection c=executeCollectionQuery(myShepherd);
          collectionQueryCount=new Integer(c.size());
          nextExpirationTimeout=System.currentTimeMillis()+expirationTimeoutDuration;
        }
        catch(Exception e){e.printStackTrace();}
      }
      return collectionQueryCount;
    } 
    
  
  

}

