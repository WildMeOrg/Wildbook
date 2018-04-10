package org.ecocean.genetics.distance;

import java.util.StringTokenizer;
import java.util.Hashtable;
import java.util.NoSuchElementException;

/*
 *
 * HashTokenizer
 *
 */

// Suppose we use the fields in a list only for the purposes of comparison.
// All we need is a list of integers representing the fields, such that
// two integers in the list are the same iff the corresponding fields are the same.
// (This is used, for example, by routines that just look at names of alleles
// to compute genetic distances).
// We need to augment the StringTokenizer with a hashtable:

public class HashTokenizer extends StringTokenizer
{
	Hashtable hash;
	int maxCode;

	public HashTokenizer (String s, String zeroToken)
	{
		// a constructor that adds a string to the hash table
		// for this tokenizer, so that a hash code of zero
		// represents a known string

		this (s);
		if (zeroToken != null)
			hash.put (zeroToken, new Integer (maxCode ++));
	}

	public HashTokenizer (String s)
	{
		super (s);
		hash = new Hashtable ();
		maxCode = 0;
	}

	public int nextTokenCode () throws NoSuchElementException
	{
		String s = nextToken ();
		if (! hash.containsKey (s))
			hash.put (s, new Integer (maxCode ++));

		return ((Integer) hash.get (s) ).intValue ();
	}
}

