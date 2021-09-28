package org.ecocean.servlet;

import org.ecocean.resumableupload.UploadServlet;

import java.io.*;

import java.io.File;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.json.JSONObject;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ValidateImportExcel extends HttpServlet {
    

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }
    
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        
        String uploadDirectory = UploadServlet.getUploadDir(request);

        String filename = request.getParameter("filename");

        if (filename!=null&&filename.length()>0) {
            filename = uploadDirectory+"/"+filename;
        }

        File dataFile = new File(filename);
        boolean dataFound = dataFile.exists();
        PrintWriter out = response.getWriter();
        JSONObject res = new JSONObject();
        res.put("success", false);
        res.put("message", "Error validating Excel sheet.");

        if (dataFound) {
            Workbook wb = null;
            try {
                wb = WorkbookFactory.create(dataFile);

                if (wb.getNumberOfSheets()>0&&wb.getSheetAt(0)!=null) {

                    Sheet sheet = wb.getSheetAt(0);

                    if (sheet.getLastRowNum()>=1000) {
                        res.put("success", false);
                        res.put("message", "This sheet exceeds the maximum of 1000 rows per import.");
                    } else {
                        res.put("success", true);
                        res.put("message", "Excel sheet valid!");
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (wb!=null) {
                    try {
                        wb.close();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            }

        }
        out.println(res);
        out.close();
    }

}


