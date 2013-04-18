package org.ecocean.security.dao;

import java.io.IOException;
import java.sql.DriverManager;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.shiro.realm.jdbc.JdbcRealm;
import org.ecocean.*;

import org.ecocean.servlet.ServletUtilities;

//import org.springframework.jdbc.datasource.DriverManagerDataSource;

import org.apache.commons.dbcp.*;

/**
 * Sub-class of JdbcRealm that defines
 * the Data Source the JdbcRealm should
 * use
 * The configuration specified in web.xml
 * will cause an object of this class to
 * be injected into the SecurityManager
 * @author brucephillips
 *
 */
public class RoleSecurityJdbcRealm extends JdbcRealm {

	public RoleSecurityJdbcRealm() {

		super();

		//get the DataSource that JSecurity's JdbcRealm
		//should use to find the user's password
		//using the provided username
		//see context.xml for this DataSource's properties
        //InitialContext ic;
        //DataSource dataSource;
        //DriverManagerDataSource dataSource;
		try {

			//ic = new InitialContext();
			
			//ic.
			
			//dataSource = (DataSource) ic.lookup("java:/comp/env/jdbc/security");
			
			 Properties overrideProps=ShepherdPMF.loadOverrideProps(CommonConfiguration.getProperty("dataDirectoryName"));
       //System.out.println(overrideProps);
       
       if(overrideProps.size()==0){
         //otherwise load the embedded commonConfig
         
         try {
           overrideProps.load(ShepherdPMF.class.getResourceAsStream("/bundles/jdoconfig.properties"));
         } 
         catch (IOException ioe) {
           ioe.printStackTrace();
         }
       }
			BasicDataSource bds = new BasicDataSource();
			bds.setDriverClassName(overrideProps.getProperty("datanucleus.ConnectionDriverName").trim());
			System.out.println(overrideProps.getProperty("datanucleus.ConnectionDriverName").trim());
			bds.setPassword(overrideProps.getProperty("datanucleus.ConnectionPassword").trim());
			System.out.println(overrideProps.getProperty("datanucleus.ConnectionPassword").trim());
			bds.setUrl(overrideProps.getProperty("datanucleus.ConnectionURL").trim());
			System.out.println(overrideProps.getProperty("datanucleus.ConnectionURL").trim());
			bds.setUsername(overrideProps.getProperty("datanucleus.ConnectionUserName").trim());
			System.out.println(overrideProps.getProperty("datanucleus.ConnectionUserName").trim());
      
			bds.setMaxActive(5);
			bds.setMaxIdle(3);
			bds.setMaxWait(10000);
			bds.setLogAbandoned(true);
			bds.setRemoveAbandoned(true);

			
	
			
			
			//DriverManagerDataSource dataSource2 = new DriverManagerDataSource(CommonConfiguration.getProperty("datanucleus.ConnectionURL"), CommonConfiguration.getProperty("datanucleus.ConnectionUserName"),CommonConfiguration.getProperty("datanucleus.ConnectionPassword"));
			//dataSource2.setDriverClassName(CommonConfiguration.getProperty("datanucleus.ConnectionDriverName"));
			
			/*
			 * 
			 *     connectionProps.put("user", CommonConfiguration.getProperty("datanucleus.ConnectionUserName"));
    connectionProps.put("password", CommonConfiguration.getProperty("datanucleus.ConnectionPassword"));

    
    conn = DriverManager.getConnection(
           CommonConfiguration.getProperty("datanucleus.ConnectionURL"),
           connectionProps);
			 */
			
			this.setDataSource(bds);

		} catch (Exception e) {

			e.printStackTrace();

		}

	}

}
