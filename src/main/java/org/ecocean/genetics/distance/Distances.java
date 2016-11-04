package org.ecocean.genetics.distance;


public final class Distances 
{
  private Distances () {};

  static boolean noCache = true;

  // if noCache is false, 
  // we cache values of some of the intermediate computations, because
  // these functions will often be called for many pairs of populations,
  // with only one population changing at a time (for example, when
  // called from inside a pair of for loops)
  // A caller must know about the cache and call CacheOn to use it.
  // Any caller that knows about CacheOn is likely to know about CacheClear,
  // and so will use it appropriately.

  static double Jx, Jy, Jxy, Wx, Wy, Wxy;

  // small array caches (size is number of loci)

  static double Mux[];
  static double Muy[];

  // the previous values of the inputs, to determine whether the cached
  // intermediates need to be recomputed
  // Notice that only references to arrays are being cached, not the whole arrays.

  static double JprevXfreqs[][];
  static double JprevYfreqs[][];
  static double WprevXfreqs[][];
  static double WprevYfreqs[][];
  static double MuprevXfreqs[][];
  static double MuprevYfreqs[][];

  //	Just in case separate runs of the program produce arrays stored in the
  //  same location, we need to have a way of clearing the cache.

  public static final void clearCache ()
    {
      JprevXfreqs = JprevYfreqs = WprevXfreqs = WprevYfreqs = MuprevXfreqs = MuprevYfreqs = null;
      Mux = Muy = null;
    }

  public static final void cacheOn ()
    {
      noCache = false;
      clearCache ();
    }

  public static final void cacheOff ()
    {
      noCache = true;
    }


  private static final void recalcJ ( double fX[][], double fY[][] )
    {
      int r = fX.length;
      boolean recalcJxy = false;

      //		if (JprevXfreqs != fX || noCache)
      {
	Jx = MyFuns.SumSquares (fX) / r;
	JprevXfreqs = fX;
	recalcJxy = true;
      }

      //		if (JprevYfreqs != fY || noCache)
      {
	Jy = MyFuns.SumSquares (fY) / r;
	JprevYfreqs = fY;
	recalcJxy = true;
      }

      //		if (recalcJxy || noCache)
      {
	Jxy = MyFuns.SumProduct (fX, fY) / r;
      }
    }
	
  private static final double funnyIP ( double x[][], double y[][], double aLength [][], int alleleRepSize[] )
    {

      // compute the weighted inner product required for the W values
      // sum over all loci k the weighted product of x[k][i] and y[k][j]
      // where the weight is the difference in state 

      double IP = 0.0;

      int i, j, k;
		
      int r = x.length;		// number of loci

      for (k = 0; k < r; ++k )
	for (i = 0; i < x[k].length; ++ i)
	  for (j = 0; j < x[k].length; ++j )
	    IP +=  Math.abs (aLength[k][i] - aLength[k][j]) / alleleRepSize[k] / 2
	      * x [k][i] * y [k][j];

      return IP;
    }

  private static final void recalcW ( double fX[][], double fY[][], double aLength[][], int alleleRepSize[] )
    {
      int r = fX.length;
      boolean recalcWxy = false;

      if (WprevXfreqs != fX || noCache)
	{
	  Wx = funnyIP (fX, fX, aLength, alleleRepSize) / r;
	  WprevXfreqs = fX;
	  recalcWxy = true;
	}

      if (WprevYfreqs != fY || noCache)
	{
	  Wy = funnyIP (fY, fY, aLength, alleleRepSize) / r;
	  WprevYfreqs = fY;
	  recalcWxy = true;
	}

      if (recalcWxy || noCache)
	{
	  Wxy = funnyIP (fX, fY, aLength, alleleRepSize) / r;
	}
    }
	
  private static final void recalcMu ( double fX[][], double fY[][], double aLengths[][] )
    {
      int r = fX.length;

      int i;

      // recalculate mean allele lengths at each locus for each population, if necessary

      if (MuprevXfreqs != fX || noCache)
	{
	  Mux = new double[r];
	  for (i=0; i < r; ++i)
	    Mux[i] = MyFuns.SumProduct ( fX[i], aLengths[i] );
			
	  MuprevXfreqs = fX;
	}

      if (MuprevYfreqs != fY || noCache)
	{
	  Muy = new double[r];
	  for (i=0; i < r; ++i)
	    Muy[i] = MyFuns.SumProduct ( fY[i], aLengths[i] );
			
	  MuprevYfreqs = fY;
	}

    }
	
