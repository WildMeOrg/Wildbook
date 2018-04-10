package org.ecocean.genetics.distance;

//import java.applet.*;
//import java.awt.*;
//import java.io.*;

import java.util.ArrayList;
import java.util.Hashtable;

import org.ecocean.*;
import org.ecocean.genetics.*;


public class ShareDst{

  //NumInputBox NIBnumLoci;
  //NumInputBox NIBgenoTypes;
  //NumOutputBox NOBoutput;
  //Checkbox	cbNoDivide;				   
  //Checkbox	cbLowerTriangle;
  //Checkbox    cbNamesPresent;
  //Button       buClearInputs;
  //Button	buCalculate;

  public final static int ploidy = 2;  // hard wired, for now


  public ShareDst(String context){
    //setLayout (new GridBagLayout ());


/*
    NIBnumLoci = new NumInputBox (this,
				  "Number of Loci",
				  "How many loci are you examining?",
				  0,
				  0, 0, 1, 1,
				  GridBagConstraints.NONE,
				  GridBagConstraints.WEST
				  );
*/
    
    
    /*
    NIBgenoTypes = new NumInputBox (this,
				    "Individual Genotypes",
				    "Enter in this order:  I1L1A1 I1L1A2 I1L2A1 I1L2A2... I2L1A1 I2L1A2...",
				    2,
				    0, 2, 5, 1,
				    GridBagConstraints.BOTH,
				    GridBagConstraints.WEST,
				    10.0, 10.0, 1, 1, 1, 1
				    );
*/
		
    //buClearInputs = new Button ("Clear Inputs");
    //MyFuns.constrain (this, buClearInputs, 0, 18, 1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST, 1.0, 1.0, 1, 1, 1, 1 );

    //buCalculate = new Button ("Calculate!");
    //MyFuns.constrain (this, buCalculate, 1, 18, 1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST, 1.0, 1.0, 1, 1, 1, 1 );

    //cbNamesPresent = new Checkbox ("Individual names are present as first item in each line", null, false);
    //MyFuns.constrain (this, cbNamesPresent, 0, 19, 2, 1, GridBagConstraints.NONE, GridBagConstraints.WEST, 1.0, 1.0, 1, 1, 1, 1 );
    //cbNamesPresent.setFont (new Font("Times", Font.BOLD, 12));

    //cbNoDivide = new Checkbox ("Don't divide by 2r - makes smaller output", null, false);
    //MyFuns.constrain (this, cbNoDivide, 0, 20, 2, 1, GridBagConstraints.NONE, GridBagConstraints.WEST, 1.0, 1.0, 1, 1, 1, 1 );
    //cbNoDivide.setFont (new Font("Times", Font.BOLD, 12));

    //cbLowerTriangle = new Checkbox ("Only show lower triangle - makes smaller output", null, false);
    //MyFuns.constrain (this, cbLowerTriangle, 0, 21, 2, 1, GridBagConstraints.NONE, GridBagConstraints.WEST, 1.0, 1.0, 1, 1, 1, 1 );
    //cbLowerTriangle.setFont (new Font("Times", Font.BOLD, 12));

/*   
    NOBoutput		= new NumOutputBox (this,
					    "Output",
					    "Use copy/paste to move the results into other programs.",
					    2,
					    0, 22, 5, 1,
					    GridBagConstraints.BOTH,
					    GridBagConstraints.WEST,
					    10.0, 10.0, 1, 1, 1, 1
		);
*/

    //run();
    
  }

    /*
  public String getAppletInfo()
  {
    return "Name: ShareDst\n" +
      "Author: John Brzustowski - jbrzusto@powersurfr.com\n" +
      "Compute the shared allele measure of genetic distance between individuals.\n";
  }
*/

