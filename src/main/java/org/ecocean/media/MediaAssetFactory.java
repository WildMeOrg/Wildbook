package org.ecocean.media;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.jdo.*;
import org.ecocean.Shepherd;
import org.ecocean.Util;

public class MediaAssetFactory {

    public static final int NOT_SAVED = -1;

    private MediaAssetFactory() {
        // prevent instantiation
    }

    /**
     * Fetch a single asset from the database by id.
     */
    public static MediaAsset load(final int id, Shepherd myShepherd) {
        try {
            return ((org.ecocean.media.MediaAsset)(myShepherd.getPM().getObjectById(
                       myShepherd.getPM().newObjectIdInstance(MediaAsset.class, id), true)));
        } catch (org.datanucleus.exceptions.NucleusObjectNotFoundException ex) {
            return null;
        } catch (javax.jdo.JDOObjectNotFoundException ex) {
            return null;
        }
    }

    public static MediaAsset loadByUuid(final String uuid, Shepherd myShepherd) {
        MediaAsset ma = null;

        if (!Util.isUUID(uuid)) return null;
        Query query = myShepherd.getPM().newQuery(MediaAsset.class);
        query.setFilter("uuid=='" + uuid + "'");
        List results = (List)query.execute();
        // uuid column is constrained unique, so should always get 0 or 1
        if (results.size() < 1) {
            query.closeAll();
            return null;
        }
        ma = (MediaAsset)results.get(0);
        query.closeAll();
        return ma;
    }

    // NOTE!!!   acmId is NOT unique, so there could be more than one....  this will return "oldest" (order by revision)
    public static MediaAsset loadByAcmId(final String id, Shepherd myShepherd) {
        MediaAsset ma = null;
        // if (!Util.isUUID(uuid)) return null; 
        Query query = myShepherd.getPM().newQuery(MediaAsset.class);

        query.setFilter("acmId=='" + id + "'");
        query.setOrdering("revision");
        List results = (List)query.execute();
        if (results.size() > 0) ma = (MediaAsset)results.get(0);
        query.closeAll();
        return ma;
    }

    private static Path createPath(final String pathstr) {
        if (pathstr == null) {
            return null;
        }
        return new File(pathstr).toPath();
    }

    /**
     * Store to the given database.
     */
    public static void save(MediaAsset ma, Shepherd myShepherd) {
        if ((ma.getParentId() != null) && (ma.getParentId() == NOT_SAVED)) {
            throw new RuntimeException(ma + " has a parentId == " + NOT_SAVED +
                    "; parent MediaAsset object likely not yet persisted; aborting save");
        }
        // ma.setRevision();
        // for some reason (!?) parameters are getting lost when saving... sigh.  HACK for now... lookout.
        ////JSONObject p = ma.getParameters();
        myShepherd.getPM().makePersistent(ma);
        ////if (p != null) ma.setParameters(p);
    }

    /**
     * Delete this asset and any child assets from the given database. Does not delete any asset files.
     *
     * @param db Database where the asset lives.
     */
    public static void delete(final int id) {
    }

    public static void deleteFromStore(final MediaAsset ma) {
        ma.store.deleteFrom(ma);
    }
}
