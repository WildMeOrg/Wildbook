package org.ecocean.grid.optimization;

import org.ecocean.grid.*;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.*;

import org.apache.commons.math3.analysis.MultivariateFunction;

// public class GrothAnalysis implements MultivariateDifferentiableVectorFunction {

public class GrothAnalysis implements MultivariateFunction {
  
  private static ArrayList<ScanWorkItem> matchedComparisons=new ArrayList<ScanWorkItem>();
  private static ArrayList<ScanWorkItem> unmatchedComparisons=new ArrayList<ScanWorkItem>();
  
  private static ArrayList<MatchObject> matches=new ArrayList<MatchObject>();
  private static ArrayList<MatchObject> nonmatches=new ArrayList<MatchObject>();
  
  private static ArrayList<Double> matchScores=new ArrayList<Double>();
  private static ArrayList<Double> nonmatchScores=new ArrayList<Double>();
  
  private int numComparisons = 200;
  private int maxNumSpots = 30;
  private String defaultSide = "left";
  
  private int targetScore = 100;
  private double weightAmount = 0.1;
  private boolean useWeights = false;
  private int matchedRankEvalsEach = 10;


  private double[] parameterScaling = new double[] {1.0, 1.0, 1.0, 1.0, 1.0};
  private boolean scalingSet = false; 

  // We will just init these once during our first comparison
  private static GridManager gm = null;
  private static ConcurrentHashMap<String,EncounterLite> chm = null;

  private boolean useMatchedRanking = false;

  public ArrayList<Double> getMatchScores() {
    return matchScores;
  }

  public ArrayList<Double> getNonMatchScores() {
    return nonmatchScores;
  }

  public static void flush() {
    matchedComparisons=new ArrayList<ScanWorkItem>();
    unmatchedComparisons=new ArrayList<ScanWorkItem>();
    matchScores=new ArrayList<Double>();
    nonmatchScores=new ArrayList<Double>();
    matches=new ArrayList<MatchObject>();
    nonmatches=new ArrayList<MatchObject>();
  }
  
