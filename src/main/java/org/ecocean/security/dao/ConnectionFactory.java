package org.ecocean.security.dao;

import java.sql.*;

import org.ecocean.CommonConfiguration;
import org.ecocean.ShepherdPMF;
import java.util.Properties;

/**
 *
 * <p>Title: ConnectionFactory.java</p>
 *
 * <p>Description: A class that other classes may use
 * to get a connection to the data storage.  This static
 * class manages the connection and associated resources.
 * We can use this calls to test connecting to the data store.</p>
 *
 * <p>Reference:  Pages 281-288 of Beginning Java Databases</p>
 *
 *
 * <p>Date: September 2005</p>
 *
 * @author Bruce Phillips
 *
 * @version 1.5
 */
public class ConnectionFactory {

 //Call the private constructor to initialize the DriverManager
  @SuppressWarnings("unused")
private static ConnectionFactory ref = new ConnectionFactory();

 private static Properties dbProperties = new Properties();
  
  
  /**
   * Private default constructor
   * No outside objects can create
   * an object of this class
   * This constructor initializes the DriverManager
   * by loading the driver for the database
   */
  private ConnectionFactory() {

    String driverName = "org.apache.derby.jdbc.EmbeddedDriver";

    String shepherdDataDir="shepherd_data_dir";
    if((CommonConfiguration.getProperty("dataDirectoryName")!=null)&&(!CommonConfiguration.getProperty("dataDirectoryName").trim().equals(""))){shepherdDataDir=CommonConfiguration.getProperty("dataDirectoryName");}
    dbProperties=ShepherdPMF.loadOverrideProps(shepherdDataDir);

    
    System.out.println("in connection factory constructor");

    try {

    	Class.forName(driverName);

    } catch (ClassNotFoundException e) {

      System.out.println("ERROR: exception loading driver class");
      System.out.println( e.getMessage() ) ;

    }//end try-catch

  } //end default private constructor


  /**
   * Get and return a Connection object
   * that can be used to connect to the
   * data storage
   * @return Connection
   * @throws SQLException
   */
  public static Connection getConnection() throws SQLException {

	  System.out.println("in get connection");

	  //String dbName = "securityDB";
    //Connect to the data storage
    //In this case it is a Derby database
    //String sourceURL = "jdbc:derby:/derby/" + dbName + ";create=false";
    
    String sourceURL = dbProperties.getProperty("datanucleus.ConnectionURL");
    System.out.println("ConnectionFactory: sourceURL = "+sourceURL);
    //String userName = "users";
    
    String userName=dbProperties.getProperty("datanucleus.ConnectionUserName");
    System.out.println("ConnectionFactory: userName = "+userName);
    //String password = "password";
    
    String password=dbProperties.getProperty("datanucleus.ConnectionPassword");
    System.out.println("ConnectionFactory: password = "+password);
    



    return DriverManager.getConnection(sourceURL, userName, password);

  } //end method getConnection


  /**
   * Close the ResultSet
   * @param rs ResultSet
   */
  public static void close(ResultSet rs) {

    try {

      rs.close();

    } catch (SQLException e) {

      System.out.println("ERROR: Unable to close Result Set");
      System.out.println(e.getMessage() ) ;

    } //end try-catch block

  } //end method close

  /**
   * Close statement object
   * @param stmt Statement
   */
  public static void close(Statement stmt) {

    try {

      stmt.close();

    } catch (SQLException e) {

      System.out.println("ERROR: Unable to close Statement");
      System.out.println(e.getMessage() ) ;

    } //end try-catch block

  } //end method close


  /**
   * Close connection
   * @param conn Connection
   */
  public static void close(Connection conn) {

    try {

      conn.close();

    } catch (SQLException e) {

      System.out.println("ERROR: Unable to close Statement");
      System.out.println(e.getMessage() ) ;

    } //end try-catch block

  } //end method close

} //end class ConnectionFactory
