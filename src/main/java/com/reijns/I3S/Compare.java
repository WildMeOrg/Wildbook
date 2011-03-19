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

package com.reijns.I3S;
/*#ifndef FINGERPRINT_HPP
#define FINGERPRINT_HPP 1

#include <stdlib.h>
#include "mm_cstring.hpp"



*/

import java.util.*;

public class Compare {

  //private StringList  allFiles;
  private FingerPrint[] fpa;
  private int cnt;
  private int realcnt;
  private FingerPrint best;

  public Compare(FingerPrint fp) {
    fpa = new FingerPrint[]{fp};
    cnt = fpa.length;  // Should be always 1
    realcnt = cnt;
  }

  public Compare(FingerPrint[] fpa) {
    this.fpa = new FingerPrint[fpa.length];
//Changed Jan 23, 2007
// Old : deleted System.arraycopy code and replaced it with for loop
//		System.arraycopy(fpa, 0, this.fpa, 0, fpa.length);
// New :		
    for (int i = 0; i < fpa.length; i++) {
      this.fpa[i] = new FingerPrint(fpa[i]);
    }
//End change
    cnt = fpa.length;
    realcnt = cnt;
  }

  public int getCnt() {
    return cnt;
  }

  public int getRealCnt() {
    return realcnt;
  }


  /*
    * Implementation parameters were changed from arrays to Maps.
    * By using a Map we avoid multiple parameters and were able to remove
    * "filterOutDuplicatePairs" as was required in the original C++ code.
    *
    * If using Java 1.5 Maps should be parameterized, to avoid casting,
    * boxing/unboxing issues and compiler warnings as example:
    * In method parameter : Map<Integer,Pair>
    * In constructor : HashMap<Integer,Pair> pairMap = new HashMap<Integer,Pair>(pairs);
    */

  /**
   * Compares an unknown visitor FingerPrint to a prior encountered FingerPrint.
   *
   * @param unknown     FingerPrint : New FingerPrint object to test (should have
   *                    points already loaded)
   * @param encountered FingerPrint : Existing FingerPrint from database filled
   *                    with best matching FingerPrint if any
   * @param pairs       Map : Map of Pair objects that record which points are nearest
   *                    neighbors and their distance.
   * @param exhaustive  boolean : True if you want to test all possible FingerPrints,
   *                    False if you want to compute and fill the pairs array from the unknown FingerPrint.
   * @return boolean : Returns true if any calculation was peformed and results
   *         were returned in the pairs parameter, false otherwise.  Note that if
   *         exhaustive is false, then only unknown and encountered will be tested
   *         without an affine transformation.  In either case, the encountered.score
   *         value will be what should be compare to determine if a close enough
   *         match was found.
   * @see Also see overloaded function : compareTwo (FingerPrint unknown,
   *      int encounterIndex, Point2D[] orig, Point2D[] tf, Pair[] pairs,
   *      int paircnt, boolean exhaustive)
   */
  public boolean compareTwo(FingerPrint unknown, FingerPrint encountered, Map pairs, boolean exhaustive) {
    if (unknown == null || encountered == null || pairs == null) {
      throw new NullPointerException("Parameter error in Compare.compareTwo, null parameter.");
    }

    best = encountered;
    //System.out.println("Num pairs going into Fingerprint.distance: "+pairs.size());
    best.distance(unknown, pairs, 0);

    if (exhaustive) {   //If a better match was found with a smaller valued score,
      //then encountered is changed to that better FingerPrint.
      exhaustiveSearch(unknown, pairs);
    }

    return true;
  }


  /**
   * Finds the best score for an unknown visitor FingerPrint from an array of
   * encountered FingerPrints (loaded into the Compare Class at construction).
   *
   * @param unknown    FingerPrint : The visitor FingerPrint object we wish to test.
   * @param best       FingerPrint[] : The returned array of "best" FingerPrint matches.
   * @param bestnr     int : The total number of "best" matches we wish to return
   * @param exhaustive boolean : True if you want to test all FingerPrints and
   *                   set their score values relative to the visitor FimgerPrint Object.
   * @return boolean : True if no errors occured.  Note this does not mean a
   *         match was found.  You have to check returned best.length>0 for matches
   *         and if there were any, test the score of each to determine if they represent
   *         a match.
   */
  // SupressWarnings for Java 1.5 where collection parameters are not
  // specified to make them Java 1.4x compatable.
  //@SuppressWarnings("unchecked")
  public boolean find(FingerPrint unknown, FingerPrint[] best, int bestnr, boolean exhaustive, TreeMap pairs) {
    //System.out.println("entering the Compare.find method, pairs size is: "+pairs.size()+" and cnt is "+cnt );
    // Java 1.5 constructor:
    // ArrayList<FingerPrint> results = new SortedMap<FingerPrint>[];
    ArrayList results = new ArrayList();


    for (int i = 0; i < cnt; i++) {
      /*
          * Java 1.5 HashMap should be parameterized, to avoid casting,
          * boxing/unboxing issues and compiler warnings as:
          * In constructor : HashMap<Integer,Pair> pairMap = new HashMap<Integer,Pair>(pairs);
          */
      //TreeMap pairs = new TreeMap();

      //System.out.println("Iterating cnt...");

      results.add(i, fpa[i]);
      fpa[i].distance(unknown, pairs, 0);

      if (exhaustive) {
        //System.out.println("Going into Compare.exhaustiveSearch with pairs: "+pairs.size());
        this.best = (FingerPrint) results.get(results.size() - 1);
        exhaustiveSearch(unknown, pairs);
        results.set(results.size() - 1, this.best);

      }
    }
    //System.out.println("Finished exhaustive scan, this.best.score is: "+this.best.getScore());
    Collections.sort(results, FPCMP);

    // return only the sublist of length bestnr.
    if (results.size() < bestnr) {
      //System.out.println("First array copy!");
      System.arraycopy(results.subList(0, results.size()).toArray(), 0, best, 0, results.size());
    } else {
      //System.out.println("Second array copy!");
      System.arraycopy(results.subList(0, bestnr).toArray(), 0, best, 0, bestnr);
    }

    return true;
  }

