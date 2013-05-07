package org.ecocean.genetics;
/*
    Copyright 2009 Stephane De Mita, Mathieu Siol

    This file is part of the EggLib library.

    You should have received a copy of the GNU General Public License
    along with EggLib.  If not, see <http://www.gnu.org/licenses/>.
    
    Ported from the EggLib C++ library to Java by Jason Holmberg.
    
    Basic usage:
    1. Instantiate a new FStatistics object for each allele
    2. Use the loadIndividual(...) method to load the individuals and alleles from each of the populations being compared
    3. Call your getter to get the FStatistic of interest: getF, getf, getTheta, etc.
    
*/

import java.util.*;

public class FStatistics{

  int numPopulations=0;
  
  boolean d_flag;
  Integer  d_reserved;
  Integer  d_numberOfGenotypes;
  ArrayList<Integer> d_genotypes;
  ArrayList<Integer> d_populationLabels;

  boolean s_flag;
  Integer    s_numberOfAlleles;
  ArrayList<Integer>   s_alleleValueMapping;
  Integer    s_numberOfPopulations;
  ArrayList<Integer> s_populationLabelMapping;
  ArrayList<Integer> s_populationFrequencies;
  ArrayList<Integer>  s_alleleFrequenciesTotal;
  Integer[][]  s_alleleFrequenciesPerPopulation;
  Integer[][]  s_genotypeFrequenciesTotal;
  Integer[][][] s_genotypeFrequenciesPerPopulation;

  boolean w_flag;
  Double  w_F;
  Double  w_T;
  Double  w_f;
  Double[] w_a;
  Double[] w_b;
  Double[] w_c;
  Double  w_nbar;
  Double  w_nc;
  Double[] w_pbar;
  Double[] w_ssquare;
  Double[] w_hbar;
  Double  w_sum_a;
  Double  w_sum_b;
  Double  w_sum_c;
  Double  w_sum_abc;
  Double  w_sum_bc;


    void d_init() {
        d_reserved = 0;
        d_flag = false;
        d_numberOfGenotypes = 0;
        d_genotypes = new ArrayList<Integer>();
        d_populationLabels = new ArrayList<Integer>();

    }
    


    void s_init() {
        s_flag = false;
        s_numberOfAlleles = 0;
        s_alleleValueMapping = new ArrayList<Integer>();
        s_numberOfPopulations = 0;
        s_populationLabelMapping = new ArrayList<Integer>();
        
        s_populationFrequencies = new ArrayList<Integer>();
        s_alleleFrequenciesTotal = new ArrayList<Integer>();
        s_alleleFrequenciesPerPopulation = new Integer[numPopulations][30];
        s_genotypeFrequenciesTotal = new Integer[30][50];
        s_genotypeFrequenciesPerPopulation = new Integer[numPopulations][30][50];
    }


    void s_clear() {

        if (s_alleleValueMapping!=null) s_alleleValueMapping=null;
        if (s_populationLabelMapping!=null) s_populationLabelMapping=null;
        if (s_populationFrequencies!=null) s_populationFrequencies=null;
        if (s_alleleFrequenciesTotal!=null) s_alleleFrequenciesTotal=null;
        //for (int i=0; i<s_numberOfPopulations; i++) {
            if (s_alleleFrequenciesPerPopulation!=null) s_alleleFrequenciesPerPopulation=null;
        //}
        if (s_alleleFrequenciesPerPopulation!=null) s_alleleFrequenciesPerPopulation=null;
        //for (int i=0; i<s_numberOfAlleles; i++) {
            if (s_genotypeFrequenciesTotal!=null) s_genotypeFrequenciesTotal=null;
        //}
        if (s_genotypeFrequenciesTotal!=null) s_genotypeFrequenciesTotal=null;
        /*
        for (int i=0; i<s_numberOfPopulations; i++) {
            for (int j=0; j<s_numberOfAlleles; j++) {
                if (s_genotypeFrequenciesPerPopulation[i][j]) {
                    free(s_genotypeFrequenciesPerPopulation[i][j]);
                }
            }
            if (s_genotypeFrequenciesPerPopulation[i]!=null) {
                s_genotypeFrequenciesPerPopulation[i]=null;
            }
        }
        */
        if (s_genotypeFrequenciesPerPopulation!=null) {
            s_genotypeFrequenciesPerPopulation=null;
        }
        s_init();
    }


