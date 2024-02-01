/*
 * Wildbook - A Mark-Recapture Framework
 * Copyright (C) 2014 Jason Holmberg
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


import java.util.Properties;
import java.util.ArrayList;
import java.util.List;

public class ContextConfiguration {
  
  private static final String CONTEXTS_PROPERTIES = "/bundles/contexts.properties";
  
  //class setup
  private static Properties props = new Properties();
  
  private static volatile int propsSize = 0;

public static Properties getContextsProperties(){
  initialize();
  return props;
}
  
  private static void initialize() {
    //set up the file input stream
    if (propsSize == 0) {
      loadProps();
    }
  }

  public static synchronized void refresh() {
      props.clear();
      propsSize = 0;
      loadProps();
  }
  
  private static void loadProps(){
    
    Properties localesProps = new Properties();
    
      try {
        localesProps.load(ContextConfiguration.class.getResourceAsStream(CONTEXTS_PROPERTIES));
        props=localesProps;
        propsSize=props.size();
        //System.out.println("     Context props are: "+props.toString());
        } 
      catch (Exception ioe) {
        System.out.println("Hit an error loading contexts.properties.");
        ioe.printStackTrace();
      }
    
  }
  
  public static String getDataDirForContext(String context){
    initialize();
    if(props.getProperty((context+"DataDir"))!=null){return props.getProperty((context+"DataDir"));}
    return null;
  }
  
  public static String getNameForContext(String context){
    initialize();
    if(props.getProperty((context+"Name"))!=null){return props.getProperty((context+"Name"));}
    return null;
  }
  
  public static String getDefaultContext(){
    initialize();
    if(props.getProperty("defaultContext")!=null){return props.getProperty("defaultContext");}
    return "context0";
  }
  
  public static List<String> getContextNames(){
    List<String> names=new ArrayList<String>();
    int contextNum=0;
    while(getNameForContext(("context"+contextNum))!=null){
      
      names.add(getNameForContext(("context"+contextNum)));
      contextNum++;
    }
    
    return names;
  }
  
  public static List<String> getContextDomainNames(String contextName){
    initialize();
    List<String> domainNames=new ArrayList<String>();
    int domainNum=0;
    while(props.getProperty(contextName+"DomainName"+domainNum)!=null){
      
      domainNames.add(props.getProperty(contextName+"DomainName"+domainNum));
      domainNum++;
    }
    return domainNames;
    
  }
  
  
  public static String getVersion() {
   if(props.getProperty("application.version")!=null){
		return props.getProperty("application.version");
   }
   return "Version Unknown";
  }
  
}

  
  

  
  