<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.HashMap,
java.util.Iterator,
java.net.URLEncoder,
java.util.ArrayList,
org.json.JSONObject,
java.io.UnsupportedEncodingException,

org.ecocean.identity.IBEISIA
              "
%>


<%!
    /*
        NOTE!  this is an "internal use" test script that is probably very boring for anyone else to use.
        it is for tracking down a very specific bug that seems to be java-version-based.   jv might have more info for ya.
    */


    //this is replicated from RestClient.java
    private static String getPostDataString(JSONObject obj) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        Iterator<?> keys = obj.keys();
        while (keys.hasNext()) {
            String key = (String)keys.next();
            if (first) {
                first = false;
            } else {
                result.append("&");
            }
            try {
                result.append(URLEncoder.encode(key, "UTF-8"));
                result.append("=");
                //  THIS LINE seems to be where the thing fails. specifically:  obj.get(key).toString() .... !!!???
                //    this varies depending on java version -- or something?
                result.append(URLEncoder.encode(obj.get(key).toString(), "UTF-8"));
            } catch (UnsupportedEncodingException uee) {
                System.out.println("caught exception on key " + key + ": " + uee.toString());
            }
        }
//////System.out.println("------- getPostDataString=(\n" + result.toString() + "\n)--------\n");
        return result.toString();
    }


    //from IBEISIA.java
    public static JSONObject hashMapToJSONObject(HashMap<String,ArrayList> map) {
        if (map == null) return null;
        //return new JSONObject(map);  // this *used to work*, i swear!!!
        JSONObject rtn = new JSONObject();
        for (String k : map.keySet()) {
            rtn.put(k, map.get(k));
        }
        return rtn;
    }

    public static JSONObject hashMapToJSONObject2(HashMap<String,Object> map) {   //note: Object-flavoured
        if (map == null) return null;
        //return new JSONObject(map);  // this *used to work*, i swear!!!
        JSONObject rtn = new JSONObject();
        for (String k : map.keySet()) {
            rtn.put(k, map.get(k));
        }
        return rtn;
    }

%>

<%


HashMap<String,Object> input1 = new HashMap<String,Object>();

ArrayList<Double> list1 = new ArrayList<Double>();
list1.add(1.0);
list1.add(2.0);
list1.add(3.0);
input1.put("listDouble", list1);

ArrayList<String> list2 = new ArrayList<String>();
list2.add("test1");
list2.add("test2");
list2.add(Util.generateUUID());
input1.put("listString", list2);

out.println("<p>input1.toString(): <b>" + input1.toString() + "</b></p>");

JSONObject jdirect = new JSONObject(input1);
out.println("<hr /><h2>'direct' conversion - new JSONObject(input1)</h2>");
out.println("<p>jdirect.toString(): <b>" + jdirect.toString() + "</b></p>");


String postString = getPostDataString(jdirect);
out.println("<p>getPostDataString(jdirect): <b>" + postString + "</b></p>");

String conv = java.net.URLDecoder.decode(postString, "UTF-8");
out.println("<p style=\"color: #02a;\">above, converted: <b>" + conv + "</b></p>");

out.println("<hr /><h2>'hack' conversion - hashMapToJSONObject(input1)</h2>");
JSONObject jconv = hashMapToJSONObject2(input1);
out.println("<p>jconv.toString(): <b>" + jconv.toString() + "</b></p>");

postString = getPostDataString(jconv);
out.println("<p>getPostDataString(jconv): <b>" + postString + "</b></p>");

conv = java.net.URLDecoder.decode(postString, "UTF-8");
out.println("<p style=\"color: #a20;\">above, converted: <b>" + conv + "</b></p>");



%>


