/*
	myfuns	 - various utilities by jmb

*/

package org.ecocean.genetics.distance;

import java.lang.Math;
import java.util.List;
import java.util.*;
import java.awt.*;


public final class MyFuns {
	static final String spaces = "                                                         ";
	
	// no initialization by anyone
	private MyFuns () {}
	
	// count the number of whitespace-separated fields in a string

	public static final int CountFieldsInString (String list)
	{
		return (new StringTokenizer (list)).countTokens ();
	}

	public static final int CountCharsInString (String s, char c)
	{
		int count = 0;
		for (int i=0; i < s.length (); ++i)
			if (s.charAt(i) == c)
				++count;
		return count;
	}

 	// allocate an array of integers and fill with values from a string

	public static final int[] StringToInts (String list) throws Exception
	{
		StringTokenizer tok = new StringTokenizer (list);

		int count = tok.countTokens ();
		int numbers[] = new int[count];
		StringToNumbers (numbers, tok, false);
		return numbers;
	}

	// fill an array of integers from a string
	// an exception is thrown if mustFill is true and there are not enough numbers in the string

	public static final void StringToNumbers (int n[], String list, boolean mustFill) throws Exception
	{
		StringTokenizer tok = new StringTokenizer (list);

		StringToNumbers (n, tok, mustFill);
	}

	// fill a vector of vectors of integers from a string
	// an exception is thrown if mustFill is true and there are not enough numbers in the string

	public static final void StringToNumbers (int n[][], String list, boolean mustFill) throws Exception
	{
		StringTokenizer tok = new StringTokenizer (list);

		int i;

		for (i=0; i < n.length; ++i )
			if (! StringToNumbers (n[i], tok, true) && ! mustFill)
				break;
	}

	// fill a vector of vectors of vectors of integers from a string
	// an exception is thrown if mustFill is true and there are not enough numbers in the string

	public static final void StringToNumbers (int n[][][], String list, boolean mustFill) throws Exception
	{
		StringTokenizer tok = new StringTokenizer (list);

		int i, j;

outside:	for (i=0; i < n.length; ++i )
				for (j=0; j < n[i].length; ++j )
						if (! StringToNumbers (n[i][j], tok, mustFill) && ! mustFill)
							break outside;
	}


	// continue tokenizing a string into integers and storing them
	// return true if there are any more integers to be converted

	private static final boolean StringToNumbers (int n[], StringTokenizer tok, boolean mustFill) throws Exception
	{
		int i;
		String t="";
		
		try 
		{
			for (i=0; i < n.length && tok.hasMoreTokens() ; ++i )
			{
				t = tok.nextToken ();
				n[i] = Double.valueOf (t).intValue ();
			}
		}
		catch (Exception e)
		{
			throw new Exception ("An item in the list is not an integer:  " + t );
		}

		if (mustFill)
			if (i < n.length)
				if (i > 0)
					throw new Exception ("Expected more integers after: " + String.valueOf (n[i-1]) );
				else
					throw new Exception ("Expected some integers.");

		return tok.hasMoreTokens();
	}

	// allocate an array of doubles and fill with values from a string

	public static final double[] StringToDoubles (String list) throws Exception
	{
		StringTokenizer tok = new StringTokenizer (list);

		int count = tok.countTokens ();
		double numbers[] = new double[count];
		StringToNumbers (numbers, tok, false);
		return numbers;
	}

	// fill an array of doubles from a string
	// an exception is thrown if mustFill is true and there are not enough numbers in the string

	public static final void StringToNumbers (double n[], String list, boolean mustFill) throws Exception
	{
		StringTokenizer tok = new StringTokenizer (list);

		StringToNumbers (n, tok, mustFill);
	}

	// fill a vector of vectors of doubles from a string
	// an exception is thrown if mustFill is true and there are not enough numbers in the string

	public static final void StringToNumbers (double n[][], String list, boolean mustFill) throws Exception
	{
		StringTokenizer tok = new StringTokenizer (list);

		int i;

		for (i=0; i < n.length; ++i )
			if (! StringToNumbers (n[i], tok, true) && ! mustFill)
				break;
	}