  public void useMatchedRanking(boolean bool) {
    this.useMatchedRanking = bool;
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

  protected void setParameterScales(double[] scales) {
    this.parameterScaling = scales;
  }

  public boolean getScaling() {
    return scalingSet;
  }

  public void setScaling(boolean set) {
    this.scalingSet = set; 
  }

  public void setMatchedRankEvalsEach(int evals) {
    this.matchedRankEvalsEach = evals;
  }

  /*
  * 
  * 
  */

  public double value(double[] point) {
    //final double[] valArr = new double[0];
    double val = -1;
     //Parameter order: {epsilon, R, sizeLim, maxTriangleRotation, C}  
     // okay, we need to undo parameter scaling for actually feeding params into the grid

    double[] scaledPoint = new double[5];
    if (scalingSet==true) {
      for (int i=0;i<5;i++) {
        scaledPoint[i] = point[i]*parameterScaling[i];
      }
    }
    System.out.println("SCALED INPUT ==> Epsilon: "+point[0]+"  R: "+point[1]+"  sizeLim: "+point[2]+"  maxTriangleRotation: "+point[3]+"  C: "+point[4]);
    System.out.println("DE-SCALED INPUT FRO GRID ==> Epsilon: "+scaledPoint[0]+"  R: "+scaledPoint[1]+"  sizeLim: "+scaledPoint[2]+"  maxTriangleRotation: "+scaledPoint[3]+"  C: "+scaledPoint[4]);

    try {
      if (!useMatchedRanking) {
        val = getScoreDiffMatchesMinusNonmatches(numComparisons, scaledPoint[0], scaledPoint[1], scaledPoint[2], scaledPoint[3], scaledPoint[4], defaultSide, maxNumSpots, useWeights, targetScore, weightAmount );
      } else {
        // num match comparison is how many indys we compare, numEach is the number of indys we compare against, affecting total potential score
        // numComparisons * numComparisonsEach = num evals
        Integer rawVal = getMatchedRankSum(numComparisons, matchedRankEvalsEach, point[0], point[1], point[2], point[3],
                                            point[4], defaultSide, maxNumSpots, useWeights, targetScore, weightAmount, 2);

        val = new Double(rawVal);
      }

      //valArr[0] = val;
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("----> Current matches-nonmatches score: "+val);
    return val;
  }


  //public static Integer getMatchedRankSum(int numMatchedComparisons, int numComparisonsEach, double epsilon, double R, double Sizelim, double maxTriangleRotation,
  //double C, String side, int maxNumSpots, boolean useWeights, int targetScore, double weightAmount, Integer numProcessorsToUse) throws Exception {


  public double valueForCSV(double[] point, int numComparisons) {
    System.out.println("VALUE: SCALED INPUT ==> Epsilon: "+point[0]+"  R: "+point[1]+"  sizeLim: "+point[2]+"  maxTriangleRotation: "+point[3]+"  C: "+point[4]);
    double val = -1;
    try {
      val = getScoreDiffMatchesMinusNonmatches(numComparisons, point[0], point[1], point[2], point[3], point[4], defaultSide, maxNumSpots, false, targetScore, weightAmount );
      //valArr[0] = val;
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("----> Current matches-nonmatches score: "+val);
    return val;
  }

  public static Double getScoreDiffMatchesMinusNonmatches(int numComparisonsEach, double epsilon, double R, double Sizelim, double maxTriangleRotation,
                                                          double C, String side, int maxNumSpots, boolean useWeights, int targetScore, double weightAmount) throws Exception {
    
    Double totalMatchScores=0.0;
    Double totalNonmatchScores=0.0;
    
    //clear our match object results lists
    matches=new ArrayList<MatchObject>();
    nonmatches=new ArrayList<MatchObject>();
    
    //clear our resulting scores for new values
    matchScores=new ArrayList<Double>();
    nonmatchScores=new ArrayList<Double>();
    
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
    
    
    if(matchedComparisons.size()==0 || unmatchedComparisons.size()==0 ) {

      //get GridManager, for speed, we're only going to use data in the GM
      initGridManagerHashMap();

      System.out.println("Finished init grid hashmap...");

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

     try {
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
             matchedComparisons.add(swi);
             if (matchedComparisons.size()>=numComparisonsEach) break;
           }
           if (matchedComparisons.size()>=numComparisonsEach) {
            break;
           }
         }
       }
       System.out.println("........................... finished matched map... All encs = "+allEncs.size());
       
       //OK, indyMap now has non-matched individuals from the chosen side, let's create our comparison set
      

       for(int q=0;q<(allEncs.size()-1);q++) {
         System.out.println("....... q loop "+q);
         for(int r=0;r<allEncs.size();r++) {
           EncounterLite el1=allEncs.get(q);
           EncounterLite el2=allEncs.get(r);
           if(!el1.getEncounterNumber().equals(el2.getEncounterNumber())) {
             ScanWorkItem swi = new ScanWorkItem(el1, el2, (el1.getEncounterNumber()+"-"+el2.getEncounterNumber()), "GrothAnalysis", props2);
             unmatchedComparisons.add(swi);
             if (unmatchedComparisons.size()>=numComparisonsEach) break;
           }
         }
         if (matchedComparisons.size()>=numComparisonsEach) {
          break;
         }
        }
     } catch (Exception e) {
       e.printStackTrace();
     }

    }
   
   System.out.println("All right, I should have my full candidate set of matched="+matchedComparisons.size()+" and nonmatched="+unmatchedComparisons.size()+" from side="+side);
   
   if(numComparisonsEach>matchedComparisons.size()) {throw new Exception("numComparisonsEach is greater than available matches");}
   if(numComparisonsEach>unmatchedComparisons.size()) {throw new Exception("numComparisonsEach is greater than available nonmatches");}
   
   
   //let's run the matches!
   for(int i=0;i<numComparisonsEach;i++) {
     ScanWorkItem swi=matchedComparisons.get(i);
     swi.setProperties(props2);
     swi.run();
     MatchObject mo=swi.getResult();
     matches.add(mo);
     double score=mo.getMatchValue() * mo.getAdjustedMatchValue();
     
     if (useWeights&&score<targetScore) {
       score = score*(1.0-weightAmount);
      }

     matchScores.add(new Double(score));
     totalMatchScores+=score;
   }
   
   //let's run the nonmatches!
   for(int i=0;i<numComparisonsEach;i++) {
     ScanWorkItem swi=unmatchedComparisons.get(i);
     swi.setProperties(props2);
     swi.run();
     MatchObject mo=swi.getResult();
     nonmatches.add(mo);
     double score=mo.getMatchValue() * mo.getAdjustedMatchValue();

     if (useWeights&&score>targetScore) {
      score = score*(1.0-weightAmount);
     }

     nonmatchScores.add(new Double(score));
     totalNonmatchScores+=score;
   }
   
   System.out.println("======> matchScores-matchScores: "+(totalMatchScores-totalNonmatchScores));
   System.out.println("======> matchScores/matchScores: "+(totalMatchScores/totalNonmatchScores));

   return totalMatchScores-totalNonmatchScores;
    
    
  }

