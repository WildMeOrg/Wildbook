package org.ecocean;

import org.ecocean.Util;
import org.ecocean.media.MediaAsset;
import org.json.JSONObject;

import java.util.List;
import javax.jdo.Query;
import java.util.HashMap;


public class WBQuery implements java.io.Serializable {

    private static final long serialVersionUID = -7934850478934029842L;

    protected JSONObject parameters;
    protected String className;
    protected long id;
    protected String parametersAsString;
    protected String name;
    protected AccessControl owner;
    protected long revision;


    public WBQuery() {
    }

    public WBQuery(final int id, final JSONObject params, final AccessControl owner) {
        this.id = id;
        this.owner = owner;
        this.className = params.optString("class");
        this.parameters = params.optJSONObject("query");
        if (params != null) this.parametersAsString = params.toString();
        this.setRevision();
    }

    public WBQuery(final JSONObject params) {
        this(-1, params, null);
    }

    public WBQuery(final JSONObject params, final AccessControl owner) {
        this(-1, params, owner);
    }

    public JSONObject getParameters() {
        if (parameters != null) return parameters;
        //System.out.println("NOTE: getParameters() on " + this + " was null, so trying to get from parametersAsString()");
        JSONObject j = Util.stringToJSONObject(parametersAsString);
        parameters = j;
        return j;
    }

    public void setParameters(JSONObject p) {
        if (p == null) {
            //System.out.println("WARNING: attempted to set null parameters on " + this + "; ignoring");
            return;
        }
        parameters = p;
        parametersAsString = p.toString();
    }

    //this *should* magically return a List of the proper classed object. good luck with that!
    public List<Object> doQuery(Shepherd myShepherd) throws RuntimeException {
        Query query = toQuery(myShepherd);
        return (List<Object>) query.execute();
    }


/* something like this?
    WBQuery qry = new WBQuery(new JSONObject("{ \"foo\" : \"bar\" }"));
    List<Object> res = qry.doQuery(myShepherd);
*/
    public Query toQuery(Shepherd myShepherd) throws RuntimeException {
        Query query = null;
        try {  //lets catch any shenanigans that happens here, and throw our own RuntimeException
            String qString = toJDOQL();
            System.out.println("starting toQuery with query string = "+qString);
            query = myShepherd.getPM().newQuery(qString);
            query.setClass(getCandidateClass());
            querySetRange(query);
            querySetOrdering(query);
        } catch (Exception ex) {
            throw new RuntimeException(ex.toString());
        }
        System.out.println("Query parsed. Returning...");
        return query;
    }

    /**
     * Translates this.parameters into JDOQL by calling
     * parseField on each top-level key in parameters
     */
    public String toJDOQL() {
        /////getParameters() will give the JSONObject we need to magically turn into JDOQL!!
        String output = "SELECT FROM "+className+" WHERE ";
        String[] names = JSONObject.getNames(parameters);
        String[] parsedFields = new String[names.length];
        for (int i=0; i<names.length; i++) {
          parsedFields[i]=parseField(names[i]);
        }
        output += "(" + joinString(parsedFields, " && ") + ")";

        return output;
    }

    public Class getCandidateClass() throws java.lang.ClassNotFoundException {
        if (className == null) throw new ClassNotFoundException("missing class name in query");
        return Class.forName(className);  //this also will throw Exception if no good
    }

    //TODO
    public void querySetRange(Query query) {
        query.setRange(0,10);
    }

    //TODO
    public void querySetOrdering(Query query) {
        query.setOrdering("id DESC");
    }

    public long setRevision() {
        this.revision = System.currentTimeMillis();
        return this.revision;
    }

    /**
     *  parses a single field from this.parameters, returning a condition of a JDOQL "WHERE" clause.
     */
    private String parseField(String field) {

      String output = "(";
      try {
        String valueClass = parameters.opt(field).getClass().getName();
        switch(valueClass) {
          case "java.lang.String": {
            // This is the simple case of field: value
            output += parseEqualityField(field);
            break;
          }
          case "org.json.JSONObject": {
            // This case deals with operators such as $ne and $and
            JSONObject value = parameters.getJSONObject(field);
            output += parseOperatorField(field);
            break;
          }
          default: {
            output += field+": ERROR PARSING VALUE CLASS "+valueClass;
          }
        }
      }
      catch (Exception e) {
        System.out.println("Exception found parsing field "+field+".");
        e.printStackTrace();
      }
      output+=")";
      return output;
    }

    /**
     * @param field the name of field with a basic fieldName : value entry in this.parameters
     * @returns a simple JDOQL field-equality check such as
     * sex == "female"
     */
    private String parseEqualityField(String field, boolean escapeValueInQuotes) {
      if (escapeValueInQuotes) {
        return field +" == \""+parameters.optString(field, "VALUE NOT FOUND")+"\"";
      }
      return field +" == "+parameters.optString(field, "VALUE NOT FOUND");
    }

    private String parseEqualityField(String field) {
      return parseEqualityField(field, true);
    }