    void w_init() {
        w_flag = false;
        w_F = 0.;
        w_T = 0.;
        w_f = 0.;
        w_a = new Double[99];
        w_b = new Double[99];
        w_c = new Double[99];
        w_nbar = 0.;
        w_nc = 0.;
        w_pbar = new Double[99];
        w_ssquare = new Double[99];
        w_hbar = new Double[99];
        w_sum_a = 0.;
        w_sum_b = 0.;
        w_sum_c = 0.;
        w_sum_abc = 0.;
        w_sum_bc = 0.;
    }
    
    
    void w_clear() {
        if (w_a!=null) w_a=null;
        if (w_b!=null) w_b=null;
        if (w_c!=null) w_c=null;
        if (w_pbar!=null) w_pbar=null;
        if (w_ssquare!=null) w_ssquare=null;
        if (w_hbar!=null) w_hbar=null;
        w_init();
    }


/* ****************************************************************** */

    public FStatistics(int numPopulations) {
        this.numPopulations=numPopulations;
        d_init();
        s_init();
        w_init();
    }



    
/* ****************************************************************** */

    /*
    void reserve(int numberOfIndividuals) {
        if (numberOfIndividuals==0) {
            throw new Exception("FStatistics: cannot reserve memory for zero sites for F-statistics analysis");
        }

        if (numberOfIndividuals<d_numberOfGenotypes) {
            throw new Exception("FStatistics: cannot reserve less memory than what is currently used");
        }
        
        d_genotypes = (int) realloc(d_genotypes, 2*numberOfIndividuals*sizeof(int));
        if (!d_genotypes) throw EggMemoryError();

        d_populationLabels = (int) realloc(d_populationLabels, numberOfIndividuals*sizeof(int));
        if (!d_populationLabels) throw EggMemoryError();
            
        d_reserved = numberOfIndividuals;
    }
    */

    public void loadIndividual(int genotype1,int genotype2, int populationLabel) {
    
    
        // clears statistics if already computed
        if (s_flag) s_clear();
        d_flag = true;

        // allocates memory
        d_numberOfGenotypes++;

        /**
        if (d_numberOfGenotypes>d_reserved) {
            d_genotypes = (int) realloc(d_genotypes, 2*d_numberOfGenotypes*sizeof(int));
            if (!d_genotypes) throw EggMemoryError();

            d_populationLabels = (int) realloc(d_populationLabels, d_numberOfGenotypes*sizeof(int));
            if (!d_populationLabels) throw EggMemoryError();
        }
         */
        // loads data
        d_genotypes.add(2*(d_numberOfGenotypes-1), genotype1);
        d_genotypes.add((2*(d_numberOfGenotypes-1) + 1), genotype2);

        d_populationLabels.add((d_numberOfGenotypes-1),populationLabel);

    }


/* ****************************************************************** */