  public static void initGridManagerHashMap() {
    System.out.println("trying to init grid hashmap...");
    if (gm==null||chm==null) {
      gm = GridManagerFactory.getGridManager();
      chm=gm.getMatchGraph();
    }
  }
  
  //When used, will penalize all non-match scores over the target score, and inflate match scores over it.
  public void useWeightsForTargetScore(boolean use, int target, double weight) {
    this.targetScore = target;
    this.weightAmount = weight;
    this.useWeights = use;
  }
  
  /*
   * @numMatchedComparisons The number of matched pairs you want to evaluate in this run.
   * @numComparisonsEach The number of total comparisons you want numMatchedComparisons evaluated against.
   * 
   * @numProcessorsToUse The number of CPUs to use in parallel to accelerate this run. If left null, all processors available in the system will be used
   */
  public static Integer getMatchedRankSum(int numMatchedComparisons, int numComparisonsEach, double epsilon, double R, double Sizelim, double maxTriangleRotation,
      double C, String side, int maxNumSpots, boolean useWeights, int targetScore, double weightAmount, Integer numProcessorsToUse) throws Exception {

      
    //We'll return this value at the end of the method
    //It's value should be in the range of numMatchedComparison (perfect rank 1 comparison for each match of numMatchedComparisons
    //to perfect algorithm failure of numMatchedComparisons*numComparisonsEach (all true matches were ranked last)
    int totalMatchRank=0;
    
    //this is a sanity check that should sum to numMatchedComparisons*numComparisonsEach at the end
    int totalNumComparisonsRun=0;
      
      //check the number of processors and set up parallel execution queue
      Runtime rt = Runtime.getRuntime();
      int numProcessors = rt.availableProcessors();
      if(numProcessorsToUse!=null){
        numProcessors=numProcessorsToUse.intValue();
      }                //set up our thread processor for each comparison thread
      ArrayBlockingQueue abq = new ArrayBlockingQueue(1000);

      
      
      //clear our match object results lists
      //matches=new ArrayList<MatchObject>();
      //nonmatches=new ArrayList<MatchObject>();
      
      //clear our resulting scores for new values
      //matchScores=new ArrayList<Double>();
      //nonmatchScores=new ArrayList<Double>();
      
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
      
      
      //this will be an ArrayList of many EncounterLites that we can use to build false positive sets of size numComparisonsEach
      ArrayList<EncounterLite> allEncounterLites=new ArrayList<EncounterLite>();
      
      if(matchedComparisons.size()==0 ) {
        
        //inside here, we're building an ArrayList of ScanWorkItems that represents true matches of known MarkedIndividuals
        
      
        //get GridManager, for speed, we're only going to use data in the GM
        initGridManagerHashMap();
        
        System.out.println("Finished init grid hashmap...");
        
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
            }
            else if(side.equals("right")) {
              if(el.getRightSpots()==null || el.getRightSpots().size()==0 || el.getRightSpots().size()>maxNumSpots) {
                thisAL.remove(el);
                i--;
              }
            }
          }
        } //end while
        
        
        //filter out any individuals with only one EncounterLite 
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
        
        
        
        
        
        //OK, indyMap now has matched individuals from the chosen side, let's create our comparison set
        //of matchedComparisons until it is of size numMatchedComparisons
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
              if(!allEncounterLites.contains(el1)) {allEncounterLites.add(el1);}
              if(!allEncounterLites.contains(el2)) {allEncounterLites.add(el2);}
              
              ScanWorkItem swi = new ScanWorkItem(el1, el2, (el1.getEncounterNumber()+"-"+el2.getEncounterNumber()), "GrothAnalysis", props2);
              
              if (matchedComparisons.size()<numMatchedComparisons) {matchedComparisons.add(swi);}
            }
  
          }
        }
        System.out.println("........................... finished building my matchedComparisons list... = "+matchedComparisons.size()+", which should equal: "+numMatchedComparisons);
        
        System.out.println("........................... finished matched map... All encs = "+allEncounterLites.size());
        
      }
      
      //now let's iterate through our known matches that we want to sum the ranks for
      //for each one we are going to:
      //     1. run the ScanWorkItem of the matched result itself
      //     2. build a set of mismatches to compare and rank it against, using one of the same EncounterLites of the matched ScanWorkItem.
      for(ScanWorkItem swi:matchedComparisons) {
        
        ArrayList<ScanWorkItem> falsePositivesToCompareAgainst=new ArrayList<ScanWorkItem>();
        
        //first, run the match to ensure it gets its MatchObject internally embedded
        //this MatchObject will have the score of the matched, which is our key to determining the rank of the match
        swi.run();
        
        //here's our reference EncounterLite from the matched pair to match against known non-matches
        //essentially we're prentendin that we're trying to find this pattern in every comparison
        //and then get the rank when it's compared against the true match, which was already scored above
        EncounterLite el1=swi.getNewEncounterLite();

                
        for(EncounterLite el2:allEncounterLites) {
            if(falsePositivesToCompareAgainst.size()<(numComparisonsEach-1) && !el2.getBelongsToMarkedIndividual().equals(el1.getBelongsToMarkedIndividual())) {
              ScanWorkItem swi2 = new ScanWorkItem(el1, el2, (el1.getEncounterNumber()+"-"+el2.getEncounterNumber()), "GrothAnalysis", props2);
              falsePositivesToCompareAgainst.add(swi2);
            }
        }
        
        
        //OK, now let's match all of these!
        
        //thread pool handling comparison threads
        //spawn the thread for each comparison
        ThreadPoolExecutor threadHandler = new ThreadPoolExecutor(numProcessors, numProcessors, 0, TimeUnit.SECONDS, abq);
        
        
        ArrayList<Double> results=new ArrayList<Double>();
        int vectorSize = falsePositivesToCompareAgainst.size();
        System.out.println("Sanity check: "+falsePositivesToCompareAgainst.size()+" should be = "+(numComparisonsEach-1));
        Vector<ScanWorkItemResult> workItemResults = new Vector<ScanWorkItemResult>();
        for(ScanWorkItem matchme:falsePositivesToCompareAgainst) {
          

          matchme.setProperties(props2);
          

          try{
            threadHandler.submit(new AppletWorkItemThread(matchme, workItemResults));
          }
          catch(Exception e){
            System.out.println("...a thread threw an error, so I'm gonna skip it...");
          }
          
          
        }
        
        //block until all threads are done
        long vSize = vectorSize;
        //hang out until all threads are done
        while (threadHandler.getCompletedTaskCount() < vSize) {}
        
        
        threadHandler.shutdown();
        
        
        //let's build a list of all the scores from the matches
        for(ScanWorkItemResult matchme:workItemResults) {
          MatchObject mo=matchme.getResult();
          Double score=mo.getMatchValue() * mo.getAdjustedMatchValue();
          
          if (useWeights&&score<targetScore) {
            score = score*(1.0-weightAmount);
          }
          //System.out.println("Putting a score of "+score+" for "+matchme.+"("+matchme.getNewEncounterLite().getBelongsToMarkedIndividual()+") against "+matchme.getExistingEncNumber()+"("+matchme.getExistingEncounterLite().getBelongsToMarkedIndividual());
          results.add(score);
           
        }
        
        //add the true positive's score
        Double swiscore=swi.getResult().getMatchValue() * swi.getResult().getAdjustedMatchValue();
        results.add(swiscore);
        
        totalNumComparisonsRun+=results.size();
        
        //sort the results
        Collections.sort(results,Collections.reverseOrder());
        
        
        System.out.println("======>======>true match score of "+swiscore+" should be reflected in results: "+results.toString());
        
        totalMatchRank+=results.indexOf(swiscore)+1;
        System.out.println("======>======>Adding rank of: "+(results.indexOf(swiscore)+1));
  
        

        
      }
      
      
      
      

      
      //System.out.println("======> matchScores-matchScores: "+(totalMatchScores-totalNonmatchScores));
      //System.out.println("======> matchScores/matchScores: "+(totalMatchScores/totalNonmatchScores));
      
      System.out.println("");
      System.out.println("");
      System.out.println("======> RUN COMPLETE!");    
      System.out.println("======>Sanity check of intended num comparisons (numbers should be equals): "+totalNumComparisonsRun+" = "+(numComparisonsEach*numMatchedComparisons));
      System.out.println("=>Returning total match rank for true positives = "+numMatchedComparisons+" in a field of "+numComparisonsEach+" total possible matches: "+totalMatchRank+ "(best="+numMatchedComparisons+",worst="+(numComparisonsEach*numMatchedComparisons)+")");
      
      System.out.println("");
      System.out.println("");
      return totalMatchRank;
      }
  




      

  
  

}
