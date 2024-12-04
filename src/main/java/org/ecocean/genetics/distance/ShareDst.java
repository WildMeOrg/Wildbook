package org.ecocean.genetics.distance;

import java.util.ArrayList;
import java.util.Hashtable;

import org.ecocean.*;

public class ShareDst {
    public final static int ploidy = 2; // hard wired, for now
    public ShareDst(String context) {

    }

    /*
     *
     * @parameter numLoci The number of loci in the study.
     * @parameter individualNames An array of MarkedIndividual.individualID values, with the first one being the individual of interest for finding
     * kin
     * @parameter noDivide Whether to divide by 2r- for smaller output
     * @parameter lowTriangle Only show lower triangle for smaller output
     */
    public static String getDistanceOuput(String individualNames[], String lociNames[],
        boolean noDivide, boolean lowTriangle, String EOL, String DELIM, String context) {
        String output = "";

        int numIndividuals = individualNames.length;
        int numLoci = lociNames.length;
        int numAlleles = numLoci * 2;
        int genoTypes[][];
        Hashtable hash = new Hashtable();
        int maxCode = 0;

        hash.put("empty", new Integer(maxCode++));
        boolean namesPresent = true;
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("ShareDst.java");
        myShepherd.beginDBTransaction();
        try {
            genoTypes = new int[numIndividuals][];
            for (int i = 0; i < numIndividuals; ++i) {
                MarkedIndividual indy = myShepherd.getMarkedIndividual(individualNames[i]);

                genoTypes[i] = new int[numLoci * 2];
                int j = 0;
                while (j < numAlleles) {
                    String thisLocus = lociNames[(j / 2)];
                    ArrayList<Integer> vals = indy.getAlleleValuesForLocus(thisLocus);
                    if ((vals.size() > 0) && (vals.get(0) != null)) {
                        if (!hash.containsKey(vals.get(0))) {
                            hash.put(vals.get(0), new Integer(maxCode++));
                        }
                        genoTypes[i][j] = ((Integer)hash.get(vals.get(0))).intValue();
                    } else {
                        genoTypes[i][j] = ((Integer)hash.get("empty")).intValue();
                    }
                    if ((vals.size() > 1) && (vals.get(1) != null)) {
                        if (!hash.containsKey(vals.get(1))) {
                            hash.put(vals.get(1), new Integer(maxCode++));
                        }
                        genoTypes[i][j + 1] = ((Integer)hash.get(vals.get(1))).intValue();
                    } else {
                        genoTypes[i][j + 1] = ((Integer)hash.get("empty")).intValue();
                    }
                    j = j + 2;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            myShepherd = null;
            return e.getLocalizedMessage();
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();

        int i, j;
        int intDist[][] = null;
        double Dist[][] = null;
        if (lowTriangle) {
            if (noDivide) {
                intDist = new int[numIndividuals][];
                for (i = 0; i < numIndividuals; ++i)
                    intDist[i] = new int[i + 1];
            } else {
                Dist = new double[numIndividuals][];
                for (i = 0; i < numIndividuals; ++i)
                    Dist[i] = new double[i + 1];
            }
        } else {
            if (noDivide)
                intDist = new int[numIndividuals][numIndividuals];
            else
                Dist = new double[numIndividuals][numIndividuals];
        }
        if (!noDivide) {
            for (i = 0; i < numIndividuals; ++i) {
                Dist[i][i] = 0;
                for (j = 0; j < i; ++j) {
                    Dist[i][j] = Math.floor(1000 * DistSAD.calculate(genoTypes[i], genoTypes[j],
                        ploidy)) / 1000;
                    if (!lowTriangle)
                        Dist[j][i] = Dist[i][j];
                }
            }
        } else {
            for (i = 0; i < numIndividuals; ++i) {
                intDist[i][i] = 0;
                for (j = 0; j < i; ++j) {
                    intDist[i][j] = DistSAD.countUnsharedAlleles(genoTypes[i], genoTypes[j],
                        ploidy);
                    if (!lowTriangle)
                        intDist[j][i] = intDist[i][j];
                }
            }
        }

        if (noDivide) {
        } else {
        }
        if (namesPresent) {
            StringBuffer s = new StringBuffer(numIndividuals * numIndividuals *
                (lowTriangle ? 2 : 4));
            for (i = 0; i < numIndividuals; ++i) {
                s.append(individualNames[i]).append(DELIM);
                int top = lowTriangle ? i : numIndividuals - 1;
                if (noDivide) {
                    s.append(intDist[i][0]);
                    for (j = 1; j <= top; ++j)
                        s.append(DELIM).append(intDist[i][j]);
                } else {
                    s.append(Dist[i][0]);
                    for (j = 1; j <= top; ++j)
                        s.append(DELIM).append(Dist[i][j]);
                }
                s.append(EOL);
            }
            output = output.concat(s.toString());
        } else {
            if (noDivide)
                output = output.concat(MyFuns.MatrixToString(intDist, 6, DELIM, EOL, lowTriangle));
            else
                output = output.concat(MyFuns.MatrixToString(Dist, 6, 1, DELIM, EOL, lowTriangle));
        }
        Dist = null;

        return output;
    }

    public String[][] getParameterInfo() {
        String[][] info = {
            { "numLoci", "integer", "number of loci" }, {
                "genoTypes", "list of integers separated by spaces and/or commas",
                    "for each individual, the list of its alleles, two per locus"
            },
        };

        return info;
    }

    public void init() {

    }
}
