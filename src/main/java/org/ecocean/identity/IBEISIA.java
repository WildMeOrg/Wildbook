package org.ecocean.identity;

import org.ecocean.ImageAttributes;
import org.ecocean.Annotation;
import org.ecocean.Util;
import java.util.ArrayList;
import java.util.HashMap;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.URL;
import org.ecocean.CommonConfiguration;
import org.ecocean.media.*;
import org.ecocean.RestClient;
import java.io.IOException;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import org.joda.time.DateTime;


public class IBEISIA {

    //public static JSONObject post(URL url, JSONObject data) throws RuntimeException, MalformedURLException, IOException {

    //a convenience way to send MediaAssets with no (i.e. with only the "trivial") Annotation
    public static JSONObject sendMediaAssets(ArrayList<MediaAsset> mas) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        return sendMediaAssets(mas, null);
    }

    //other is a HashMap of additional properties to build lists out of (e.g. Encounter ids and so on), that do not live in/on MediaAsset
    public static JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, HashMap<MediaAsset,HashMap<String,Object>> other) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = CommonConfiguration.getProperty("IBEISIARestUrlAddImages", "context0");
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlAddImages is not set");
        URL url = new URL(u);

        //see: https://erotemic.github.io/ibeis/ibeis.web.html?highlight=add_images_json#ibeis.web.app.add_images_json
        HashMap<String,ArrayList> map = new HashMap<String,ArrayList>();
        map.put("image_uri_list", new ArrayList<JSONObject>());
        map.put("image_uuid_list", new ArrayList<String>());
        map.put("image_width_list", new ArrayList<Integer>());
        map.put("image_height_list", new ArrayList<Integer>());
        map.put("image_time_posix_list", new ArrayList<Integer>());
        map.put("image_gps_lat_list", new ArrayList<Double>());
        map.put("image_gps_lon_list", new ArrayList<Double>());

        for (MediaAsset ma : mas) {
            map.get("image_uuid_list").add(ma.getUUID());
            map.get("image_uri_list").add(mediaAssetToUri(ma));

            ImageAttributes iatt = null;
            try {
                iatt = ma.getImageAttributes();
            } catch (Exception ex) { }
            if (iatt == null) {
                map.get("image_width_list").add(0);
                map.get("image_height_list").add(0);
            } else {
                map.get("image_width_list").add((int) iatt.getWidth());
                map.get("image_height_list").add((int) iatt.getHeight());
            }

            map.get("image_gps_lat_list").add(ma.getLatitude());
            map.get("image_gps_lon_list").add(ma.getLongitude());

            DateTime t = ma.getDateTime();
            if (t == null) {
                map.get("image_time_posix_list").add(0);
            } else {
                map.get("image_time_posix_list").add((int)Math.floor(t.getMillis() / 1000));  //IBIES-IA wants seconds since epoch
            }
        }

        return RestClient.post(url, new JSONObject(map));
    }



            //Annotation ann = new Annotation(ma, species);

    public static JSONObject sendAnnotations(ArrayList<Annotation> anns) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = CommonConfiguration.getProperty("IBEISIARestUrlAddAnnotations", "context0");
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlAddAnnotations is not set");
        URL url = new URL(u);

        HashMap<String,ArrayList> map = new HashMap<String,ArrayList>();
        map.put("image_uuid_list", new ArrayList<String>());
        map.put("annot_uuid_list", new ArrayList<String>());
        map.put("annot_species_list", new ArrayList<String>());
        map.put("annot_bbox_list", new ArrayList<int[]>());

        for (Annotation ann : anns) {
            map.get("image_uuid_list").add(ann.getMediaAsset().getUUID());
            map.get("annot_uuid_list").add(ann.getUUID());
            map.get("annot_species_list").add(ann.getSpeciesText());
            map.get("annot_bbox_list").add(ann.getBbox());
        }

        return RestClient.post(url, new JSONObject(map));
    }


    private static Object mediaAssetToUri(MediaAsset ma) {
        if (ma.getStore() instanceof LocalAssetStore) {
            return ma.localPath().toString();
        } else if (ma.getStore() instanceof S3AssetStore) {
            return ma.getParameters();
/*
            JSONObject params = ma.getParameters();
            if (params == null) return null;
            //return "s3://s3.amazon.com/" + params.getString("bucket") + "/" + params.getString("key");
            JSONObject b = new JSONObject();
            b.put("bucket", params.getString("bucket"));
            b.put("key", params.getString("key"));
            return b;
*/
        } else {
            return ma.toString();
        }
    }

