package org.ecocean.metrics.junit; 

import org.junit.Before;
import org.junit.Test; 
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;

import org.ecocean.Shepherd;
import org.ecocean.metrics.Prometheus; 

public class TestJunit 
{
  String messsage = "Hello worl";
  Shepherd myShepherd;
  File myFile;
  PrintWriter pw; 
  Prometheus promObject; 
  

  @Before
  public void setUp()
  {
    //initialize our global variables
//    this.myShepherd = new Shepherd("context0");
//    this.promObject = new Prometheus(true);
    
  }
  
  @Test
  public void testPrintMessage()
  {
    assertEquals(messsage, "Hello worl"); 
  }
  
  @Test
  public void testSetNumberOfUsers()
  {
    //initialize global vars
    this.myShepherd = new Shepherd("context0");
    this.promObject = new Prometheus(true);
    //run method
    this.promObject.setNumberOfUsers(this.pw, this.myShepherd);
    int s = this.myShepherd.getNumUsers();
    assertEquals((int) this.promObject.numUsersInWildbook.get(), s - 6);
//    
  }
  
  
}