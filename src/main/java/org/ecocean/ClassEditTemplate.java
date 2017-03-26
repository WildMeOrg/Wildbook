package org.ecocean;

import java.io.Writer;
import javax.servlet.jsp.JspWriter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;

import org.ecocean.datacollection.DataPoint;

/*
 * An almost entirely static / functional class for printing html UI elements
 * for editing class fields on pages such as occurrence.jsp. Note that this
 * class works only in conjunction with webapp/css/classEditTemplate.css
 * and webapp/javascript/classEditTemplate.js
 */

public class ClassEditTemplate {

  public ClassEditTemplate() {
  }

  public static void updateObjectField(Object obj, String setterName, String valueAsString) throws NoSuchMethodException {
    try {
      Class c = findTypeOfField(obj.getClass(), setterName);

      if (c == Double.class){
        Double dbl = Double.parseDouble(valueAsString);
        Method setter = obj.getClass().getMethod(setterName, Double.class);
        setter.invoke(obj, dbl);
        System.out.println("updateObjectField: just invoked "+setterName+" with value "+dbl);
      }

      if (c == Integer.class){
        Integer in = Integer.parseInt(valueAsString);
        Method setter = obj.getClass().getMethod(setterName, Integer.class);
        setter.invoke(obj, in);
        System.out.println("updateObjectField: just invoked "+setterName+" with value "+in);
      }

      if (c == Boolean.class){
        Boolean bo = Boolean.parseBoolean(valueAsString);
        Method setter = obj.getClass().getMethod(setterName, Boolean.class);
        setter.invoke(obj, bo);
        System.out.println("updateObjectField: just invoked "+setterName+" with value "+bo);
      }

      if (c == String.class){
        Method setter = obj.getClass().getMethod(setterName, String.class);
        setter.invoke(obj, valueAsString);
        System.out.println("updateObjectField: just invoked "+setterName+" with value "+valueAsString);
      }

      if (c == DateTime.class){
        DateTime dt = DateTime.parse(valueAsString);
        Method setter = obj.getClass().getMethod(setterName, DateTime.class);
        setter.invoke(obj, dt);
        System.out.println("updateObjectField: just invoked "+setterName+" with value "+dt);

      }
    } catch (Exception e) {
      System.out.println("updateObjectField: was not able to invoke "+setterName+" with value "+valueAsString);
      e.printStackTrace();
    }
  }

  public static Class findTypeOfField(Class parentClass, String fieldSetterName) throws NoSuchMethodException {
    String fieldGetterName = "get"+fieldSetterName.substring(3);
    Method fieldGetter = parentClass.getMethod(fieldGetterName);
    return fieldGetter.getReturnType();
  }

  public static boolean fieldHasType(Class parentClass, String fieldSetterName, Class candidateType) {
    try {
      Method m = parentClass.getMethod(fieldSetterName, candidateType);
      return true;
    }
    catch (NoSuchMethodException e) {
      return false;
    }
  }

  public static boolean newValEqualsOldVal(Occurrence occ, String getterName, int newVal) {
    try {
      java.lang.reflect.Method getter = occ.getClass().getMethod(getterName);
      int oldVal = (Integer) getter.invoke(occ);
      boolean result = (oldVal == newVal);
      System.out.println("ClassEditTemplate: "+getterName+" = "+oldVal);
      System.out.println("                 : newVal = "+newVal);
      System.out.println("                 : result = "+result);
      return result;
    } catch (Exception e) {
      System.out.println("exception caught");
    } finally {
      return (false);
    }
  }

  // excellent helper function by polygenelubricants on http://stackoverflow.com/a/2560017
  public static String splitCamelCase(String s) {
     return s.replaceAll(
        String.format("%s|%s|%s",
           "(?<=[A-Z])(?=[A-Z][a-z])",
           "(?<=[^A-Z])(?=[A-Z])",
           "(?<=[A-Za-z])(?=[^A-Za-z])"
        ),
        " "
     ).toLowerCase();
  }


  public static String prettyFieldNameFromGetMethod(Method getMeth) {
    String withoutGet = getMeth.getName().substring(3);
    return splitCamelCase(withoutGet);
  }

  public static String prettyFieldName(String fieldName) {
    return splitCamelCase(fieldName);
  }


  public static String getClassNamePrefix(Class classy) {
    String name = classy.getName();
    return ((name.length()>2) ? name.substring(0,3).toLowerCase() : name.toLowerCase() );
  }