/*   no longer needed??
    public static JSONObject send(URL url, JSONObject jobj) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
System.out.println("SENDING: ------\n" + jobj.toString() + "\n---------- to " + iaUrl.toString());
        JSONObject jrtn = RestClient.post(iaUrl, jobj);
System.out.println("RESPONSE:\n" + jrtn.toString());
        return jrtn;
    }
*/



/*
image_attrs = {
    ~'image_rowid': 'INTEGER',
    'image_uuid': 'UUID',
    'image_uri': 'TEXT',
    'image_ext': 'TEXT',
    *'image_original_name': 'TEXT',
    'image_width': 'INTEGER',
    'image_height': 'INTEGER',
    *'image_time_posix': 'INTEGER',
    *'image_gps_lat': 'REAL',
    *'image_gps_lon': 'REAL',
    !'image_toggle_enabled': 'INTEGER',
    !'image_toggle_reviewed': 'INTEGER',
    ~'image_note': 'TEXT',
    *'image_timedelta_posix': 'INTEGER',
    *'image_original_path': 'TEXT',
    !'image_location_code': 'TEXT',
    *'contributor_tag': 'TEXT',
    *'party_tag': 'TEXT',
}
*/

/*
    public static JSONObject imageJSONObjectFromMediaAsset(MediaAsset ma) {
        JSONObject obj = new JSONObject();
        obj.put("image_uuid", ma.getUUID());
        ImageAttributes iatt = ma.getImageAttributes();
        obj.put("image_width", (int) iatt.getWidth());
        obj.put("image_height", (int) iatt.getHeight());
        obj.put("image_ext", iatt.getExtension());

        JSONObject params = new JSONObject(ma.getParameters(), JSONObject.getNames(ma.getParameters()));
        params.put("store_type", ma.getStore().getType());
        obj.put("image_storage_parameters", params);
        return obj;
    }
*/

}


/*
Shepherd myShepherd=null;
myShepherd = new Shepherd("context0");



JSONObject params = new JSONObject();

//LocalAssetStore las = new LocalAssetStore("testStore2", new File("/tmp/store").toPath(), "http://foo.bar/webroot/testStore", false);
LocalAssetStore las = ((LocalAssetStore) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(LocalAssetStore.class, 1), true)));
S3AssetStore s3as = ((S3AssetStore) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(S3AssetStore.class, 3), true)));
out.println(las);
out.println(s3as);
*/
 




//myShepherd.getPM().makePersistent(las);

/*
params.put("path", "/tmp/store/test.txt");
MediaAsset ma = las.copyIn(new File("/tmp/incoming.txt"), params);
out.println(ma.localPath());
out.println(ma.webURL());
*/


/*
params.put("path", "/tmp/store/fluke2.jpg");
MediaAsset ma = las.create(params);
MediaAssetFactory.save(ma, myShepherd);
*/


/*
MediaAsset ma = MediaAssetFactory.load(1, myShepherd);

out.println(ma.localPath());
//out.println(ma.webPathString());
out.println(ma.getId());
*/






/*
S3AssetStore s3as = new S3AssetStore("test S3", true);
myShepherd.getPM().makePersistent(s3as);
*/




/*
sp.put("bucket", "temporary-test");
sp.put("key", "dorsal-fin.jpg");
sp.put("urlAccessible", true);
MediaAsset ma3 = s3as.create(sp);
out.println(ma3.localPath());
out.println(ma3.webURL());
ma3.cacheLocal();
*/

