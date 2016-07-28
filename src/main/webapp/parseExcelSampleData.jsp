<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.servlet.ServletUtilities,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException, org.apache.poi.hssf.usermodel.*, org.apache.poi.poifs.filesystem.POIFSFileSystem, org.apache.poi.ss.usermodel.Cell, org.apache.poi.ss.usermodel.Row, org.apache.poi.xssf.usermodel.XSSFSheet, org.apache.poi.xssf.usermodel.XSSFWorkbook,org.apache.commons.lang.StringUtils;


import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.PrefixFileFilter;
"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
out.println("\n\n"+new java.util.Date().toString()+": Starting to parse excel.");

%>

<html>
<head>
<title>Fix Some Fields</title>

<style type="text/css">
  body {
    font-family: "Lucida Console", Monaco, monospace;
    font-size: 12pt;
  }

</style>


</head>


<body>
<%

myShepherd.beginDBTransaction();

//build queries

int numFixes=0;
String maSetId = "603fd5ce-dbbf-4025-ba25-670219d043b6";

String defaultFileName = "/home/ubuntu/documents/sample_data.xls";

try {

  String fileName = ServletUtilities.getParameterOrAttribute("filename", request);
  if (fileName == null) fileName = defaultFileName;
  File dataFile = new File(fileName);
  boolean dataFound = dataFile.exists();

  out.println("</br><p>File found="+String.valueOf(dataFound)+" at "+dataFile.getAbsolutePath()+"</p>");
  try {
  FileInputStream dataFIStream = new FileInputStream(dataFile);
  //Create Workbook instance holding reference to .xlsx file
  //XSSFWorkbook workbook = new XSSFWorkbook(dataFIStream);

  POIFSFileSystem fs = new POIFSFileSystem(dataFIStream);
  HSSFWorkbook wb = new HSSFWorkbook(fs);
  HSSFSheet sheet = wb.getSheetAt(0);
  HSSFRow row;
  HSSFCell cell;

  int numSheets = wb.getNumberOfSheets();
  out.println("<p>Num Sheets = "+numSheets+"</p>");

  int physicalNumberOfRows = sheet.getPhysicalNumberOfRows();
  out.println("<p>Num Rows = "+physicalNumberOfRows+"</p>");

  int rows; // No of rows
  rows = sheet.getPhysicalNumberOfRows();

  int cols = 0; // No of columns
  int tmp = 0;

  // This trick ensures that we get the data properly even if it doesn't start from first few rows
  for(int i = 0; i < 10 || i < rows; i++) {
      row = sheet.getRow(i);
      if(row != null) {
          tmp = sheet.getRow(i).getPhysicalNumberOfCells();
          if(tmp > cols) cols = tmp;
      }
  }
  out.println("<p>Num Cols = "+cols+"</p>");

  String[] fieldNames = new String[cols];
  String colName;
  for(int i=0; i<sheet.getRow(0).getPhysicalNumberOfCells(); i++) {
    colName = sheet.getRow(0).getCell(i).getStringCellValue();
    fieldNames[i] = parseColNameToFieldName(colName);
  }

  String[] fieldType = new String[cols];
  List<Integer> colsWithUnknownType = new ArrayList<Integer>();
  for(int i=0; i<cols; i++) {
    colsWithUnknownType.add(i);
  }

  // find class names
  for (int i=1; i<rows && colsWithUnknownType.size()>0; i++) {
    row = sheet.getRow(i);
    if (i == 1) continue;
    // loop over the columns whose types are unknown and try and find the types

    for (Iterator<Integer> colIterator = colsWithUnknownType.iterator(); colIterator.hasNext(); ) {

      Integer column = colIterator.next();
      int colInt = (int) column;
      cell = row.getCell(colInt);
      if (cell==null) continue;

      String excelFileType = parseIOTCellType(fieldNames[colInt], cell);
      fieldType[colInt] = excelFileType;

      if (fieldType[colInt] != null && !fieldType[colInt].equals("")) {
        colIterator.remove();
      }
    }
  }

  for (int i=0;i<cols;i++) {
    if (fieldNames[i]==null) fieldNames[i]="NULL";
    if (fieldType[i] ==null) fieldType[i] ="NULL";
  }


  for (int i=0;i<cols;i++) {
    out.println("<p>"+fieldNames[i]+": "+fieldType[i]+"</p>");
  }

  //composeClassFieldValuesToFile(fieldNames, fieldType, "parseExcelSampleData.java");
  //composePackageJdosToFile(fieldNames, fieldType, "parseExcelSampleData.jdo");

  out.println("<h2>Properties file contents: </h2>");
  Integer[] columnsOfInterest = findCategoricalVarColumns(sheet);
  out.println("<hr>");
  for (Integer i: columnsOfInterest) {
    out.println("</br>");
    out.println(composeCategoryColumn(sheet, i.intValue()));
  }
  out.println("<hr>");
  out.println("</br><h3>End of properties file</h3>");

  for (int i=0;i<cols;i++) {
    out.println(fieldNames[i]+": "+fieldType[i]);
    out.println("</br>");
    //out.println(composePackageJdoEntry(fieldNames[i],fieldType[i]));
  }





  // this code block parses a separate tab of the workbook, which holds some temperature info
  //XSSFSheet dataSheet = workbook.getSheetAt(0);
  //int numRows = dataSheet.getPhysicalNumberOfRows();
  //out.println("<p>number of rows in the sheet: "+numRows+"</p>");
  out.println("<p></p>");
} catch (Exception e) {
  out.println("Found an exception!!");
  e.printStackTrace(new PrintWriter(out, true));

}


}
catch (Exception ex) {


	System.out.println("!!!An error occurred on page parseExcelSampleData.jsp. The error was:");
	ex.printStackTrace();
	myShepherd.rollbackDBTransaction();


}
finally{

	myShepherd.closeDBTransaction();
	myShepherd=null;
}
%>