    /*
  public boolean action( Event evt, Object arg )
  {
    if ( evt.target instanceof Button )
      {
	if (evt.target == buClearInputs )
	  {
	    NIBnumLoci.setText ("");
	    NIBgenoTypes.setText ("");
	    return true;
	  }
	else if (evt.target == buCalculate)
	  {
	    run ();
	    return true;
	  }
      }
    return false;
  }
*/

  /*
   * 
   * @parameter numLoci The number of loci in the study.
   * @parameter individualNames An array of MarkedIndividual.individualID values, with the first one being the individual of interest for finding kin
   * @parameter noDivide Whether to divide by 2r- for smaller output
   * @parameter lowTriangle Only show lower triangle for smaller output
   */
  public static String getDistanceOuput(String individualNames[], String lociNames[],boolean noDivide, boolean lowTriangle, String EOL, String DELIM,String context){

    String output = "";
		
		
    //NOBoutput.setText (output);

    int numIndividuals=individualNames.length;
    int numLoci=lociNames.length;
    int numAlleles=numLoci*2;
    int genoTypes[][];
    Hashtable hash = new Hashtable ();
    int maxCode = 0;
    //put in the null value field
    hash.put ("empty", new Integer (maxCode ++));
    
  //String EOL = NOBoutput.getEOL ();
    //String EOL = "\n";
    //String DELIM = NOBoutput.getDelim ();
    //String DELIM = " ";
    
    
    //String individualNames[] = null;
    boolean namesPresent = true;
    Shepherd myShepherd=new Shepherd(context);
    myShepherd.setAction("ShareDst.java");
    myShepherd.beginDBTransaction();
    try{
	//numLoci = NIBnumLoci.posIntValue ();
	//numIndividuals = NIBgenoTypes.countValues () / (numLoci * 2 + (namesPresent ? 1 : 0));
	//if (namesPresent) individualNames = new String[numIndividuals];
	genoTypes = new int[numIndividuals][];
	//HashTokenizer htok = new HashTokenizer (NIBgenoTypes.getText(), "-");
	for (int i=0; i < numIndividuals; ++i){
	    
	    MarkedIndividual indy=myShepherd.getMarkedIndividual(individualNames[i]);
	  
	  
	    genoTypes[i] = new int[numLoci * 2];
	    //if (namesPresent)
	    //  individualNames[i] = htok.nextToken();
	    int j=0;
	    while(j < numAlleles){
	      
	      String thisLocus=lociNames[(j/2)];
	      ArrayList<Integer> vals=indy.getAlleleValuesForLocus(thisLocus);
	      
	     
	      
	      if((vals.size()>0)&&(vals.get(0)!=null)){
	        if (! hash.containsKey (vals.get(0))) {hash.put (vals.get(0), new Integer (maxCode ++));}
	        //return ((Integer) hash.get (s) ).intValue ();
	        genoTypes[i][j] = ((Integer)hash.get(vals.get(0))).intValue();
	      }
	      else{
	        genoTypes[i][j] = ((Integer)hash.get("empty")).intValue();
	      }
	      
	       if((vals.size()>1)&&(vals.get(1)!=null)){
	          if (! hash.containsKey (vals.get(1))) {hash.put (vals.get(1), new Integer (maxCode ++));}
	          //return ((Integer) hash.get (s) ).intValue ();
	          genoTypes[i][j+1] = ((Integer)hash.get(vals.get(1))).intValue();
	       }
	       else{
	            genoTypes[i][j+1] = ((Integer)hash.get("empty")).intValue();
	       }
	      
	      j=j+2;
	    }
	}
 }
 catch (Exception e){
      //NOBoutput.setText (e.getMessage ());
      e.printStackTrace();
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      myShepherd=null;
      return e.getLocalizedMessage();
    }
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    
    int i, j;
    //boolean noDivide = cbNoDivide.getState ();
    //boolean lowTriangle = cbLowerTriangle.getState ();
    int intDist[][] = null;
    double Dist[][] = null;

    if (lowTriangle)
      {
	if (noDivide)
	  {
	    intDist = new int[numIndividuals][];
	    for (i=0; i < numIndividuals; ++i)
	      intDist[i] = new int[i+1];
	  }
	else
	  {
	    Dist = new double[numIndividuals][];
	    for (i=0; i < numIndividuals; ++i)
	      Dist[i] = new double[i+1];
	  }
      }
    else
      {
	if (noDivide)
	  intDist = new int[numIndividuals][numIndividuals];
	else
	  Dist = new double[numIndividuals][numIndividuals];
      }

    //ProgressBar pb = new ProgressBar (this, "Computing shared allele distance:  ");
    //pb.SetTotalCount (numIndividuals);

    if (!noDivide)
      {
	for (i=0; i < numIndividuals; ++i)
	  {
	    Dist [i][i] = 0;
	    for (j = 0; j < i; ++j )
	      {
		Dist [i][j] = Math.floor (1000 * DistSAD.calculate (genoTypes[i], genoTypes[j], ploidy)) / 1000;
		if (!lowTriangle)
		  Dist [j][i] = Dist [i][j];
	      }
	    //pb.ShowProgress ();
	  }
      }
    else
      {
	for (i=0; i < numIndividuals; ++i)
	  {
	    intDist [i][i] = 0;
	    for (j = 0; j < i; ++j )
	      {
		intDist [i][j] = DistSAD.countUnsharedAlleles (genoTypes[i], genoTypes[j], ploidy);
		if (!lowTriangle)
		  intDist [j][i] = intDist [i][j];
	      }
	    //pb.ShowProgress ();
	  }
      }

    //pb.finalize ();

    //output = output.concat ("There are " + numIndividuals + " individuals and " + numLoci + " loci.\n");
    if (noDivide)
      {

      //output = output.concat (  "\nUnShared Allele Count\n" +   "======================\n" );
      }
    else
      {
      //output = output.concat (  "\nShared Allele Distance\n"+   "======================\n" );
      }	

    //pb = new ProgressBar (this, "Formatting output:  ");
    //pb.SetTotalCount (numIndividuals);
    if (namesPresent)
      {
	StringBuffer s = new StringBuffer (numIndividuals * numIndividuals * (lowTriangle ? 2 : 4));
	
	for (i = 0; i < numIndividuals; ++i)
	  {
	    s.append (individualNames[i]) .append(DELIM);
	    int top = lowTriangle ? i : numIndividuals - 1;
	    if (noDivide)
	      {
		s.append (intDist[i][0]);
		for (j=1; j <= top; ++j)
		  s.append (DELIM) .append (intDist[i][j]);
	      }
	    else
	      {
		s.append (Dist[i][0]);
		for (j=1; j <= top; ++j)
		  s.append (DELIM) .append (Dist[i][j]);
	      }
	    s.append (EOL);
	  }
	output = output.concat (s.toString());
      }
    else
      {
	if (noDivide)
	  output = output.concat (MyFuns.MatrixToString (intDist, 6, DELIM, EOL, lowTriangle));
	else
	  output = output.concat (MyFuns.MatrixToString (Dist, 6, 1, DELIM, EOL, lowTriangle));
      }

    Dist = null;
    //pb.finalize ();

    /*
    try
      {
	NOBoutput.setText (output);
      }
    catch (Exception e)
      {
	showStatus (e.getMessage ());
      }
      */
    return output;
  }

  public String[][] getParameterInfo ()
  {
    String[][] info = 
    {
      {"numLoci", "integer", "number of loci"},
      {"genoTypes", "list of integers separated by spaces and/or commas", "for each individual, the list of its alleles, two per locus"},
    };
    return info;
  }

  public void init()
  {
    // TODO: Place additional initialization code here
    
    /*
    String s;
		
    s = this.getParameter ("numLoci");
    if (s != null)
      NIBnumLoci.setText (s);
		
    s = this.getParameter ("genoTypes");
    if (s != null)
      NIBgenoTypes.setText (s.replace (',', '\n'));
	  */
  }

}
