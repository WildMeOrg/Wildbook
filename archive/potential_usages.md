## the following are cases where (potentially?) active code still references items in the archive/ dir:

- src/main/webapp/javascript/flukeScanEnd.js:        url: '**flukeScanIntersectVisualization.jsp**?enc1=' + enc1 + '&enc2=' + enc2,
- src/main/webapp/javascript/imageDisplayTools.js:  				window.location.href = '**matchResults.jsp**?taskId=' + d.taskID;
- src/main/java/org/ecocean/CommonConfiguration.java:      originalString=originalString.replaceAll("REMOVEME",("\n\n" + getProperty("removeEmailString",context) + "\nhttp://" + getURLLocation(request) + "/**removeEmailAddress.jsp**?hashedEmail=" + Encounter.getHashOfEmailString(emailAddress)));
- src/main/java/org/ecocean/servlet/EncounterCreate.java:            taskLinks += " - " + linkPrefix + "/encounters/**matchResultsMulti.jsp**?taskId=" + id + "&accessKey=" + accessKey + "\n";
- src/main/java/org/ecocean/servlet/EncounterCreate.java:            taskLinksHtml += " + linkPrefix + "/encounters/**matchResultsMulti.jsp**?taskId=" + id + "&accessKey=" + accessKey + "\"(" + tcount + ") " + ((i >= fname.length) ? "Result " + (i+1) : fname[i]) + "</a></li>\n";

### Conclusions/Changes:

- `flukeScanEnd.js` not used anywhere, rm'ed
- `imageDisplayTools.js` changed to reference `iaResults.jsp` instead
- url still seems in use via `CommonConfiguration.appendEmailRemoveHashString()` - put back into webapps/ dir
- updated to reference `iaResults.jsp` instead
- ditto

