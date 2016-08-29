package org.ecocean.datacollection;


import org.ecocean.Util;
import org.ecocean.ClassEditTemplate;

public class Amount extends DataPoint {

  private Double value;

  private String units;

  public Amount() {
  }

  public Amount(Double value, String units) {
    super.setID(Util.generateUUID());
    this.value = value;
    this.units = units;
  }

  public Amount(String name, Double value, String units) {
    super.setID(Util.generateUUID());
    super.setName(name);
    this.value = value;
    this.units = units;
  }


  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }

  public Double[] getCategories(String context) {
    String[] strings = super.getCategoriesAsStrings(context);
    Double[] res = new Double[strings.length];
    for (int i=0; i<strings.length; i++) {
      res[i] = Double.valueOf(strings[i]);
    }
    return res;
  }

  public String getUnits() {
    return units;
  }

  public String toString() {
    return ((this.getName()+": "+value+units).replaceAll("null",""));
  }

  public String toLabeledString() {
    return ("amount-"+(this.getName()+": "+value+units).replaceAll("null",""));
  }

  public void printOutClassFieldModifierRow(javax.servlet.jsp.JspWriter out, Class superClass) throws IOException, IllegalAccessException, InvocationTargetException {

    String printVal = (value!=null) ? value.toString() : "";
    String fieldName = ClassEditTemplate.prettyFieldNameFromGetMethod(getMethod);

    String inputName = inputElemName(getMethod, classNamePrefix);

    out.println("<tr data-original-value=\""+printValue+"\">");
    out.println("\t<td>"+fieldName+"</td>");
    out.println("\t<td>");
    out.println("\t\t<input ");
    out.println("name=\""+inputName+"\" ");
    out.println("value=\""+printValue+"\"");
    out.println("/>");
    out.println("\t</td>");
    out.println("<td class=\"undo-container\">");
    out.println("<div title=\"undo this change\" class=\"undo-button\">&#8635;</div>");
    out.println("</td>");
    out.println("\n</tr>");
  }



}
