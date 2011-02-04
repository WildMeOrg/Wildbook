# Shepherd Project #

The Shepherd project is a web application that supports 

* input of mark-recapture records
* management of these records
* analysis of these records, including spot analysis techniques to correlate sightings of individuals 

The shepherd project is the basis of the [ECOCEAN](http://ecocean.org "ECOCEAN") site, and has been used to collect and analyze numerous whaleshark sightings.

# Getting Started #

There are currently no binary distributions of the shepherd project, however building is quite simple. You'll need 

* a Java webapp container (we've tested with Jetty and Tomcat, but others should work
* a recent version of ant (tested using 1.8.1)

To build 

1. Clone the project from github by executing "git clone git://github.com/holmbergius/Shepherd-Project.git"
2. Run ant package from the project directory
3. Copy target/shepherd-alpha2.war to your containers deploy directory (webapps for Tomcat and Jetty)
4. Set up a security realm named "Shepherd Realm" for your container (see instructions for [Jetty](http://docs.codehaus.org/display/JETTY/Realms "Jetty Realms") and [Tomcat](http://tomcat.apache.org/tomcat-6.0-doc/realm-howto.html "Tomcat Realms")
5. Start your container
6. Go to http://localhost:8080/shepherd-alpha2/ (note the port number may differ based on your container)

This will start a running, _transient_ instance of the shepherd project. Transient means that all data will be destroyed when your container stops. If you would like data preserved across container restarts, you'll need to set up a persistent database.

## Database Configuration##

To set up a specific database configuration you will need to modify the src/bundles/en/commonConfiguration.properties file. Look for the lines that begin with datanucleus. The default configuration is for an embedded Derby database, but there are example lines for MySQL commented out. Enter appropriate driver, connection, user, and password values. Then repackage the webapp (ant package), copy the webapp to your container, and restart your container.

## Jetty Notes ##

Note that by default Jetty uses a tmp directory to store exploded webapps. This tmp directory is deleted when the container is stopped. Currently shepherd photo uploads go into the exploded webapp directory, meaning they disappear when the container is stopped. To avoid this, create a "work" directory in your Jetty home directory. Jetty will use this directory instead of /tmp, and will not remove it on exit.

## Wiki integration ##

Coming soon!

# More help #

Currently the best way to get help is by opening an issue at [https://github.com/holmbergius/Shepherd-Project/issues](https://github.com/holmbergius/Shepherd-Project/issues)
