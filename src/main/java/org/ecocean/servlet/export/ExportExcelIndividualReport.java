package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;

import org.ecocean.*;
import org.ecocean.genetics.*;
import org.ecocean.social.Relationship;
import org.ecocean.servlet.ServletUtilities;

import javax.jdo.*;

import java.lang.reflect.Method;
import java.lang.StringBuffer;

import jxl.write.*;
import jxl.Workbook;


public class ExportExcelIndividualReport extends HttpServlet{

  private static final int BYTES_DOWNLOAD = 1024;


  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }



  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{

    //set the response

    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    //String query = request.getParameter("query");
    //String[] headers = request.getParameterValues("headers");
    //String[] columns = request.getParameterValues("columns");
    //Collection c = (Collection) myShepherd.getPM().newQuery(query).execute();
    //Vector v = new Vector(c);
    //Class cls = v.get(0).getClass();

    String filename = request.getParameter("filename");
    if (filename == null) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        filename = "export-" + df.format(cal.getTime()) + ".xls";
    }
    String sheetname = request.getParameter("sheetname");
    if (sheetname == null) sheetname = "Export";

    File excelFile = new File("/tmp/" + filename);
    FileOutputStream exfos = new FileOutputStream(excelFile);
    OutputStreamWriter exout = new OutputStreamWriter(exfos);
    //WritableCellFormat floatFormat = new WritableCellFormat(NumberFormats.FLOAT);
    //WritableCellFormat integerFormat = new WritableCellFormat(NumberFormats.INTEGER);
    WritableWorkbook workbook = Workbook.createWorkbook(excelFile);
    WritableSheet sheet = workbook.createSheet(sheetname, 0);

    int sheetRow = 0;

    String[] headers = new String[] {"ID", "Date", "Area", "GPS x", "GPS y", "sex", "age first sighted", "status", "class", "foal", "group size", "habitat", "bearing", "distance", "image file", "enc ID"};
    int col = 0;
    for (int i = 0 ; i < headers.length ; i++) {
       Label l = new Label(col, sheetRow, headers[i]);
        try {
            sheet.addCell(l);
        } catch (Exception addex) {
            System.out.println("exception adding cell: " + addex.toString());
        }
        col++;
    }
    sheetRow++;


    Iterator all = myShepherd.getAllEncountersNoQuery();
    Encounter enc = null;
    while (all.hasNext()) {
        enc = (Encounter) all.next();
        if (!enc.hasMarkedIndividual()) continue;
        MarkedIndividual indiv = myShepherd.getMarkedIndividual(enc.getIndividualID());

        Occurrence occ = null;
        if (enc.getOccurrenceID() != null) occ = myShepherd.getOccurrence(enc.getOccurrenceID());

        //this is the date of the encounter, to compute age at time of encounter
        Calendar encCal = null;
        if (enc.getYear() > 0) {
            encCal = Calendar.getInstance();
            encCal.clear();
            encCal.set(Calendar.MILLISECOND, 0);
            encCal.set(Calendar.YEAR, enc.getYear());
            if (enc.getMonth() > 0) encCal.set(Calendar.MONTH, enc.getMonth() - 1);
            if (enc.getDay() > 0) encCal.set(Calendar.DAY_OF_MONTH, enc.getDay());
        }

/*  oops... snuck in from lewa-master maybe?  FIXME later and clean this up!
        Double ageFirstSighted = indiv.getAgeAtFirstSighting();
        String ageFirstSightedString = "";
        if (ageFirstSighted!=null) {
          ageFirstSightedString = String.valueOf(ageFirstSighted);
        }
*/


        Vector<Label> cols =  new Vector<Label>();
        cols.add(new Label(0, sheetRow, enc.getIndividualID()));
        cols.add(new Label(1, sheetRow, enc.getShortDate()));
        cols.add(new Label(2, sheetRow, enc.getLocationCode()));
        cols.add(new Label(3, sheetRow, enc.getDecimalLatitude()));
        cols.add(new Label(4, sheetRow, enc.getDecimalLongitude()));
        cols.add(new Label(5, sheetRow, enc.getSex()));
        //cols.add(new Label(6, sheetRow, ageFirstSightedString));
        cols.add(new Label(7, sheetRow, (indiv.isDeceased() ? "dead" : "alive") ));
        //cols.add(new Label(6, sheetRow, enc.getLifeStage()));
        cols.add(new Label(8, sheetRow, enc.getZebraClass()));

        String foals = "";
        List<Relationship> rels = indiv.getAllRelationships(myShepherd);
        ///TODO should we really look for *co-occurring* offspring for this Encounter?
//System.out.println(indiv.getIndividualID() + ": ");
        for (Relationship rel : rels) {
            if ("familial".equals(rel.getType())) {
                if ("calf".equals(rel.getMarkedIndividualRole1()) && indiv.getIndividualID().equals(rel.getMarkedIndividualName2())) {
                    foals += rel.getMarkedIndividualName1() + " ";
                } else if ("calf".equals(rel.getMarkedIndividualRole2()) && indiv.getIndividualID().equals(rel.getMarkedIndividualName1())) {
                    foals += rel.getMarkedIndividualName2() + " ";
                }
            }
        }
        cols.add(new Label(9, sheetRow, foals));

        if (occ == null) {
            cols.add(new Label(10, sheetRow, "-"));
            cols.add(new Label(11, sheetRow, "-"));
            cols.add(new Label(12, sheetRow, "-"));
            cols.add(new Label(13, sheetRow, "-"));
        } else {
            Integer gs = occ.getGroupSize();
            if (gs == null) {
                cols.add(new Label(10, sheetRow, "-"));
            } else {
                cols.add(new Label(10, sheetRow, gs.toString()));
            }
						String ht = occ.getHabitat();
            if (ht == null) {
                cols.add(new Label(11, sheetRow, "-"));
            } else {
                cols.add(new Label(11, sheetRow, ht));
            }
            Double br = occ.getBearing();
            if (br == null) {
                cols.add(new Label(12, sheetRow, "-"));
            } else {
                cols.add(new Label(12, sheetRow, br.toString()));
            }
            Double ds = occ.getDistance();
            if (ds == null) {
                cols.add(new Label(13, sheetRow, "-"));
            } else {
                cols.add(new Label(13, sheetRow, ds.toString()));
            }
        }

        cols.add(new Label(14, sheetRow, enc.getImageOriginalName()));
        cols.add(new Label(15, sheetRow, enc.getCatalogNumber()));

        for (Label l : cols) {
            try {
                sheet.addCell(l);
            } catch (Exception addex) {
                System.out.println("exception adding cell: " + addex.toString());
            }
        }
        sheetRow++;
    }

    try {
        workbook.write();
        workbook.close();
    } catch (Exception wex) {
        System.out.println("exception writing excel: " + wex.toString());
    }

    //response.setContentType("application/vnd.ms-excel");
    response.setContentType("application/octet-stream");
    response.setHeader("Content-Transfer-Encoding", "binary");
    response.setHeader("Content-Disposition", "filename=" + filename);

    InputStream is = new FileInputStream(excelFile);
    OutputStream os = response.getOutputStream();
    byte[] buf = new byte[1000];
    for (int n = is.read(buf) ; n > -1 ; n = is.read(buf)) {
        os.write(buf, 0, n);
    }
    os.flush();
    os.close();
    is.close();


  }

}
