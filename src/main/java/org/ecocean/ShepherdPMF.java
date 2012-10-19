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


public class ShepherdPMF {

  private static PersistenceManagerFactory pmf;

  public synchronized static PersistenceManagerFactory getPMF() {
    //public static PersistenceManagerFactory getPMF(String dbLocation) {
    try {
      if (pmf == null) {

        Properties dnProperties = new Properties();


        dnProperties.setProperty("javax.jdo.PersistenceManagerFactoryClass", "org.datanucleus.api.jdo.JDOPersistenceManagerFactory");

        Properties props = new Properties();
        String shepherdDataDir="shepherd_data_dir";
        if((CommonConfiguration.getProperty("dataDirectoryName")!=null)&&(!CommonConfiguration.getProperty("dataDirectoryName").trim().equals(""))){shepherdDataDir=CommonConfiguration.getProperty("dataDirectoryName");}
        Properties overrideProps=loadOverrideProps(shepherdDataDir);
        //System.out.println(overrideProps);
        
        if(overrideProps.size()>0){props=overrideProps;}
        else {
          //otherwise load the embedded commonConfig
          
          try {
            props.load(ShepherdPMF.class.getResourceAsStream("/bundles/jdoconfig.properties"));
          } 
          catch (IOException ioe) {
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

        pmf = JDOHelper.getPersistenceManagerFactory(dnProperties);


      }
      return pmf;
    } catch (JDOException jdo) {
      jdo.printStackTrace();
      System.out.println("I couldn't instantiate a PMF.");
      return null;
    }
  }
  
  private static Properties loadOverrideProps(String shepherdDataDir) {
    //System.out.println("Starting loadOverrideProps");
    Properties myProps=new Properties();
    File configDir = new File("webapps/"+shepherdDataDir+"/WEB-INF/classes/bundles");
    //System.out.println(configDir.getAbsolutePath());
    //sometimes this ends up being the "bin" directory of the J2EE container
    //we need to fix that
    if((configDir.getAbsolutePath().contains("/bin/")) || (configDir.getAbsolutePath().contains("\\bin\\"))){
      String fixedPath=configDir.getAbsolutePath().replaceAll("/bin", "").replaceAll("\\\\bin", "");
      configDir=new File(fixedPath);
      //System.out.println("Fixing the bin issue in Shepherd PMF. ");
      //System.out.println("The fix abs path is: "+configDir.getAbsolutePath());
    }
    //System.out.println(configDir.getAbsolutePath());
    if(!configDir.exists()){configDir.mkdirs();}
    File configFile = new File(configDir, "jdoconfig.properties");
    if (configFile.exists()) {
      //System.out.println("Overriding default properties with " + configFile.getAbsolutePath());
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

}
