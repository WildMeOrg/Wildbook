# Containerized Wildbook Installation

Two different use cases for dockerized Wildbook are presented in these subdirectories:

- `deploy/` - run Wildbook and required docker images for testing/production usage
- `development/` - run docker images necessary to launch _wildbook.war_ file developed via java/maven

# Local tomcat usage

If you are running tomcat locally (not using docker), in order to access it as `http://localhost:8080/` (rather than with `/wildbook` trailing directory),
you should modify the `<Host>...</Host>` block of tomcat's `conf/server.xml` to contain the following:

```
	<Context docBase="wildbook" path="" />
	<Context docBase="wildbook_data_dir" path="/wildbook_data_dir" />
```

