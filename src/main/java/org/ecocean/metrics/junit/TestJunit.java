package org.ecocean.metrics.junit; 

import org.junit.Test; 
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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
    //initialize global vars
    try 
    {
      this.myFile = new File("test.txt");
      if (this.myFile.createNewFile()) 
      {
        System.out.println("File created: " + this.myFile.getName());
      } 
      else 
      {
        System.out.println("File already exists.");
      }
    } 
    catch (IOException e) 
    {
      System.out.println("An error occurred.");
      e.printStackTrace();
    }
    this.myShepherd = new Shepherd("context");
    try 
    {
      this.pw = new PrintWriter(this.myFile);
    } 
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    this.promObject = new Prometheus(true);
    //run method
    this.promObject.setNumberOfUsers(pw, myShepherd);
    
    assertEquals(messsage, "hi");
  }
  
  
}