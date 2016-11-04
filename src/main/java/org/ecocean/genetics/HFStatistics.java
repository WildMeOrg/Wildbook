/*
    Copyright 2009 Stephane De Mita, Mathieu Siol

    This file is part of the EggLib library.

    EggLib is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    EggLib is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with EggLib.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.ecocean.genetics;


import java.util.*;

public class HFStatistics{
  
  
  boolean d_flag;

  int  d_reserved;
  int  d_numberOfGenotypes;
  ArrayList<Integer> d_genotypes;
  ArrayList<Integer> d_populationLabels;

  boolean s_flag;
  int    s_numberOfAlleles;
  ArrayList<Integer>   s_alleleValueMapping;
  int    s_numberOfPopulations;
  ArrayList<Integer> s_populationLabelMapping;
  ArrayList<Integer> s_populationFrequencies;
  ArrayList<Integer> s_alleleFrequenciesTotal;
  Integer[][]  s_alleleFrequenciesPerPopulation;
 
 int numPopulations;

  boolean w_flag;

  double  w_T;
  Double[] w_T1;
  Double[] w_T2;
  double  w_nbar;
  double  w_nc;
  Double[] w_pbar;
  Double[] w_ssquare;
  double  w_sum_T1;
  double  w_sum_T2;


    private void d_init() {
        d_reserved = 0;
        d_flag = false;
        d_numberOfGenotypes = 0;
        d_genotypes = new ArrayList<Integer>();
        d_populationLabels = new ArrayList<Integer>();
    }
    
    
    private void d_clear() {
        d_genotypes=null;
        d_populationLabels=null;
        d_init();
    }


    private void s_init() {
        s_flag = false;
        s_numberOfAlleles = 0;
        s_alleleValueMapping = new ArrayList<Integer>();
        s_numberOfPopulations = 0;
        s_populationLabelMapping = new ArrayList<Integer>();
        
        s_populationFrequencies = new ArrayList<Integer>();
        s_alleleFrequenciesTotal = new ArrayList<Integer>();
        s_alleleFrequenciesPerPopulation = new Integer[numPopulations][30];

    }


    private void s_clear() {

        s_alleleValueMapping=null;
        s_populationLabelMapping=null;
        s_populationFrequencies=null;
        s_alleleFrequenciesTotal=null;
        for (int i=0; i<s_numberOfPopulations; i++) {
            s_alleleFrequenciesPerPopulation[i]=null;
        }
        s_alleleFrequenciesPerPopulation=null;
        s_init();
    }


    private void w_init() {
        w_flag = false;
        w_T = 0.;
        w_T1 = new Double[90];
        w_T2 = new Double[90];
        w_nbar = 0;
        w_nc = 0;
        w_pbar = new Double[90];
        w_ssquare = new Double[90];
        w_sum_T1 = 0;
        w_sum_T2 = 0;
    }
    
    
    private void w_clear(){
        w_T1=null;
        w_T2=null;
        w_pbar=null;
        w_ssquare=null;
        w_init();
    }


/* ****************************************************************** */

    public HFStatistics(int numPopulations) {
      this.numPopulations=numPopulations;
        d_init();
        s_init();
        w_init();
    }



    
