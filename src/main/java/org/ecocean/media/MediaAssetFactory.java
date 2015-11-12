package org.ecocean.media;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaAssetFactory {
    //private static final Logger logger = LoggerFactory.getLogger(MediaAssetFactory.class);

    public static final String TABLENAME_MEDIAASSET = "mediaasset";
    public static final String ALIAS_MEDIAASSET = "ma";
    public static final String PK_MEDIAASSET = "id";

    public static final int NOT_SAVED = -1;

    private MediaAssetFactory() {
        // prevent instantiation
    }


    /**
     * Fetch a single asset from the database by id.
     */
    public static MediaAsset load(final long id) {
        return null;
    }

/*
    public static MediaAsset load(final Database db, final long id)
        throws DatabaseException
    {
        SqlWhereFormatter where = new SqlWhereFormatter();
        where.append(PK_MEDIAASSET, id);

        Table table = db.getTable(TABLENAME_MEDIAASSET);

        RecordSet rs = table.getRecordSet(where.getWhereClause(), 1);
        if (rs.next()) {
            return valueOf(rs);
        }
        return null;
    }
*/


    private static Path createPath(final String pathstr)
    {
        if (pathstr == null) {
            return null;
        }

        return new File(pathstr).toPath();
    }


    /**
     * Store to the given database.
     */
    public static void save(final MediaAsset ma) {
    }
/*
    public static void save(final Database db, final MediaAsset ma) throws DatabaseException {
        Table table = db.getTable(TABLENAME_MEDIAASSET);

        if (ma.id == NOT_SAVED) {
            SqlInsertFormatter formatter = new SqlInsertFormatter();
            fillFormatter(formatter, ma);

            ma.id = table.insertSequencedRow(formatter, PK_MEDIAASSET);
        } else {
            SqlUpdateFormatter formatter = new SqlUpdateFormatter();
            fillFormatter(formatter, ma);

            SqlWhereFormatter where = new SqlWhereFormatter();
            where.append(PK_MEDIAASSET, ma.id);
            table.updateRow(formatter.getUpdateClause(), where.getWhereClause());
        }
    }
*/

    /**
     * Delete this asset and any child assets from the given database.
     * Does not delete any asset files.
     *
     * @param db Database where the asset lives.
     */
    public static void delete(final int id) {
    }

/*
    public static void delete(final Database db, final int id) throws DatabaseException {
        Table table = db.getTable(TABLENAME_MEDIAASSET);
        table.deleteRows("id = " + id);
    }
*/


    public static void deleteFromStore(final MediaAsset ma) {
        if (ma.thumbStore != null) {
            ma.thumbStore.deleteFrom(ma.thumbPath);
            ma.thumbStore.deleteFrom(ma.midPath);
        }
        ma.store.deleteFrom(ma.path);
    }


}
