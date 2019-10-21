package org.ecocean.grid.optimization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.ecocean.grid.AppletWorkItemThread;
import org.ecocean.grid.EncounterLite;
import org.ecocean.grid.GridManager;
import org.ecocean.grid.GridManagerFactory;
import org.ecocean.grid.MatchObject;
import org.ecocean.grid.ScanWorkItem;
import org.ecocean.grid.ScanWorkItemResult;

//public class GrothAnalysis implements MultivariateDifferentiableVectorFunction {

public class GrothAnalysis implements MultivariateFunction {
  
  private static ArrayList<ScanWorkItem> matchedComparisons = new ArrayList<ScanWorkItem>();
  private static ArrayList<ScanWorkItem> unmatchedComparisons = new ArrayList<ScanWorkItem>();
  
  private static ArrayList<MatchObject> matches = new ArrayList<MatchObject>();
  private static ArrayList<MatchObject> nonmatches = new ArrayList<MatchObject>();
  
  private static ArrayList<Double> matchScores = new ArrayList<Double>();
  private static ArrayList<Double> nonmatchScores = new ArrayList<Double>();

  private static ArrayList<EncounterLite> allEncounterLites = new ArrayList<EncounterLite>();
  
  private int numComparisons = 200;
  private int maxNumSpots = 30;
  private String defaultSide = "left";
  
  private int targetScore = 100;
  private double weightAmount = 0.1;
  private boolean useWeights = false;

  private int matchedRankEvalsEach = 10;

  private static boolean normalizeTopN = false; // use top ranking (top 1, top 5 ect) percentage for optimization return 
  private static int numToNormalize = 5; // how many top ranking to look at
  private static Double normalizedScores = 0.0;

  private double[] parameterScaling = new double[] {1.0, 1.0, 1.0, 1.0, 1.0};
  private boolean scalingSet = false; 

  // We will just init these once during our first comparison
  private static GridManager gm = null;
  private static ConcurrentHashMap<String,EncounterLite> chm = null;

  private boolean useMatchedRanking = false;

  private int callsMade = 0; // chk just in case you oop the settings 

  private List<Double> matchRankScores = new ArrayList<>();

  public String getMatchRankScoresAsString() {
    return Arrays.toString(this.matchRankScores.toArray());
  }

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

  public static void normalizeTopN(boolean bool) {
    normalizeTopN = bool;
  }

