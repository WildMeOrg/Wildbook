package org.ecocean.grid;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

import org.apache.commons.math3.analysis.MultivariateFunction;

// public class GrothAnalysis implements MultivariateDifferentiableVectorFunction {

public class GrothAnalysis implements MultivariateFunction {
  
  private static ArrayList<ScanWorkItem> matchedswis=new ArrayList<ScanWorkItem>();
  private static ArrayList<ScanWorkItem> nonmatchedswis=new ArrayList<ScanWorkItem>();
  
  private int numComparisons = 200;
  private int maxNumSpots = 30;
  private String defaultSide = "left";

  public static void flush() {
    matchedswis=new ArrayList<ScanWorkItem>();
    nonmatchedswis=new ArrayList<ScanWorkItem>();
  }
  
  public void setNumComparisonsEach(int numComparisons ) {
    this.numComparisons = numComparisons;
  }

  public void setMaxSpots(int maxNumSpots) {
    this.maxNumSpots = maxNumSpots;
  }
  
  public void setSide(String side) {
    this.defaultSide = side;
  }

  /*
  * 
  * 
  */

  public double value(double[] point) {
    //final double[] valArr = new double[0];
    double val = -1;
     //Parameter order: {epsilon, R, sizeLim, maxTriangleRotation, C}  
    System.out.println("Epsilon: "+point[0]+"  R: "+point[1]+"  sizeLim: "+point[2]+"  maxTriangleRotation: "+point[3]+"  C: "+point[4]);
    try {
      val = getScoreDiffMatchesMinusNonmatches(numComparisons, point[0], point[1], point[2], point[3], point[4], defaultSide, maxNumSpots );
      //valArr[0] = val;
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("----> Current matches-nonmatches score: "+val);
    return val;
  }

  // public DerivativeStructure[] value(DerivativeStructure[] point) throws MathIllegalArgumentException {
  //   //final double[] params = new double[0];
  //   //final DerivativeStructure[] vals = new DerivativeStructure[point.length];
    
  //   //DerivativeStructure vi = new DerivativeStructure(point.length, 1, f.value(observed.getX(), params));
  //   //   // params[i] = point[i].getValue();
  //   // }
  //   DerivativeStructure[] dsArr = new DerivativeStructure[point.length];
  //   for (int i=0;i<point.length;i++) {
  //   //for (DerivativeStructure ds : point) {

  //     //wat
  //     dsArr[i] = point[i].multiply(point[i]);
  //   }
  //   return dsArr;
  // }

  public static Double getScoreDiffMatchesMinusNonmatches(int numComparisonsEach, double epsilon, double R, double Sizelim, double maxTriangleRotation, double C, String side, int maxNumSpots) throws Exception {
    
    Double totalMatchScores=0.0;
    Double totalNonmatchScores=0.0;
   
    
    java.util.Properties props2 = new java.util.Properties();
    String secondRun = "true";
    String rightScan = "false";
    if (side.equals("right")) {
      rightScan = "true";
    }
    props2.setProperty("rightScan", rightScan);
 
 
    //Modified Groth algorithm parameters
    //pulled from the gridManager
    props2.setProperty("epsilon", Double.toString(epsilon));
    props2.setProperty("R", Double.toString(R));
    props2.setProperty("Sizelim", Double.toString(Sizelim));
    props2.setProperty("maxTriangleRotation", Double.toString(maxTriangleRotation));
    props2.setProperty("C", Double.toString(C));
    props2.setProperty("secondRun", secondRun);
    
    
    if(matchedswis.size()==0 || nonmatchedswis.size()==0 ) {
      //get GridManager, for speed, we're only going to use data in the GM
      GridManager gm = GridManagerFactory.getGridManager();
      
      ConcurrentHashMap<String,EncounterLite> chm=gm.getMatchGraph();
      Enumeration<String> keys=chm.keys();
      
      HashMap<String,ArrayList<EncounterLite>> indyMap=new HashMap<String,ArrayList<EncounterLite>>();
      
      
      while (keys.hasMoreElements()) {
  
        String kv=keys.nextElement();
        EncounterLite el=chm.get(kv);
        
        String individualID=el.getBelongsToMarkedIndividual();
        if((individualID!=null) && (!individualID.equals(""))) {
          if(indyMap.get(individualID)!=null) {
            ArrayList<EncounterLite> thisAL=indyMap.get(individualID);
            thisAL.add(el);
            indyMap.put(individualID,thisAL);
          }
          else {
            ArrayList<EncounterLite> thisAL=new ArrayList<EncounterLite>();
            thisAL.add(el);
            indyMap.put(individualID,thisAL);
          }
          
        }
        
      }  //end while
      
      
      //filter out EncounterLite objects without the correct side
      Iterator<String> keys2=indyMap.keySet().iterator();
      while(keys2.hasNext()) {
        String myKey=keys2.next();
        ArrayList<EncounterLite> thisAL=indyMap.get(myKey);
       
        for(int i=0;i<thisAL.size();i++) {
          EncounterLite el=thisAL.get(i);
        
          if(side.equals("left")) {
            if(el.getSpots()==null || el.getSpots().size()==0 || el.getSpots().size()>maxNumSpots) {
              thisAL.remove(el);
              i--;
            }
          }else if(side.equals("right")) {
            if(el.getRightSpots()==null || el.getRightSpots().size()==0 || el.getRightSpots().size()>maxNumSpots) {
              thisAL.remove(el);
              i--;
            }
          }
        }
      } //end while
  
      
      //filter out any individuals with only one ENcounterLite 
      Iterator<String> keys3=indyMap.keySet().iterator();
      ArrayList<String> keys2remove=new ArrayList<String>();
      while(keys3.hasNext()) {
        String myKey=keys3.next();
        ArrayList<EncounterLite> thisAL=indyMap.get(myKey);
        if(thisAL.size()<=1) {keys2remove.add(myKey);}
      }
      
      //remove the unneeded keys for only single EncounterLite individuals
      for(int f=0;f<keys2remove.size();f++) {
        indyMap.remove(keys2remove.get(f));
      }
      
      
     System.out.println("I now have this many MarkedIndividuals ("+indyMap.keySet().size()+") with >=2 patterns from side: "+side);
     

     
     ArrayList<EncounterLite> allEncs=new ArrayList<EncounterLite>();
     
     //OK, indyMap now has matched individuals from the chosen side, let's create our comparison set
     
     keys2=indyMap.keySet().iterator();
     while(keys2.hasNext()) {
       String myKey=keys2.next();
       ArrayList<EncounterLite> thisAL=indyMap.get(myKey);
       
       int numThisAL=thisAL.size();
       for(int z=0;z<(numThisAL-1);z++) {
         for(int z2=z+1;z2<numThisAL;z2++) {
           
           EncounterLite el1=thisAL.get(z);
           EncounterLite el2=thisAL.get(z2);
           
           //populate the all list while at it
           if(!allEncs.contains(el1)) {allEncs.add(el1);}
           if(!allEncs.contains(el2)) {allEncs.add(el2);}
           
           
           ScanWorkItem swi = new ScanWorkItem(el1, el2, (el1.getEncounterNumber()+"-"+el2.getEncounterNumber()), "GrothAnalysis", props2);
           matchedswis.add(swi);
           
         }
       }
     }
     
     //OK, indyMap now has non-matched individuals from the chosen side, let's create our comparison set
  
     for(int q=0;q<allEncs.size()-1;q++) {
       for(int r=0;r<allEncs.size();r++) {
         EncounterLite el1=allEncs.get(q);
         EncounterLite el2=allEncs.get(r);
         if(!el1.getEncounterNumber().equals(el2.getEncounterNumber())) {
           ScanWorkItem swi = new ScanWorkItem(el1, el2, (el1.getEncounterNumber()+"-"+el2.getEncounterNumber()), "GrothAnalysis", props2);
           nonmatchedswis.add(swi);
         }
         
       }
     }
    }
   
   System.out.println("All right, I should have my full candidate set of matched="+matchedswis.size()+" and nonmatched="+nonmatchedswis.size()+" from side="+side);
   
   if(numComparisonsEach>matchedswis.size()) {throw new Exception("numComparisonsEach is greater than available matches");}
   if(numComparisonsEach>nonmatchedswis.size()) {throw new Exception("numComparisonsEach is greater than available nonmatches");}
   
   
   //let's run the matches!
   for(int i=0;i<numComparisonsEach;i++) {
     ScanWorkItem swi=matchedswis.get(i);
     swi.setProperties(props2);
     swi.run();
     MatchObject mo=swi.getResult();
     double score=mo.getMatchValue() * mo.getAdjustedMatchValue();
     totalMatchScores+=score;
   }
   
   //let's run the nonmatches!
   for(int i=0;i<numComparisonsEach;i++) {
     ScanWorkItem swi=nonmatchedswis.get(i);
     swi.setProperties(props2);
     swi.run();
     MatchObject mo=swi.getResult();
     double score=mo.getMatchValue() * mo.getAdjustedMatchValue();
     totalNonmatchScores+=score;
   }
   

   return totalMatchScores-totalNonmatchScores;
    
    
  }
  

}
