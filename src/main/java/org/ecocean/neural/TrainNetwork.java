/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.neural;

import org.ecocean.*;
import org.ecocean.servlet.ServletUtilities;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;

import org.neuroph.core.*;
import org.neuroph.nnet.*;
import org.neuroph.nnet.learning.*;
import org.neuroph.core.data.*;
import org.ecocean.grid.*;

import java.util.Vector;

import com.fastdtw.timeseries.TimeSeriesBase.*;
import com.fastdtw.dtw.*;
import com.fastdtw.util.Distances;
import com.fastdtw.timeseries.TimeSeriesBase.Builder;
import com.fastdtw.timeseries.*;

public class TrainNetwork extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String number = request.getParameter("number");

    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    
    double intersectionProportion=0.2;
    
    myShepherd.beginDBTransaction();
    
      try {
       
        
     // create new perceptron network
        NeuralNetwork neuralNetwork = new Perceptron(4, 1);
        // create training set
        DataSet trainingSet = new DataSet(4, 1);
        
        
        // add training data to training set (logical OR function)
        
        Vector encounters=myShepherd.getAllEncountersNoFilterAsVector();
        int numEncs=encounters.size();
        for(int i=0;i<(numEncs-1);i++){
          for(int j=(i+1);j<numEncs;j++){
            
            Encounter enc1=(Encounter)encounters.get(i);
            Encounter enc2=(Encounter)encounters.get(j);
            if(((enc1.getSpots()!=null)&&(enc1.getRightSpots()!=null))&&((enc1.getSpots()!=null)&&(enc1.getRightSpots()!=null))){
              
              System.out.println("Learning: "+enc1.getCatalogNumber()+" and "+enc2.getCatalogNumber());
              
              //if both have spots, then we need to compare them
           
              //first, are they the same animal?
              //default is 1==no
              double output=1;
              if((enc1.getIndividualID()!=null)&&(!enc1.getIndividualID().toLowerCase().equals("unassigned"))){
                if((enc2.getIndividualID()!=null)&&(!enc2.getIndividualID().toLowerCase().equals("unassigned"))){
                  //train a match
                  if(enc1.getIndividualID().equals(enc2.getIndividualID())){output=0;}
                }
                
              }
              
              
              EncounterLite el1=new EncounterLite(enc1);
              EncounterLite el2=new EncounterLite(enc2);
              
              //HolmbergIntersection
              Integer numIntersections=EncounterLite.getHolmbergIntersectionScore(el1, el2,intersectionProportion);
              double finalInter=-1;
              if(numIntersections!=null){finalInter=numIntersections.intValue();}
             
              
              //FastDTW
              TimeWarpInfo twi=EncounterLite.fastDTW(el1, el2, 30);
              
              java.lang.Double distance = new java.lang.Double(-1);
              if(twi!=null){
                WarpPath wp=twi.getPath();
                  String myPath=wp.toString();
                distance=new java.lang.Double(twi.getDistance());
              }   
              
              //I3S
              I3SMatchObject newDScore=EncounterLite.improvedI3SScan(el1, el2);
              double i3sScore=-1;
              if(newDScore!=null){i3sScore=newDScore.getI3SMatchValue();}
              
              //Proportion metric
              Double proportion=EncounterLite.getFlukeProportion(el1,el2);
              
              trainingSet. addRow (
                  new DataSetRow (new double[]{finalInter, distance, i3sScore, proportion},
                  new double[]{output}));
              
              
            }
            
          }
          
          
        }
       
        
        
       
        System.out.println("Trying to learn the data set...");
        PerceptronLearning kl=new PerceptronLearning();
        kl.setLearningRate(0.5);
        kl.setMaxError(5);
        
        // learn the training set
        neuralNetwork.learn(trainingSet,kl);
        
        // save the trained network into file
        neuralNetwork.save(shepherdDataDir.getAbsolutePath()+"/fluke_perceptron.nnet"); 
        
        


      } catch (Exception le) {
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }

      if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success!</strong> I have successfully trained the network.");

        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/adoptions/adoption.jsp\">Return to the Adoption Create/Edit page.</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
      } 
      else {

        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> I failed to train the network. Check the logs for more details.");

        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/adoptions/adoption.jsp\">Return to the Adoption Create/Edit page.</a></p>\n");
        out.println(ServletUtilities.getFooter(context));

      }


    out.close();
  }


}
	
	