	// fill a vector of vectors of vectors of doubles from a string
	// an exception is thrown if mustFill is true and there are not enough numbers in the string

	public static final void StringToNumbers (double n[][][], String list, boolean mustFill) throws Exception
	{
		StringTokenizer tok = new StringTokenizer (list);

		int i, j;

outside:	for (i=0; i < n.length; ++i )
				for (j=0; j < n[i].length; ++j )
						if (! StringToNumbers (n[i][j], tok, mustFill) && ! mustFill )
							break outside;
	}


	// continue tokenizing a string into doubles and storing them
	// return true if there are any more doubles to be converted

	private static final boolean StringToNumbers (double n[], StringTokenizer tok, boolean mustFill) throws Exception
	{
		int i;
		String t="";
		
		try 
		{
			for (i=0; i < n.length && tok.hasMoreTokens() ; ++i )
			{
				t = tok.nextToken ();
				n[i] = Double.valueOf (t).doubleValue ();
			}
		}
		catch (Exception e)
		{
			throw new Exception ("An item in the list is not a real number:  " + t );
		}

		if (mustFill)
			if (i < n.length)
				if (i > 0)
					throw new Exception ("Expected more real numbers after: " + String.valueOf (n[i-1]) );
				else
					throw new Exception ("Expected some real numbers.");

		return tok.hasMoreTokens();
	}

 	// allocate an array of integers and fill with hashcodes of fields from a string

	public static final int[] StringToHashCodes (String list) throws Exception
	{
		HashTokenizer htok = new HashTokenizer (list);

		int count = htok.countTokens ();
		int numbers[] = new int[count];
		StringToHashCodes (numbers, htok, false);
		return numbers;
	}

 	// fill a vector of integers with hashcodes of fields from a string
	// an exception is thrown if mustFill is true and there are not enough fields in the string

	public static final void StringToHashCodes (int n[], String list, boolean mustFill) throws Exception
	{
		HashTokenizer htok = new HashTokenizer (list);

		StringToHashCodes (n, htok, mustFill);
	}

 	// fill an vector of vectors of integers with hashcodes of fields from a string
	// an exception is thrown if mustFill is true and there are not enough fields in the string

	public static final void StringToHashCodes (int n[][], String list, boolean mustFill) throws Exception
	{
		HashTokenizer htok = new HashTokenizer (list);

		int i;

		for (i=0; i < n.length; ++i )
			if (! StringToHashCodes (n[i], htok, true) && ! mustFill)
				break;
	}

 	// fill an vector of vectors of integers with hashcodes of fields from a string
	// an exception is thrown if mustFill is true and there are not enough fields in the string

	public static final void StringToHashCodes (int n[][][], String list, boolean mustFill) throws Exception
	{
		HashTokenizer htok = new HashTokenizer (list);

		int i, j;

outside:	for (i=0; i < n.length; ++i )
				for (j=0; j < n[i].length; ++j )
						if (! StringToHashCodes (n[i][j], htok, mustFill) && ! mustFill)
							break outside;
	}


	// continue tokenizing a string, hashing its fields, and storing the
	// hash values in an integer array
	// return true if there are any more fields to be converted

	private static final boolean StringToHashCodes (int n[], HashTokenizer htok, boolean mustFill) throws Exception
	{
		int i = 0;
		
		try 
		{
			for (i=0; i < n.length; ++i )
			{
				n[i] = htok.nextTokenCode ();
			}
		}
		catch (Exception e)
		{
			if (mustFill)
				if (i < n.length)
					if (i > 0)
						throw new Exception ("Expected more labels after: " + String.valueOf (n[i-1]) );
					else
						throw new Exception ("Expected some labels.");
		}


		return htok.hasMoreTokens();
	}

	public static final String padRight (String S, int len)
	{
		int l = S.length ();

		if (l < len)
			return S + spaces.substring (0, len - l);
		else
			return S.substring (0, len);
	}