    /**
     * @param field the name of field whose entry in this.parameters looks like "fieldName : {$operator: value}"
     * @returns a JDOQL WHERE-clause entry such as
     * (length > 7)
     */
    private String parseOperatorField(String field) throws NullPointerException {

      String output = "";
      JSONObject fieldQuery = parameters.optJSONObject(field);
      String[] operators = JSONObject.getNames(fieldQuery);
      String[] values = new String[operators.length];
      for (int i=0; i<operators.length; i++) {
        String operator = operators[i];
        if (comparisonOperator.containsKey(operator)) {
          String value = fieldQuery.optString(operator, "PARSE-ERROR");
          output += comparisonOperator.get(operator).execute(field, value);
        }
        else if (logicalOperator.containsKey(operator)) {
          output += ("LOGICAL OPERATORS NOT SUPPORTED YET: Error parsing "+operator);
        }
      }
      return output;//" operators = ("+output+"): ( (not parsable)" + fieldQuery.toString() + ")";
    }

    private static String parseOperator(String field, String operator, String value) {
      return "this isn't ready yet OK!";
    }

    /**
     * Really a utility function, this is like glue.join(strings) in JavaScript
     */
    private static String joinString (String[] strings, String glue) {
      if (strings.length==0) return "";
      String res = strings[0];
      for (int i=1; i<strings.length; i++) {
        res += glue + strings[i];
      }
      return res;
    }



    /**
     * This is the function that stitches together a comparison such as
     * field < value.
     * @param field the name of the class field being searched on
     * @param operator the comparison being made, e.g. "<="
     * @param value
     * @param quoteValue whether value should be escaped in quotes
     */
    private static String buildComparisonOperator(String field, String operator, String value, boolean quoteValue) {
      if (quoteValue) {
        return field+' '+operator+" \""+value+"\"";
      }
      return field+' '+operator+' '+value;
    }

    private static String buildComparisonOperator(String field, String operator, String value) {
      return buildComparisonOperator(field, operator, value, true);
    }

    private static String buildBooleanOperator(String field, String operator, String value) {
      String isNot = "";
      if (value!="true") isNot=" not"; //TODO: double-check the logic on this line once we have examples
      return buildComparisonOperator(field, "is"+isNot, value);
    }

    /**
     * Stitches together an argument such as an AND over a list of values.
     * @param operator the comparison being made, such as && or ||
     * @param values
     */
    private static String buildLogicalOperator(String operator, String[] values) {
      String result = joinString(values, ' ' + operator + ' ');
      if (values.length > 1) result = '(' + result + ')';
      return result;
    }

    /**
     * A CompOperator handles comparison operations such as <, >, and !=.
     * All CompOperators will be stored in the HashMap comparisonOperator,
     * whose keys are the mongo-query-syntax comparison operations
     * themselves, e.g. $eq, $lte, etc.
     */
    interface CompOperator {
      /**
       * returns the inverse operation (so we know e.g. that the negation of < is =>)
       */
      String inverseOp();
      /**
       * The execute function is where each unit of translation
       * actually happens.
       * returns a JDOQL WHERE-clause statement such as (field <= value)
       */
      String execute(String field, String value);
    }



    // the below is a class static literal defined in the WBQuery instance initializer
    private static HashMap<String, CompOperator> comparisonOperator = new HashMap<String, CompOperator>();
    {
      comparisonOperator.put("$eq", new CompOperator() {
        public String inverseOp() {return "$ne";}
        public String execute(String field, String value) {
          return (buildComparisonOperator(field, "=", value));
        }
      });
      comparisonOperator.put("$ne", new CompOperator() {
        public String inverseOp() {return "$eq";}
        public String execute(String field, String value) {
          return (buildComparisonOperator(field, "!=", value));
        }
      });
      comparisonOperator.put("$lt", new CompOperator() {
        public String inverseOp() {return "$gte";}
        public String execute(String field, String value) {
          return (buildComparisonOperator(field, "<", value));
        }
      });
      comparisonOperator.put("$gt", new CompOperator() {
        public String inverseOp() {return "$lte";}
        public String execute(String field, String value) {
          return (buildComparisonOperator(field, ">", value));
        }
      });
      comparisonOperator.put("$lte", new CompOperator() {
        public String inverseOp() {return "$gt";}
        public String execute(String field, String value) {
          return (buildComparisonOperator(field, "<=", value));
        }
      });
      comparisonOperator.put("$gte", new CompOperator() {
        public String inverseOp() {return "$lt";}
        public String execute(String field, String value) {
          return (buildComparisonOperator(field, ">=", value));
        }
      });
    }

    interface LogicOperator {
      String execute(String[] values);
    }
    HashMap<String, LogicOperator> logicalOperator = new HashMap<String, LogicOperator>();
    {
      logicalOperator.put("$and", new LogicOperator() {
        public String execute(String[] values) {
          return buildLogicalOperator("and", values);
        }
      });
      logicalOperator.put("$or", new LogicOperator() {
        public String execute(String[] values) {
          return buildLogicalOperator("or", values);
        }
      });
    }
}
