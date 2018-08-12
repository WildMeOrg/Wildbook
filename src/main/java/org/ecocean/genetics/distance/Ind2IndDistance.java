package org.ecocean.genetics.distance;

/*
 *
 * Ind2IndDistance
 *
 */
public abstract class Ind2IndDistance 
{
	int [][] alleleLengths;
	int alleleRepSize;
	int ploidy;
	//int [][] alleleCounts;	// overall count of each each allele at each locus
	int numAlleleCopies;	// number of allele copies at each locus, for entire population
							// equals ploidy times population size

	public final void setAlleleLengths (int [][] al)
	{
		alleleLengths = al;
	}

	/*
	public final void setAlleleCounts (int [][] c)
	{
		alleleCounts = c;
		numAlleleCopies = (int) APL.sumAll (alleleCounts[0]);
	}
	*/

	public final void setRepSize (int rs)
	{
		alleleRepSize = rs;
	}

	public final void setPloidy (int ploidy)
	{
		this.ploidy = ploidy;
	}

	public final static int SAD = 0;
	public final static int WAD = 1;

	final static String distNames[] =
	{
		"SAD - shared allele distance",
		"WAD - shared allele distance, weighted by allele frequencies in total dataset"
	};
	
	public static final String getName (int i)
	{
		return (i < distNames.length) ? distNames[i] : "";
	}

	public static final int getNumMeasures ()
	{
		return distNames.length;
	}

	public static Ind2IndDistance getDistMeasure (int which)
	{
		Ind2IndDistance dm = null;
		
		switch (which)
		{
		case SAD:
			dm = new DistSAD ();
			break;
		case WAD:
			dm = new DistWAD ();
			break;
		}
		return dm;
	}


	public float calculate (int[] genes1, int[] genes2) {return 0;}

	public static final int countSharedAlleles (int genes1[], int genes2[], int ploidy)
	{
		// calculate the count of shared alleles between two individuals

		int na = genes1.length;
		int shared = 0;

		switch (ploidy)
		{
/*
	commented out to make sure the general case gets tested!
		// do a couple fast special cases
		case 1:
			for (int i=0; i < na; ++i)
				if (genes1[i] == genes2[i])
					++shared;
			break;

		case 2:
			for (int i=0; i < na; i += 2)
			{
				if  (genes1[i] == genes2[i] || genes1[i+1] == genes2[i+1] || genes1[i] == genes2[i+1] || genes1[i+1] == genes2[i])
				{
					++shared;	// at least one allele in common at this locus
					if ((genes1[i] == genes2[i] && genes1[i+1] == genes2[i+1]) || (genes1[i] == genes2[i+1] && genes1[i+1] == genes2[i]))
						++shared;	// same pair of alleles at this locus
				}
			}
			break;
*/

		default:
			// if we knew that alleles at distinct loci always had distinct tags,
			// we could sort each genelist and compare matches in time O(na log na)
			// Unfortunately, this isn't necessarily true, so we use a brute force
			// search, marking matched alleles to prevent rematching them
			// This takes time O(na * ploidy), which is actually faster
			// than sorting if ploidy < k * log na for some constant k that
			// you are welcome to figure out

			boolean used[] = new boolean[genes1.length];
			for (int l=0; l < na; l += ploidy)	// l loops over loci
			{
				for (int j=l; j < l + ploidy; ++j)	// loops over alleles at one locus
				{
					for (int k=l; k < l + ploidy; ++k)	// loops over alleles at same locus in other individual
					{
						if ((genes1[j] == genes2[k]) && !used[k])
						{
							used[k] = true;
							++shared;
							break;		// found a match for this allele, now quit
						}
					}
				}
			}
		}

		return shared;
	}

	/*
	public float wtCountSharedAlleles (int genes1[], int genes2[])
	{
		// calculate the weighted count of shared alleles between two individuals
		// for each allele found to be shared, add 1-f, where f is the frequency
		// of that allele flavour in the population

		int na = genes1.length;
		int numLoci = na / ploidy;
		int shared = 0;

		switch (ploidy)
		{
		default:
			// if we knew that alleles at distinct loci always had distinct tags,
			// we could sort each genelist and compare matches in time O(na log na)
			// Unfortunately, this isn't necessarily true, so we use a brute force
			// search, marking matched alleles to prevent rematching them
			// This takes time O(na * ploidy), which is actually faster
			// than sorting if ploidy < k * log na for some constant k that
			// you are welcome to figure out

			boolean used[] = new boolean[genes1.length];
			int i=0;
			for (int l=0; l < numLoci; ++l)	// l loops over loci
			{
				for (int j=i; j < i + ploidy; ++j)	// loops over alleles at one locus
				{
					for (int k=i; k < i + ploidy; ++k)	// loops over alleles at same locus in other individual
					{
						if ((genes1[j] == genes2[k]) && !used[k])
						{
							used[k] = true;
							shared += numAlleleCopies - alleleCounts[l][genes1[j]];
							break;		// found a match for this allele, now quit
						}
					}
				}
				i += ploidy;
			}
		}

		return shared / (float) numAlleleCopies;
	}
	*/
	
	
	
}

