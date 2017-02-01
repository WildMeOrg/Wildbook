<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.List,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONObject,

org.ecocean.media.*
              "
%>




<%

TwitterUtil.init(request);
Shepherd myShepherd = new Shepherd("context0");
TwitterAssetStore tas = TwitterAssetStore.find(myShepherd);
if (tas == null) {
	out.println("<h1>no TwitterAssetStore available</h1>");
	return;
}

String ids[] = request.getParameterValues("id");
if ((ids == null) || (ids.length < 1)) {
	out.println("<h1>pass <b>?id=A&id=B...</b> with tweet ids</h1>");
	return;
}

for (int i = 0 ; i < ids.length ; i++) {
	MediaAsset ma = tas.create(ids[i]);
	ma.addLabel("_original");
	MediaAssetMetadata md = ma.updateMetadata();
	out.println("<p>" + ma + "</p>");
	MediaAssetFactory.save(ma, myShepherd);
	System.out.println("created " + ma);
	out.println("<p><a href=\"obrowse.jsp?type=MediaAsset&id=" + ma.getId() + "\">" + ma.getId() + "</a> " + ma + "<ul>");
out.println("<xmp>" + ma.getMetadata().getDataAsString() + "</xmp>");
	List<MediaAsset> mas = TwitterAssetStore.entitiesAsMediaAssets(ma);
	if ((mas == null) || (mas.size() < 1)) {
		out.println("<li>no media entities</li>");
	} else {
		for (MediaAsset ent : mas) {
			MediaAssetFactory.save(ent, myShepherd);
			out.println("<li><a href=\"obrowse.jsp?type=MediaAsset&id=" + ent.getId() + "\">" + ent.getId() + "</a> " + ent + "</li>");
		}
	}
	out.println("</ul></p>");
}

%>



