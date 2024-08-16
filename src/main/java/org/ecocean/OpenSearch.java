package org.ecocean;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jdo.Query;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import org.ecocean.SystemValue;
import org.json.JSONArray;
import org.json.JSONObject;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.transport.rest_client.RestClientTransport;

import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexResponse;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.client.transport.OpenSearchTransport;

import java.lang.Runnable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// https://opensearch.org/docs/latest/clients/java/
// https://github.com/opensearch-project/opensearch-java/blob/main/USER_GUIDE.md

public class OpenSearch {
    public static OpenSearchClient client = null;
    public static RestClient restClient = null;
    public static Map<String, Boolean> INDEX_EXISTS_CACHE = new HashMap<String, Boolean>();
    public static Map<String, String> PIT_CACHE = new HashMap<String, String>();
    public static String SEARCH_SCROLL_TIME = "10m";
    public static String SEARCH_PIT_TIME = "10m";
    public static String INDEX_TIMESTAMP_PREFIX = "OpenSearch_index_timestamp_";
    public static String[] VALID_INDICES = { "encounter", "individual", "occurrence" };
    public static int BACKGROUND_DELAY_MINUTES = 20;
    public static int BACKGROUND_SLICE_SIZE = 2500;
    public static String QUERY_STORAGE_DIR = "/tmp"; // FIXME

    private int pitRetry = 0;

    public OpenSearch() {
        if (client != null) return;
        // System.setProperty("javax.net.ssl.trustStore", "/full/path/to/keystore");
        // System.setProperty("javax.net.ssl.trustStorePassword", "password-to-keystore");

        // final HttpHost host = new HttpHost("http", "opensearch", 9200);
        final HttpHost host = new HttpHost("opensearch", 9200, "http");
/*
    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    // Only for demo purposes. Don't specify your credentials in code.
    credentialsProvider.setCredentials(new AuthScope(host), new UsernamePasswordCredentials("admin", "admin".toCharArray()));

    final SSLContext sslcontext = SSLContextBuilder
      .create()
      .loadTrustMaterial(null, (chains, authType) -> true)
      .build();
 */

        //////final ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(host);
/*
    builder.setHttpClientConfigCallback(httpClientBuilder -> {
      final TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
        .setSslContext(sslcontext)
        // See https://issues.apache.org/jira/browse/HTTPCLIENT-2219
        .setTlsDetailsFactory(new Factory<SSLEngine, TlsDetails>() {
          @Override
          public TlsDetails create(final SSLEngine sslEngine) {
            return new TlsDetails(sslEngine.getSession(), sslEngine.getApplicationProtocol());
          }
        })
        .build();

      final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder
        .create()
        .setTlsStrategy(tlsStrategy)
        .build();

      return httpClientBuilder
        .setDefaultCredentialsProvider(credentialsProvider)
        .setConnectionManager(connectionManager);
    });
 */

        /////final OpenSearchTransport transport = builder.build();
        ///final RestClient restClient = RestClient.builder(host).build();
        restClient = RestClient.builder(host).build();
        final OpenSearchTransport transport = new RestClientTransport(restClient,
            new JacksonJsonpMapper());

        client = new OpenSearchClient(transport);
    }

    public static boolean isValidIndexName(String indexName) {
        return Arrays.asList(VALID_INDICES).contains(indexName);
    }

    public static boolean skipAutoIndexing() {
        return new java.io.File("/tmp/skipAutoIndexing").exists();
    }

// http://localhost:9200/encounter/_search?pretty=true&q=*:*
// http://localhost:9200/_cat/indices?v

