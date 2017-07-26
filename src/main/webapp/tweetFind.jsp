<%@ page contentType="text/plain; charset=utf-8" language="java"

import="org.ecocean.*,
java.util.ArrayList,
java.io.FileNotFoundException,
java.io.IOException,
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
twitter4j.*,
org.ecocean.servlet.ServletUtilities,

org.ecocean.media.*,
org.ecocean.ParseDateLocation.*
              "
%>

<%
String baseUrl = null;
String tweeterScreenName = null;
Long tweetID = null;
Long mostRecentTweetID = null;
String rootDir = request.getSession().getServletContext().getRealPath("/");
String dataDir = ServletUtilities.dataDir("context0", rootDir);
Long sinceId = 890302524275662848L;

//Test parseLocation TODO remove this after testing complete
String dateTest = "Saw a whale on monday June 13, 2017";
String context = ServletUtilities.getContext(request);
String testTweetText = "Saw this cool humpback whale in the galapagos, Ecuador!";
String testTweetTextNonEnglish = "Ayer vi una ballena increible en los galapagos en mexico. Sé que no están en mexico. No sea camote.";
String textTweetGpsText = "saw a whale at 45.5938,-122.737 in ningaloo. #bestvacationever";
String testTweetMultipleLocations = "whale! In Phuket, Thailand!";
String testTweetNLPLocation = "land whale! In Nashville, tennessee!";


ArrayList<String> results = null;
results = ParseDateLocation.parseLocation(testTweetText, context);
// out.println("results from " + testTweetText + " is " + results);

results = ParseDateLocation.parseLocation(testTweetTextNonEnglish, context);
// out.println("results from " + testTweetTextNonEnglish + " is " + results);

results = ParseDateLocation.parseLocation(textTweetGpsText, context);
// out.println("results from " + textTweetGpsText + " is " + results);

results = ParseDateLocation.parseLocation(testTweetMultipleLocations, context);
// out.println("results from " + testTweetMultipleLocations + " is " + results);

results = ParseDateLocation.parseLocation(testTweetNLPLocation, context);
// out.println("results from " + testTweetNLPLocation + " is " + results);
//End test parseLocation TODO remove this after testing complete

try {
    baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
} catch (java.net.URISyntaxException ex) {}

JSONObject rtn = new JSONObject("{\"success\": false}");

Twitter twitterInst = TwitterUtil.init(request);
Shepherd myShepherd = new Shepherd(ServletUtilities.getContext(request));
myShepherd.setAction("tweetFind.jsp");

// Find or create TwitterAssetStore and make it persistent with myShepherd
TwitterAssetStore tas = TwitterAssetStore.find(myShepherd);
if(tas == null){
	myShepherd.beginDBTransaction();
	tas = new TwitterAssetStore("twitterAssetStore");
	myShepherd.getPM().makePersistent(tas);
	myShepherd.commitDBTransaction();
}

try{
	// the timestamp is written with a new line at the end, so we need to strip that out before converting
  String timeStampAsText = Util.readFromFile(dataDir + "/twitterTimeStamp.txt");
  timeStampAsText = timeStampAsText.replace("\n", "");
  sinceId = Long.parseLong(timeStampAsText, 10);
} catch(FileNotFoundException e){
	e.printStackTrace();
} catch(IOException e){
	e.printStackTrace();
} catch(NumberFormatException e){
	e.printStackTrace();
}
rtn.put("sinceId", sinceId);
out.println("sinceId is " + sinceId);
QueryResult qr = TwitterUtil.findTweets("@wildmetweetbot", sinceId);
JSONArray tarr = new JSONArray();
// out.println(qr.getTweets().size());