/* ****************************************************************** */

    /*
    private void reserve(int numberOfIndividuals) {
        if (numberOfIndividuals==0) {
            throw n("HFStatistics: cannot reserve memory for zero sites for F-statistics analysis");
        }

        if (numberOfIndividuals<d_numberOfGenotypes) {
            throw EggArgumentValueError("HFStatistics: cannot reserve less memory than what is currently used");
        }
        
        d_genotypes = (int*) realloc(d_genotypes, numberOfIndividuals*sizeof(int));
        if (!d_genotypes) throw EggMemoryError();

        d_populationLabels = (int*) realloc(d_populationLabels, numberOfIndividuals*sizeof(int));
        if (!d_populationLabels) throw EggMemoryError();
            
        d_reserved = numberOfIndividuals;
    }
    */

    public void loadIndividual(int genotype, int populationLabel) {
    
        // clears statistics if already computed
        if (s_flag) s_clear();
        d_flag = true;

        // allocates memory
        d_numberOfGenotypes++;

        /*
        if (d_numberOfGenotypes>d_reserved) {
            d_genotypes = (int*) realloc(d_genotypes, d_numberOfGenotypes*sizeof(int));
            if (!d_genotypes) throw EggMemoryError();

            d_populationLabels = (int*) realloc(d_populationLabels, d_numberOfGenotypes*sizeof(int));
            if (!d_populationLabels) throw EggMemoryError();
        }
        */

        // loads data
        d_genotypes.add((d_numberOfGenotypes-1),genotype);
        d_populationLabels.add((d_numberOfGenotypes-1),populationLabel);

    }


/* ****************************************************************** */

    private void s_compute() {
       // if (!d_flag) throw new Exception("HFStatistics: cannot compute frequency statistics: data not loaded");
       // if (s_flag) throw new Exception("HFStatistics: inconsistency: frequency statistics already computed");

        // gets population data (number, mapping and frequencies)
        processPopulations();

        /*
        // makes memory allocation accordingly
        s_alleleFrequenciesPerPopulation = (int**) malloc(s_numberOfPopulations*sizeof(int*));
        if (!s_alleleFrequenciesPerPopulation) {
            s_clear();
            throw EggMemoryError();
        }
        for (int i=0; i<s_numberOfPopulations; i++) {
            s_alleleFrequenciesPerPopulation[i] = NULL;
        }
        if (s_numberOfPopulations<2) throw EggRuntimeError("HFStatistics: cannot compute frequency statistics: not enough different populations");
        */
        // gets alleles, mapping and frequencies
        processAlleles();
        
        s_flag = true;
    }
    
    
    private void processPopulations() {
        for (int genotype=0; genotype<d_numberOfGenotypes; genotype++) {
            int populationLabel = d_populationLabels.get(genotype);
            int populationIndex = getPopulationIndex(populationLabel);
            
            // new population
            if (populationIndex==s_numberOfPopulations) {
                
                s_numberOfPopulations++;

                // C++ memory
                /*
                s_populationLabelMapping = (int*) realloc(s_populationLabelMapping, s_numberOfPopulations*sizeof(int));
                if (!s_populationLabelMapping) {
                    s_clear();
                    throw EggMemoryError();
                }
                s_populationFrequencies = (int*) realloc(s_populationFrequencies, s_numberOfPopulations*sizeof(int));
                if (!s_populationFrequencies) {
                    s_clear();
                    throw EggMemoryError();
                }
                */
                
                // data
                //s_populationLabelMapping[populationIndex] = populationLabel;
                //s_populationFrequencies[populationIndex] = 1;
                
                s_populationLabelMapping.add(populationIndex,populationLabel);
                s_populationFrequencies.add(populationIndex,1);
            }
            
            // one of previous populations
            else {
              s_populationFrequencies.set(populationIndex,s_populationFrequencies.get(populationIndex)+1);
              }
        }
    }
    
    
    private void processAlleles() {
        for (int genotype=0; genotype<d_numberOfGenotypes; genotype++) {

            //int populationIndex = getPopulationIndex(d_populationLabels[genotype]);
            int populationIndex = getPopulationIndex(d_populationLabels.get(genotype));

            int alleleValue = d_genotypes.get(genotype).intValue();
            int alleleIndex = getAlleleIndex(alleleValue);

            // new allele

            if (alleleIndex == s_numberOfAlleles) {

                s_numberOfAlleles++;

                // adds the allele
                //s_alleleValueMapping = (int*) realloc(s_alleleValueMapping, s_numberOfAlleles*sizeof(int));
                //if (!s_alleleValueMapping) throw EggMemoryError();
                //s_alleleValueMapping[alleleIndex] = alleleValue;
                s_alleleValueMapping.add(alleleIndex,alleleValue);    
                // adds the frequency

                // memory and initialization of new cells
                    
                // ... for the allele frequency tables
               /*
                s_alleleFrequenciesTotal = (int*) realloc(s_alleleFrequenciesTotal, s_numberOfAlleles*sizeof(int));
                if (!s_alleleFrequenciesTotal) {
                    s_clear();
                    throw EggMemoryError();
                }
                */
                //s_alleleFrequenciesTotal[alleleIndex] = 0;
                s_alleleFrequenciesTotal.add(alleleIndex,0);
                    
                for (int i=0; i<s_numberOfPopulations; i++) {
                  /*  
                  s_alleleFrequenciesPerPopulation[i] = (int*) realloc(s_alleleFrequenciesPerPopulation[i], s_numberOfAlleles*sizeof(int));
                    if (!s_alleleFrequenciesPerPopulation[i]) {
                        s_clear();
                        throw EggMemoryError();
                    }
                    */
                    s_alleleFrequenciesPerPopulation[i][alleleIndex] = 0;
                }
                    
            }

            // increments allele frequencies (even if new)
            //s_alleleFrequenciesTotal[alleleIndex]++;
            s_alleleFrequenciesTotal.set(alleleIndex,s_alleleFrequenciesTotal.get(alleleIndex)+1);
            s_alleleFrequenciesPerPopulation[populationIndex][alleleIndex]++;
            //s_alleleFrequenciesPerPopulation[populationIndex][alleleIndex]++;
            
        }
    }


    private int getAlleleIndex(int alleleValue) {
        for (int allele=0; allele<s_numberOfAlleles; allele++) {
            if (s_alleleValueMapping.get(allele).intValue() == alleleValue) {
                return allele;
            }
        }
        return s_numberOfAlleles;
    }
    
    
    private int getPopulationIndex(int populationLabel) {
        for (int population=0; population<s_numberOfPopulations; population++) {
            if (s_populationLabelMapping.get(population).intValue() == populationLabel) {
                return population;
            }
        }
        return s_numberOfPopulations;
    }


