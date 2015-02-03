package com.ecostats.flukes;

import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

import com.mongodb.MongoClient;

// Opening and closing the database is inefficient and can lead to memory leaks.
// All MongoDB issues really should be put into a ServletContextListener


/**
 * 
 * The MongoDB NoSQL database is used as it provides more flexible 
 * and dynamic object persistence over other options and removes some 
 * development complexity by avoiding having to create a database table schema. 
 * 
 * Ideally rather than the database defining relations, the Java persistent  
 * classes should self define their relationships, via inheritance (from 
 * parent and abstract classes) and class variable (i.e. field) annotations.
 * 
 */

public class FlukeMongodb {

	private MongoClient mongodb = null; 
	private Datastore datastore = null;
		
	public FlukeMongodb(String host, int port) throws UnknownHostException {
		// TODO Auto-generated constructor stub
		this.getDb(host,port);
	}
	
	private Datastore getDb(String host, int port) throws UnknownHostException{
		if (host==null){
			host="localhost";
		}
		if (port==0){
			port=27017;
		}
		if (this.mongodb==null){
			this.mongodb = new MongoClient(host,port);
		}
		if (this.datastore==null){
			this.datastore = new Morphia().createDatastore(this.mongodb, "flukes");
		}
		return this.datastore;
	}

	public Datastore datastore(){
		return this.datastore;
	}
	
	public void close(){
    	if (this.mongodb!=null){
    		this.mongodb.close();
    	}		
	}
	
	public String sha1(String convertstr) throws NoSuchAlgorithmException{		
		MessageDigest md = MessageDigest.getInstance("SHA-1"); 
		return this.byteArray2Hex(md.digest(convertstr.getBytes()));
	}

	private String byteArray2Hex(final byte[] hash) {
	    Formatter formatter = new Formatter();
	    for (byte b : hash) {
	    	formatter.format("%02x", b);
	    }
	    String returnhex = formatter.toString();
	    formatter.close();
	    return returnhex;
	}

}