    private void s_compute() {
        if (!d_flag) System.out.println("FStatistics: cannot compute frequency statistics: data not loaded");
        if (s_flag) System.out.println("FStatistics: inconsistency: frequency statistics already computed");

        // gets population data (number, mapping and frequencies)
        processPopulations();

        // makes memory allocation accordingly
        //s_alleleFrequenciesPerPopulation = (int*) malloc(s_numberOfPopulations*sizeof(int));
        //if (!s_alleleFrequenciesPerPopulation) {
        //    s_clear();
        //    throw EggMemoryError();
        //}
        //for (int i=0; i<s_numberOfPopulations; i++) {
        //   s_alleleFrequenciesPerPopulation[i] = null;
        //}
        //s_genotypeFrequenciesPerPopulation = (int**) malloc(s_numberOfPopulations*sizeof(int*));
        //if (!s_genotypeFrequenciesPerPopulation) {
        //    s_clear();
        //    throw EggMemoryError();
        //}
        //for (int i=0; i<s_numberOfPopulations; i++) {
        //    s_genotypeFrequenciesPerPopulation[i] = null;
        //}

        if (s_numberOfPopulations<2) System.out.println("FStatistics: cannot compute frequency statistics: not enough different populations");
        
        // gets alleles, mapping and frequencies
        processAlleles();
        
        s_flag = true;
    }
    
    
    void processPopulations() {
        for (int genotype=0; genotype<d_numberOfGenotypes; genotype++) {
            int populationLabel = d_populationLabels.get(genotype);
            int populationIndex = getPopulationIndex(populationLabel);
            
            // new population
            if (populationIndex==s_numberOfPopulations) {
                
                s_numberOfPopulations++;

                // memory
                /*
                s_populationLabelMapping = (int) realloc(s_populationLabelMapping, s_numberOfPopulations*sizeof(int));
                if (!s_populationLabelMapping) {
                    s_clear();
                    throw EggMemoryError();
                }
                s_populationFrequencies = (int) realloc(s_populationFrequencies, s_numberOfPopulations*sizeof(int));
                if (!s_populationFrequencies) {
                    s_clear();
                    throw EggMemoryError();
                }
                */
                // data
                s_populationLabelMapping.add(populationIndex,populationLabel);
                s_populationFrequencies.add(populationIndex,1);
            }
            
            // one of previous populations
            else {
                s_populationFrequencies.set(populationIndex,s_populationFrequencies.get(populationIndex)+1);
            }
        }
    }
    
    
    void processAlleles() {
        for (int genotype=0; genotype<d_numberOfGenotypes; genotype++) {

            int populationIndex = getPopulationIndex(d_populationLabels.get(genotype));

            // both allele (for genotype indexing)
            int alleleIndex1 = 0;
            int alleleIndex2 = 0;
            
            for (int offset=0; offset<2; offset++) {

                int alleleValue = d_genotypes.get(2*genotype+offset);
                int alleleIndex = getAlleleIndex(alleleValue);

                // stores both alleles (works also if alleleIndex==s_numberOfAlleles)
                if (offset==0) alleleIndex1 = alleleIndex;
                if (offset==1) alleleIndex2 = alleleIndex;
                
                // new alleles

                if (alleleIndex == s_numberOfAlleles) {

                    s_numberOfAlleles++;

                    // adds the allele
                    //s_alleleValueMapping = (int) realloc(s_alleleValueMapping, s_numberOfAlleles*sizeof(int));
                    //if (!s_alleleValueMapping) throw EggMemoryError();
                    s_alleleValueMapping.add(alleleIndex,alleleValue);
                    
                    // adds the frequency

                    // memory and initialization of new cells
                    
                    // ... for the allele frequency tables
                    /*
                    s_alleleFrequenciesTotal = (int) realloc(s_alleleFrequenciesTotal, s_numberOfAlleles*sizeof(int));
                    if (!s_alleleFrequenciesTotal) {
                        s_clear();
                        throw EggMemoryError();
                    }
                    */
                    s_alleleFrequenciesTotal.add(alleleIndex,0);
                    
                    for (int i=0; i<s_numberOfPopulations; i++) {
                        /*
                        s_alleleFrequenciesPerPopulation[i] = (int) realloc(s_alleleFrequenciesPerPopulation[i], s_numberOfAlleles*sizeof(int));
                        if (!s_alleleFrequenciesPerPopulation[i]) {
                            s_clear();
                            throw EggMemoryError();
                        }
                        */
                        s_alleleFrequenciesPerPopulation[i][alleleIndex] = 0;
                    }
                    
                    // ... for the genotype frequency tables
                    
                    // total table
                    
                    // new row
                    
                    /*
                    s_genotypeFrequenciesTotal = (int*) realloc(s_genotypeFrequenciesTotal, s_numberOfAlleles*sizeof(int));
                    if (!s_genotypeFrequenciesTotal) {
                        s_clear();
                        throw EggMemoryError();
                    }
                    */
                    //s_genotypeFrequenciesTotal[alleleIndex] = null;
                    
                    // new column
                    for (int i=0; i<s_numberOfAlleles; i++) {
                        /*
                        s_genotypeFrequenciesTotal[i] = (int) realloc(s_genotypeFrequenciesTotal[i], s_numberOfAlleles*sizeof(int));
                        if (!s_genotypeFrequenciesTotal[i]) {
                            s_clear();
                            throw EggMemoryError();
                        }
                        */
                        s_genotypeFrequenciesTotal[i][alleleIndex] = 0;
                    }
                    
                    // initialization of the new line (except new column)
                    for (int i=0; i<(s_numberOfAlleles-1); i++) {
                        s_genotypeFrequenciesTotal[alleleIndex][i] = 0;
                    }
                    
                    // the meta-table
                    
                    for (int i=0; i<s_numberOfPopulations; i++) {

                        // adds the new row
                      /*
                        s_genotypeFrequenciesPerPopulation[i] = (int*) realloc(s_genotypeFrequenciesPerPopulation[i], s_numberOfAlleles*sizeof(int));
                        if (!s_genotypeFrequenciesPerPopulation[i]) {
                            s_clear();
                            throw EggMemoryError();
                        }
                        */
                        //s_genotypeFrequenciesPerPopulation[i][alleleIndex] = null;

                        // adds the new column (one cell per row, incl. new)
                        for (int j=0; j<s_numberOfAlleles; j++) {
                            /*
                            s_genotypeFrequenciesPerPopulation[i][j] = (int) realloc(s_genotypeFrequenciesPerPopulation[i][j], s_numberOfAlleles*sizeof(int));
                            if (!s_genotypeFrequenciesPerPopulation[i][j]) {
                                s_clear();
                                throw EggMemoryError();
                            }
                            */
                            // initializes the new column
                            s_genotypeFrequenciesPerPopulation[i][j][alleleIndex] = 0;
                        }

                        // initializes the new row (but not the new column)
                        for (int j=0; j<(s_numberOfAlleles-1); j++) {
                            s_genotypeFrequenciesPerPopulation[i][alleleIndex][j] = 0;
                        }
                    }
                }
                
                // increments allele frequencies (even if new)
                s_alleleFrequenciesTotal.set(alleleIndex,s_alleleFrequenciesTotal.get(alleleIndex)+1);
                s_alleleFrequenciesPerPopulation[populationIndex][alleleIndex]++;
            }
            
            // increments frequencies of genotype
            s_genotypeFrequenciesTotal[alleleIndex1][alleleIndex2]++;
            s_genotypeFrequenciesPerPopulation[populationIndex][alleleIndex1][alleleIndex2]++;
        }
    }