/* ****************************************************************** */

    private void w_compute() {

        /* implementation of Weir & Cockerham 1984 */
        
        if (!s_flag) s_compute();
        //if (w_flag) throw new Exception("HFStatistics: inconsistency: F-statistics already computed");
        
        // average sample size (per population)
        
        w_nbar = 0.;
        int sum_nisquare = 0;
        for (int i=0; i<s_numberOfPopulations; i++) {
            sum_nisquare += s_populationFrequencies.get(i).intValue() * s_populationFrequencies.get(i).intValue();
            w_nbar += s_populationFrequencies.get(i).intValue();
        }
        w_nbar /= s_numberOfPopulations;
        
        // sample size variation
        
        w_nc = (s_numberOfPopulations * w_nbar - sum_nisquare / (s_numberOfPopulations * w_nbar))
                                        / (s_numberOfPopulations - 1.);


        // average allele frequency

        /*
        w_pbar = (double*) malloc(s_numberOfAlleles * sizeof(double));
        if (!w_pbar) {
            w_clear();
            throw EggMemoryError();
        }
        */

        for (int allele=0; allele<s_numberOfAlleles; allele++) {
            w_pbar[allele] = 0.;
            for (int population=0; population<s_numberOfPopulations; population++) {
                w_pbar[allele] += s_alleleFrequenciesPerPopulation[population][allele];       // absolule frquency

            }
            w_pbar[allele] /= ( s_numberOfPopulations * w_nbar );
        }

        // allele frequency variation

        /*
        w_ssquare = (double*) malloc(s_numberOfAlleles * sizeof(double));
        if (!w_ssquare) {
            w_clear();
            throw EggMemoryError();
        }
        */
        
        for (int allele=0; allele<s_numberOfAlleles; allele++) {
            w_ssquare[allele] = 0.;
            for (int population=0; population<s_numberOfPopulations; population++) {
                w_ssquare[allele] += ( s_populationFrequencies.get(population).intValue() *
        (1.*s_alleleFrequenciesPerPopulation[population][allele] / s_populationFrequencies.get(population).intValue() - w_pbar[allele]) *
        (1.*s_alleleFrequenciesPerPopulation[population][allele] / s_populationFrequencies.get(population).intValue() - w_pbar[allele]) );
            }
            w_ssquare[allele] /= ( (s_numberOfPopulations - 1.) * w_nbar );
        }
        
        // between-population component of variance
        /*
        w_T1 = (double*) malloc(s_numberOfAlleles * sizeof(double));
        if (!w_T1) {
            w_clear();
            throw EggMemoryError();
        }
        */

        for (int allele=0; allele<s_numberOfAlleles; allele++) {

            w_T1[allele] = //(w_nbar/w_nc)* () ???
                w_ssquare[allele] - (1./(w_nbar-1)) * (
                w_pbar[allele]*(1-w_pbar[allele])
                - w_ssquare[allele]*(s_numberOfPopulations-1.)/s_numberOfPopulations ) ;
        }
        
        // within-population, between individual component of variance
        
        /*
        w_T2 = (double*) malloc(s_numberOfAlleles * sizeof(double));
        if (!w_T2) {
            w_clear();
            throw EggMemoryError();
        }
        */

        // total variance
        
        for (int allele=0; allele<s_numberOfAlleles; allele++) {
            w_T2[allele] = ((w_nc-1)/(w_nbar-1)) * ( w_pbar[allele]*(1-w_pbar[allele])
                + (w_ssquare[allele]/s_numberOfPopulations) * 
                    (1+((s_numberOfPopulations-1)*(w_nbar-w_nc)/(w_nbar-1))) );
        }

        
        // F-statistics  equation modified for multi allele

        for (int allele=0; allele<s_numberOfAlleles; allele++) {
            w_sum_T1 += w_T1[allele];
            w_sum_T2 += w_T2[allele];
        }
        
        w_T = w_sum_T1 / w_sum_T2;
        
        // conclusion
        
        w_flag = true;
        
    }

    private int allele(int individualIndex) {
        return d_genotypes.get(individualIndex).intValue();
    }

    private int individualLabel(int individualIndex) {
        return d_populationLabels.get(individualIndex);
    }


