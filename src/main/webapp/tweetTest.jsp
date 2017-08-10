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
org.ecocean.ParseDateLocation.*,
java.util.concurrent.ThreadLocalRandom"

%>

<%
String rootDir = request.getSession().getServletContext().getRealPath("/");
String dataDir = ServletUtilities.dataDir("context0", rootDir);
String testFileName = "/twitterTimeStampTestFile.txt";
long sinceId = 890302524275662848L;

Twitter twitterInst = TwitterUtil.init(request);

// Test strings
String dateTest = "Saw a whale on monday June 13, 2017";
String dateTest2 = "Saw a whale on 6/13/2017";
String context = ServletUtilities.getContext(request);
String testTweetText = "Saw this cool humpback whale in the galapagos, Ecuador!";
String testTweetTextNonEnglish = "Ayer vi una ballena increible en los galapagos en mexico. Sé que no están en mexico. No sea camote.";
String textTweetGpsText = "saw a whale at 45.5938,-122.737 in ningaloo. #bestvacationever";
String testTweetMultipleLocations = "whale! In Phuket, Thailand!";
String testTweetNLPLocation = "land whale! In Nashville, tennessee!";
String futureString = "Saw a whale on July 2, 2017. I'm going to see one tomorrow too! Tomorrow will be a better day for whale-watching.";
String pastString = "Saw a whale on July 2, 2017. I saw one yesterday, too! Yesterday's was cooler. Yesterday it was warm outside.";
String futureFirstString = "I'm going to see whales tomorrow! I saw one on July 3 2017 as well.";
String pastFirstString = "I saw a whale yesterday, and last week! I saw one on July 4 2017 as well.";
String yesterdayString = "Saw a whale yesterday.";

// Test methods
ArrayList<String> results = null;
results = ParseDateLocation.parseLocation(dateTest, context);
out.println("results from " + dateTest + " is " + results);

results = ParseDateLocation.parseLocation(dateTest2, context);
out.println("results from " + dateTest2 + " is " + results);

results = ParseDateLocation.parseLocation(testTweetText, context);
out.println("results from " + testTweetText + " is " + results);

results = ParseDateLocation.parseLocation(testTweetTextNonEnglish, context);
out.println("results from " + testTweetTextNonEnglish + " is " + results);

results = ParseDateLocation.parseLocation(textTweetGpsText, context);
out.println("results from " + textTweetGpsText + " is " + results);

results = ParseDateLocation.parseLocation(testTweetMultipleLocations, context);
out.println("results from " + testTweetMultipleLocations + " is " + results);

results = ParseDateLocation.parseLocation(testTweetNLPLocation, context);
out.println("results from " + testTweetNLPLocation + " is " + results);

out.println("Don't forget to conduct additional tests on an emulator with gps coordinates from somewhere in asia.");

// Testing tweetMethods
String randomNumStr = Integer.toString(ThreadLocalRandom.current().nextInt(1, 10000 + 1));
String randomNum2Str = Integer.toString(ThreadLocalRandom.current().nextInt(1, 10000 + 1));
TwitterUtil.sendDetectionAndIdentificationTweet("markaaronfisher", randomNumStr, twitterInst, randomNum2Str, true, true, "http://www.google.com");

randomNumStr = Integer.toString(ThreadLocalRandom.current().nextInt(1, 10000 + 1));
randomNum2Str = Integer.toString(ThreadLocalRandom.current().nextInt(1, 10000 + 1));
TwitterUtil.sendDetectionAndIdentificationTweet("markaaronfisher", randomNumStr, twitterInst, randomNum2Str, true, false, "");

randomNumStr = Integer.toString(ThreadLocalRandom.current().nextInt(1, 10000 + 1));
randomNum2Str = Integer.toString(ThreadLocalRandom.current().nextInt(1, 10000 + 1));
TwitterUtil.sendDetectionAndIdentificationTweet("markaaronfisher", randomNumStr, twitterInst, randomNum2Str, false, false, "");
// End Testing tweetMethods

// Timestamp test
try{
  Util.writeToFile(Long.toString(sinceId), dataDir + testFileName);
  out.println("wrote a new twitterTimeStamp: " + sinceId);
} catch(FileNotFoundException e){
  e.printStackTrace();
}

try{
	// the timestamp is written with a new line at the end, so we need to strip that out before converting
  String timeStampAsText = Util.readFromFile(dataDir + testFileName);
  timeStampAsText = timeStampAsText.replace("\n", "");
  sinceId = Long.parseLong(timeStampAsText, 10);
} catch(FileNotFoundException e){
	e.printStackTrace();
} catch(IOException e){
	e.printStackTrace();
} catch(NumberFormatException e){
	e.printStackTrace();
}

// Natural Language Processing (NLP) tests
String testWithFutureString = null;
String testWithPastString = null;
String testWithNoDate = null;
String testWithNoDateAndGPSCoordinates = null;
String testWithFutureFirstString = null;
String testWithPastFirstString = null;
String testWithYesterdayString = null;

try{
  testWithFutureString = ServletUtilities.nlpDateParse(futureString);
} catch(Exception e){
  e.printStackTrace();
}

try{
  testWithPastString = ServletUtilities.nlpDateParse(pastString);
} catch(Exception e){
  e.printStackTrace();
}

try{
  testWithNoDate = ServletUtilities.nlpDateParse(testTweetText);
} catch(Exception e){
  e.printStackTrace();
}

try{
  testWithNoDateAndGPSCoordinates = ServletUtilities.nlpDateParse(textTweetGpsText);
} catch(Exception e){
  e.printStackTrace();
}

try{
  testWithFutureFirstString = ServletUtilities.nlpDateParse(futureFirstString);
} catch(Exception e){
  e.printStackTrace();
}

try{
  testWithPastFirstString = ServletUtilities.nlpDateParse(pastFirstString);
} catch(Exception e){
  e.printStackTrace();
}

try{
  testWithYesterdayString = ServletUtilities.nlpDateParse(yesterdayString);
} catch(Exception e){
  e.printStackTrace();
}

try{
  out.println("Input String: " + futureString + " || Output: " + testWithFutureString);
} catch(Exception e){
  out.println("futureString");
  e.printStackTrace();
}
try{
  out.println("Input String: " + pastString + " || Output: " + testWithPastString);
} catch(Exception e){
  out.println("futureString");
  e.printStackTrace();
}
try{
  out.println("Input String: " + testTweetText + " || Output: " + testWithNoDate);
} catch(Exception e){
  out.println("testWithNoDate");
  e.printStackTrace();
}
try{
  out.println("Input String: " + textTweetGpsText + " || Output: " + testWithNoDateAndGPSCoordinates);
} catch(Exception e){
  out.println("testWithNoDateAndGPSCoordinates");
  e.printStackTrace();
}
try{
  out.println("Input String: " + futureFirstString + " || Output: " + testWithFutureFirstString);
} catch(Exception e){
  out.println("testWithNoDateAndGPSCoordinates");
  e.printStackTrace();
}
try{
  out.println("Input String: " + pastFirstString + " || Output: " + testWithPastFirstString);
} catch(Exception e){
  out.println("testWithNoDateAndGPSCoordinates");
  e.printStackTrace();
}
try{
  out.println("Input String: " + yesterdayString + " || Output: " + testWithYesterdayString);
} catch(Exception e){
  out.println("testWithNoDateAndGPSCoordinates");
  e.printStackTrace();
}
// End NLP tests
%>
