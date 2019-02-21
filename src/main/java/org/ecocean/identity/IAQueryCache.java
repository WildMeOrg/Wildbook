package org.ecocean.identity;
import org.ecocean.Annotation;
import org.ecocean.cache.QueryCache;
import org.ecocean.cache.QueryCacheFactory;


public class IAQueryCache extends QueryCache {
    private static String namePrefix = "IBEISIA.";
    public synchronized static QueryCache get(String context) {
        QueryCache qc = QueryCacheFactory.getQueryCache(context);
        return (IAQueryCache)qc;
    }



    private String generateQueryName(Annotation ann) {
        if (ann == null) return null;
        //FIXME wtf, should we use enc.taxonomyString() i guess?
        String val = ann.getIAClass();
        if (val == null) val = "UNKNOWN";
        return namePrefix + val;
    }
}

