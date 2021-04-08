package org.ecocean.metrics.junit; 

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.services.cloudsearchv2.model.IntArrayOptions;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

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
  
  //Testing vars
  int usersInWildbook = -1; 
  int wLogin = -1;
  int woLogin = -1; 

  @Before
  public void setUp() throws FileNotFoundException
  {
    //initialize our global variables
    this.myShepherd = new Shepherd("context0");
    this.promObject = new Prometheus(true);

    //Read input from file
    File myFile = new File("databaseDump.txt"); 
    Scanner sc = new Scanner(myFile);
    boolean reachedUserSection = false;
     
    while(sc.hasNextLine())
    {
      if(reachedUserSection)
      {
        try
        {
          this.usersInWildbook = Integer.parseInt(sc.nextLine());
          this.wLogin = Integer.parseInt(sc.nextLine());
          this.woLogin = Integer.parseInt(sc.nextLine());
        }
        catch(Exception e) {}
        reachedUserSection = false; 
      }
      else if(sc.hasNext("NumberOfUsers"))
      {
        reachedUserSection = true; 
      }
      sc.nextLine();
    }
    
  }
  
  @Test
  public void testPrintMessage()
  {
    assertEquals(messsage, "Hello worl"); 
  }
  
  @Test
  public void testSetNumberOfUsers() 
  {
    //run method
    this.promObject.setNumberOfUsers(this.pw, this.myShepherd);
    //int s = this.myShepherd.getNumUsers();
    assertEquals((int) this.promObject.numUsersInWildbook.get()- 1, this.usersInWildbook);
    assertEquals((int) this.promObject.numUsersWithLogin.get() - 1, this.wLogin);
    assertEquals((int) this.promObject.numUsersWithoutLogin.get() - 1, this.woLogin);
  
  }
  
  
}