<%!

public String parseColNameToFieldName(String colName) {
  String[] pieces = colName.split("-|\\.|_|\\s+");
  String result = joinCamelCase(pieces);
  // take out non-alphanumeric chars (there's only a few)
  result = result.replace("(|)|&|","");
  return result;
}

public String joinCamelCase(String[] words) {
  String[] correctlyCapitalized = new String[words.length];
  if (words.length>0) {
    correctlyCapitalized[0] = words[0].toLowerCase();
  }
  for (int i=1;i<words.length;i++) {
    correctlyCapitalized[i] = capitolizeFirstLetter(words[i].toLowerCase());
  }
  return StringUtils.join(correctlyCapitalized);
}

public String capitolizeFirstLetter(String str) {
  return str.substring(0,1).toUpperCase() + str.substring(1);
}

public void composeClassFieldValuesToFile(String[] fieldNames, String[] fieldTypes, String fileName) throws FileNotFoundException, UnsupportedEncodingException {
  PrintWriter writer = new PrintWriter(fileName, "UTF-8");
  for (int i=0; i<fieldNames.length; i++) {
    writer.println(composeFieldDeclaration(fieldNames[i], fieldTypes[i]));
  }
  writer.println("");
  for (int i=0; i<fieldNames.length; i++) {
    printWriteFieldGetter(writer, fieldNames[i], fieldTypes[i]);
    printWriteFieldSetter(writer, fieldNames[i], fieldTypes[i]);
  }
  writer.close();
}

public void composeCategoricalVarsToPropertiesFile(HSSFSheet sheet, String fileName) throws FileNotFoundException, UnsupportedEncodingException {
  PrintWriter writer = new PrintWriter(fileName, "UTF-8");
  Integer[] columnsOfInterest = findCategoricalVarColumns(sheet);
  for (Integer i: columnsOfInterest) {
    printWriteCategoryColumn(writer, sheet, i.intValue());
  }
  writer.close();
}

public Integer[] findCategoricalVarColumns(HSSFSheet iotSheet) {
  ArrayList<Integer> categoricalVarCols = new ArrayList<Integer>();
  HSSFRow categoryRow = iotSheet.getRow(3);
  for (int i = 1; i < categoryRow.getPhysicalNumberOfCells(); i++) {
    HSSFCell cell = categoryRow.getCell(i);
    if (cell != null && cell.getStringCellValue()!=null && !cell.getStringCellValue().equals("")) categoricalVarCols.add((Integer) i);
  }
  return categoricalVarCols.toArray(new Integer[1]);
}

public void printWriteCategoryColumn(PrintWriter writer, HSSFSheet sheet, Integer i) {
  writer.write(composeCategoryColumn(sheet, i));
  return;
}

