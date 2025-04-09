package org.ecocean.shepherd.core;

import org.ecocean.LinkedProperties;
import org.ecocean.Organization;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.servlet.ServletUtilities;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

public class ShepherdProperties {

    // The "catalina.home" property is the path to the Tomcat install ("Catalina Server").
    // This is often set as the working directory.
    // Property files are resolved relative to this directory.
    // Can throw IllegalStateException if Tomcat is not running ... i.e., unit testing
    private static Path getPropertiesBase() {
        String catalinaHome = System.getProperty("catalina.home");
        if (catalinaHome == null) {
            throw new IllegalStateException("catalina.home system property is not set.");
        }
        return Paths.get(catalinaHome);
    }

    public static Properties getProperties(String fileName) {
        return getProperties(fileName, "en");
    }

    public static Properties getProperties(String fileName, String langCode) {
        return getProperties(fileName, langCode, "context0");
    }

    public static Properties getProperties(String fileName, String langCode, String context) {
        return getProperties(fileName, langCode, context, null);
    }

    // This method works just like getProperties, except it gives priority to your organization-specific .properties overwrite file
    // + It checks the logged-in user to see if they are in an organization with an overwrite .properties file
    // + If they are, this overwrite .properties file gets priority over the default file.
    // + overwrite files are in wildbook_data_dir/classes/bundles/<organization>.properties
    public static Properties getOrgProperties(String fileName, String langCode, String context,
        HttpServletRequest request) {
        return getProperties(fileName, langCode, context, getOverwriteStringForUser(request));
    }

    public static Properties getOrgProperties(String fileName, String langCode, String context,
        HttpServletRequest request, Shepherd myShepherd) {
        return getProperties(fileName, langCode, context,
                getOverwriteStringForUser(request, myShepherd));
    }

    public static Properties getOrgProperties(String fileName, String langCode,
        HttpServletRequest request) {
        return getOrgProperties(fileName, langCode, ServletUtilities.getContext(request), request);
    }

    // ONLY the overwrite props, not any other .properties
    public static Properties getOverwriteProps(HttpServletRequest request) {
        String filename = getOverwriteStringForUser(request);

        if (filename == null) return null;
        String fullPath = "webapps/wildbook_data_dir/WEB-INF/classes/bundles/" + filename;
        return (Properties)loadProperties(fullPath);
    }

    public static String getOverwriteStringForUser(HttpServletRequest request,
        Shepherd myShepherd) {
        if (request == null) return null;
        // now try based on the user's organizations
        User user = myShepherd.getUser(request);
        if (user == null) return null;
        return getOverwriteStringForUser(user);
    }

    public static String getOverwriteStringForUser(HttpServletRequest request) {
        Shepherd myShepherd = new Shepherd(request);

        myShepherd.setAction("getOverwriteStringForUser");
        myShepherd.beginDBTransaction();
        String myString = getOverwriteStringForUser(request, myShepherd);
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        return myString;
    }

    public static String getOverwriteStringForUser(User user) {
        if (user == null || user.getOrganizations() == null) return null;
        for (Organization org : user.getOrganizations()) {
            String name = org.getName();
            if (name == null) continue;
            name = name.toLowerCase();
        }
        return null;
    }

    public static boolean userHasOverrideString(User user) {
        return (getOverwriteStringForUser(user) != null);
    }

    public static boolean userHasOverrideString(HttpServletRequest request) {
        return (getOverwriteStringForUser(request) != null);
    }

    public static Properties getProperties(String fileName, String langCode, String context,
        String overridePrefix) {
        // initialize props as empty (no override provided) or the default values (if override provided)
        // initializing
        boolean verbose = (Util.stringExists(overridePrefix));
        String shepherdDataDir = "wildbook_data_dir";
        Properties contextsProps = getContextsProperties();

        if (contextsProps.getProperty(context + "DataDir") != null) {
            shepherdDataDir = contextsProps.getProperty(context + "DataDir");
        }
        if (Util.stringExists(langCode) && !langCode.endsWith("/")) langCode += "/";
        // we load defaultProps from the (on git) webapps/wildbook location
        String defaultPathStr = "webapps/wildbook/WEB-INF/classes/bundles/" + langCode + fileName;
        // defaultProps are selectively overwritten from the override dir. This way we can keep
        // security-sensitive prop off github in the shepherdDataDir. Only need to store the sensitive fields there
        // bc we fall-back to the defaultProps
        String overridePathStr = "webapps/" + shepherdDataDir + "/WEB-INF/classes/bundles/" +
            langCode + fileName;

        // System.out.printf("getProperties has built strings %s and %s.\n",defaultPathStr, overridePathStr);
        Properties defaultProps = loadProperties(defaultPathStr);
        if (defaultProps == null) {
            // could not find props w this lang code, try english
            if (Util.stringExists(langCode) && !langCode.contains("en")) {
                defaultPathStr = "webapps/wildbook/WEB-INF/classes/bundles/en/" + fileName;
                System.out.printf(
                    "\t Weird Shepherd.properties non-english case reached with langCode %s. Trying again with path %s.\n",
                    langCode, defaultPathStr);
                defaultProps = loadProperties(defaultPathStr);
            } else {
                System.out.printf(
                    "Super weird case met in ShepherdProperties.getProps(%s, %s, %s, %s). Returning generated default props.\n",
                    fileName, langCode, context, overridePrefix);
            }
        }
        Properties props = loadProperties(overridePathStr, defaultProps);
        if (!Util.stringExists(overridePrefix)) return (Properties)props;
        String customUserPathString = "webapps/" + shepherdDataDir + "/WEB-INF/classes/bundles/" +
            overridePrefix;
        return (Properties)loadProperties(customUserPathString, props);
    }

        /**
          * Loads the properties file at the specified path, returning a default value upon failure.
          * @param pathStr The path to the properties file, relative to the Tomcat install.
          * @param defaults The value to return if the properties cannot be read or parsed.
          * @return The parsed properties from the path provided, or the default value.
         * */
        public static Properties loadProperties(@Nonnull String pathStr, Properties defaults) {

        File propertiesFile = getPropertiesBase().resolve(pathStr).toFile();
        if (!propertiesFile.exists()) return defaults;
        try {
            InputStream inputStream = Files.newInputStream(propertiesFile.toPath());
            LinkedProperties props = (defaults !=
                null) ? new LinkedProperties(defaults) : new LinkedProperties();
            props.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            inputStream.close();
            return props;
        } catch (Exception e) {
            System.out.println("Exception on loadProperties()");
            e.printStackTrace();
        }
        return defaults;
    }

    @Nullable
    public static Properties loadProperties(@Nonnull String pathStr) {
        return loadProperties(pathStr, null);
    }

    public static Properties getContextsProperties() {
        LinkedProperties props = new LinkedProperties();

        try {
            InputStream inputStream = ShepherdProperties.class.getResourceAsStream(
                "/bundles/contexts.properties");
            props.load(inputStream);
            inputStream.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return (Properties)props;
    }

    public static List<String> getIndexedPropertyValues(Properties props, String baseKey) {
        List<String> list = new ArrayList<String>();
        boolean hasMore = true;
        int index = 0;

        while (hasMore) {
            String key = baseKey + index++;
            String value = props.getProperty(key);
            if (value != null) {
                if (value.length() > 0) {
                    list.add(value.trim());
                }
            } else {
                hasMore = false;
            }
        }
        return list;
    }
}
