package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.CommonConfiguration;
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;
//import org.ecocean.servlet.ServletUtilities;
import org.datanucleus.api.rest.orgjson.JSONException;
import org.datanucleus.api.rest.orgjson.JSONObject;

import javax.jdo.*;

import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DownloadQueriedPhotos extends HttpServlet{

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public static String OUTPUT_ZIP = "olive_ridleys.zip";


    public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        
        Shepherd myShepherd = new Shepherd("context0");
        myShepherd.beginDBTransaction();
        
        
        String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
        File zipfile = new File(System.getProperty("catalina.base")+"/webapps/wildbook_data_dir/"+OUTPUT_ZIP);
        byte[] buffer = new byte[2048];
        FileOutputStream fos = new FileOutputStream(zipfile);
        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(fos));

        System.out.println("URLLOC? "+urlLoc);

        Long twoYears=new Long("63072000000");
        long currentDate=System.currentTimeMillis()-twoYears.longValue();
        String filter="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && (enc.submitterOrganization == 'Olive Ridley Project') && (enc.specificEpithet == 'mydas') && (enc.genus == 'Chelonia') VARIABLES org.ecocean.Encounter enc";
        System.out.println("Trying to extract exemplar images that fit this filter = "+filter);
        Query query=myShepherd.getPM().newQuery(filter);
        query.setOrdering("numberEncounters descending");
        query.setRange(0, 500);
        Collection c = (Collection) (query.execute());
        ArrayList<MarkedIndividual> candidates = new ArrayList<MarkedIndividual>(c);
        query.closeAll();
        
        System.out.println("Extraction candidates: "+candidates.size());
        int count = 0;
        for (MarkedIndividual indy : candidates ) {
            String url = null;
            String id = null;
            try {
                JSONObject asset = indy.getExemplarImage(myShepherd, request);
                url = asset.get("url").toString();
                url = url.split("wildbook_data_dir")[1];
                id = indy.getName();
            } catch (JSONException jsone) {
                jsone.printStackTrace();
            }
            
            if (id!=null&&url!=null) {
                count++;
                try {
                    File file = new File(System.getProperty("catalina.base")+"/webapps/wildbook_data_dir"+url);
                    FileInputStream in = new FileInputStream(file.getPath());
                    BufferedInputStream bis = new BufferedInputStream(in);
                    out.putNextEntry(new ZipEntry(id+".jpg"));
                    System.out.println("#"+count+" "+file.getPath());
                    int len;
                    while((len = bis.read(buffer)) >0) {
                        out.write(buffer, 0, len);
                    }
                    out.closeEntry();
                    in.close();
                    bis.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }  
        try {
            out.flush();
            out.close();
            fos.flush();
            fos.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        
        response.setContentType("application/zip");
        //response.setContentLength((int) zipfile.length());
        response.setHeader("Content-Disposition", String.format("attachment; filename=\""+OUTPUT_ZIP+"\""));
        int bytesRead = -1;
        try {
            System.out.println("Zipfile path : "+zipfile.getPath());
            FileInputStream is = new FileInputStream(zipfile.getPath());
            BufferedInputStream bis = new BufferedInputStream(is);
            OutputStream os = response.getOutputStream();
            while ((bytesRead = bis.read(buffer)) >0) {
                os.write(buffer, 0, bytesRead);
            } 
            bis.close();
            is.close();
            os.flush();
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            myShepherd.closeDBTransaction();
        }
    }

}