  public static final double Pop2PopDistDs (double freqsX [][], double freqsY [][])
    {
      // Nei standard distance  Ds (Nei 1972)

      // update cached values of intermediate results, if necessary

      recalcJ ( freqsX, freqsY );

      return - Math.log (Jxy / Math.sqrt(Jx * Jy));
    }
	
  public static final double Pop2PopDistDm (double freqsX [][], double freqsY [][])
    {
      // Nei minimum distance Dm (Nei 1973)

      // update cached values of intermediate results, if necessary

      recalcJ ( freqsX, freqsY );

      return (Jx + Jy) / 2 - Jxy;
    }
	
  public static final double Pop2PopDistDa (double freqsX [][], double freqsY [][])
    {
      // Nei (1983) 'improved' distance Da

      int r = freqsX.length;	// number of loci

      int i, j;

      double Da = 0.0;

      for (i = 0; i < freqsX.length; ++i)
	for (j=0; j < freqsX[i].length; ++j)
	  Da += Math.sqrt (freqsX[i][j] * freqsY[i][j]);
		
      return 1 - Da / r;

    }
	
  public static final double Pop2PopDistDsw (double freqsX [][], double freqsY [][], double alleleLengths [][], int alleleRepSize [])
    {
      // Shriver et al (1995)'s distance  Dsw 

      // update cached values of intermediate results, if necessary

      recalcW ( freqsX, freqsY, alleleLengths, alleleRepSize );

      return Wxy - (Wx + Wy) / 2.0;
    }
	
  public static final double Pop2PopDistDmu (double freqsX [][], double freqsY [][], double alleleLengths [][], int alleleRepSize [])
    {

      // Goldstein et al (1995)'s distance Dmu (or (delta mu)^2, in their notation)
	
      recalcMu ( freqsX, freqsY, alleleLengths );
		
      int i;
      int r = freqsX.length;

      double Dmu = 0.0;
      double d;

      // this is the mean squared difference between mean allele lengths at each locus

      for (i = 0; i < r; ++i )
	{
	  d = (Mux [i] - Muy [i]) / alleleRepSize[i];
	  Dmu += d * d;
	}

      return Dmu / r;
    }


  public static final double Pop2PopDistRst (double freqsX [][], double freqsY [][], double alleleLengths [][], int alleleRepSize [])
    {
      // Slatkin (1995) distance Rst
      // - the fraction of total variance in allele size that is between
      // populations, averaged over all loci
      // this statistic can be computed for any number of populations, but
      // here we only implement the case of two populations

      int r = freqsX.length;	// number of loci

      int i, j, k;

      double Sbar = 0.0;
      double Sw = 0.0;
      double sampleSizeX, sampleSizeY;
      double groupSumX, groupSumY;
      double totalSampleSize, totalSum, totalSumSq;
      double CT, SStotal, SSgroups, SSwithin;
      double x;

      for (i=0; i < r; ++i )			// do a simple (2-group) anova of allele size for each locus, then average over them
	{
		
	  // Anova formulas are adapted from Sokal & Rohlf (1969) Biometry. pp. 208-209
	
	  sampleSizeX = 
	    sampleSizeY = 
	    groupSumX = 
	    groupSumY = 
	    totalSumSq = 0.0;

	  int na = freqsX[i].length;		// number of alleles for this locus
	  for (j=0; j < na; ++j )
	    {
	      sampleSizeX += freqsX[i][j];	// freqs are actually counts, here
	      x = freqsX[i][j] * alleleLengths[i][j] / alleleRepSize[i];
	      groupSumX += x;
	      totalSumSq += x * alleleLengths[i][j] / alleleRepSize[i];

	      sampleSizeY += freqsY[i][j];
	      x = freqsY[i][j] * alleleLengths[i][j] / alleleRepSize[i];
	      groupSumY += x;
	      totalSumSq += x * alleleLengths[i][j] / alleleRepSize[i];
	    }
	  totalSum = groupSumX + groupSumY;
	  totalSampleSize = sampleSizeX + sampleSizeY;

	  CT = totalSum * totalSum / totalSampleSize;
	  SStotal = totalSumSq - CT;
	  SSgroups = groupSumX * groupSumX / sampleSizeX + groupSumY * groupSumY / sampleSizeY - CT;
	  SSwithin = SStotal - SSgroups;

	  // add in estimates of total and within group variance for this locus

	  Sw += 2.0 * SSwithin / (totalSampleSize - 2);
	  Sbar += 2.0 * SStotal / (totalSampleSize - 1);
	}
		
      // take averages and compute Rst

      Sw /= r;
      Sbar /= r;
      return (Sbar - Sw) / Sbar;
    }

}
			
