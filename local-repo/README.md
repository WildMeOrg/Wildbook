This library is still needed to support some spotmatching, such as `encounters/encounterSpotVisualizer.jsp`, which will fail without it.

It also seems to somehow be utilized in the _filter_ in `web.xml` which uses `/di/ImgFilter/`. Without this filter, tomcat will run and then
fail with cryptic messages in secondary logs.