  /**
   * Finds the best matching FingerPrint compared to unknown, starting with
   * a known FingerPrint fp, and trys to correct for affine translation
   * differences between unknown and fp, and will returns the best matching
   * affine translation back in private parameter encountered.
   *
   * @param unknown FingerPrint : The visitor FingerPrint object we wish to test.
   * @param pairs   Map : A sorted map of best point pairs to test
   */
  // SupressWarnings for Java 1.5 where collection parameters are not
  // specified to make them Java 1.4x compatable.
  //@SuppressWarnings("unchecked")
  private void exhaustiveSearch(FingerPrint unknown, Map pairs) {
    //System.out.println("     Doing an exhaustive scan!");
    int paircnt = pairs.size();
    double[] matrix = new double[6];

    TreeMap bestPairs = new TreeMap();
    Pair[] arrayPairs = new Pair[pairs.values().size()];
//Changed Jan 23, 2007
// Old : deleted System.arraycopy code and replaced it with for loop
//		System.arraycopy(pairs.values().toArray(), 0, arrayPairs, 0, arrayPairs.length);
// New :
    Pair aPair;
    for (int i = 0; i < pairs.values().size(); i++) {
      aPair = (Pair) pairs.values().toArray()[i];
      arrayPairs[i] = new Pair();
      arrayPairs[i].dist = aPair.getDist();
      arrayPairs[i].m1 = aPair.getM1();
      arrayPairs[i].m2 = aPair.getM2();
    }
//End change

    int bestPairCnt = 0;

    for (int j = 0; j < paircnt - 2; j++)
      for (int k = j + 1; k < paircnt - 1; k++)
        for (int l = k + 1; l < paircnt; l++) {
          FingerPrint test = new FingerPrint(best);

          Point2D from1 = test.getFpp(arrayPairs[j].getM1());
          Point2D from2 = test.getFpp(arrayPairs[k].getM1());
          Point2D from3 = test.getFpp(arrayPairs[l].getM1());
          Point2D to1 = unknown.getFpp(arrayPairs[j].getM2());
          Point2D to2 = unknown.getFpp(arrayPairs[k].getM2());
          Point2D to3 = unknown.getFpp(arrayPairs[l].getM2());

          // the affine class is used to "unskew, unstretch, unrotate
          // and otherwise untransform" points so that they represent
          // being on the same coordinate plane.
          //System.out.println("Compare.aff.calcAffine values from: "+from1.getX()+","+from1.getY()+","+from2.getX()+","+from2.getY()+from3.getX()+","+from3.getY());
          //System.out.println("Compare.aff.calcAffine values to: "+to1.getX()+","+to1.getY()+","+to2.getX()+","+to2.getY()+to3.getX()+","+to3.getY());
          Affine.calcAffine(from1.getX(), from1.getY(),
            from2.getX(), from2.getY(),
            from3.getX(), from3.getY(),
            to1.getX(), to1.getY(),
            to2.getX(), to2.getY(),
            to3.getX(), to3.getY(),
            matrix);

          test.doAffine(matrix);
          TreeMap tmppairs = new TreeMap();
          test.distance(unknown, tmppairs, -3);

          //pairs=tmppairs;
          if (test.getScore() < best.getScore()) {
            //bestPairs.put((new Integer(bestPairCnt)), tmppairs.clone());
            pairs = bestPairs;
            best = test;
            bestPairCnt++;
          }
        }

    //if(bestPairCnt>0)
    //{
    //	pairs.putAll(bestPairs);
    //}
  }

  public void reset() {
    for (int i = 0; i < fpa.length; i++)
      fpa[i].resetScore();
  }

  public boolean close() {
    cnt = 0;
    realcnt = 0;
    System.gc();
    return true;
  }

  /**
   * The Comparator method class for sorting in the find method.
   */
  private final static Comparator FPCMP = new Comparator() {
    public int compare(Object p1, Object p2) {
      FingerPrint f1 = (FingerPrint) p1;
      FingerPrint f2 = (FingerPrint) p2;
      if (p1 == null && p2 == null) {
        return 0;
      } else if (p1 == null) {
        return 1;
      } else if (p2 == null) {
        return -1;
      } else {
        return ((f1.getScore() < f2.getScore()) ? -1 :
          (f1.getScore() == f2.getScore()) ? 0 : 1);
      }
    }
  };

}