    int getAlleleIndex(int alleleValue) {
        for (int allele=0; allele<s_numberOfAlleles; allele++) {
            if (s_alleleValueMapping.get(allele).intValue() == alleleValue) {
                return allele;
            }
        }
        return s_numberOfAlleles;
    }
    
    
    int getPopulationIndex(int populationLabel) {
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
        if (w_flag) System.out.println("FStatistics: inconsistency: F-statistics already computed");
        
        // average sample size (per population)
        
        w_nbar = 0.;
        int sum_nisquare = 0;
        for (int i=0; i<s_numberOfPopulations; i++) {
            sum_nisquare += s_populationFrequencies.get(i) *  s_populationFrequencies.get(i);
            w_nbar +=  s_populationFrequencies.get(i);
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
                w_pbar[allele] += 0.5 * s_alleleFrequenciesPerPopulation[population][allele];       // absolule frquency

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
                w_ssquare[allele] += ( s_populationFrequencies.get(population) *
        (0.5* s_alleleFrequenciesPerPopulation[population][allele] /  s_populationFrequencies.get(population) - w_pbar[allele]) *
        (0.5* s_alleleFrequenciesPerPopulation[population][allele] /  s_populationFrequencies.get(population) - w_pbar[allele]) );
            }
            w_ssquare[allele] /= ( (s_numberOfPopulations - 1.) * w_nbar );
        }
        
        // average heterozygosity
        
        /*
        w_hbar = (double*) malloc(s_numberOfAlleles * sizeof(double));
        if (!w_hbar) {
            w_clear();
            throw EggMemoryError();
        }
        */

        for (int allele=0; allele<s_numberOfAlleles; allele++) {
            w_hbar[allele] = 0.;
            for (int population=0; population<s_numberOfPopulations; population++) {
                
                // computes the frequency of heterozygotes
                double H = 0.; // equivalent to ni*hi
                for (int otherAllele = 0; otherAllele<s_numberOfAlleles; otherAllele++) {
                    if (allele!=otherAllele) {
                        H += ((
                        s_genotypeFrequenciesPerPopulation[population][allele][otherAllele] +
                        s_genotypeFrequenciesPerPopulation[population][otherAllele][allele]));
                    }
                }
                
                w_hbar[allele] += H;
            }
            w_hbar[allele] /= ( s_numberOfPopulations * w_nbar );
        }
            

