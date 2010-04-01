package org.ecocean;

import javax.jdo.*;

import java.io.IOException;
import java.util.Properties;
import java.util.Enumeration;


public class ShepherdPMF {
	
private static PersistenceManagerFactory pmf;
	
	public synchronized static PersistenceManagerFactory getPMF(String dbLocation) {
	//public static PersistenceManagerFactory getPMF(String dbLocation) {
		try{
			if (pmf==null) {
				
				Properties dnProperties = new Properties();
				
				
				dnProperties.setProperty("datanucleus.PersistenceManagerFactoryClass","org.datanucleus.PersistenceManagerFactoryImpl");
				
				/*
				dnProperties.setProperty("datanucleus.ConnectionDriverName",CommonConfiguration.getConnectionDriverName());
				dnProperties.setProperty("datanucleus.ConnectionURL",CommonConfiguration.getConnectionURL());
				dnProperties.setProperty("datanucleus.ConnectionUserName",CommonConfiguration.getConnectionUserName());
				dnProperties.setProperty("datanucleus.ConnectionPassword",CommonConfiguration.getConnectionPassword());
				dnProperties.setProperty("datanucleus.NontransactionalRead","true");
				dnProperties.setProperty("datanucleus.Multithreaded","true");
				dnProperties.setProperty("datanucleus.RestoreValues","true");
				dnProperties.setProperty("datanucleus.storeManagerType","rdbms");
				*/
				
				//class setup
				Properties props=new Properties();
				try{
					props.load(ShepherdPMF.class.getResourceAsStream("/bundles/en/commonConfiguration.properties"));
				}
				catch(IOException ioe){ioe.printStackTrace();}
				
				Enumeration<Object> propsNames=props.keys();
				while(propsNames.hasMoreElements()){
					String name=(String)propsNames.nextElement();
					if(name.startsWith("datanucleus")){
						dnProperties.setProperty(name, props.getProperty(name).trim());
					}
				}

				pmf = JDOHelper.getPersistenceManagerFactory(dnProperties);

	
				}
			return pmf;
			}
		catch (JDOException jdo){
			jdo.printStackTrace();
			System.out.println("I couldn't instantiate a PMF.");
			return null;
			}
		}
	
}
