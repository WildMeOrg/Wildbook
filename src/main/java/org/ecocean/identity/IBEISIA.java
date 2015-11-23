package org.ecocean.identity;

import org.ecocean.ImageAttributes;
import org.ecocean.Annotation;
import java.util.ArrayList;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.URL;
import org.ecocean.CommonConfiguration;
import org.ecocean.media.*;
import org.ecocean.RestClient;
import java.io.IOException;
import java.net.MalformedURLException;


public class IBEISIA {


    //public static JSONObject post(URL url, JSONObject data) throws RuntimeException, MalformedURLException, IOException {

    //a convenience way to send MediaAssets with no (i.e. with only the "trivial") Annotation
    public static JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, String species) throws RuntimeException, MalformedURLException, IOException {
        JSONArray annotations = new JSONArray();
        JSONArray images = new JSONArray();
        for (MediaAsset ma : mas) {
            Annotation ann = new Annotation(ma, species);
            annotations.put(ann.toJSONObject());
            images.put(imageJSONObjectFromMediaAsset(ma));
        }
        JSONArray all = new JSONArray();
        all.put(annotations);
        all.put(images);
        return send(all);
    }

    public static JSONObject sendAnnotations(ArrayList<Annotation> anns, String species) throws RuntimeException, MalformedURLException, IOException {
        JSONArray annotations = new JSONArray();
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
        for (Annotation ann : anns) {
            annotations.put(ann.toJSONObject());
            if (!mas.contains(ann.getMediaAsset())) mas.add(ann.getMediaAsset());
        }

        JSONArray images = new JSONArray();
        for (MediaAsset ma : mas) {
            images.put(imageJSONObjectFromMediaAsset(ma));
        }
        JSONArray all = new JSONArray();
        all.put(annotations);
        all.put(images);
        return send(all);
    }

    public static JSONObject send(JSONArray jsa) throws RuntimeException, MalformedURLException, IOException {
        String u = CommonConfiguration.getProperty("IBEISIARestUrl", "context0");
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrl is not set");
        URL iaUrl = new URL(CommonConfiguration.getProperty("IBEISIARestUrl", "context0"));
System.out.println("SENDING: \n" + jsa.toString() + " to " + iaUrl.toString());
        JSONObject jout = RestClient.post(iaUrl, jsa);
System.out.println("RESPONSE:\n" + jout.toString());
        return jout;
    }



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
    public static JSONObject imageJSONObjectFromMediaAsset(MediaAsset ma) {
        JSONObject obj = new JSONObject();
        obj.put("image_uuid", ma.getUUID());
        ImageAttributes iatt = ma.getImageAttributes();
        obj.put("image_width", (int) iatt.getWidth());
        obj.put("image_height", (int) iatt.getHeight());
        obj.put("image_ext", iatt.getExtension());
        return obj;
    }

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