	public static final String padLeft (String S, int len)
	{
		int l = S.length ();

		if (l < len)
			return spaces.substring (0, len - l) + S;
		else
			return S.substring (0, len);
	}

	public static final String padDecimal (String S, int len, int preDigits)
	{
		int p = S.indexOf ('.');

		if (p < 0)
		{
			p = S.length ();
			S = S + ".";
		}

		preDigits = Math.max (p, preDigits);

		len = Math.max (len, preDigits + 1);

		int e = S.indexOf ('E');
		if (e < 0)
			e = S.indexOf ('e');

		if (e < 0)
			return padLeft (S.substring (0, p), preDigits) + padRight (S.substring (p), len - preDigits - 1);
		else
			return padLeft (S.substring (0, p), preDigits) + padRight (S.substring (p, e), Math.min (len - preDigits - 1, e-p)) + S.substring (e);
	}

	public static final String trimDecimal (String S, int postDigits)
	{
		// trim a number to postDigits digits after the decimal place
		// keeping any exponential notation

		int p = S.indexOf ('.');

		if (p < 0)
		{
			p = S.length ();
			S = S + ".";
		}

		int e = S.indexOf ('E');

		if (e < 0)
			e = S.indexOf ('e');

		if (e < 0)
			return S.substring (0, p) + S.substring (p, Math.min (p + postDigits, S.length ()));
		else
			return S.substring (0, p) + S.substring (p, p + Math.min(postDigits, e - p)) + S.substring (e);

	}

	public static final long Sum (int a[])
	{
		int i;
		long s = 0;
		for (i=0; i < a.length; ++i )
			s += a[i];
		return s;
	}

	public static final double Sum (double a[])
	{
		int i;
		double s = 0.0;
		for (i=0; i < a.length; ++i )
			s += a[i];
		return s;
	}

	public static final double Sum (double a[][])
	{
		int i, j;
		double s = 0.0;
		for (i=0; i < a.length; ++i )
			for (j=0; j < a[i].length; ++j)
				s += a[i][j];
		return s;
	}

	public static final double SumSquares (double a[])
	{
		int i;
		double s = 0.0;
		for (i=0; i < a.length; ++i )
			s += a[i] * a[i];
		return s;
	}

	public static final double SumSquares (double a[][])
	{
		int i, j;
		double s = 0.0;
		for (i=0; i < a.length; ++i )
			for (j=0; j < a[i].length; ++j)
				s += a[i][j] * a[i][j];
		return s;
	}

	public static final double SumProduct (double a[], double b[])
	{
		int i;
		double s = 0.0;
		for (i=0; i < a.length; ++i )
			s += a[i] * b[i];
		return s;
	}

	public static final double SumProduct (double a[][], double b[][])
	{
		int i, j;
		double s = 0.0;
		for (i=0; i < a.length; ++i )
			for (j=0; j < a[i].length; ++j)
				s += a[i][j] * b[i][j];
		return s;
	}

	public static final int Max (int a[])
	{
		if (a.length == 0)
			return Integer.MIN_VALUE;

		int m = a[0];
		for (int i=1; i < a.length; ++i )
			if (m > a[i])
				m = a[i];

		return m;
	}

	public static final double Max (double a[])
	{
		if (a.length == 0)
			return Double.MIN_VALUE;

		double m = a[0];
		for (int i=1; i < a.length; ++i )
			if (m > a[i])
				m = a[i];

		return m;
	}

	// allocate a list of vectors of integers of varying length
	// the supplied lengths are reused so that every vector in a is
	// allocated
	// the point is to create a 2-dimensional array where the range
	// of the second index depends on the first index

	public static final void AllocVectors (int a[][], int sizes[])
	{
		int i;
		int j = 0;
		
		for (i=0; i < a.length; ++i)
		{
			a[i] = new int[sizes[j]];
			++j;
			if (j == sizes.length)
				j = 0;
		}
	}

