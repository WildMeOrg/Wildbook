package org.ecocean.api;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collections;
import org.json.JSONObject;
import org.json.JSONArray;
import org.apache.commons.codec.digest.DigestUtils;

public class QueryParser {

    public static String whereClause(JSONObject qry) throws QueryParserException {
        if (qry == null) return "true";
        List<String> ands = new ArrayList<String>();
        Set<String> keys = qry.keySet();
        for (String k : keys) {
            JSONObject next = qry.optJSONObject(k);
            if (qry.isNull(k)) {
                if (k.equals("$ne")) {
                    ands.add("IS NOT NULL");
                } else {
                    ands.add("(" + k + " IS NULL)");
                }
            } else if (next != null) {  //recurse
                ands.add("(" + k + " " + whereClause(next) + ")");
            } else {
                JSONArray narr = qry.optJSONArray(k);
                if (narr != null) {
                    if (k.equals("$or")) {
                        List<String> ors = new ArrayList<String>();
                        for (int i = 0 ; i < narr.length() ; i++) {
                            JSONObject orj = narr.optJSONObject(i);
                            if (orj == null) throw new QueryParserException("$or has bad elements");
                            ors.add(whereClause(orj));  //recurse
                        }
                        ands.add(" (" + String.join(" OR ", ors) + ") ");
                    } else if (k.equals("$and")) {
                        List<String> ands2 = new ArrayList<String>();
                        for (int i = 0 ; i < narr.length() ; i++) {
                            JSONObject andj = narr.optJSONObject(i);
                            if (andj == null) throw new QueryParserException("$and has bad elements");
                            ands2.add(whereClause(andj));  //recurse
                        }
                        ands.add(" (" + String.join(" AND ", ands2) + ") ");
                    } else if (k.equals("$in")) {
                        List<String> ins = new ArrayList<String>();
                        for (int i = 0 ; i < narr.length() ; i++) {
                            String val = narr.optString(i, null);
                            if (val != null) ins.add(val);
                        }
                        ands.add(" IN (" + String.join(", ", ins) + ") ");
                    } else {
                        throw new QueryParserException("bad JSONArray value");
                    }
                } else {
                    if (k.equals("$lt")) {
                        ands.add(" < " + qry.get(k));
                    } else if (k.equals("$gt")) {
                        ands.add(" > " + qry.get(k));
                    } else if (k.equals("$lte")) {
                        ands.add(" <= " + qry.get(k));
                    } else if (k.equals("$gte")) {
                        ands.add(" >= " + qry.get(k));
                    } else if (k.equals("$ne")) {
                        ands.add(" != " + qry.get(k));
                    } else {
                        ands.add("(" + k + " = " + qry.get(k) + ")");
                    }
                }
            }
        }
        return String.join(" AND ", ands);
    }


    public static String wcHashCode(String s) {
        if (s == null) return "";
        //return Integer.toHexString(s.hashCode()) + "[" + s + "]";
        return Integer.toHexString(s.hashCode());
    }

    //this should only be called on the top-level qry, not recursed within
    public static String wcHashSmall(JSONObject qry) throws QueryParserException {
        String h = wcHash(qry);
        return DigestUtils.md5Hex(h) + h.length();
    }

    public static String wcHash(JSONObject qry) throws QueryParserException {
        if (qry == null) return "<0>";  //snh
        List<String> ands = new ArrayList<String>();
        List<String> keys = new ArrayList<String>(qry.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            JSONObject next = qry.optJSONObject(k);
            if (next != null) {
                ands.add(wcHashCode(k) + ":" + wcHash(next));
            } else {
                JSONArray narr = qry.optJSONArray(k);
                if (narr != null) {
                    if (k.equals("$or") || k.equals("$and")) {
                        List<String> ors = new ArrayList<String>();
                        for (int i = 0 ; i < narr.length() ; i++) {
                            JSONObject orj = narr.optJSONObject(i);
                            if (orj == null) throw new QueryParserException("$or/$and has bad elements");
                            ors.add(wcHash(orj));
                        }
                        Collections.sort(ors);
                        ands.add(wcHashCode(k) + ":" + String.join(":", ors));
                    } else {
                        List<String> list = new ArrayList<String>();
                        for (int i = 0 ; i < narr.length() ; i++) {
                            list.add(wcHashCode(narr.get(i).toString()));
                        }
                        Collections.sort(list);
                        ands.add(wcHashCode(k) + ":" + String.join(":", list));
                    }
                } else {
                    ands.add(wcHashCode(k) + ":" + wcHashCode(qry.get(k).toString()));
                }
            }
        }
        return String.join(":", ands);
    }


}


//JSONObject q = new JSONObject("{ \"val\": { \"$in\": [3,4,5,6] }, \"foo\": null, \"cat\": \"monty\", \"dog\": \"ruddy\", \"$or\": [ { \"color\": { \"$lt\": 99 } }, {\"colour\": { \"$ne\": 2 } } ], \"$and\": [ {\"fu\": {\"$lte\": 3} }, {\"fu\": {\"$ne\": null} }, {\"fuu\": null} ]  }");