  public static void numToNormalize(int num) {
    numToNormalize = num;
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

    callsMade++;
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
    
    System.out.println("----> Current matches-nonmatches score: "+val);
    try {
      if (!useMatchedRanking) {
        val = getScoreDiffMatchesMinusNonmatches(numComparisons, scaledPoint[0], scaledPoint[1], scaledPoint[2], scaledPoint[3], scaledPoint[4], defaultSide, maxNumSpots, useWeights, targetScore, weightAmount );
      } else {
        // num match comparison is how many indys we compare, numEach is the number of indys we compare against, affecting total potential score
        // numComparisons * numComparisonsEach = num evals
        //Integer rawVal = getMatchedRankSum(numComparisons, matchedRankEvalsEach, point[0], point[1], point[2], point[3],
        //                                    point[4], defaultSide, maxNumSpots, useWeights, targetScore, weightAmount, null);

        Double rawVal = GrothAnalysis.getMatchedRankSum(numComparisons, matchedRankEvalsEach, scaledPoint[0], scaledPoint[1], scaledPoint[2], scaledPoint[3], scaledPoint[4], "left", maxNumSpots, false,1,0.0,null);

        //val = new Double(rawVal);

        System.out.println("======================================= Val DOUBLE for function call: "+val); 
        matchRankScores.add(val);
      }

      //valArr[0] = val;
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("----> Calls made: "+callsMade+"  of  "+numComparisons+" comparisons * "+matchedRankEvalsEach+" evals...");


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
      

       for(int q=0;q<(allEncs.size()-1);q++) {System.out.println("");
         System.out.println("....... q loop "+q);System.out.println("");
         for(int r=0;r<allEncs.size();r++) {System.out.println("");
           EncounterLite el1=allEncs.get(q);System.out.println("");
           EncounterLite el2=allEncs.get(r);System.out.println("");
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
  public static Double getMatchedRankSum(int numMatchedComparisons, int numComparisonsEach, double epsilon, double R, double Sizelim, double maxTriangleRotation,
      double C, String side, int maxNumSpots, boolean useWeights, int targetScore, double weightAmount, Integer numProcessorsToUse) throws Exception {
      
    //We'll return this value at the end of the method
    //It's value should be in the range of numMatchedComparison (perfect rank 1 comparison for each match of numMatchedComparisons
    //to perfect algorithm failure of numMatchedComparisons*numComparisonsEach (all true matches were ranked last)
    Double totalMatchRank=0.0;
    
    //this is a sanity check that should sum to numMatchedComparisons*numComparisonsEach at the end
    int totalNumComparisonsRun=0;
      
      //check the number of processors and set up parallel execution queue
      Runtime rt = Runtime.getRuntime();
      int numProcessors = rt.availableProcessors();
      if(numProcessorsToUse!=null){
        numProcessors=numProcessorsToUse.intValue();
      }                //set up our thread processor for each comparison thread
      ArrayBlockingQueue abq = new ArrayBlockingQueue(1000);
      
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
          if ((individualID!=null) && (!individualID.equals(""))) {
            if (indyMap.get(individualID)!=null) {
              ArrayList<EncounterLite> thisAL=indyMap.get(individualID);
              thisAL.add(el);
              indyMap.put(individualID,thisAL);
            } else {
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
      int swiCount = 0;
      for(ScanWorkItem swi:matchedComparisons) {
        System.out.println("About to RUN swi "+swiCount+"/"+matchedComparisons.size());
        //System.out.println("1) Iterating through "+allEncounterLitesUnmodifiable.size()+" encounterLites...");
      
        swiCount++;
        ArrayList<ScanWorkItem> falsePositivesToCompareAgainst=new ArrayList<ScanWorkItem>();
        
        //first, run the match to ensure it gets its MatchObject internally embedded
        //this MatchObject will have the score of the matched, which is our key to determining the rank of the match  
        try {
          swi.run();
        } catch (Exception e) {
          e.printStackTrace();
        }
        //here's our reference EncounterLite from the matched pair to match against known non-matches
        //essentially we're prentendin that we're trying to find this pattern in every comparison
        //and then get the rank when it's compared against the true match, which was already scored above
        EncounterLite el1=swi.getNewEncounterLite();

        //System.out.println("2) Iterating through "+allEncounterLitesUnmodifiable.size()+" encounterLites...");
        // the second run of the method gets 0 for falsePositivesToCompareAgainst
        try {
          for(EncounterLite el2:allEncounterLites) {
            //System.out.println("[Still need more false positives? : "+falsePositivesToCompareAgainst.size()+"/"+(numComparisonsEach-1)+"]");
            //System.out.println("[Is a non match? "+!el2.getBelongsToMarkedIndividual().equals(el1.getBelongsToMarkedIndividual())+" ]");
            if(falsePositivesToCompareAgainst.size()<(numComparisonsEach-1) && !el2.getBelongsToMarkedIndividual().equals(el1.getBelongsToMarkedIndividual())) {
                ScanWorkItem swi2 = new ScanWorkItem(el1, el2, (el1.getEncounterNumber()+"-"+el2.getEncounterNumber()), "GrothAnalysis", props2);
                falsePositivesToCompareAgainst.add(swi2);
            }
            if (falsePositivesToCompareAgainst.size()==(numComparisonsEach-1)) {
              break;
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }

        //System.out.println("3) Iterating through "+allEncounterLitesUnmodifiable.size()+" encounterLites...");
        //OK, now let's match all of these!
        //thread pool handling comparison threads
        //spawn the thread for each comparison
        ThreadPoolExecutor threadHandler = new ThreadPoolExecutor(numProcessors, numProcessors, 0, TimeUnit.SECONDS, abq);
        
        ArrayList<Double> results=new ArrayList<Double>();
        int vectorSize = falsePositivesToCompareAgainst.size();

        //System.out.println("4) Iterating through "+allEncounterLitesUnmodifiable.size()+" encounterLites...");
        System.out.println("Sanity check: falsePositivesToCompareAgainst ("+falsePositivesToCompareAgainst.size()+") should be (numComparisonsEach-1): "+(numComparisonsEach-1));
        
        Vector<ScanWorkItemResult> workItemResults = new Vector<ScanWorkItemResult>();
        for (ScanWorkItem matchme:falsePositivesToCompareAgainst) {
          try {
            matchme.setProperties(props2);
            threadHandler.submit(new AppletWorkItemThread(matchme, workItemResults));
          } catch (Exception e){
            System.out.println("...a thread threw an error, so I'm gonna skip it...");
          }
        }

        //System.out.println("5) Iterating through "+allEncounterLitesUnmodifiable.size()+" encounterLites...");
        //block until all threads are done
        long vSize = vectorSize;
        //hang out until all threads are done
        System.out.println("Gonna sit here and wait for "+threadHandler.getCompletedTaskCount()+" to == "+vSize);
        while (threadHandler.getCompletedTaskCount() < vSize) {}
              
        threadHandler.shutdown();
        //let's build a list of all the scores from the matches
        System.out.println("Building score list...");
        for (ScanWorkItemResult matchme:workItemResults) {
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
  
        if (normalizeTopN) {
          
          System.out.println("Normalizing scores in top "+numToNormalize+" to  calculate score.");

          //Double xMin = 0.0;
          Double xMin = results.get(0);
          for (Double result : results) {
            if (result<xMin) {
              xMin = result;
              if (xMin==0.0) break;  
            } 
          }

          Double xMax = results.get(0);
          Double normalizedScore = 0.0;
          //if (results.indexOf(swiscore)<numToNormalize) {
          normalizedScore = ((swiscore-xMin) / (xMax-xMin));
          normalizedScores+=normalizedScore;
          //} else {
          //  System.out.println("Score was out of bounds, discarded.");
          //}
          System.out.println("Normalization produced this score: "+normalizedScore);

        } else {
          if (swiscore==0.0) {
            totalMatchRank += (results.size()-1);
            System.out.println("======>======>Zero score for match! adding worst rank: "+(results.size()-1));
          } else {
            totalMatchRank+=results.indexOf(swiscore)+1;
            System.out.println("======>======>Adding rank of: "+(results.indexOf(swiscore)+1));
          }
        }
        //System.out.println("7) Iterating through "+allEncounterLitesUnmodifiable.size()+" encounterLites...");
      }
      System.out.println("");
      System.out.println("======> RUN COMPLETE!");    
      System.out.println("======>Sanity check of intended num comparisons (numbers should be equals): "+totalNumComparisonsRun+" = "+(numComparisonsEach*numMatchedComparisons));
      
      if (normalizeTopN) {
        //System.out.println("Returning this total for normalized scores: "+normalizedScores);
        System.out.println("Judging by average normalized score for each comparison: "+normalizedScores/numMatchedComparisons);
        System.out.println("");
        return (normalizedScores/numMatchedComparisons);
      }
      System.out.println("=>Returning total match rank for true positives = "+numMatchedComparisons+" in a field of "+numComparisonsEach+" total possible matches: "+totalMatchRank+ "(best="+numMatchedComparisons+",worst="+(numComparisonsEach*numMatchedComparisons)+")");
      System.out.println("");
      return totalMatchRank;
      }

}