	// allocate a list of vectors of integers of varying length
	// the supplied lengths are reused so that every vector in a is
	// allocated
	// the point is to create a 3-dimensonal array where the range
	// of the second index depends on the first two indices, and the
	// range of the second index depends on the first

	public static final void AllocVectors (int a[][][], int sizes[])
	{
		int i, j;
		int k = 0;

		for (i=0; i < a.length; ++i)
		{
			for (j=0; j < a[i].length; ++j)
			{
				a[i][j] = new int[sizes[k]];
				++k;
				if (k == sizes.length)
					k = 0;
			}
		}
	}

	// allocate a list of vectors of doubles of varying length
	// the supplied lengths are reused so that every vector in a is
	// allocated
	// the point is to create a 2-dimensional array where the range
	// of the second index depends on the first index

	public static final void AllocVectors (double a[][], int sizes[])
	{
		int i;
		int j = 0;
		
		for (i=0; i < a.length; ++i)
		{
			a[i] = new double[sizes[j++]];
			if (j == sizes.length)
				j = 0;
		}
	}

	// allocate a list of vectors of doubles of varying length
	// the supplied lengths are reused so that every vector in a is
	// allocated
	// the point is to create a 3-dimensonal array where the range
	// of the third index depends on the first two indices, and the
	// range of the second index depends on the first

	public static final void AllocVectors (double a[][][], int sizes[])
	{
		int i, j;
		int k = 0;

		for (i=0; i < a.length; ++i)
		{
			for (j=0; j < a[i].length; ++j)
			{
				a[i][j] = new double[sizes[k]];
				++k;
				if (k == sizes.length)
					k = 0;
			}
		}
	}

	public static final String MatrixToString (double a[][], int fldWidth, int digitsBeforeDecimal )
	{
		return MatrixToString (a, fldWidth, digitsBeforeDecimal, " ", "\n");
	}

	public static final String MatrixToString (double a[][], int fldWidth, int digitsBeforeDecimal, String delim, String eol)
	{
		return MatrixToString (a, fldWidth, digitsBeforeDecimal, delim, eol);
	}

//	public static final String MatrixToString (double a[][], int fldWidth, int digitsBeforeDecimal, String delim, String eol )
	//{
		//return MatrixToString (a, fldWidth, digitsBeforeDecimal, delim, eol, false);
	//}

	public static final String MatrixToString (double a[][], int fldWidth, int digitsBeforeDecimal, String delim, String eol, boolean lowTriangle )
	{
		int i, j;

		StringBuffer s = new StringBuffer (a.length * a[0].length * 5);

		for (i=0; i < a.length; ++i )
		{
			int upperLimit = lowTriangle ? i + 1 : a[i].length;
			if (delim.equals (" "))	// use multiple space for alignment
			{
				for (j=0; j < upperLimit; ++j )
					s = s.append (padDecimal (String.valueOf (a[i][j]), fldWidth, digitsBeforeDecimal ) ) ;
			}
			else				// use a single delimiter character
			{
				s = s.append (a[i][0]);
				for (j=1; j < upperLimit; ++j )
				{
					s = s.append (delim) .append (a[i][j]);
				}
			}
			//if (pb != null)pb.ShowProgress ();

			s = s.append (eol);
		}
		return s.toString ();
	}

	public static final String MatrixToString (int a[][], int fldWidth )
	{
		return MatrixToString (a, fldWidth, " ", "\n", false);
	}

	public static final String MatrixToString (int a[][], int fldWidth, String delim, String eol  )
	{
		return MatrixToString (a, fldWidth, delim, eol, false);
	}

