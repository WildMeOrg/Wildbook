package org.ecocean.genetics.distance;


public class DistWAD extends Ind2IndDistance
{
	public float calculate (int genes1[], int genes2[])
	{
		/*
			calculate the weighted shared allele distance
			- when counting shared alleles, each allele found to be shared
			is counted as 1 - f, where f is the frequency of that allele
			at that locus in the population as a whole
		*/

		// 1 - wtCountSharedAlleles (genes1, genes2) / (float) genes1.length;
	  return 0;
	}
}
