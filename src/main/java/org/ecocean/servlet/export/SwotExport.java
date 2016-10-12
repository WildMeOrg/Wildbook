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


public class SwotExport extends HttpServlet{

  private static final int BYTES_DOWNLOAD = 1024;


  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }

  private String doubleToString(Double dbl) {
    if (dbl == null) return ("");
    return dbl.toString();
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{

    //set the response

    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    // String query = request.getParameter("query");
    // String[] headers = request.getParameterValues("headers");
    // String[] columns = request.getParameterValues("columns");
    // Collection c = (Collection) myShepherd.getPM().newQuery(query).execute();
    // Vector v = new Vector(c);
    // Class cls = v.get(0).getClass();

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

    String[] headers = new String[] {"ID", "Name", "LocationID", "Location Note", "Latitude", "Longitude"};
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


    Iterator all = myShepherd.getAllNests();
    Nest nest = null;
    while (all.hasNext()) {
        nest = (Nest) all.next();

        System.out.println("SwotExport: starting to export Nest "+nest.getID());


        //this is the date of the Nest, to compute age at time of Nest
        Calendar nestCal = null;
        // if (nest.getYear() > 0) {
        //     nestCal = Calendar.getInstance();
        //     nestCal.clear();
        //     nestCal.set(Calendar.MILLISECOND, 0);
        //     nestCal.set(Calendar.YEAR, nest.getYear());
        //     if (nest.getMonth() > 0) nestCal.set(Calendar.MONTH, nest.getMonth() - 1);
        //     if (nest.getDay() > 0) nestCal.set(Calendar.DAY_OF_MONTH, nest.getDay());
        // }
        Double age = null;
        //if (nestCal != null) age = indiv.calculatedAge(nestCal);

        Vector<Label> cols =  new Vector<Label>();
        cols.add(new Label(0, sheetRow, nest.getID()));
        cols.add(new Label(1, sheetRow, nest.getName()));
        cols.add(new Label(2, sheetRow, nest.getLocationID()));
        cols.add(new Label(3, sheetRow, nest.getLocationNote()));
        cols.add(new Label(4, sheetRow, doubleToString(nest.getLatitude())));
        cols.add(new Label(5, sheetRow, doubleToString(nest.getLongitude())));
        //cols.add(new Label(6, sheetRow, nest.getLifeStage()));
        //cols.add(new Label(7, sheetRow, nest.getZebraClass()));

        /*
        String foals = "";
        List<Relationship> rels = indiv.getAllRelationships(myShepherd);
        ///TODO should we really look for *co-occurring* offspring for this Nest?
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
        cols.add(new Label(8, sheetRow, foals));

        if (occ == null) {
            cols.add(new Label(9, sheetRow, "-"));
         }// else {
        //     Integer gs = occ.getGroupSize();
        //     if (gs == null) {
        //         cols.add(new Label(9, sheetRow, "-"));
        //     } else {
        //         cols.add(new Label(9, sheetRow, gs.toString()));
        //     }
        // }

        //cols.add(new Label(10, sheetRow, nest.getImageOriginalName()));
        */

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
    response.setHeader("Content-Transfer-nestoding", "binary");
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