    public static void backgroundStartup(String context) {
        final ScheduledExecutorService schedExec = Executors.newScheduledThreadPool(2);
        final ScheduledFuture schedFuture = schedExec.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                Shepherd myShepherd = new Shepherd(context);
                myShepherd.setAction("OpenSearch.background");
                try {
                    myShepherd.beginDBTransaction();
                    System.out.println("OpenSearch background running...");
                    Encounter.opensearchSyncIndex(myShepherd, BACKGROUND_SLICE_SIZE);
                    System.out.println("OpenSearch background finished.");
                    myShepherd.rollbackAndClose();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    myShepherd.rollbackAndClose();
                }
            }
        }, 2, // initial delay
            BACKGROUND_DELAY_MINUTES, // period delay *after* execution finishes
            TimeUnit.MINUTES); // unit of delays above

        try {
            schedExec.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (java.lang.InterruptedException ex) {
            System.out.println("WARNING: OpenSearch.backgroundStartup(" + context +
                ") interrupted: " + ex.toString());
        }
        System.out.println("OpenSearch.backgroundStartup(" + context + ") backgrounded");
    }

    public void createIndex(String indexName, JSONObject mapping)
    throws IOException {
        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
        CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder().index(
            indexName).build();

        client.indices().create(createIndexRequest);
        // ideally we would pass these as settings() in CreateIndexRequest but that is kind of a mess
        indexClose(indexName);
        JSONObject analysis = new JSONObject(
            "{\"analysis\": {\"normalizer\": {\"wildbook_keyword_normalizer\": {\"type\": \"custom\", \"char_filter\": [], \"filter\": [\"lowercase\", \"asciifolding\"]} } } }");
        putSettings(indexName, analysis);
        createMapping(indexName, mapping);
        indexOpen(indexName);
        INDEX_EXISTS_CACHE.put(indexName, true);
        System.out.println(indexName + " OpenSearch index created");
    }

    public void ensureIndex(String indexName, JSONObject mapping)
    throws IOException {
        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
        if (existsIndex(indexName)) return;
        createIndex(indexName, mapping);
    }

    public void deleteIndex(String indexName)
    throws IOException {
        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest.Builder().index(
            indexName).build();

        // DeleteIndexResponse deleteIndexResponse = client.indices().delete(deleteIndexRequest);
        client.indices().delete(deleteIndexRequest);
        INDEX_EXISTS_CACHE.remove(indexName);
        System.out.println(indexName + " OpenSearch index deleted");
    }

    public boolean existsIndex(String indexName) {
        if (!isValidIndexName(indexName)) return false;
        if (INDEX_EXISTS_CACHE.get(indexName) != null) return true;
        try {
            client.indices().get(i -> i.index(indexName));
            INDEX_EXISTS_CACHE.put(indexName, true);
            return true;
        } catch (Exception ex) {
            System.out.println("existsIndex(" + indexName + "): " + ex.toString());
        }
        return false;
    }

    public void index(String indexName, Base obj)
    throws IOException {
        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
        String id = obj.getId();
        if (!Util.stringExists(id))
            throw new RuntimeException("must have id property to index: " + obj);
        ensureIndex(indexName, obj.opensearchMapping());
        IndexRequest<Base> indexRequest = new IndexRequest.Builder<Base>()
                .index(indexName)
                .id(id)
                .document(obj)
                .build();
        client.index(indexRequest);
/*
        IndexResponse indexResponse = client.index(indexRequest);
        System.out.println(id + ": " + String.format("Document %s.",
            indexResponse.result().toString().toLowerCase()));
 */
    }

/*
    a mapping cannot be changed after data has been indexed, so we allow mapping to be made
    only right after index is created. any properties we do not define will be autoset upon first document creation.
    https://opensearch.org/docs/latest/api-reference/index-apis/put-mapping/
 */
    private JSONObject createMapping(String indexName, final JSONObject mapProperties)
    throws IOException {
        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
        if (!existsIndex(indexName)) throw new IOException("non-existent index: " + indexName);
        if (mapProperties == null) return null;
        JSONObject set = new JSONObject();
        set.put("properties", mapProperties);
        Request req = new Request("PUT", indexName + "/_mapping");
        req.setJsonEntity(set.toString());
        String rtn = getRestResponse(req);
        System.out.println("createMapping(" + indexName + "): " + set + " => " + rtn);
        return set;
    }