  private static String constructInputElemName(String classNamePrefix, String fieldName) {
    return ("oldValue-"+classNamePrefix+":"+fieldName);

  }

  public static String inputElemName(Method getMeth, String classNamePrefix) {
    String fieldName = getMeth.getName().substring(3);
    return constructInputElemName(classNamePrefix, fieldName);
  }

  public static String inputElemName(DataPoint dp, String classNamePrefix) {
    String fieldName = dp.getName();
    if (dp.getNumber()!=null) fieldName = fieldName + (dp.getNumber() + 1);
    return constructInputElemName(classNamePrefix+"-dp-"+dp.getID(), fieldName);
  }



  public static boolean isDisplayableGetter(Method method) {
    try {
      String methName = method.getName();
      if (!methName.startsWith("get")) return false;
      String fieldName = methName.substring(3);

      Class fieldType = method.getReturnType();

      Method setter = method.getDeclaringClass().getMethod("set"+fieldName, fieldType);
      return true;
    } catch (Exception e) {
      System.out.println(e.toString());
      return false;
    }
  }

  public static void printDateTimeSetterRow(Object obj, String objID, javax.servlet.jsp.JspWriter out) throws NoSuchMethodException, IOException, IllegalAccessException, InvocationTargetException {
    Method getDateTime = obj.getClass().getMethod("getDateTime");
    String className = obj.getClass().getSimpleName(); // e.g. "Occurrence"
    String classNamePrefix = ""; // e.g. "occ"
    if (className.length()>2) classNamePrefix = className.substring(0,3).toLowerCase();
    else classNamePrefix = className.toLowerCase();

    String printValue;
    if (getDateTime.invoke(obj)==null) printValue = "";
    else {
      DateTime dt = (DateTime) getDateTime.invoke(obj);
      LocalDateTime lt = dt.toLocalDateTime();
      System.out.println("DateTime "+dt+" converted to LocalDateTime "+lt);
      printValue = dt.toString("MM-dd-yyyy HH:mm");
    }
    String fieldName = prettyFieldNameFromGetMethod(getDateTime);
    String inputName = inputElemName(getDateTime, classNamePrefix);

    out.println("<tr data-original-value=\""+printValue+"\">");
    out.println("\t<td>"+fieldName+"</td>");
    out.println("\t<td>");
    // hidden input for setting default va a la http://stackoverflow.com/a/11904956
    //out.println("\t\t<input type=\"hidden\" id=\"datepicker\" />");
    out.println("<input class=\"form-control datepicker\" type=\"text\"");
    out.println("name=\""+inputName+"\" ");
    out.println("value=\""+printValue+"\"");
    out.println("/>");
    out.println("\t</td>");


  }

  public static void printOutClassFieldModifierRow(Object obj, DataPoint dp, javax.servlet.jsp.JspWriter out) throws IOException, IllegalAccessException, InvocationTargetException {
    printOutClassFieldModifierRow(obj, dp, out, "context0");
  }

  public static void printOutClassFieldModifierRow(Object obj, DataPoint dp, javax.servlet.jsp.JspWriter out, String context) throws IOException, IllegalAccessException, InvocationTargetException {
    String className = obj.getClass().getSimpleName(); // e.g. "Occurrence"
    String classNamePrefix = ""; // e.g. "occ"
    if (className.length()>2) classNamePrefix = className.substring(0,3).toLowerCase();
    else classNamePrefix = className.toLowerCase();

    String printValue = dp.getValueString();
    if (printValue == null) System.out.println("It's really null! I knew it!");
    //if (printValue.equals("null")) printValue = "";
    String fieldName = splitCamelCase(dp.getNumberedName());
    String inputName = inputElemName(dp, classNamePrefix);

    boolean isSequential = dp.isSequential();

    if (dp.isCategorical(context)) {
      printOutClassFieldSelectorRow(fieldName, printValue, dp.getCategoriesAsStrings(context), inputName, out, isSequential);
      //printOutClassFieldModifierRow(fieldName, printValue, dp.getUnits(), inputName, out);
    } else if (dp.getClass().getSimpleName().equals("Instant")) {
      printOutDateTimeModifierRow(fieldName, printValue, dp.getUnits(), inputName, out, isSequential);
    } else {
      printOutClassFieldModifierRow(fieldName, printValue, dp.getUnits(), inputName, out, isSequential);
    }
  }


