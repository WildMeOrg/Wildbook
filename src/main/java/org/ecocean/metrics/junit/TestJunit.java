package org.ecocean.metrics.junit; 

import org.junit.Test; 
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.PrintWriter;

import org.ecocean.Shepherd;
import org.ecocean.metrics.Prometheus; 

public class TestJunit 
{
  String messsage = "Hello worl";
  Shepherd myShepherd;
  File myFile;
  PrintWriter pw; 
  Prometheus promObject; 

  @Test
  public void testPrintMessage()
  {
    assertEquals(messsage, "Hello worl"); 
  }
  
  @Test
  public void testSetNumberOfUsers()
  {
    assertEquals(messsage, "hi");
  }
  
}