        // between-population component of variance
        
        /*
        w_a = (double*) malloc(s_numberOfAlleles * sizeof(double));
        if (!w_a) {
            w_clear();
            throw EggMemoryError();
        }
        */

        /* equation (2) of WC84 */

        for (int allele=0; allele<s_numberOfAlleles; allele++) {
            w_a[allele] = (w_nbar/w_nc)*( 
            w_ssquare[allele] - (1./(w_nbar-1)) * (
                w_pbar[allele]*(1-w_pbar[allele])
                - w_ssquare[allele]*(s_numberOfPopulations-1.)/s_numberOfPopulations
                - w_hbar[allele] / 4.) );
        }
        
        // within-population, between individual component of variance
        
        /*
        w_b = (double*) malloc(s_numberOfAlleles * sizeof(double));
        if (!w_b) {
            w_clear();
            throw EggMemoryError();
        }
         */
        /* equation (3) of WC84) */
        
        for (int allele=0; allele<s_numberOfAlleles; allele++) {
            w_b[allele] = (w_nbar/(w_nbar-1)) * ( w_pbar[allele]*(1-w_pbar[allele])
                - w_ssquare[allele] * ((s_numberOfPopulations-1.)/s_numberOfPopulations)
                - w_hbar[allele] * (2*w_nbar-1)/(4*w_nbar) );
        }

        // within individual component of variance
        /*
        w_c = (double*) malloc(s_numberOfAlleles * sizeof(double));
        
        if (!w_c) {
            w_clear();
            throw EggMemoryError();
        }
        */
        for (int allele=0; allele<s_numberOfAlleles; allele++) {
            w_c[allele] = 0.5 * w_hbar[allele];
        }

        // sums (for multi-allele computation, also available for multi-locus)
        
        for (int allele=0; allele<s_numberOfAlleles; allele++) {
            w_sum_a += w_a[allele];
            w_sum_b += w_b[allele];
            w_sum_c += w_c[allele];
            w_sum_bc += (w_b[allele] + w_c[allele]);
            w_sum_abc += (w_a[allele] + w_b[allele] + w_c[allele]);
        }
        
        // F-statistics  WC equation (1) modified for multi allele
        
        w_F = 1 - w_sum_c / w_sum_abc;
        w_T = w_sum_a / w_sum_abc;
        w_f = 1 - w_sum_c / w_sum_bc;
        
        // conclusion
        
        w_flag = true;
        
    }

    int firstAllele(int individualIndex) {
        return d_genotypes.get(2*individualIndex);
    }

    int secondAllele(int individualIndex) {
        return d_genotypes.get(2*individualIndex+1);
    }

    int individualLabel(int individualIndex) {
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

    
    public int genotypeFrequencyTotal(int alleleIndex1, int alleleIndex2) {
        if (!s_flag) s_compute();
        return s_genotypeFrequenciesTotal[alleleIndex1][alleleIndex2];
    }

    
    public int genotypeFrequencyPerPopulation(int populationIndex, int alleleIndex1, int alleleIndex2) {
        if (!s_flag) s_compute();
        return s_genotypeFrequenciesPerPopulation[populationIndex][alleleIndex1][alleleIndex2];
    }

    public int populationFrequency(int populationIndex) {
        if (!s_flag) s_compute();
        return  s_populationFrequencies.get(populationIndex);
    }


/* ****************************************************************** */

    public double getF() {
        if (!w_flag) w_compute();
        return w_F;
    }
    
    public double getTheta() {
        if (!w_flag) w_compute();
        return w_T;
    }
    
    public double getf() {
        if (!w_flag) w_compute();
        return w_f;
    }
    
    public double getVpopulation() {
        if (!w_flag) w_compute();
        return w_sum_a;
    }
    
    public double getVindividual() {
        if (!w_flag) w_compute();
        return w_sum_b;
    }

    public double getVallele() {
        if (!w_flag) w_compute();
        return w_sum_c;
    }

}
