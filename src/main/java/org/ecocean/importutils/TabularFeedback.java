package org.ecocean.importutils;

import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;

import java.io.PrintWriter;

public class TabularFeedback {

    PrintWriter out;

    boolean committing = false;
    //Set<String> unusedColumns;
    //Set<String> missingColumns; // columns we look for but don't find
    List<String> missingPhotos = new ArrayList<String>();
    List<String> foundPhotos = new ArrayList<String>();
    List<Integer> skipCols = new ArrayList<>();

    String[] colNames;

    RowFeedback currentRow;

    public TabularFeedback(String[] colNames, boolean committing, PrintWriter out, List<Integer> skipCols) {
      this.committing = committing;  
      this.colNames = colNames;
      this.out = out;
      this.skipCols = skipCols;
      missingPhotos = new ArrayList<String>();
      foundPhotos = new ArrayList<String>();
      currentRow=null; // must be manually initialized during row loop with startRow
    }

    public void startRow(Row row, int i) {
      if (!committing) {
        currentRow = new RowFeedback(row, i, committing, skipCols);
        System.out.println("StartRow called for i="+i);
      }
    }

    public void addMissingPhoto(String localPath) {
      missingPhotos.add(localPath);
    }

    public void addFoundPhoto(String localPath) {
      foundPhotos.add(localPath);
    }

    public void printMissingPhotos() {
      //if (!isUserUpload) {
        out.println("<h2><em>Missing photos</em>("+missingPhotos.size()+"):</h2><ul>");
        for (String photo: missingPhotos) {
          out.println("<li>"+photo+"</li>");
        }
        out.println("</ul>");
      //} 
    }

    public void printRow() {
      System.out.println("Starting to printRow");
      if (!committing) out.println(currentRow);
      //System.out.println(currentRow);
      System.out.println("Done with printRow");
    }
  
    public void printFoundPhotos() {
        out.println("<h2><em>Found photos</em>("+foundPhotos.size()+"):</h2><ul>");
        for (String photo: foundPhotos) {
            out.println("<li>"+photo+"</li>");
        }
        out.println("</ul>");
    }

    public void printStartTable() {
      out.println("<div class=\"tableFeedbackWrapper\"><table class=\"tableFeedback\">");
      out.println("<tr class=\"headerRow\"><th class=\"rotate\"><div><span><span></div></th>"); // empty header cell for row # column
      boolean isNull = (colNames==null);
      System.out.println("colNames isNull "+isNull);
      System.out.println("starting to print table. num colNames="+colNames.length+" and the array itself = "+colNames);
      for (int i=0;i<colNames.length;i++) {
        if (skipCols.contains(i)) continue; 
        out.println("<th class=\"rotate\"><div><span class=\"tableFeedbackColumnHeader\">"+colNames[i]+"</span></div></th>");
      }
      System.out.println("done printing start table");
      out.println("</tr>");
    }
    public void printEndTable() {
      out.println("</table></div>");
    }

    public void logParseValue(int colNum, Object value, Row row) {
      if (!committing) {
        System.out.println("TabularFeedback.logParseValue called on object: "+value+" and colNum "+colNum);
        if (value==null||String.valueOf(value)=="") {
          this.currentRow.logParseNoValue(colNum);
        }
        this.currentRow.logParseValue(colNum, value, row);
      }
    }

    public void logParseError(int colNum, Object value, Row row) {
      if (!committing) {
        this.currentRow.logParseError(colNum, value, row);
      }
    }

    public void logParseNoValue(int colNum) {
      if (!committing) {
        this.currentRow.logParseNoValue(colNum);
      }
    }

    public String toString() {
      if (!committing) {
        return "Tabular feedback with "+colNames.length+" columns, on row "+currentRow.getColNum();
      }
      return "";
    }

    public String[] getColNames() {
         return colNames;
    }

  }