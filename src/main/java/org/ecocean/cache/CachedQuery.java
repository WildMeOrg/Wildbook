package org.ecocean.cache;

import java.io.File;
import java.lang.String;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
    
    public File jsonSerializedQueryResult;
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
    
    public List<Object> executeCollectionQuery(Shepherd myShepherd,boolean useSerializedJSONCache){

      //TBD - handle JSONFileSerialization
      
      Query query=myShepherd.getPM().newQuery(queryString);
      Collection c = (Collection) (query.execute());
      ArrayList<Object> al=new ArrayList<Object>(c);
      if(useSerializedJSONCache){
        
      }
      query.closeAll();
      return al;
    }
    
    public Integer executeCountQuery(Shepherd myShepherd){
      if((collectionQueryCount==null)||((expirationTimeoutDuration>-1)&&(System.currentTimeMillis()>nextExpirationTimeout))){
        try{
          System.out.println("Executing executeCountQuery");
          List<Object> c=executeCollectionQuery(myShepherd,false);
          collectionQueryCount=new Integer(c.size());
          nextExpirationTimeout=System.currentTimeMillis()+expirationTimeoutDuration;
        }
        catch(Exception e){e.printStackTrace();}
      }
      return collectionQueryCount;
    } 
    
    public void invalidate(){
      collectionQueryCount=null;
      jsonSerializedQueryResult=null;
    }
    
    public File getJSONSerializedQueryResult(){
      return jsonSerializedQueryResult;
    }
  
  

}

