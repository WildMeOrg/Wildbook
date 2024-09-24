package org.ecocean;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ContextConfiguration {
    private static final String CONTEXTS_PROPERTIES = "/bundles/contexts.properties";

    // class setup
    private static Properties props = new Properties();

    private static volatile int propsSize = 0;

    public static Properties getContextsProperties() {
        initialize();
        return props;
    }

    private static void initialize() {
        // set up the file input stream
        if (propsSize == 0) {
            loadProps();
        }
    }

    public static synchronized void refresh() {
        props.clear();
        propsSize = 0;
        loadProps();
    }

    private static void loadProps() {
        Properties localesProps = new Properties();

        try {
            localesProps.load(ContextConfiguration.class.getResourceAsStream(CONTEXTS_PROPERTIES));
            props = localesProps;
            propsSize = props.size();
            // System.out.println("     Context props are: "+props.toString());
        } catch (Exception ioe) {
            System.out.println("Hit an error loading contexts.properties.");
            ioe.printStackTrace();
        }
    }

    public static String getDataDirForContext(String context) {
        initialize();
        if (props.getProperty((context + "DataDir")) != null) {
            return props.getProperty((context + "DataDir"));
        }
        return null;
    }

    public static String getNameForContext(String context) {
        initialize();
        if (props.getProperty((context + "Name")) != null) {
            return props.getProperty((context + "Name"));
        }
        return null;
    }

    public static String getDefaultContext() {
        initialize();
        if (props.getProperty("defaultContext") != null) {
            return props.getProperty("defaultContext");
        }
        return "context0";
    }

    public static List<String> getContextNames() {
        List<String> names = new ArrayList<String>();
        int contextNum = 0;

        while (getNameForContext(("context" + contextNum)) != null) {
            names.add(getNameForContext(("context" + contextNum)));
            contextNum++;
        }
        return names;
    }

    public static List<String> getContextDomainNames(String contextName) {
        initialize();
        List<String> domainNames = new ArrayList<String>();
        int domainNum = 0;
        while (props.getProperty(contextName + "DomainName" + domainNum) != null) {
            domainNames.add(props.getProperty(contextName + "DomainName" + domainNum));
            domainNum++;
        }
        return domainNames;
    }

    public static String getVersion() {
        if (props.getProperty("application.version") != null) {
            return props.getProperty("application.version");
        }
        return "Version Unknown";
    }
}
