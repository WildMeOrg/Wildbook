package org.ecocean;

import org.ecocean.Util;
import org.ecocean.media.*;
import org.datanucleus.api.rest.orgjson.JSONObject;

import java.util.List;
import javax.jdo.Query;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collection;


public class WBQuery implements java.io.Serializable {

    private static final long serialVersionUID = -7934850478934029842L;

    protected JSONObject parameters;
    protected String className;
    protected long id;
    protected String parametersAsString;
    protected String name;
    protected AccessControl owner;
    protected long revision;

    // TODO: ? find a more elegant solution to range queries
    protected int range;
    protected int minRange;

    // query ordering arg
    protected String ordering;

    protected String jdoQuery;

    public WBQuery() {
    }

    public WBQuery(final int id, final JSONObject params, final AccessControl owner) throws org.datanucleus.api.rest.orgjson.JSONException {
        System.out.println("initializing WBQuery with params = "+params.toString());
        this.id = id;
        this.owner = owner;
        this.className = params.optString("class");
        this.parameters = new JSONObject(params.optString("query", "{}"));
        System.out.println("initialized query-param as "+this.parameters.toString());
        // TODO: ? find a more elegant solution to range queries
        this.minRange = params.optInt("minRange", 0);
        this.range = params.optInt("range", 100);

        this.ordering = params.optString("ordering", WBQuery.defaultSortOrdering(this.className));

        if (this.parameters==null) this.parameters = new JSONObject();


        if (params != null) this.parametersAsString = params.toString();
        this.setRevision();
    }

    public WBQuery(final JSONObject params) throws org.datanucleus.api.rest.orgjson.JSONException {
        this(-1, params, null);
    }

    public WBQuery(final JSONObject params, final AccessControl owner) throws org.datanucleus.api.rest.orgjson.JSONException {
        this(-1, params, owner);
    }

    private static String defaultSortOrdering(String className) {
      switch (className) {
        case "org.ecocean.Encounter" : return "catalogNumber descending";

        case "org.ecocean.Annotation" : return "id descending";

        case "org.ecocean.media.MediaAsset" : return "id descending";

        case "org.ecocean.media.MediaAssetSet" : return "id descending";

        case "org.ecocean.MarkedIndividual" : return "individualID descending";

        default : return "";
      }
    }

