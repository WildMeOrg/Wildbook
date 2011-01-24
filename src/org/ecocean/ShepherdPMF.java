package org.ecocean;

import javax.jdo.JDOException;
import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManagerFactory;
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


        dnProperties.setProperty("datanucleus.PersistenceManagerFactoryClass", "org.datanucleus.PersistenceManagerFactoryImpl");

        //class setup
        Properties props = new Properties();
        try {
          props.load(ShepherdPMF.class.getResourceAsStream("/bundles/en/commonConfiguration.properties"));
        } catch (IOException ioe) {
          ioe.printStackTrace();
        }

        Enumeration<Object> propsNames = props.keys();
        while (propsNames.hasMoreElements()) {
          String name = (String) propsNames.nextElement();
          if (name.startsWith("datanucleus")) {
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

}