	public static final String MatrixToString (int a[][], int fldWidth, String delim, String eol, boolean lowTriangle )
	{
		int i, j;

		StringBuffer s = new StringBuffer (a.length * a[0].length * 5);

		for (i=0; i < a.length; ++i )
		{
			int upperLimit = lowTriangle ? i + 1 : a[i].length ;
			if (delim.equals (" "))	// use multiple space for alignment
			{
				for (j=0; j < upperLimit; ++j )
					s = s.append (padLeft (String.valueOf (a[i][j]), fldWidth )) ;
			}
			else				// use a single delimiter character
			{
				s = s.append (a[i][0]);
				for (j=1; j < upperLimit; ++j )
				{
					s = s.append (delim) . append (a[i][j]);
				}
			} 
			s = s.append (eol);
		//	if (pb != null) pb.ShowProgress ();
		}
		return s.toString ();
	}


	// functions to normalize vectors:  divide each element by the sum
	// of all elements, unless this is zero, in which case do nothing
	// As above, there are versions for vectors of and vectors of vectors of
	// vectors of doubles

	public static final void NormalizeVectors (double a[])
	{
		int i;
		double s = 0.0;

		for (i=0; i < a.length; ++i )
			s += a[i];
		
		if (s != 0.0)
			for (i=0; i < a.length; ++i )
				a[i] /= s;
	}

	public static final void NormalizeVectors (double a[][])
	{
		int i;

		for (i=0; i < a.length; ++i )
			NormalizeVectors (a[i]);
	}

	public static final void NormalizeVectors (double a[][][])
	{
		int i, j;

		for (i = 0; i < a.length; ++i )
			for (j = 0; j < a[i].length; ++j )
				NormalizeVectors (a[i][j]);
	}


	public final static void constrain (Container container, Component component, 

		int grid_x, int grid_y, int grid_width, int grid_height,
                  int fill, int anchor, double weight_x, double weight_y,
                  int top, int left, int bottom, int right)
    {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = grid_x; c.gridy = grid_y;
        c.gridwidth = grid_width; c.gridheight = grid_height;
        c.fill = fill; c.anchor = anchor;
        c.weightx = weight_x; c.weighty = weight_y;
        if (top+bottom+left+right > 0)
            c.insets = new Insets(top, left, bottom, right);
        
        ((GridBagLayout)container.getLayout()).setConstraints(component, c);
        container.add(component);
    }
    
    public final static void constrain(Container container, Component component, 
                  int grid_x, int grid_y, int grid_width, int grid_height,
				  int fill, int anchor
				  )
	{
        constrain(container, component, grid_x, grid_y, 
              grid_width, grid_height, fill, 
              anchor, 1.0, 1.0, 0, 0, 0, 0);
    }
    
    public final static void constrain(Container container, Component component, 
                  int grid_x, int grid_y, int grid_width, int grid_height)
	{
        constrain(container, component, grid_x, grid_y, 
              grid_width, grid_height, GridBagConstraints.NONE, 
              GridBagConstraints.NORTHWEST, 0.0, 0.0, 0, 0, 0, 0);
    }
    