/*
    // https://github.com/opensearch-project/opensearch-java/issues/824
    // https://forum.opensearch.org/t/how-can-i-create-a-simple-match-query-using-java-client/7748/2
    // https://forum.opensearch.org/t/java-client-searchrequest-query-building-for-neural-plugin/15895/4
    public List<Base> queryx(String indexName, String query)
    throws IOException {
        List<Base> results = new ArrayList<Base>();
        final SearchRequest request = new SearchRequest.Builder()
                .index(indexName)
                .from(0)
                .size(200)
            // .sort(sortOptions)
                .trackScores(true)
            // .query(q -> q.queryString("{}"))
                .build();

   // Unnecessary casting/deserialisation imo
   // final var response = openSearchClient.search(request, ObjectNode.class);

   // Unnecessary conversion
   // final var str = objectMapper.writeValueAsString(response);

        // SearchResponse<Base> searchResponse = client.search(request, Base.class);
        SearchResponse<Base> searchResponse = client.search(s -> s.index(indexName), Base.class);

        for (int i = 0; i < searchResponse.hits().hits().size(); i++) {
            System.out.println(searchResponse.hits().hits().get(i).source());
        }
        return results;
    }

 */

    // https://opensearch.org/docs/latest/search-plugins/searching-data/point-in-time-api/
    public String createPit(String indexName)
    throws IOException {
        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
        if (PIT_CACHE.containsKey(indexName)) return PIT_CACHE.get(indexName);
        Request searchRequest = new Request("POST",
            indexName + "/_search/point_in_time?keep_alive=" + SEARCH_PIT_TIME);
        String rtn = getRestResponse(searchRequest);
        JSONObject jrtn = new JSONObject(rtn);
        String id = jrtn.optString("pit_id", null);
        if (id == null) throw new IOException("failed to get PIT id");
        PIT_CACHE.put(indexName, id);
        return id;
    }

    public void deleteAllPits()
    throws IOException {
        Request searchRequest = new Request("DELETE", "/_search/point_in_time/_all");

        getRestResponse(searchRequest);
        PIT_CACHE.clear();
        System.out.println("OpenSearch.deleteAllPits() completed");
    }

    public void deletePit(String indexName)
    throws IOException {
        String pitId = PIT_CACHE.get(indexName);

        if (pitId == null) return;
        Request req = new Request("DELETE", "/_search/point_in_time");
        JSONObject body = new JSONObject();
        body.put("pit_id", pitId);
        req.setJsonEntity(body.toString());
        getRestResponse(req);
        PIT_CACHE.remove(indexName);
        System.out.println("OpenSearch.deletePit(" + indexName + ") [" + pitId + "] completed");
    }

    public JSONObject queryPit(String indexName, final JSONObject query, int numFrom, int pageSize,
        String sort, String sortOrder)
    throws IOException {
        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
        String pitId = createPit(indexName);
        Request searchRequest = new Request("POST", "/_search?track_total_hits=true");
        query.put("from", numFrom);
        query.put("size", pageSize);
        // "sort": [ {"@timestamp": {"order": "asc"}} ]
        if (sort != null) {
            JSONArray sortArr = new JSONArray();
            if ((sortOrder == null) || !"desc".equals(sortOrder)) sortOrder = "asc";
            sortArr.put(new JSONObject("{\"" + sort + "\":{\"order\":\"" + sortOrder + "\"}}"));
            query.put("sort", sortArr);
        }
        JSONObject jpit = new JSONObject();
        jpit.put("id", pitId);
        jpit.put("keep_alive", SEARCH_PIT_TIME);
        query.put("pit", jpit);
        searchRequest.setJsonEntity(query.toString());
        String rtn = null;
        try {
            rtn = getRestResponse(searchRequest);
            pitRetry = 0;
        } catch (ResponseException ex) {
            System.out.println("queryPit() using pitId=" + pitId + " failed[" + pitRetry +
                "] with: " + ex);
            pitRetry++;
            if (pitRetry > 5) {
                ex.printStackTrace();
                throw new IOException("queryPit() failed to POST query");
            }
            // we try again, but attempt to get new PIT
            PIT_CACHE.remove(indexName);
            return queryPit(indexName, query, numFrom, pageSize, sort, sortOrder);
        }
        return new JSONObject(rtn);
    }

    // https://opensearch.org/docs/2.3/opensearch/search/paginate/
    public JSONObject queryRawScroll(String indexName, final JSONObject query, int pageSize)
    throws IOException {
        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
        Request searchRequest = new Request("POST",
            indexName + "/_search?scroll=" + SEARCH_SCROLL_TIME);

        query.put("size", pageSize);
        searchRequest.setJsonEntity(query.toString());
        String rtn = getRestResponse(searchRequest);
        return new JSONObject(rtn);
    }

    // this expects only json passed in, which is to continue paging on results from above
    public JSONObject queryRawScroll(JSONObject scrollData)
    throws IOException {
        if (scrollData == null) throw new IOException("null data passed");
        String scrollId = scrollData.optString("_scroll_id", null);
        if (scrollData == null) throw new IOException("no _scroll_id");
        JSONObject data = new JSONObject();
        data.put("scroll", SEARCH_SCROLL_TIME);
        data.put("scroll_id", scrollId);
        Request searchRequest = new Request("POST", "_search/scroll");
        searchRequest.setJsonEntity(data.toString());
        String rtn = getRestResponse(searchRequest);
        return new JSONObject(rtn);
    }

    public Map<String, Long> getAllVersions(String indexName)
    throws IOException {
        Map<String, Long> versions = new HashMap<String, Long>();
        boolean reachedEnd = false;
        JSONObject query = new JSONObject("{\"sort\":[{\"version\": \"asc\"}]}");
        JSONObject res = queryRawScroll(indexName, query, 2000);

        while (!reachedEnd) {
            JSONObject outerHits = res.optJSONObject("hits");
            if (outerHits == null) throw new IOException("outer hits failed");
            JSONArray hits = outerHits.optJSONArray("hits");
            if (hits == null) throw new IOException("hits failed");
            if (hits.length() < 1) {
                reachedEnd = true;
            } else {
                for (int i = 0; i < hits.length(); i++) {
                    String id = hits.optJSONObject(i).optString("_id", "__FAIL__");
                    Long version = hits.optJSONObject(i).optJSONObject("_source").optLong("version",
                        -999L);
                    versions.put(id, version);
                }
                // continue with next scroll...
                query = new JSONObject();
                query.put("_scroll_id", res.optString("_scroll_id", "__FAIL__"));
                res = queryRawScroll(query);
            }
        }
        // this is a little hacky, but allows us to page thru results enough to cover what we have
        if (versions.size() > 10000) {
            putSettings(indexName,
                new JSONObject("{\"index.max_result_window\": " +
                Math.round(1.2 * versions.size()) + "}"));
        }
        return versions;
    }

    public JSONObject getSettings(final String indexName)
    throws IOException {
        Request settingsRequest = new Request("GET", indexName + "/_settings");
        String rtn = getRestResponse(settingsRequest);

        try {
            JSONObject jrtn = new JSONObject(rtn);
            // since we are asking for a specific index's settings, let go ahead and dig down
            return jrtn.getJSONObject(indexName).getJSONObject("settings").getJSONObject("index");
        } catch (Exception ex) {
            System.out.println("OpenSearch.getSettings() failed with rtn=" + rtn);
            ex.printStackTrace();
        }
        // lets just avoid null return for simplicity
        return new JSONObject();
    }

    public void putSettings(final String indexName, final JSONObject settings)
    throws IOException {
        if (settings == null) throw new IOException("null data passed");
        Request settingsRequest = new Request("PUT",
            indexName + "/_settings?preserve_existing=true");
        settingsRequest.setJsonEntity(settings.toString());
        String rtn = getRestResponse(settingsRequest);
        System.out.println("OpenSearch.putSettings() on " + indexName + ": " + settings + " => " +
            rtn);
    }

    public void indexOpen(final String indexName)
    throws IOException {
        Request searchRequest = new Request("POST", indexName + "/_open");
        String rtn = getRestResponse(searchRequest);

        System.out.println("OpenSearch.indexOpen() on " + indexName + ": " + rtn);
    }

    public void indexClose(final String indexName)
    throws IOException {
        Request searchRequest = new Request("POST", indexName + "/_close");
        String rtn = getRestResponse(searchRequest);

        System.out.println("OpenSearch.indexClose() on " + indexName + ": " + rtn);
    }

    // returns 2 lists: (1) items needing (re-)indexing; (2) items needing removal
    public static List<List<String> > resolveVersions(Map<String, Long> objVersions,
        Map<String, Long> indexVersions) {
        List<List<String> > rtn = new ArrayList<List<String> >(2);
        List<String> needIndexing = new ArrayList<String>();

        for (String objId : objVersions.keySet()) {
            Long oVer = objVersions.get(objId); // i think these should never be null but we be careful anyway
            Long iVer = indexVersions.get(objId);
            if ((iVer == null) || (oVer == null) || (oVer > iVer)) needIndexing.add(objId);
        }
        rtn.add(needIndexing);

        List<String> needRemoval = new ArrayList<String>();
        for (String idxId : indexVersions.keySet()) {
            if (!objVersions.containsKey(idxId)) needRemoval.add(idxId);
        }
        rtn.add(needRemoval);

        return rtn;
    }

    public List<Base> queryResultsToObjects(Shepherd myShepherd, String indexName,
        final JSONObject results)
    throws IOException {
        JSONArray jarr = null;

        try {
            jarr = results.getJSONObject("hits").getJSONArray("hits");
        } catch (Exception ex) {
            System.out.println(ex);
            throw new IOException("could not parse results");
        }
        List<Base> list = new ArrayList<Base>();
        for (int i = 0; i < jarr.length(); i++) {
            Base obj = null;
            if ("encounter".equals(indexName)) {
                obj = myShepherd.getEncounter(jarr.optJSONObject(i).optString("_id", "__FAIL__"));
            } else if ("occurrence".equals(indexName)) {
                obj = myShepherd.getEncounter(jarr.optJSONObject(i).optString("_id", "__FAIL__"));
            } else if ("individual".equals(indexName)) {
                obj = myShepherd.getEncounter(jarr.optJSONObject(i).optString("_id", "__FAIL__"));
            }
            if (obj == null) {
                System.out.println("failed to load " + indexName + " object: " + jarr.get(i));
            } else {
                list.add(obj);
            }
        }
        return list;
    }

