package org.ecocean.metrics.junit; 

import java.io.PrintWriter;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class TestRunner
{

  public static void main(PrintWriter out)
  {
    Result result = JUnitCore.runClasses(TestJunit.class);
    
    for(Failure failure : result.getFailures())
    {
      out.println(failure.toString()); 
    }
    
    out.println("Did all tests pass? " + result.wasSuccessful() + "\n\n");
  }
}
