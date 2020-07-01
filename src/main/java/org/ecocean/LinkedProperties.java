package org.ecocean;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.HashMap;

public class LinkedProperties extends Properties {
    private final HashSet<Object> keys = new LinkedHashSet<Object>();

    public LinkedProperties() {
    }
    
    public LinkedProperties(Properties props) {
      super(props);
    }

    public Iterable<Object> orderedKeys() {
        return Collections.list(keys());
    }

    public Enumeration<Object> keys() {
        return Collections.<Object>enumeration(keys);
    }

    public Object put(Object key, Object value) {
        keys.add(key);
        return super.put(key, value);
    }
}