  public static void printOutClassFieldModifierRow(Object obj, Method getMethod, javax.servlet.jsp.JspWriter out) throws IOException, IllegalAccessException, InvocationTargetException {
    String className = obj.getClass().getSimpleName(); // e.g. "Occurrence"
    String classNamePrefix = ""; // e.g. "occ"
    if (className.length()>2) classNamePrefix = className.substring(0,3).toLowerCase();
    else classNamePrefix = className.toLowerCase();

    String printValue;
    if (getMethod.invoke(obj)==null) printValue = "";
    else printValue = getMethod.invoke(obj).toString();
    String fieldName = prettyFieldNameFromGetMethod(getMethod);
    String inputName = inputElemName(getMethod, classNamePrefix);

    printOutClassFieldModifierRow(fieldName, printValue, (String) null, inputName, out, false);

  }


  // custom method to replicate a very specific table row format on this page
  public static void printOutClassFieldModifierRow(String fieldName, String printValue, String units, String inputName, javax.servlet.jsp.JspWriter out, boolean isSequential) throws IOException, IllegalAccessException, InvocationTargetException {
    printFieldRowStart(printValue, out, isSequential);

    printFieldLabelCell(fieldName, out);
    printInputCell(inputName, printValue, out);
    printUnitsCell(units, out);
    printUndoCell(out);

    out.println("\n</tr>");
  }

  public static void printOutSequentialClassFieldModifierRow(String fieldName, String printValue, String units, String inputName, javax.servlet.jsp.JspWriter out, boolean isTemplate) throws IOException, IllegalAccessException, InvocationTargetException {
    printSequentialFieldRowStart(printValue, out, isTemplate);

    printFieldLabelCell(fieldName, out);
    printInputCell(inputName, printValue, out);
    printUnitsCell(units, out);
    printUndoCell(out);

    out.println("\n</tr>");
  }


  // custom method to replicate a very specific table row format on this page
  public static void printOutDateTimeModifierRow(String fieldName, String printValue, String units, String inputName, javax.servlet.jsp.JspWriter out, boolean isSequential) throws IOException, IllegalAccessException, InvocationTargetException {
    printFieldRowStart(printValue, out, isSequential);

    printFieldLabelCell(fieldName, out);
    printDateTimeCell(inputName, printValue, out);
    printUnitsCell(units, out);
    printUndoCell(out);

    out.println("\n</tr>");
  }

  public static void printOutClassFieldSelectorRow(String fieldName, String printValue, String[] possibleValues, String inputName, javax.servlet.jsp.JspWriter out, boolean isSequential) throws IOException, IllegalAccessException, InvocationTargetException {
    printFieldRowStart(printValue, out, isSequential);

    printFieldLabelCell(fieldName, out);
    printSelectCell(inputName, printValue, possibleValues, out);
    printSelectUndoCell(out);

    out.println("\n</tr>");
  }


  public static void printInput(String inputName, String printValue, javax.servlet.jsp.JspWriter out) throws IOException {
    out.println("\t\t<input name=\""+inputName+"\" value=\""+printValue+"\" />");
  }

  public static void printInputCell(String inputName, String printValue, javax.servlet.jsp.JspWriter out) throws IOException {
    out.println("\t<td class=\"value\">");
    printInput(inputName, printValue, out);
    out.println("</td>");
  }

  public static void printDateTimeCell(String inputName, String printValue, javax.servlet.jsp.JspWriter out) throws IOException {
    out.println("\t<td class=\"display\">");
    printDisplayDateTimeSetter(inputName, printValue, out);
    printMillisDateTimeSetter(inputName, printValue, out);
    out.println("</td>");
  }


  public static void printDisplayDateTimeSetter(String inputName, String printValue, javax.servlet.jsp.JspWriter out) throws IOException {
    out.println("<input class=\"form-control datepicker display\" type=\"text\"");
    out.println("name=\"display-"+inputName+"\" ");
    out.println("value=\""+printValue+"\"");
    out.println("/>");
  }

  public static void printMillisDateTimeSetter(String inputName, String printValue, javax.servlet.jsp.JspWriter out) throws IOException {
    out.println("<input class=\"form-control datepicker millis\" type=\"text\" style=\"display:none;\"");
    out.println("name=\""+inputName+"\" ");
    //out.println("value=\""+printValue+"\"");
    out.println("/>");
  }


  public static void printSelectCell(String inputName, String printValue, String[] possibleValues, javax.servlet.jsp.JspWriter out) throws IOException {
    out.println("\t<td class=\"value\">");
    printSelect(inputName, printValue, possibleValues, out);
    out.println("</td>");
  }

