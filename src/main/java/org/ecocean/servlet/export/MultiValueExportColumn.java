package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;
import org.ecocean.*;
import org.ecocean.media.*;
import org.ecocean.genetics.*;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.security.*;
import javax.jdo.*;
import java.lang.StringBuffer;
import jxl.write.*;
import jxl.Workbook;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

public class MultiValueExportColumn extends ExportColumn {

    // since we have multiple names per row, we need to know this
    private int nameNum;
    private boolean isLabel; // if isLabel is true, it's a name label. Else it's a name value.

    public MultiValueExportColumn(Class declaringClass, String header, Method getter, int colNum) {
      super(declaringClass, header, getter, colNum);
    }

    // this is the initializer I'm gonna use
    public MultiValueExportColumn(int nameNum, int colNum, boolean isLabel) {
      super(MultiValue.class, nameNumString(nameNum, isLabel), null, colNum);
      this.nameNum = nameNum;
      this.isLabel = isLabel;
    }

    private static String nameNumString(int nameNum, boolean isLabel) {
      if (isLabel) return "Name"+nameNum+".label";
      return "Name"+nameNum+".value";
    }


    public void writeLabel(MultiValue names, int rowNum, WritableSheet sheet) throws jxl.write.WriteException, InvocationTargetException, IllegalAccessException {
      List<String> sortedKeys = names.getSortedKeys();
      writeLabel(sortedKeys, names, rowNum, sheet);
    }

    // it saves time to only sort the keys once per MultiValue by shortcutting into this method
    public void writeLabel(List<String> sortedKeys, MultiValue names, int rowNum, WritableSheet sheet) throws jxl.write.WriteException, InvocationTargetException, IllegalAccessException {
      if (names==null || sortedKeys.size()<(nameNum+1)) {
        return; // out of bounds for this names list
      }
      String key = sortedKeys.get(nameNum);
      String value = names.getValue(key);
      if (!Util.stringExists(value)) return; // don't print out names without values

      String writeValue = (isLabel) ? key : value; // are we writing the key or value
      writeValue = cleanWriteValue(writeValue);
      sheet.addCell(new Label(colNum, rowNum, writeValue));     

    }

    private String cleanWriteValue(String writeValue) {
      if (MultiValue.DEFAULT_KEY_VALUE.equals(writeValue)) return "Default";
      return writeValue;
    }

    public static void addNameColumns(int numNames, List<ExportColumn> columns) {
      System.out.println("About to add "+numNames+" nameColumns. Columns.size()="+columns.size());
      for (int i=0; i<numNames; i++) {
        MultiValueExportColumn keyColumn = new MultiValueExportColumn(i, columns.size(), true);
        columns.add(keyColumn);
        MultiValueExportColumn valColumn = new MultiValueExportColumn(i, columns.size(), false);
        columns.add(valColumn);
      }
      System.out.println("Done adding "+numNames+" nameColumns. Columns.size()="+columns.size());

      // that was easy!
    }


}