/*
    public JSONObject queryRaw(String indexName, String query)
    throws IOException {
        Request searchRequest = new Request("POST", indexName + "/_search");

        searchRequest.setJsonEntity("{\"query\": { \"match_all\": {} }}");
        String rtn = getRestResponse(searchRequest);
        System.out.println(rtn);
        return new JSONObject(rtn);
    }
 */
    public String getRestResponse(Request request)
    throws IOException {
        Response response = restClient.performRequest(request);
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            response.getEntity().getContent(), "UTF-8"), 8);
        StringBuilder sb = new StringBuilder();
        String line = null;

        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    public void delete(String indexName, String id)
    throws IOException {
        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
        if (!existsIndex(indexName)) return;
        client.delete(b -> b.index(indexName).id(id));
        System.out.println("deleted id=" + id + " from OpenSearch index " + indexName);
    }

    public void delete(String indexName, Base obj)
    throws IOException {
        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
        String id = obj.getId();
        if (id == null) throw new RuntimeException("must have id property to delete");
        delete(indexName, id);
    }

    public long setIndexTimestamp(Shepherd myShepherd, String indexName) {
        long now = System.currentTimeMillis();

        SystemValue.set(myShepherd, INDEX_TIMESTAMP_PREFIX + indexName, now);
        return now;
    }

    public Long getIndexTimestamp(Shepherd myShepherd, String indexName) {
        return SystemValue.getLong(myShepherd, INDEX_TIMESTAMP_PREFIX + indexName);
    }

    public static JSONObject querySanitize(JSONObject query, User user) {
        if ((query == null) || (user == null)) return query;
        JSONObject newQuery = new JSONObject(query.toString());
        try {
            JSONArray filter = newQuery.getJSONObject("query").getJSONObject("bool").getJSONArray(
                "filter");
            filter.put(new JSONObject("{\"match\": {\"viewUsers\": \"" + user.getId() + "\"}}"));
        } catch (Exception ex) {
            System.out.println("OpenSearch.querySanitize() failed to find filter element: " + ex);
        }
        return newQuery;
    }

    // TODO right now this respects index timestamp and only indexes objects with versions > timestamp.
    // probably want to make an option to index everything and ignore version/timestamp.
    public void indexAll(Shepherd myShepherd, Base obj)
    throws IOException {
        String clause = "";
        String indexName = obj.opensearchIndexName();
        Long last = getIndexTimestamp(myShepherd, indexName);

        if (last != null) {
            // hacky. we dont have a 'version' property on all tables.... SIGH
            if (Encounter.class.isInstance(obj)) {
                // LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(last), ZoneId.systemDefault());
                String lastString = java.time.Instant.ofEpochMilli(last).toString();
                clause = "this.modified > \"" + lastString + "\"";
            }
        }
        // we set this *before* we index, cuz then it wont miss any stuff indexed after we started, as this can take a while
        Long now = setIndexTimestamp(myShepherd, indexName);

        System.out.println("indexAll() >>>>> querying using clause: " + clause);
        Query query = myShepherd.getPM().newQuery(obj.getClass(), clause);
        List<Base> all = null;
        try {
            all = (List)query.execute();
        } catch (Exception ex) {
            System.out.println("OpenSearch.indexAll(" + obj.getClass() + ") failed: " + ex);
            ex.printStackTrace();
            return;
        }
        long initTime = System.currentTimeMillis();
        System.out.println("OpenSearch.indexAll() [" +
            java.time.Instant.ofEpochMilli(now).toString() + "] indexing " + indexName + ": size=" +
            all.size() + " (" + obj.getClass() + ")");
        int ct = 0;
        for (Base item : all) {
            ct++;
            if (ct > 2100) break;
            index(indexName, item);
            if (ct % 500 == 0)
                System.out.println("OpenSearch.indexAll() [" +
                    (System.currentTimeMillis() - initTime) + "] indexed " + indexName + ": " + ct +
                    " of " + all.size());
        }
        query.closeAll();
        System.out.println("OpenSearch.indexAll() [" + (System.currentTimeMillis() - initTime) +
            "] completed indexing " + indexName);
    }

    public static String queryStoragePath(String id) {
        return QUERY_STORAGE_DIR + "/OpenSearch-query-" + id + ".json";
    }

    public static String queryStore(final JSONObject query, final String indexName,
        final User user) {
        if (query == null) return null;
        JSONObject stored = new JSONObject(query.toString());
        String id = Util.generateUUID();
        stored.put("id", id);
        stored.put("indexName", indexName);
        stored.put("created", System.currentTimeMillis());
        stored.put("creator", user.getUUID());
        try {
            Util.writeToFile(stored.toString(), queryStoragePath(id));
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
        return id;
    }

    public static JSONObject queryLoad(String id) {
        if (id == null) return null;
        try {
            String jsonData = Util.readFromFile(queryStoragePath(id));
            return new JSONObject(jsonData);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static JSONObject queryScrubStored(final JSONObject query) {
        if (query == null) return null;
        JSONObject scrubbed = new JSONObject();
        scrubbed.put("query", query.optJSONObject("query"));
        return scrubbed;
    }
}
