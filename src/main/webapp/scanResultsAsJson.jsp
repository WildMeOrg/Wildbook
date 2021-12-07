<%@ page contentType="application/json; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
java.io.File,
java.util.List,
org.json.JSONObject,
java.nio.file.Files,
java.nio.charset.Charset,
org.json.XML,
org.apache.commons.lang3.StringUtils
              "
%><%

String context = ServletUtilities.getContext(request);
String rootDir = getServletContext().getRealPath("/");
String baseDir = ServletUtilities.dataDir(context, rootDir);

//   e.g. wildbook_data_dir/encounters/f/c/fccd603c-6589-45c2-8539-ff45c2ef3571/lastFullRightScan.xml
String path = request.getQueryString();
if (path == null) {
    out.println("{\"error\": \"missing path\"}");
    return;
}
String fullPath = baseDir + "/" + path.replaceAll("\\.\\.", "");  //no updir hankypanky;
if (!fullPath.endsWith(".xml")) {
    System.out.println("ERROR: scanResultsAsJson - invalid path (no xml): " + fullPath);
    out.println("{\"error\": \"invalid path\"}");
    return;
}

File f = new File(fullPath);
if (!f.exists()) {
    System.out.println("ERROR: scanResultsAsJson - file does not exist: " + fullPath);
    out.println("{\"error\": \"invalid path\"}");
    return;
}

try {
    List<String> lines = Files.readAllLines(f.toPath(), Charset.defaultCharset());
    JSONObject j = org.json.XML.toJSONObject(StringUtils.join(lines, ""));
    out.println(j);
} catch (Exception ex) {
    System.out.println("ERROR: scanResultsAsJson - file " + fullPath + " threw " + ex.toString());
    out.println("{\"error\": \"error parsing xml\"}");
    return;
}


%>
