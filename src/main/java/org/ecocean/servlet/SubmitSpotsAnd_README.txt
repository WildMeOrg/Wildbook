currently there are two similar servlets:

* src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java
* src/main/java/org/ecocean/servlet/SubmitSpotsAndTransformImage.java

the first one has its history in whaleshark.org spot-placing front end (encounterSpotTool.jsp)
and the second in the flukemapping tool (from caribwhale).  for "general usage" (whatever that means),
these things *should be* consilidated.  probably(?) the latter option saves things "better", but really
you should use the one that works with front end you want.

i.e. TODO: clean this mess up!