    public final static void constrain(Container container, Component component, 
                  int grid_x, int grid_y, int grid_width, int grid_height,
                  int top, int left, int bottom, int right)
	{
        constrain(container, component, grid_x, grid_y, 
              grid_width, grid_height, GridBagConstraints.NONE, 
              GridBagConstraints.NORTHWEST, 
              0.0, 0.0, top, left, bottom, right);
    }

/*
 * @(#)QSortAlgorithm.java	1.6f 95/01/31 James Gosling
 *
 * Copyright (c) 1994-1995 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Permission to use, copy, modify, and distribute this software
 * and its documentation for NON-COMMERCIAL or COMMERCIAL purposes and
 * without fee is hereby granted. 
 * Please refer to the file http://java.sun.com/copy_trademarks.html
 * for further important copyright and trademark information and to
 * http://java.sun.com/licensing.html for further important licensing
 * information for the Java (tm) Technology.
 * 
 * SUN MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SUN SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 * 
 * THIS SOFTWARE IS NOT DESIGNED OR INTENDED FOR USE OR RESALE AS ON-LINE
 * CONTROL EQUIPMENT IN HAZARDOUS ENVIRONMENTS REQUIRING FAIL-SAFE
 * PERFORMANCE, SUCH AS IN THE OPERATION OF NUCLEAR FACILITIES, AIRCRAFT
 * NAVIGATION OR COMMUNICATION SYSTEMS, AIR TRAFFIC CONTROL, DIRECT LIFE
 * SUPPORT MACHINES, OR WEAPONS SYSTEMS, IN WHICH THE FAILURE OF THE
 * SOFTWARE COULD LEAD DIRECTLY TO DEATH, PERSONAL INJURY, OR SEVERE
 * PHYSICAL OR ENVIRONMENTAL DAMAGE ("HIGH RISK ACTIVITIES").  SUN
 * SPECIFICALLY DISCLAIMS ANY EXPRESS OR IMPLIED WARRANTY OF FITNESS FOR
 * HIGH RISK ACTIVITIES.
 */

/**
 * A quick sort demonstration algorithm
 * SortAlgorithm.java, Thu Oct 27 10:32:35 1994
 *
 * @author James Gosling
 * @version 	1.6f, 31 Jan 1995
 */
/**
 * 19 Feb 1996: Fixed to avoid infinite loop discoved by Paul Haberli.
 *              Misbehaviour expressed when the pivot element was not unique.
 *              -Jason Harrison
 *
 * 2  Mar 1997: Fixed:  if the pivot was the minimum element
 *			    in a list, the first element of the list stayed in place.
 *				Changed logic slightly and documented.
 *				- John Brzustowski
 */

	public final static void QuickSort (int a[], int lo0, int hi0) throws Exception
	{
		int T;
		int lo = lo0;
		int hi = hi0;

	// deal with a couple of special cases
	// to avoid having to check for these before each recursive
	// invocation below
	
		if (lo >= hi) {
			return;
		}
		else if (lo == hi - 1)
		{
			if (a[lo] > a[hi])			// 2-element list: sort by swapping
			{
				T = a[lo];
				a[lo] = a[hi];
				a[hi] = T;
			}
			return;
		}

		int pivotLoc = (lo + hi) / 2;

		int pivot = a[pivotLoc];		// Move pivot element to end of list
		a[pivotLoc] = a[hi];			// where it will stay as the following
		a[hi] = pivot;					// loop runs

		while (lo < hi) {
			
			while (lo<hi && a[lo] <= pivot)		// the "<=" is OK, given the other changes
				lo++;							// In lists with many repeated elements, it is a slight improvement
												// that reduces the number of swaps and can help keep
												// the sizes of the sublists more balanced
			
			while (lo<hi && a[hi] >= pivot)  
				hi--;
			
			/*
				If lo < hi, there is an element in the low list
				that's greater than pivot, and one in the high list
				that's smaller than pivot.  So swap these.
			*/

			if (lo < hi)		
			{
				T = a[lo];
				a[lo] = a[hi];
				a[hi] = T;
			}
		}

		// After the preceding while loop, lo == hi, and the element
		// a[lo] >= pivot

		// Now put the pivot element into a position we know to be correct:
		// and put the element previously there into the high list

		a[hi0] = a[hi];
		a[hi] = pivot;

		/*
		
		 Notice that

			lo == hi
			all elements in a[lo0..lo] are <= pivot
			all elements in a[hi..hi0] are >= pivot

		 so lo is a correct final position for holding pivot
		 and this location can be excluded from the recursive calls below
		 This also guarantees that each recursive call is passing a smaller
		 list than the current invocation received, since hi+1 > lo0
		 and lo-1 < hi0

		*/

		QuickSort (a, lo0, lo-1);
		QuickSort (a, hi+1, hi0);
	}

	public final static void QuickSort(int a[]) throws Exception
	{
		QuickSort(a, 0, a.length-1);
	}