    public JSONObject getParameters() {
        if (parameters != null) return parameters;
        //System.out.println("NOTE: getParameters() on " + this + " was null, so trying to get from parametersAsString()");
        JSONObject j = Util.stringToDatanucleusJSONObject(parametersAsString);
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
      List<Object> out=new ArrayList<Object>();
      Query query = toQuery(myShepherd);
      Collection c = (Collection) query.execute();
      if(c!=null){out=new ArrayList<Object>(c);}
      System.out.println("calling doQuery on WBQuery with params = "+parametersAsString);

      // closing the query for some reason makes out inaccessible
      query.closeAll();
      return out;
    }


/* something like this?
    WBQuery qry = new WBQuery(new JSONObject("{ \"foo\" : \"bar\" }"));
    List<Object> res = qry.doQuery(myShepherd);
*/
    public Query toQuery(Shepherd myShepherd) throws RuntimeException {
        Query query = null;
        try {  //lets catch any shenanigans that happens here, and throw our own RuntimeException
            System.out.println("starting toQuery");
            String qString = toJDOQL();
            jdoQuery = qString;
            System.out.println("starting toQuery with query string = "+qString);
            query = myShepherd.getPM().newQuery(qString);
            query.setClass(getCandidateClass());

            // TODO: double-check that this is the best way to do datestuff
            queryDeclareImports(query);

            querySetOrdering(query);
            querySetRange(query);



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
        System.out.println("starting toJDOQL with parameters = "+parameters.toString());
        String output = "SELECT FROM "+className;
        String[] names = JSONObject.getNames(parameters);
        System.out.println("continuing toJDOQL...");
        if (names==null || names.length==0) {return output;}
        output += " WHERE ";
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


    /**
     * this is run on each query, and is necessary to enable
     * e.g. datetime comparisons in the query
     * TODO: double-check that this is necessary; I haven't seen
     * this kind of thing elsewhere in wildbook -DB
     **/
    private void queryDeclareImports(Query query) {
      if (containsDateTime()) {
        query.declareImports("import java.util.Date");
      }
    }
    /**
     * TODO: make this not constant
     * @returns whether or not this WBQuery contains a datetime within it, which has implications for how to handle the jdoql query
     **/
    private boolean containsDateTime() {
      return parameters.has("dateTime");
    }

    //TODO: parse parameters for an optional range entry
    // note: mongoDB's 'find' command (which is our syntactic model for json queries) just adds this as a callback function 'limit' e.g. db.collection.find({queryargs}).limit(10);
    // this parses the range, which is passed as an optional third argument of the original query TODO:
    public void querySetRange(Query query) {
      // TODO: ? find a more elegant solution to range queries
      query.setRange(minRange,range);
    }

    //TODO
    private void querySetOrdering(Query query) {
      if ( this.ordering!=null && !this.ordering.equals("")) {
        query.setOrdering(this.ordering);
      }
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
          case "org.datanucleus.api.rest.orgjson.JSONObject": {
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
     * This does type-checking, and should correspond to our global classDefinitions.json.
     * @param className is a wildbook class name e.g. org.ecocean.Encounter
     * @param field is a field name e.g. maxYearsBetweenResightings
     * @returns whether the described class field has values that should be escaped in quotes in a JDOQL call, e.g. if the type of class.field is String or dateTime (returns true) vs numeric (returns false)
     */
    public static boolean classFieldIsEscapedInQuotes(String className, String field) {
      switch (className) {
        case "org.ecocean.Encounter": {
          return (!encounterNonStringFields.contains(field));
        }
        case "org.ecocean.MarkedIndividual": {
          return (!markedIndividualNonStringFields.contains(field));
        }
        case "org.ecocean.Annotation": {
          return (!annotationNonStringFields.contains(field));
        }
        case "org.ecocean.media.MediaAsset": {
          return (!mediaAssetNonStringFields.contains(field));
        }
        case "org.ecocean.media.MediaAssetSet": {
          return (!mediaAssetSetNonStringFields.contains(field));
        }
      }
      return true;
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
          // here is where type inference happens
          boolean escapeValueInQuotes = classFieldIsEscapedInQuotes(className, field);

          output += comparisonOperator.get(operator).execute(field, value, escapeValueInQuotes);
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
    public static String joinString (String[] strings, String glue) {
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
      String execute(String field, String value, boolean escapeValueInQuotes);
      /**
       *
       */
    }

    // the below is a class static literal defined in the WBQuery instance initializer
    private static final Map<String, CompOperator> comparisonOperator;
    static {
      HashMap<String, CompOperator> compOperator = new HashMap<String, CompOperator>();
      compOperator.put("$eq", new CompOperator() {
        public String inverseOp() {return "$ne";}
        public String execute(String field, String value, boolean escapeValueInQuotes) {
          return (buildComparisonOperator(field, "=", value, escapeValueInQuotes));
        }
      });
      compOperator.put("$ne", new CompOperator() {
        public String inverseOp() {return "$eq";}
        public String execute(String field, String value, boolean escapeValueInQuotes) {
          return (buildComparisonOperator(field, "!=", value, escapeValueInQuotes));
        }
      });
      compOperator.put("$lt", new CompOperator() {
        public String inverseOp() {return "$gte";}
        public String execute(String field, String value, boolean escapeValueInQuotes) {
          return (buildComparisonOperator(field, "<", value, escapeValueInQuotes));
        }
      });
      compOperator.put("$gt", new CompOperator() {
        public String inverseOp() {return "$lte";}
        public String execute(String field, String value, boolean escapeValueInQuotes) {
          return (buildComparisonOperator(field, ">", value, escapeValueInQuotes));
        }
      });
      compOperator.put("$lte", new CompOperator() {
        public String inverseOp() {return "$gt";}
        public String execute(String field, String value, boolean escapeValueInQuotes) {
          return (buildComparisonOperator(field, "<=", value, escapeValueInQuotes));
        }
      });
      compOperator.put("$gte", new CompOperator() {
        public String inverseOp() {return "$lt";}
        public String execute(String field, String value, boolean escapeValueInQuotes) {
          return (buildComparisonOperator(field, ">=", value, escapeValueInQuotes));
        }
      });
      comparisonOperator = Collections.unmodifiableMap(compOperator);
    }

    interface LogicOperator {
      String execute(String[] values);
    }
    private static final Map<String, LogicOperator> logicalOperator;
    static {
      HashMap<String, LogicOperator> logicOperator = new HashMap<String, LogicOperator>();
      logicOperator.put("$and", new LogicOperator() {
        public String execute(String[] values) {
          return buildLogicalOperator("and", values);
        }
      });
      logicOperator.put("$or", new LogicOperator() {
        public String execute(String[] values) {
          return buildLogicalOperator("or", values);
        }
      });
      logicalOperator = Collections.unmodifiableMap(logicOperator);
    }

    // Here are literal sets for looking up which class fields are _not_ string-like (for use with classFieldIsEscapedInQuotes)
    // TODO: perhaps generate these dynamically? These MUST correspond with classDefinitions.json
    private static final Set<String> encounterNonStringFields;
    private static final Set<String> mediaAssetNonStringFields;
    private static final Set<String> mediaAssetSetNonStringFields;
    private static final Set<String> markedIndividualNonStringFields;
    private static final Set<String> annotationNonStringFields;
    static {

      HashSet<String> encNSFields = new HashSet<String>();
      encNSFields.add("decimalLatitude");
      encNSFields.add("decimalLongitude");
      encounterNonStringFields = Collections.unmodifiableSet(encNSFields);

      HashSet<String> maNSFields = new HashSet<String>();
      maNSFields.add("id");
      mediaAssetNonStringFields = Collections.unmodifiableSet(maNSFields);

      HashSet<String> masNSFields = new HashSet<String>();
      mediaAssetSetNonStringFields = Collections.unmodifiableSet(masNSFields);

      HashSet<String> miNSFields = new HashSet<String>();
      markedIndividualNonStringFields = Collections.unmodifiableSet(miNSFields);

      HashSet<String> anNSFields = new HashSet<String>();
      annotationNonStringFields = Collections.unmodifiableSet(anNSFields);

    }
}