  public static void printSelect(String inputName, String printValue, String[] possibleValues, javax.servlet.jsp.JspWriter out) throws IOException {
    out.println("<script>console.log(\"printSelect called on inputName="+inputName+", printValue="+printValue+"\")</script>");
    out.println("<select name=\""+inputName+"\">");
    printOption("", printValue, out); // gotta be able to unset the values
    for (String possibleValue : possibleValues) printOption(possibleValue, printValue, out);
    out.println("</select>");
  }
  public static void printOption(String possibleValue, String currentValue, javax.servlet.jsp.JspWriter out) throws IOException {
    out.println("\t<option value=\""+possibleValue+"\"");
    if (possibleValue!=null && possibleValue.equals(currentValue)) {
      out.print("selected=\"selected\"");
    }
    out.print("> "+possibleValue+" </option>");
  }


  public static void printFieldLabelCell(String fieldName, javax.servlet.jsp.JspWriter out) throws IOException {
    out.println("\t<td class=\"fieldName\">"+fieldName+"</td>");
  }

  public static void printUndoCell(javax.servlet.jsp.JspWriter out) throws IOException {
    out.println("<td class=\"undo-container\">");
    out.println("<div title=\"undo this change\" class=\"undo-button\">&#8635;</div>");
    out.println("</td>");
  }

  public static void printSelectUndoCell(javax.servlet.jsp.JspWriter out) throws IOException {
    out.println("<td class=\"undo-container\">");
    out.println("<div title=\"undo this change\" class=\"select-undo-button\">&#8635;</div>");
    out.println("</td>");
  }







  public static void printUnitsCell(String units, javax.servlet.jsp.JspWriter out) throws IOException {
    if (units!=null && !units.equals("")) {
      out.println("<td class=\"unit-label\">"+units+"</td>");
    }
  }

  public static void printFieldRowStart(String dataOriginalValue, javax.servlet.jsp.JspWriter out, boolean isSequential) throws IOException {
    if (isSequential) {
      out.println("<tr class=\"sequential\" data-original-value=\""+dataOriginalValue+"\">");
    } else {
      out.println("<tr data-original-value=\""+dataOriginalValue+"\">");
    }
  }

  public static void printSequentialFieldRowStart(String dataOriginalValue, javax.servlet.jsp.JspWriter out, boolean isTemplate) throws IOException {
    if (isTemplate) {
      out.println("<tr class=\"sequential template\" data-original-value=\""+dataOriginalValue+"\">");
    } else {
      out.println("<tr class=\"sequential\" data-original-value=\""+dataOriginalValue+"\">");
    }
  }




  public static void printUnmodifiableField(Object obj, Method getMethod, javax.servlet.jsp.JspWriter out) throws IOException, IllegalAccessException, InvocationTargetException {
    String className = obj.getClass().getSimpleName(); // e.g. "Occurrence"
    String classNamePrefix = ""; // e.g. "occ"
    if (className.length()>2) classNamePrefix = className.substring(0,3).toLowerCase();
    else classNamePrefix = className.toLowerCase();

    String printValue;
    if (getMethod.invoke(obj)==null) printValue = "";
    else printValue = getMethod.invoke(obj).toString();
    String fieldName = prettyFieldNameFromGetMethod(getMethod);

    printUnmodifiableField(fieldName, printValue, out);
  }

  public static void printUnmodifiableField(String fieldName, String printValue, javax.servlet.jsp.JspWriter out) throws IOException, IllegalAccessException, InvocationTargetException {
    out.println("\n<tr>");
    out.println("\n\t<td>"+fieldName+"</td>");
    out.println("\n\t<td>"+printValue+"</td>");
    out.println("\n</tr>");
  }

  // inverse of createNumberedRowFromTemplate's oldName -> newName process
  public static String getDataNameFromParameter(String pname) {
    String afterColon = pname.split(":")[1];
    String afterDash  = afterColon.split("-")[1];
    return splitCamelCase(afterDash).toLowerCase().replaceAll("[0-9]","");
  }

  public static Integer getDataNumberFromParameter(String pname) {
    String afterColon = pname.split(":")[1];
    String afterDash  = afterColon.split("-")[1];
    return getIntegerFromString(afterDash);
  }

  public static Integer getIntegerFromString(String str) {
    String justNum = str.replaceAll("[^0-9]", "");
    return Integer.valueOf(justNum);
  }


}
