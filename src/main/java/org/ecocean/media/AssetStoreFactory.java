package org.ecocean.media;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.jdo.*;
import org.ecocean.Shepherd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssetStoreFactory {
    private static Logger logger = LoggerFactory.getLogger(AssetStoreFactory.class);

    private AssetStoreFactory() {
        // do not instantiate
    }

    // Does not make an attempt to put the "default" one first (or generally order it in a preferred way????) based on configuration
    // this is because AssetStore.getDefault() currently uses the 0th element as default
    public static List<AssetStore> getStores(final Shepherd myShepherd) {
        System.out.println("ASF.getStores() is called for shepherd w context " +
            myShepherd.getContext());
        System.out.println("         and data directory name = " +
            myShepherd.getDataDirectoryName());
        Collection c;
        Extent ext = myShepherd.getPM().getExtent(AssetStore.class, true);
        Query all = myShepherd.getPM().newQuery(ext);
        try {
            c = (Collection)(all.execute());
        } catch (Exception npe) {
            npe.printStackTrace();
            all.closeAll();
            return null;
        }
        List<AssetStore> s = new ArrayList<AssetStore>();
        for (Object obj : c) {
            s.add((AssetStore)obj);
        }
        System.out.println("Found # asset stores = " + s.size());
        all.closeAll();
        return s;
    }

    public static List<AssetStore> getStores2(final Shepherd myShepherd) {
        System.out.println("ASF.getStores() is called for shepherd w context " +
            myShepherd.getContext());
        System.out.println("         and data directory name = " +
            myShepherd.getDataDirectoryName());

        PersistenceManager pm = myShepherd.getPM();
        String actualContext = myShepherd.getContext();
        // String filter = "this.context == '"+actualContext+"'";
        Extent assClass = pm.getExtent(AssetStore.class, true);
        // Query acceptedAssetStores = pm.newQuery(assClass, filter);
        Query acceptedAssetStores = pm.newQuery(assClass);
        Collection c = (Collection)(acceptedAssetStores.execute());
        List<AssetStore> s = new ArrayList<AssetStore>();
        for (Object obj : c) {
            s.add((AssetStore)obj);
        }
        System.out.println("Found # asset stores = " + s.size());
        acceptedAssetStores.closeAll();
        return s;
    }

    public static void save(final AssetStore store) {
    }

    public static void delete(final AssetStore store) {
        AssetStore.remove(store);
    }

}
