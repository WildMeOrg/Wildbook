package org.ecocean.genetics.distance;

public class DistSAD extends Ind2IndDistance
{
  public static float calculate (int genes1[], int genes2[], int ploidy)
  {
    // calculate the shared allele distance between two individuals
    // the inputs are lists of length ploidy*r, where r is the number of loci
    // and the values in the lists are numeric ID's of the alleles at each
    // locus for individuals 1 and 2, respectively
		
    // the distance is 1 - (total number of alleles shared) / (number of allele copies)
    // the number of alleles shared between two individuals is the size of the
    // largest matching that can be made between them, where edges in the
    // matching join copies of given allele at a given locus in the two
    // individual:

    /*
      SAD example: (ploidy = 3, numloci = 3)

      genes1:  a  b  c   d  e  f   g  h  h

      genes2:  b  a  i   d  d  j   h  f  g

      matches: aa, bb    dd        gg,hh
      total matches: 5
      distance: 1 - 5/(3*3) = .444...
    */

    // notice that genes1.length = ploidy * numLoci

    return 1 - countSharedAlleles (genes1, genes2, ploidy) / (float) genes1.length;
  }

  public static int countUnsharedAlleles (int genes1[], int genes2[], int ploidy)
  {
    return genes1.length - countSharedAlleles (genes1, genes2, ploidy);
  }

//    public static int countSharedAlleles (int genes1[], int genes2[], int ploidy)
//    {
//      // calculate the count of shared alleles between two individuals

//      int na = genes1.length;
//      int shared = 0;

//      switch (ploidy)
//        {
//  	/*
//  	  commented out to make sure the general case gets tested!
//  	  // do a couple fast special cases
//  	  case 1:
//  	  for (int i=0; i < na; ++i)
//  	  if (genes1[i] == genes2[i])
//  	  ++shared;
//  	  break;

//  	  case 2:
//  	  for (int i=0; i < na; i += 2)
//  	  {
//  	  if  (genes1[i] == genes2[i] || genes1[i+1] == genes2[i+1] || genes1[i] == genes2[i+1] || genes1[i+1] == genes2[i])
//  	  {
//  	  ++shared;	// at least one allele in common at this locus
//  	  if ((genes1[i] == genes2[i] && genes1[i+1] == genes2[i+1]) || (genes1[i] == genes2[i+1] && genes1[i+1] == genes2[i]))
//  	  ++shared;	// same pair of alleles at this locus
//  	  }
//  	  }
//  	  break;
//  	*/

//        default:
//  	// if we knew that alleles at distinct loci always had distinct tags,
//  	// we could sort each genelist and compare matches in time O(na log na)
//  	// Unfortunately, this isn't necessarily true, so we use a brute force
//  	// search, marking matched alleles to prevent rematching them
//  	// This takes time O(na * ploidy), which is actually faster
//  	// than sorting if ploidy < k * log na for some constant k that
//  	// you are welcome to figure out

//  	// 2001/02/22 - this wasn't checking for missing alleles, which
//  	// are not to be matched! - the missing allele is always zero.

//  	boolean used[] = new boolean[genes1.length];
//  	for (int l=0; l < na; l += ploidy)	// l loops over loci
//  	  {
//  	    for (int j=l; j < l + ploidy; ++j)	// loops over alleles at one locus
//  	      {
//  		for (int k=l; k < l + ploidy; ++k)	// loops over alleles at same locus in other individual
//  		  {
//  		    if ((genes1[j] != 0) && (genes1[j] == genes2[k]) && !used[k] )
//  		      {
//  			used[k] = true;
//  			++shared;
//  			break;		// found a match for this allele, now quit
//  		      }
//  		  }
//  	      }
//  	  }
//        }

//      return shared;
//    }



}
