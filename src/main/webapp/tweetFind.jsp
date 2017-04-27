<%@ page contentType="text/plain; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.ArrayList,
java.util.List,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONObject,
org.json.JSONArray,
org.ecocean.identity.IBEISIA,
twitter4j.QueryResult,
twitter4j.Status,

org.ecocean.media.*
              "
%>




<%
/* note this is kinda experimental... not really production.  you probably want instead to look at other tweet*jsp for now */
String baseUrl = null;
try {
    baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
} catch (java.net.URISyntaxException ex) {}

JSONObject rtn = new JSONObject("{\"success\": false}");

TwitterUtil.init(request);
Shepherd myShepherd = new Shepherd("context0");
TwitterAssetStore tas = TwitterAssetStore.find(myShepherd);
if (tas == null) {
	rtn.put("error", "no TwitterAssetStore");
	out.println(rtn);
	return;
}

long sinceId = 832273339657785300L;
rtn.put("sinceId", sinceId);
QueryResult qr = TwitterUtil.findTweets("whaleshark filter:media", sinceId);
JSONArray tarr = new JSONArray();
for (Status tweet : qr.getTweets()) {
	JSONObject p = new JSONObject();
	p.put("id", tweet.getId());
	MediaAsset ma = tas.find(p, myShepherd);
	if (ma != null) {
		System.out.println(ma + " exists for tweet id=" + tweet.getId() + "; skipping");
		continue;
	}
	JSONObject jtweet = TwitterUtil.toJSONObject(tweet);
	if (jtweet == null) continue;
	JSONObject ents = jtweet.optJSONObject("entities");
	if (ents == null) continue;
	JSONObject tj = new JSONObject();  //just for output purposes
	tj.put("tweet", TwitterUtil.toJSONObject(tweet));
	JSONArray emedia = null;
	if (ents != null) emedia = ents.optJSONArray("media");
	if ((ents == null) || (emedia == null) || (emedia.length() < 1)) continue;

	ma = tas.create(Long.toString(tweet.getId()));  //parent (aka tweet)
	ma.addLabel("_original");
	MediaAssetMetadata md = ma.updateMetadata();
	MediaAssetFactory.save(ma, myShepherd);
	tj.put("maId", ma.getId());
	tj.put("metadata", ma.getMetadata().getData());
	System.out.println(tweet.getId() + ": created tweet asset " + ma);
	List<MediaAsset> mas = TwitterAssetStore.entitiesAsMediaAssets(ma);
	if ((mas == null) || (mas.size() < 1)) {
		System.out.println(tweet.getId() + ": no entity assets?");
	} else {
		JSONArray jent = new JSONArray();
		for (MediaAsset ent : mas) {
			JSONObject ej = new JSONObject();
			MediaAssetFactory.save(ent, myShepherd);
    			String taskId = IBEISIA.IAIntake(ent, myShepherd, request);
			System.out.println(tweet.getId() + ": created entity asset " + ent + "; detection taskId " + taskId);
			ej.put("maId", ent.getId());
			ej.put("taskId", taskId);
			jent.put(ej);
		}
		tj.put("entities", jent);
	}
	tarr.put(tj);
}

rtn.put("success", true);
rtn.put("data", tarr);
out.println(rtn);


/*
String ids[] = null;
String forminput = request.getParameter("ids");
if ((forminput != null) && !forminput.equals("")) {
	ids = forminput.split("[^0-9]");
} else {
	ids = request.getParameterValues("id");
}
if ((ids == null) || (ids.length < 1)) {
	out.println("<h1>pass <b>?id=A&id=B...</b> with tweet ids or <b>enter below</b></h1><form method=\"post\"><textarea style=\"width: 25%; height: 30%;\" name=\"ids\" placeholder=\"twitter ids\"></textarea><br /><input type=\"submit\" value=\"create\" /></form>");
	return;
}


ArrayList<MediaAsset> detectMAs = new ArrayList<MediaAsset>();

for (int i = 0 ; i < ids.length ; i++) {
	out.println("<hr /><h1>" + ids[i] + "</h1>");
	MediaAsset ma = tas.find(p, myShepherd);
	long idLong = -1;
        try {
        	idLong = Long.parseLong(ids[i]);
        } catch (NumberFormatException ex) {
	}
	if (idLong < 0) {
		out.println("<b>could not convert to long:</b> " + ids[i]);
	} else if (ma != null) {
		out.println("<b>tweet already stored:</b> " + ma);
	} else {
		twitter4j.Status tweet = TwitterUtil.getTweet(idLong);
		JSONObject jtweet = TwitterUtil.toJSONObject(tweet);
		if ((tweet == null) || (jtweet == null)) {  //or other weirdness?
			out.println("could not getTweet or parse json thereof");
		} else {
			JSONObject ents = jtweet.optJSONObject("entities");
			JSONArray emedia = null;
			if (ents != null) emedia = ents.optJSONArray("media");
			if ((ents == null) || (emedia == null) || (emedia.length() < 1)) {
				out.println("could not find .entities.media on tweet data <pre>" + jtweet.toString() + "</pre>");
			} else {
				out.println("<b>media found: " + emedia.length() + "</b>");
				//now we do the real thing!
				ma = tas.create(ids[i]);
				ma.addLabel("_original");
				MediaAssetMetadata md = ma.updateMetadata();
				out.println("<p>" + ma + "</p>");
				MediaAssetFactory.save(ma, myShepherd);
				System.out.println("created " + ma);
				out.println("<p><b>Tweet asset:</b> <a title=\"" + ma + "\" href=\"obrowse.jsp?type=MediaAsset&id=" + ma.getId() + "\">" + ma.getId() + "</a>; entities:<ul>");
out.println("<xmp>" + ma.getMetadata().getDataAsString() + "</xmp>");
				List<MediaAsset> mas = TwitterAssetStore.entitiesAsMediaAssets(ma);
				if ((mas == null) || (mas.size() < 1)) {  // "should never happen"
					out.println("<li>no media entities</li>");
				} else {
					for (MediaAsset ent : mas) {
                    				ent.setDetectionStatus(IBEISIA.STATUS_PROCESSING);
						MediaAssetFactory.save(ent, myShepherd);
						detectMAs.add(ent);
						out.println("<li><a title=\"" + ent + "\" href=\"obrowse.jsp?type=MediaAsset&id=" + ent.getId() + "\">" + ent.getId() + "</a></li>");
					}
				}
				out.println("</ul></p>");
			}
		}
	}
}

if (detectMAs.size() < 1) {
	return;
}

boolean success = true;
String taskId = Util.generateUUID();
JSONObject res = new JSONObject();
res.put("taskId", taskId);
try {
    res.put("sendMediaAssets", IBEISIA.sendMediaAssets(detectMAs));
    JSONObject sent = IBEISIA.sendDetect(detectMAs, baseUrl);
    res.put("sendDetect", sent);
    String jobId = null;
    if ((sent.optJSONObject("status") != null) && sent.getJSONObject("status").optBoolean("success", false)) jobId = sent.optString("response", null);
    res.put("jobId", jobId);
    //IBEISIA.log(taskId, validIds.toArray(new String[validIds.size()]), jobId, new JSONObject("{\"_action\": \"initDetect\"}"), context);
} catch (Exception ex) {
    success = false;
    throw new IOException(ex.toString());
}

if (!success) {
    for (MediaAsset ma : mas) {
        ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
    }
}
*/


%>