/* ****************************************************************** */

    public int numberOfGenotypes() {
        return d_numberOfGenotypes;
    }

    public int numberOfPopulations() {
        if (!s_flag) s_compute();
        return s_numberOfPopulations;
    }
    
    
    public int numberOfAlleles() {
        if (!s_flag) s_compute();
        return s_numberOfAlleles;
    }
    
    
    public int populationLabel(int i) {
        if (!s_flag) s_compute();
        return s_populationLabelMapping.get(i).intValue();
    }


    public int alleleValue(int i) {
        if (!s_flag) s_compute();
        return s_alleleValueMapping.get(i).intValue();
    }


    public int alleleFrequencyTotal(int alleleIndex) {
        if (!s_flag) s_compute();
        return s_alleleFrequenciesTotal.get(alleleIndex);
    }

    
    public int alleleFrequencyPerPopulation(int populationIndex, int alleleIndex) {
        if (!s_flag) s_compute();
        return s_alleleFrequenciesPerPopulation[populationIndex][alleleIndex];
    }

    
    public int populationFrequency(int populationIndex) {
        if (!s_flag) s_compute();
        return s_populationFrequencies.get(populationIndex).intValue();
    }


/* ****************************************************************** */

    public double getTheta() {
        if (!w_flag) w_compute();
        return w_T;
    }
    
    public double getT1() {
        if (!w_flag) w_compute();
        return w_sum_T1;
    }
    
    public double getT2() {
        if (!w_flag) w_compute();
        return w_sum_T2;
    }
       
}