	public final static void QuickSort (double a[], int lo0, int hi0) throws Exception
	{
		double T;
		int lo = lo0;
		int hi = hi0;

	// deal with a couple of special cases
	// to avoid having to check for these before each recursive
	// invocation below
	
		if (lo >= hi) {
			return;
		}
		else if (lo == hi - 1)
		{
			if (a[lo] > a[hi])			// 2-element list: sort by swapping
			{
				T = a[lo];
				a[lo] = a[hi];
				a[hi] = T;
			}
			return;
		}

		int pivotLoc = (lo + hi) / 2;

		double pivot = a[pivotLoc];		// Move pivot element to end of list
		a[pivotLoc] = a[hi];			// where it will stay as the following
		a[hi] = pivot;					// loop runs

		while (lo < hi) {
			
			while (lo<hi && a[lo] <= pivot)		// the "<=" is OK, given the other changes
				lo++;							// In lists with many repeated elements, it is a slight improvement
												// that reduces the number of swaps and can help keep
												// the sizes of the sublists more balanced
			
			while (lo<hi && a[hi] >= pivot)  
				hi--;
			
			/*
				If lo < hi, there is an element in the low list
				that's greater than pivot, and one in the high list
				that's smaller than pivot.  So swap these.
			*/

			if (lo < hi)		
			{
				T = a[lo];
				a[lo] = a[hi];
				a[hi] = T;
			}
		}

		// After the preceding while loop, lo == hi, and the element
		// a[lo] >= pivot

		// Now put the pivot element into a position we know to be correct:
		// and put the element previously there into the high list

		a[hi0] = a[hi];
		a[hi] = pivot;

		/*
		
		 Notice that

			lo == hi
			all elements in a[lo0..lo] are <= pivot
			all elements in a[hi..hi0] are >= pivot

		 so lo is a correct final position for holding pivot
		 and this location can be excluded from the recursive calls below
		 This also guarantees that each recursive call is passing a smaller
		 list than the current invocation received, since hi+1 > lo0
		 and lo-1 < hi0

		*/

		QuickSort (a, lo0, lo-1);
		QuickSort (a, hi+1, hi0);
	}

	public final static void QuickSort(double a[]) throws Exception
	{
		QuickSort(a, 0, a.length-1);
	}

	// return a 3-d matrix with the first 2 axes transposed
	// the matrix must be rectangular in the first 2 axes
	// i.e. n[i].length == n[j].length for all i, j
	// (but n[i][j].length can vary with i and j)
	// the output matrix refers to the same instance of data as the input matrix

	public final static double[][][] Transpose12 (double mat[][][])
	{
		int n = mat.length;
		int m = mat[0].length;

		double t[][][] = new double [m][n][];
		int i;
		int j;
		for (i=0; i < m; ++i)
			for (j=0; j < n; ++j )
				t[i][j] = mat[j][i];

		return t;
	}

	// return a 2-d matrix with the axes transposed
	// the matrix must be rectangular

	public final static double[][] Transpose (double mat[][])
	{
		int n = mat.length;
		int m = mat[0].length;

		double t[][] = new double [m][n];
		int i;
		int j;
		for (i=0; i < m; ++i)
			for (j=0; j < n; ++j )
				t[i][j] = mat[j][i];

		return t;
	}

	// return a 2-d matrix with the axes transposed
	// the matrix must be rectangular

	public final static int[][] Transpose (int mat[][])
	{
		int n = mat.length;
		int m = mat[0].length;

		int t[][] = new int [m][n];
		int i;
		int j;
		for (i=0; i < m; ++i)
			for (j=0; j < n; ++j )
				t[i][j] = mat[j][i];

		return t;
	}
	
	 public static Map sortMapByDoubleValue(Map unsortMap) {
	   
	    List list = new LinkedList(unsortMap.entrySet());
	 
	    // sort list based on comparator
	    Collections.sort(list, new DistanceComparator());
	 
	    // put sorted list into map again
	                //LinkedHashMap make sure order in which keys were inserted
	    Map sortedMap = new LinkedHashMap();
	    for (Iterator it = list.iterator(); it.hasNext();) {
	      Map.Entry entry = (Map.Entry) it.next();
	      sortedMap.put(entry.getKey(), entry.getValue());
	    }
	    return sortedMap;
	  }
	

}
