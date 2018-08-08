package org.ecocean.media;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import org.ecocean.Shepherd;
import javax.jdo.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssetStoreFactory {
    private static Logger logger = LoggerFactory.getLogger(AssetStoreFactory.class);

    ///////private static final String TABLENAME_ASSETSTORE = "assetstore";

    private AssetStoreFactory() {
        // do not instantiate
    }

    //TODO this *should* make an attempt to put the "default" one first (or generally order it in a preferred way????) based on configuration
    //  this is because AssetStore.getDefault() currently uses the 0th element as default
    public static List<AssetStore> getStores(final Shepherd myShepherd) {
        System.out.println("ASF.getStores() is called for shepherd w context "+myShepherd.getContext());
        System.out.println("         and data directory name = "+myShepherd.getDataDirectoryName());
        Collection c;
        Extent ext = myShepherd.getPM().getExtent(AssetStore.class, true);
        Query all = myShepherd.getPM().newQuery(ext);
        try {
            c = (Collection) (all.execute());
        } catch (Exception npe) {
            npe.printStackTrace();
            all.closeAll();
            return null;
        }


        List<AssetStore> s = new ArrayList<AssetStore>();
        for (Object obj : c) {
            s.add((AssetStore)obj);
        }
        System.out.println("Found # asset stores = "+s.size());
        all.closeAll();
        return s;
    }


    public static List<AssetStore> getStores2(final Shepherd myShepherd) {
        System.out.println("ASF.getStores() is called for shepherd w context "+myShepherd.getContext());
        System.out.println("         and data directory name = "+myShepherd.getDataDirectoryName());

        PersistenceManager pm  =myShepherd.getPM();
        String actualContext=myShepherd.getContext();
        // String filter = "this.context == '"+actualContext+"'";
        Extent assClass = pm.getExtent(AssetStore.class, true);
        // Query acceptedAssetStores = pm.newQuery(assClass, filter);
        Query acceptedAssetStores = pm.newQuery(assClass);
        Collection c = (Collection) (acceptedAssetStores.execute());
        List<AssetStore> s = new ArrayList<AssetStore>();
        for (Object obj : c) {
            s.add((AssetStore)obj);
        }
        System.out.println("Found # asset stores = "+s.size());
        acceptedAssetStores.closeAll();
        return s;
    }


/*
    private static AssetStore buildAssetStore(final Integer id,
                                              final String name,
                                              final AssetStoreType type,
                                              final AssetStoreConfig config,
                                              final boolean writable)
    {
        if (name == null) throw new IllegalArgumentException("null asset store name");
        if (type == null) throw new IllegalArgumentException("null asset store type");

        switch (type) {
        case LOCAL:
            return new LocalAssetStore(id, name, config, writable);
        default:
            logger.error("Unhandled asset store type: " + type);
            return null;
        }
    }

*/

    public static void save(final AssetStore store) {
    }

/*
    public static void save(final Database db, final AssetStore store) throws DatabaseException {
        Table table = db.getTable(TABLENAME_ASSETSTORE);

        if (store.id == null) {
            SqlInsertFormatter formatter = new SqlInsertFormatter();
            fillFormatter(formatter, store);

            store.id = table.insertSequencedRow(formatter, "id");

            AssetStore.add(store);
        } else {
            SqlUpdateFormatter formatter = new SqlUpdateFormatter();
            fillFormatter(formatter, store);

            SqlWhereFormatter where = new SqlWhereFormatter();
            where.append("id", store.id);
            table.updateRow(formatter.getUpdateClause(), where.getWhereClause());
        }
    }
*/


    public static void delete(final AssetStore store) {
        AssetStore.remove(store);
    }

/*
    public static void delete(final Database db, final AssetStore store) throws DatabaseException {
        if (store.id == null) {
            return;
        }

        Table table = db.getTable(TABLENAME_ASSETSTORE);

        table.deleteRows("id = " + store.id);

        AssetStore.remove(store);
    }
*/

}
