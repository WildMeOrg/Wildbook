package org.ecocean.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.String;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.io.IOException;

import javax.jdo.Query;

import org.apache.commons.io.IOUtils;
import org.datanucleus.ExecutionContext;
import org.datanucleus.api.jdo.JDOPersistenceManager;
import org.datanucleus.api.rest.RESTUtils;
import org.ecocean.Shepherd;
import org.ecocean.ShepherdProperties;
import org.ecocean.Util;
import org.json.JSONArray;
import org.json.JSONObject;

//A non-persistent object representing a single StoredQuery. 
public class CachedQuery {
  
    private StoredQuery storedQuery = null;
    public static final String STATUS_PENDING = "pending";  //pending review (needs action by user)
    public static final String CACHE_PROPERTIES_PROPFILE = "cache.properties";
    public static final String CACHE_PROPERTIES_ROOTDIR = "cacheRootDirectory";

    public CachedQuery(StoredQuery sq){
      this.storedQuery = sq;
      this.uuid=sq.getUUID();
      this.queryString=sq.getQueryString();
      this.name=sq.getName();
      this.correspondingIACacheName=sq.getCorrespondingIACacheName();
      this.expirationTimeoutDuration=sq.getExpirationTimeoutDuration();
      this.nextExpirationTimeout=sq.getNextExpirationTimeoutDuration();
    }
    
    public CachedQuery(String name,String queryString,long expirationTimeoutDuration){
      this.queryString=queryString;
      this.name=name;
      this.expirationTimeoutDuration=expirationTimeoutDuration;
    }
    