public String composeCategoryColumn(HSSFSheet sheet, Integer i) {
  String ret = "";
  String fieldName = parseColNameToFieldName(sheet.getRow(0).getCell(i).getStringCellValue());
  int row = 3; // first row containing categorical info
  HSSFCell nextCategory = sheet.getRow(row).getCell(i);
  while (nextCategory != null && nextCategory.getStringCellValue()!=null && !nextCategory.getStringCellValue().equals("")) {
    ret = ret + fieldName + (row-3) + " " + nextCategory.getStringCellValue() + "</br>";
    row++;
    if (sheet.getRow(row) == null) return ret;
    nextCategory = sheet.getRow(row).getCell(i);
  }
  return ret;
}


public String composeFieldDeclaration(String name, String type) {
  return("private "+type+" "+name+";");
}

public void printWriteFieldGetter(PrintWriter writer, String name, String type) {
  writer.println("public "+type+" get"+capitolizeFirstLetter(name)+"(){");
  writer.println("\treturn("+name+");");
  writer.println("}");
}


public String composeFieldGetter(String name, String type) {
  return("public "+type+" get"+capitolizeFirstLetter(name)+"(){\n\treturn("+name+");\n"+"}");
}

public void printWriteFieldSetter(PrintWriter writer, String name, String type) {
  writer.println("public void set"+capitolizeFirstLetter(name)+"("+type+" "+name+"){");
  writer.println("\tthis."+name+" = "+name+";");
  writer.println("}");
}


/*
<field name="encounters" persistence-modifier="persistent" default-fetch-group="true">
  <collection element-type="org.ecocean.Encounter"/>
  <join/>
</field>
*/
public String composePackageJdoEntry(String name, String type) {
  String ret = "<field name=\""+name+"\" persistent-modifier=\"persistent\" default-fetch-group=\"true\">";
  ret = ret + "\n\t<column jdbc-type=\""+mapJavaTypeToJDBCType(type)+"\" allows-null=\"true\"/>";
  ret = ret + "\n</field>";
  return ret;
}

public void composePackageJdosToFile(String[] fieldNames, String[] fieldTypes, String fileName) throws FileNotFoundException, UnsupportedEncodingException {
  PrintWriter writer = new PrintWriter(fileName, "UTF-8");
  for (int i=0; i<fieldNames.length; i++) {
    writer.println("<field name=\""+fieldNames[i]+"\" persistent-modifier=\"persistent\" default-fetch-group=\"true\">");
    writer.println("\t<column jdbc-type=\""+mapJavaTypeToJDBCType(fieldTypes[i])+"\" allows-null=\"true\"/>");
    writer.println("</field>");
  }
  writer.close();
}

public String mapJavaTypeToJDBCType(String type) {
  // TODO: check that this is correct for our types
  if (type=="String") return "LONGVARCHAR";
  else if (type=="NUMERIC") return "NUMERIC";
  else return "UNKNOWN";
}

public static String parseCellType(HSSFCell cell) {
  switch (cell.getCellType()) {
    case 0:
      return("NUMERIC");
    case 1:
      return("String");
    case 2:
      return("FORMULA");
    case 3:
      return("BLANK");
    case 4:
      return("BOOLEAN");
    case 5:
      return("ERROR");
    default:
      return(String.valueOf(cell.getCellType()));
  }
}

public static String parseIOTCellType(String fieldName, HSSFCell typeCell) {

  if (typeCell.getCellType()!=1) return parseCellType(typeCell);

  String cellContents = typeCell.getStringCellValue();

  if (cellContents.trim().toLowerCase().equals("ddm"))  return parseIOTDDM(fieldName, typeCell);
  if (cellContents.trim().toLowerCase().equals("text")) return "String";
  if (cellContents.trim().toLowerCase().startsWith("numerical decimal")) return "Double";


  return cellContents;
}

public static String parseIOTDDM(String fieldName, HSSFCell typeCell) {

  if (fieldName.contains((CharSequence) "#")) return "int";
  if (typeCell.getStringCellValue().trim().toLowerCase().startsWith("numerical decimal")) return "double";

  if((fieldName.trim().toLowerCase().equals("tumor"))) return "boolean";

  return "did not parse fieldName="+fieldName+" typeCell="+typeCell.getStringCellValue();

}


%>






<p>Done successfully</p>
</body>
</html>