//Begin loop through the each of the tweets since the last timestamp
out.println("size of the arrayList of statuses is " + Integer.toString(qr.getTweets().size()));
for(int i = 0 ; i<qr.getTweets().size(); i++){
  Status tweet = qr.getTweets().get(i);

  if(i == 0){
    mostRecentTweetID = (Long) tweet.getId();
  }
  tweetID = (Long) tweet.getId();
  if(tweetID == null){
    out.println("tweetID is null. Skipping");
    continue;
  }

  out.println("newIncomingTweet: " + tweetID);

	JSONObject p = new JSONObject();
	p.put("id", tweet.getId());

  // Attempt to find MediaAsset for tweet, and skip media asset creation if it exists
	MediaAsset ma = tas.find(p, myShepherd);
	if (ma != null) {
		System.out.println(ma + " exists for tweet id=" + tweet.getId() + "; skipping");
    // out.println("media asset already exists. Skipping");
		continue;
	}

	// Check for tweet and entities
	JSONObject jtweet = TwitterUtil.toJSONObject(tweet);
	if (jtweet == null){
    continue;
  }
  try{
    out.println(jtweet.getString("text"));
  }catch(Exception e){
    e.printStackTrace();
  }


	JSONObject ents = jtweet.optJSONObject("entities");
	if (ents == null){
    // out.println("entities is null. Skipping");
    continue;
  }

  try{
    tweeterScreenName = jtweet.optJSONObject("user").getString("screen_name");
    if(tweeterScreenName == null){
      // out.println("screen name is null. Skipping");
      continue;
    }
  } catch(Exception e){
    e.printStackTrace();
  }

	JSONObject tj = new JSONObject();  //just for output purposes
	tj.put("tweet", TwitterUtil.toJSONObject(tweet));

	JSONArray emedia = null;
	if (ents != null) emedia = ents.optJSONArray("media");
  if((emedia == null) || (emedia.length() < 1)){
    //tweet doesn't have media
    Thread.sleep(30000);
    TwitterUtil.sendCourtesyTweet(tweeterScreenName, "", twitterInst, tweetID);
    // TwitterUtil.sendCourtesyTweet(tweeterScreenName, "", twitterInst, tweetID+1);
    // out.println("emedia is null or of length <1. Skipping");
    continue;
  }

  for(int j=0; j<emedia.length(); j++){
    // Boolean hasBeenTweeted = false;
    JSONObject jent = emedia.getJSONObject(j);
    String mediaType = jent.getString("type");
    try{
      if(mediaType.equals("photo")){
        Thread.sleep(30000);
        TwitterUtil.sendCourtesyTweet(tweeterScreenName, mediaType, twitterInst, tweetID);
      }
    } catch(Exception e){
      e.printStackTrace();
    }
  }

  // Attempt to create media asset, rollback DB transaction if it fails
	// myShepherd.beginDBTransaction();
	// try{
	// 	ma = tas.create(Long.toString(tweet.getId()));  //parent (aka tweet)
	// 	ma.addLabel("_original");
	// 	MediaAssetMetadata md = ma.updateMetadata();
	// 	MediaAssetFactory.save(ma, myShepherd);
		// tj.put("maId", ma.getId());
		// tj.put("metadata", ma.getMetadata().getData());
		// System.out.println(tweet.getId() + ": created tweet asset " + ma);
	// 	myShepherd.commitDBTransaction();
	// } catch(Exception e){
		// myShepherd.rollbackDBTransaction();
		// e.printStackTrace();
	// }

	// Save entities as media assets to shepherd database
	// List<MediaAsset> mas = TwitterAssetStore.entitiesAsMediaAssets(ma);
	// if ((mas == null) || (mas.size() < 1)) {
  //   out.println(tweet.getId() + ": no entity assets?");
	// 	System.out.println(tweet.getId() + ": no entity assets?");
	// } else {
	// 	JSONArray jent = new JSONArray();
		// for (MediaAsset ent : mas) {
// 			myShepherd.beginDBTransaction();
			// try {
				// JSONObject ej = new JSONObject();
// 				MediaAssetFactory.save(ent, myShepherd);
// 				String taskId = IBEISIA.IAIntake(ent, myShepherd, request);
//         out.println(tweet.getId() + ": created entity asset " + ent + "; detection taskId " + taskId);
// 				System.out.println(tweet.getId() + ": created entity asset " + ent + "; detection taskId " + taskId);
				// ej.put("maId", ent.getId());
				// ej.put("taskId", taskId);
				// jent.put(ej);
// 				myShepherd.commitDBTransaction();
			// } catch(Exception e){
// 				myShepherd.rollbackDBTransaction();
				// e.printStackTrace();
			// }
		// }
		// tj.put("entities", jent);
// 	}
	tarr.put(tj);
}
//End looping through the tweets

// Write new timestamp to track last twitter pull
Long newSinceIdString;
if(mostRecentTweetID == null){
	newSinceIdString = sinceId;
} else {
	newSinceIdString = mostRecentTweetID;
}
try{
  Util.writeToFile(Long.toString(newSinceIdString), dataDir + "/twitterTimeStamp.txt");
  out.println("wrote a new twitterTimeStamp: " + newSinceIdString);
} catch(FileNotFoundException e){
  e.printStackTrace();
}

rtn.put("success", true);
rtn.put("data", tarr);
// out.println(rtn);

myShepherd.closeDBTransaction();

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