    public CachedQuery(String name,JSONObject jsonSerializedQueryResult, boolean persistAsStoredQuery, Shepherd myShepherd){
      this.name=name;
      this.jsonSerializedQueryResult=jsonSerializedQueryResult;
      
      if(persistAsStoredQuery){
        
        try{
          
          //OK, so we need to serialize out the result
          Util.writeToFile(jsonSerializedQueryResult.toString(), getCacheFile().getAbsolutePath());
          
          StoredQuery sq=new StoredQuery(name);
          myShepherd.getPM().makePersistent(sq);
          myShepherd.commitDBTransaction();
          myShepherd.beginDBTransaction();
          
          
        }
        catch(Exception e){
          e.printStackTrace();
        }
        
      }
      
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
    
    public JSONObject jsonSerializedQueryResult;
    public Integer collectionQueryCount;
    


    public String getName(){return name;}

    
    public String getUUID(){return uuid;}

    
    
    public String getQueryString(){return queryString;}

    
    public String getCorrespondingIACacheName(){return correspondingIACacheName;}

    public long getExpirationTimeoutDuration(){return expirationTimeoutDuration;}

    public long getNextExpirationTimeout(){return nextExpirationTimeout;}

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
    
    public JSONObject executeCollectionQuery(Shepherd myShepherd,boolean useSerializedJSONCache) throws IOException {

      //first, can we use serialized cache and if so, does it exist
      if(useSerializedJSONCache){
        long time=System.currentTimeMillis();
        if((jsonSerializedQueryResult==null)||((expirationTimeoutDuration>-1)&&(time>nextExpirationTimeout))){
          //System.out.println("*****Status 1");
          //check if we have a serialized cache
          
          //first if the cache is null but not expired, then just load it.
          //((expirationTimeoutDuration==-1)||(((expirationTimeoutDuration>-1)&&(time<nextExpirationTimeout))))
          if((jsonSerializedQueryResult==null) && getCacheFile().exists()){
            //load the cache file and return the JSONObject
            //System.out.println("*****Status 1a");
            nextExpirationTimeout=time+expirationTimeoutDuration;
            return loadCachedJSON();
          }
          //gotta regen the cache
          else{
 
            //System.out.println("cached file does NOT exist or has expired!");
            //run the query and set the cache
            List results=executeQuery(myShepherd);
            
            //serialize the results
            JSONObject jsonobj=serializeCollectionToJSON(results, myShepherd);
            //System.out.println("finished serializing the result: "+jsonobj);
                
            nextExpirationTimeout=time+expirationTimeoutDuration;
            //then return the List<Object>
            System.out.println("*****Status 1b");
            return jsonobj;
          }
          
        }
        else{
          
          //data still valid, just send it back quickly! 
          System.out.println("*****Status 2");
           return jsonSerializedQueryResult;
          
        }
        
        
      }
      //just run the query since the user has chosen to override the cache
      else{
        List<Object> c=executeQuery(myShepherd);
        JSONObject jsonobj=convertToJson(c, ((JDOPersistenceManager)myShepherd.getPM()).getExecutionContext());
        System.out.println("*****Status 3");
        return jsonobj;
        
      }
      
      
      

      
    }
    
    /*
    public List executeCollectionQueryAsObjects(Shepherd myShepherd,boolean useSerializedJSONCache,String className){
      List results=new ArrayList<Object>();
      try{
        JSONObject jsonobj=executeCollectionQuery(myShepherd,useSerializedJSONCache);
        JSONArray arr=jsonobj.getJSONArray("result");
        System.out.println("arr:"+arr+"\n\n");
        int numResults=arr.length();
        for(int i=0;i<numResults;i++){
          JSONObject js=arr.getJSONObject(i);
          Object obj=RESTUtils.getObjectFromJSONObject(Util.toggleJSONObject(jsonobj), className, ((JDOPersistenceManager)myShepherd.getPM()).getExecutionContext());
          System.out.println("obj: "+obj);
          results.add(obj);
        }
        return results;
      }
      catch(Exception e){
        e.printStackTrace();
      }
      return null;
    }
    */

    
    
    public Integer executeCountQuery(Shepherd myShepherd){
      if((collectionQueryCount==null)||((expirationTimeoutDuration>-1)&&(System.currentTimeMillis()>nextExpirationTimeout))){
        try{
          System.out.println("Executing executeCountQuery");
          List<Object> c=executeQuery(myShepherd);
          collectionQueryCount=new Integer(c.size());
          nextExpirationTimeout=System.currentTimeMillis()+expirationTimeoutDuration;
        }
        catch(Exception e){e.printStackTrace();}
      }
      return collectionQueryCount;
    } 
    
    public synchronized void invalidate() throws IOException {
      collectionQueryCount=null;
      jsonSerializedQueryResult=null;
      
      //delete the serialized JSON
      getCacheFile().delete();
      
    }
    
    public synchronized JSONObject getJSONSerializedQueryResult(){
      return jsonSerializedQueryResult;
    }
    
    public synchronized void setJSONSerializedQueryResult(JSONObject jsonSerializedQueryResult, boolean serialize){
      if(jsonSerializedQueryResult==null){
        this.jsonSerializedQueryResult=null;
      }
      else{
        this.jsonSerializedQueryResult=jsonSerializedQueryResult;
      }
      try{
        //delete old cache
        getCacheFile().delete();
        
        //if set in the mwthod declaration, serialize the new object cache
        if(serialize)Util.writeToFile(jsonSerializedQueryResult.toString(), getCacheFile().getAbsolutePath());
      }
      catch(Exception e){
        e.printStackTrace();
      }
      this.nextExpirationTimeout=System.currentTimeMillis()+expirationTimeoutDuration;
    }
    
    public JSONObject convertToJson(Collection coll, ExecutionContext ec) {
      JSONArray jarr = new JSONArray();
      for (Object o : coll) {
          if (o instanceof Collection) {
              jarr.put(convertToJson((Collection)o, ec));
          } else {  //TODO can it *only* be an JSONObject-worthy object at this point?
              try{
                jarr.put(Util.toggleJSONObject(RESTUtils.getJSONObjectFromPOJO(o, ec)));
              }
              catch(Exception e){System.out.println("RESTUtils.getJSONObjectFromPOJO threw an exception on "+o.toString());}
          }
      }
      JSONObject jsonobj=new JSONObject();
      jsonobj.put("result", jarr);
      return jsonobj;
  }
    
   public List executeQuery(Shepherd myShepherd){
     //System.out.println("in CachedQuery. executeQuery");
     Query query=myShepherd.getPM().newQuery(queryString);
     Collection c = (Collection) (query.execute());
     try{
       ArrayList al=new ArrayList(c);
       //System.out.println("Finished executeQuery with: "+al.size()+" results.");
       query.closeAll();
       return al;
     }
     catch(Exception e){
       e.printStackTrace();
       query.closeAll();
     }
     return null;
   }  
   
   private JSONObject serializeCollectionToJSON(Collection c, Shepherd myShepherd) {
        File cfile = null; 
     try{
       //System.out.println("in serializeCollectionToJSON for query: "+getName());
       JSONObject jsonobj = convertToJson((Collection)c, ((JDOPersistenceManager)myShepherd.getPM()).getExecutionContext());
       jsonSerializedQueryResult=jsonobj;
       cfile = getCacheFile();
       Util.writeToFile(jsonobj.toString(), cfile.getAbsolutePath());
       //System.out.println("Checking does JSON file exist? "+getCacheFile().exists());
       return jsonobj;
     }
     catch(Exception e){
        System.out.println("NOTE: you must have tomcat-writeable directory set for " + CACHE_PROPERTIES_ROOTDIR + " in " + CACHE_PROPERTIES_PROPFILE);
        if (cfile != null) System.out.println(" dir = " + cfile.getParentFile());
       e.printStackTrace();
       return null;
     }
   }
   
   public synchronized JSONObject loadCachedJSON() {
     //just load and return the List<Object> from the cache
     
     //System.out.println("loading cached JSON: ");
     try{
       File sFile=getCacheFile();
       //System.out.println("loading cached JSON: "+sFile.getAbsolutePath());
       
       if(sFile.exists()){
         InputStream is = new FileInputStream(sFile);
         String jsonTxt = IOUtils.toString(is, "UTF-8");
         //System.out.println(jsonTxt);
         JSONObject jsonobj = new JSONObject(jsonTxt);
         //RESTUtils.getObjectFromJSONObject(jsonobj, String className, ((JDOPersistenceManager)myShepherd.getPM()).getExecutionContext());
         this.jsonSerializedQueryResult=jsonobj;
         return jsonobj;
       }
       else{return null;}
       

     }
     catch(Exception e){
       e.printStackTrace();
     }
     return null;
   }

    //this only loads from disk if "it is necessary" (TBD?)
    public synchronized JSONObject loadCachedJSONIfNeeded() {
        if (jsonSerializedQueryResult != null) return jsonSerializedQueryResult;
        return loadCachedJSON();
    }

    public File getCacheFile() throws IOException {
        Properties cprops = null;
        try {
            cprops = ShepherdProperties.getProperties(CACHE_PROPERTIES_PROPFILE, "");
        } catch (java.lang.NullPointerException npe) {}  //this is thrown above if we dont have a file, so we catch it, then:
        if (cprops == null) throw new IOException("CachedQuery.getCacheFile() failed to find " + CACHE_PROPERTIES_PROPFILE);
        String writePath = cprops.getProperty(CACHE_PROPERTIES_ROOTDIR);
        if (writePath == null) throw new IOException("CachedQuery.getCacheFile() must have set " + CACHE_PROPERTIES_ROOTDIR + " (tomcat readable) in " + CACHE_PROPERTIES_PROPFILE);
        if(!writePath.endsWith("/"))writePath+="/";
        writePath+=getName()+".json";
        //System.out.println("Cache file path to: "+writePath);
        return new File(writePath);
    }

    public StoredQuery getStoredQuery() {
        return storedQuery;
    }
  

}

