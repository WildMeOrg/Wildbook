/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean;

import javax.jdo.JDOException;
import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManagerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

import java.util.TreeMap;

import java.util.concurrent.ConcurrentHashMap;


public class ShepherdPMF {

  //private static PersistenceManagerFactory pmf;
  //private static String currentContext="context0";
  private static TreeMap<String,PersistenceManagerFactory> pmfs=new TreeMap<String,PersistenceManagerFactory>();

  private static ConcurrentHashMap<String,String> shepherds=new ConcurrentHashMap<String, String>();


  public synchronized static PersistenceManagerFactory getPMF(String context) {
    //public static PersistenceManagerFactory getPMF(String dbLocation) {
    
    if(pmfs==null){pmfs=new TreeMap<String,PersistenceManagerFactory>();}
    
    try {
      if ((!pmfs.containsKey(context))||(pmfs.get(context).isClosed())) {

        Properties dnProperties = new Properties();


        dnProperties.setProperty("javax.jdo.PersistenceManagerFactoryClass", "org.datanucleus.api.jdo.JDOPersistenceManagerFactory");

        Properties props = new Properties();
        String shepherdDataDir="shepherd_data_dir";
        
        //System.out.println("     Let's find the corresponding dataDir for context: "+context);
        if((ContextConfiguration.getDataDirForContext(context)!=null)&&(!ContextConfiguration.getDataDirForContext(context).trim().equals(""))){
          //System.out.println("     Looking up corresponding contextDir...");
          shepherdDataDir=ContextConfiguration.getDataDirForContext(context);
          
        }
        //System.out.println("ShepherdPMF: Data directory for context "+context+" is: "+shepherdDataDir);
        Properties overrideProps=loadOverrideProps(shepherdDataDir);
        //System.out.println(overrideProps);
        
        if(overrideProps.size()>0){props=overrideProps;}
        else {
          //otherwise load the embedded commonConfig
          
          try {
            //props.load(ShepherdPMF.class.getResourceAsStream("/bundles/jdoconfig.properties"));
            props=ShepherdProperties.getProperties("jdoconfig.properties", "");
          } 
          catch (Exception ioe) {
            ioe.printStackTrace();
          }
        }
        
        
        Enumeration<Object> propsNames = props.keys();
        while (propsNames.hasMoreElements()) {
          String name = (String) propsNames.nextElement();
          if (name.startsWith("datanucleus") || name.startsWith("javax.jdo")) {
            dnProperties.setProperty(name, props.getProperty(name).trim());
          }
        }
        
        //make sure to close an old PMF if switching
        //if(pmf!=null){pmf.close();}

        pmfs.put(context, JDOHelper.getPersistenceManagerFactory(dnProperties));
        return pmfs.get(context);

      }
      else{
        
        return pmfs.get(context);
        
      }
      
    } catch (JDOException jdo) {
      jdo.printStackTrace();
      System.out.println("I couldn't instantiate a PMF.");
      return null;
    }
  }
  
  public static Properties loadOverrideProps(String shepherdDataDir) {
    //System.out.println("     Starting loadOverrideProps");
    Properties myProps=new Properties();
    File configDir = new File("webapps/"+shepherdDataDir+"/WEB-INF/classes/bundles");
    //System.out.println("     In dir: "+configDir.getAbsolutePath());
    //sometimes this ends up being the "bin" directory of the J2EE container
    //we need to fix that
    if((configDir.getAbsolutePath().contains("/bin/")) || (configDir.getAbsolutePath().contains("\\bin\\"))){
      String fixedPath=configDir.getAbsolutePath().replaceAll("/bin", "").replaceAll("\\\\bin", "");
      configDir=new File(fixedPath);
      //System.out.println("     Fixing the bin issue in Shepherd PMF. ");
      //System.out.println("     The fix abs path is: "+configDir.getAbsolutePath());
    }
     //System.out.println("     Looking in: "+configDir.getAbsolutePath());
    if(!configDir.exists()){configDir.mkdirs();}
    File configFile = new File(configDir, "jdoconfig.properties");
    if (configFile.exists()) {
      //System.out.println("     Overriding default properties with " + configFile.getAbsolutePath());
      FileInputStream fileInputStream = null;
      try {
        fileInputStream = new FileInputStream(configFile);
        myProps.load(fileInputStream);
      } catch (Exception e) {
        e.printStackTrace();
      }
      finally {
        if (fileInputStream != null) {
          try {
            fileInputStream.close();
          } catch (Exception e2) {
            e2.printStackTrace();
          }
        }
      }
    }
    return myProps;
  }
  
  public static void setShepherdState(String shepherdID, String state){
    if(shepherds==null) shepherds=new ConcurrentHashMap<String, String>();

    shepherds.put(shepherdID, state);
  }
  
  public static void removeShepherdState(String shepherdID){
    if(shepherds==null) shepherds=new ConcurrentHashMap<String, String>();

    shepherds.remove(shepherdID);
  }
  
  public static String getShepherdState(String shepherdID){
    if(shepherds==null) shepherds=new ConcurrentHashMap<String, String>();

    return shepherds.get(shepherdID);
  }
  
  public static ConcurrentHashMap<String,String> getAllShepherdStates(){
    if(shepherds==null) shepherds=new ConcurrentHashMap<String, String>();
    return shepherds;
  }
  

}
