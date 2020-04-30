package org.ecocean.importutils;

import org.apache.poi.ss.usermodel.Row;

import java.util.List;

import org.ecocean.servlet.importer.StandardImport;

public class RowFeedback {

    private CellFeedback[] cells;
    private int num;
    private boolean committing = false;
    private List<Integer> skipCols;  

    //String checkingInheritance = uploadDirectory;

    public RowFeedback(Row row, int num, boolean committing, List<Integer> skipCols) {
      this.num=num;
      this.committing = committing;
      this.skipCols = skipCols;
      this.cells = new CellFeedback[row.getLastCellNum()];
    }

    public String toString() {
      StringBuffer str = new StringBuffer();
      str.append("<tr>");
      str.append("<td>"+num+"</td>");

      for (int i=0;i<cells.length; i++) {
        if (skipCols.contains(i)) {
          System.out.println("skipping this col for feedback, index "+i+" was present in skipCols");
          continue; 
        }
        CellFeedback cell = cells[i];
        if (cell==null) str.append(StandardImport.nullCellHtml());
        else str.append(cell.html());
      }
      str.append("</tr>"); 
      return str.toString();
    }

    public void logParseValue(int colNum, Object value, Row row) {
      if (!committing) {
        System.out.println("RowFeedback.logParseValue on an object: "+value+" with colNum "+colNum);
        if (value==null) { // a tad experimental here. this means we don't have to check the parseSuccess in each getWhatever method
          System.out.println("RowFeedback.logParseValue on a NULL OBJECT: trying to recover a value, or log empty");
          String valueString = StandardImport.getCellValueAsString(row, colNum);
          if (valueString==null||"".equals(valueString.trim())) {
            logParseNoValue(colNum);
            return;
          } 
          logParseError(colNum, valueString, row);
          return;
        } 
        this.cells[colNum] = new CellFeedback(value, true, false);
      }
    }

    public void logParseError(int colNum, Object value, Row row) {
      try {
        if (!committing) {
          if (this.cells[colNum]!=null) {
            CellFeedback cellFeedback = this.cells[colNum];
            System.out.println("Setting ERROR value on OLD CellFeedback for col "+colNum+" val "+String.valueOf(value)+" row "+row.getRowNum());
            // I think we can assume a BLANK or NULL cell doesn't need to get overwritten with an error. 
            if (!cellFeedback.isBlank()) {
              this.cells[colNum].setSuccess(false);
              //TODO replace this universal NOT FOUND for an overwrite with something specific.
              this.cells[colNum].setValueString(value+" NOT FOUND");
            }
          } else {
            System.out.println("Setting ERROR value on NEW CellFeedback for col "+colNum+" val "+String.valueOf(value)+" row "+row.getRowNum());
            this.cells[colNum] = new CellFeedback(value, false, false);
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    public void logParseNoValue(int colNum) {
      if (!committing&&colNum<this.cells.length) {
        this.cells[colNum] = new CellFeedback(null, true, true);
      }
    }

    public int getColNum() {
        return this.num;
    }

  }

//   public String getStringNoLog(Row row, int i) {
//     String str = null;
//     try {
//       str = row.getCell(i).getStringCellValue().trim();
//       if (str.equals("")) return null;
//     }
//     catch (Exception e) {}
//     return str;
//   }


//   // cannot put this inside CellFeedback bc java inner classes are not allowed static methods or vars (this is stupid).
//   static String nullCellHtml() {
//     return "<td class=\"cellFeedback null\" title=\"The importer was unable to retrieve this cell, or it did not exist. This is possible if it is a duplicate column, it relies on another column, or only some rows contain the cell. You may proceed if this cell OK to ignore.\"><span></span></td>";
//   }

// }