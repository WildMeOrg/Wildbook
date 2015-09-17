package org.ecocean.servlet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadPoolExecutor;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServlet;


import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.grid.FlukeMatchComparator;
import org.ecocean.grid.GridManager;
import org.ecocean.grid.GridManagerFactory;
import org.ecocean.grid.MatchObject;
import org.ecocean.grid.ScanTask;
import org.ecocean.grid.ScanTaskCleanupThread;
import org.ecocean.grid.ScanWorkItem;
import org.ecocean.grid.ScanWorkItemResult;
import org.ecocean.grid.SharkGridThreadExecutorService;
import org.ecocean.neural.TrainNetwork;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;

import weka.classifiers.meta.AdaBoostM1;
import weka.core.Instance;
import weka.core.Instances;

public class TrainAdaboostM1 extends HttpServlet {
  
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    String context=ServletUtilities.getContext(request);
    
    
  //set up for response
    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    boolean createThisUser = false;
    
    String genusSpecies=request.getParameter("genusSpecies");
    
    String fullPathToInstancesFile=TrainNetwork.getAbsolutePathToInstances(genusSpecies, request);
    
   Instances instances = TrainNetwork.buildAdaboostInstances(request, fullPathToInstancesFile);
    TrainNetwork.serializeWekaInstances(request, instances, fullPathToInstancesFile);  
   String fullPathToClassifierFile=TrainNetwork.getAbsolutePathToClassifier(genusSpecies, request);
    AdaBoostM1 booster=TrainNetwork.buildAdaBoostClassifier(request,fullPathToClassifierFile,instances);
   TrainNetwork.serializeWekaClassifier(request, booster, fullPathToClassifierFile);
   
   out.println(ServletUtilities.getHeader(request));
   out.println("<strong>Failure:</strong> User was NOT successfully created. I did not have all of the username and password information I needed.");
   out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/users.jsp?context=context0" + "\">Return to User Administration" + "</a></p>\n");
   out.println(ServletUtilities.getFooter(context));

  }



 
}
