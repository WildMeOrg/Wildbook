package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;
import org.ecocean.servlet.ServletUtilities;

/*
 * Originally created to support allele export for kinship analysis with the University of Chicago's Kinalyzer tool (now gone), this export format has
 * been modified to be a generic inividual genetics tool, allowing for export of individual ID, haplotype, genetic sex, and named allele pairs for any
 * species.
 *
 */
public class KinalyzerExport extends HttpServlet {
    private static final int BYTES_DOWNLOAD = 1024;

    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        // set the response
        response.setContentType("text/csv");
        // PrintWriter out = response.getWriter();

        // get our Shepherd
        String context = "context0";
        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IndividualSearchGeneticsExport");

        // setup data dir
        String rootWebappPath = getServletContext().getRealPath("/");
        File webappsDir = new File(rootWebappPath).getParentFile();
        File shepherdDataDir = new File(webappsDir,
            CommonConfiguration.getDataDirectoryName(context));
        if (!shepherdDataDir.exists()) { shepherdDataDir.mkdirs(); }
        File encountersDir = new File(shepherdDataDir.getAbsolutePath() + "/encounters");
        if (!encountersDir.exists()) { encountersDir.mkdirs(); }
        String kinFilename = "individualSearch_genetics_export_" + request.getRemoteUser() + ".csv";
        File kinFile = new File(encountersDir.getAbsolutePath() + "/" + kinFilename);

        try {
            // set up the output stream
            FileOutputStream fos = new FileOutputStream(kinFile);
            OutputStreamWriter outp = new OutputStreamWriter(fos);

            // set up the vector for matching encounters
            Vector query2Individuals = new Vector();

            // kick off the transaction
            myShepherd.beginDBTransaction();

            // start the query and get the results
            String order = "";
            if (request != null) {
                MarkedIndividualQueryResult queryResult2 = IndividualQueryProcessor.processQuery(
                    myShepherd, request, order);
                query2Individuals = queryResult2.getResult();
                int numSearch2Individuals = query2Individuals.size();

                // now let's start writing header row
                String headerRow = "Individual ID, Individual Default Name, Haplotype, Genetic Sex";

                // Lines 2+: write the loci
                // let's calculate Fst for each of the loci
                // iterate through the loci
                List<String> loci = myShepherd.getAllLoci();
                int numLoci = loci.size();
                ArrayList<String> allowedLoci = new ArrayList<String>();
                // filter to only loci of query individuals
                for (int i = 0; i < numSearch2Individuals; i++) {
                    MarkedIndividual indie = (MarkedIndividual)query2Individuals.get(i);
                    for (int r = 0; r < numLoci; r++) {
                        if (indie.hasLocus(loci.get(r))) {
                            if (!allowedLoci.contains(loci.get(r))) {
                                allowedLoci.add(loci.get(r));
                            }
                        }
                    }
                }
                int numAllowedLoci = allowedLoci.size();
                // let's add loci to the header row
                for (int r = 0; r < numAllowedLoci; r++) {
                    String locus = allowedLoci.get(r);
                    headerRow += ", " + locus + " Allele1";
                    headerRow += ", " + locus + " Allele2";
                }
                // write out header row
                outp.write(headerRow + "\r\n");
                // now write out POP2 for search2
                for (int i = 0; i < numSearch2Individuals; i++) {
                    MarkedIndividual indie = (MarkedIndividual)query2Individuals.get(i);
                    boolean hasValues = false;

                    // add individual UUID
                    String lociString = indie.getIndividualID() + ",";

                    // add individual default name
                    lociString += indie.getDefaultName() + ",";

                    // add individual haplotype
                    String haploString = "";
                    if (indie.getHaplotype() != null) haploString = indie.getHaplotype();
                    lociString += haploString + ",";

                    // add individualgenetic sex
                    String sexString = "";
                    if (indie.getGeneticSex() != null) sexString = indie.getGeneticSex();
                    lociString += sexString + ",";
                    for (int r = 0; r < numAllowedLoci; r++) {
                        String locus = allowedLoci.get(r);
                        ArrayList<Integer> values = indie.getAlleleValuesForLocus(locus);
                        if (indie.getAlleleValuesForLocus(locus).size() == 2) {
                            lociString += (values.get(0) + ",");
                            lociString += (values.get(1) + ",");
                            hasValues = true;
                        } else if (indie.getAlleleValuesForLocus(locus).size() == 1) {
                            lociString += (values.get(0) + "," + values.get(0) + ",");
                            hasValues = true;
                        }
                        // else{lociString+="-1,-1,";}
                        else { lociString += ",,"; }
                    }
                    int length = lociString.length();
                    if (hasValues) outp.write(lociString.substring(0, (length - 1)) + "\r\n");
                }
            }
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();

            outp.close();
            outp = null;

            // now write out the file
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment;filename=" + kinFilename);
            ServletContext ctx = getServletContext();
            // InputStream is = ctx.getResourceAsStream("/encounters/"+gisFilename);
            InputStream is = new FileInputStream(kinFile);
            int read = 0;
            byte[] bytes = new byte[BYTES_DOWNLOAD];
            OutputStream os = response.getOutputStream();
            while ((read = is.read(bytes)) != -1) {
                os.write(bytes, 0, read);
            }
            os.flush();
            os.close();
        } catch (Exception e) {
            // out.println("<p><strong>Error encountered</strong></p>");
            // out.println("<p>Please let the webmaster know you encountered an error at: KinalyzerExport servlet.</p>");
            e.printStackTrace();
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
        // out.close();
        // out=null;
    }
}
