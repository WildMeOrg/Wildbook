package org.ecocean;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.jdo.Query;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import org.ecocean.media.MediaAsset;
import org.ecocean.SystemValue;

import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdProperties;

import org.json.JSONArray;
import org.json.JSONObject;

import org.apache.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;

import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.OpenSearchClient;
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
    public static final int DEFAULT_MAX_RESULT_WINDOW = 10000;
    // per-index max_result_window: {value, expiresAtMillis}; read live (briefly cached)
    // because Wildbook raises the setting on indexes that grow past the default
    private static final Map<String, long[]> MAX_RESULT_WINDOW_CACHE =
        new java.util.concurrent.ConcurrentHashMap<String, long[]>();
    private static final long MAX_RESULT_WINDOW_CACHE_MILLIS = 60000L;
    public static String SEARCH_SCROLL_TIME = (String)getConfigurationValue("searchScrollTime",
        "10m");
    public static String SEARCH_PIT_TIME = (String)getConfigurationValue("searchPitTime", "10m");
    public static String INDEX_TIMESTAMP_PREFIX = "OpenSearch_index_timestamp_";
    public static String[] VALID_INDICES = {
        "encounter", "individual", "occurrence", "annotation", "media_asset"
    };
    public static int BACKGROUND_DELAY_MINUTES = (Integer)getConfigurationValue(
        "backgroundDelayMinutes", 20);
    public static int BACKGROUND_SLICE_SIZE = (Integer)getConfigurationValue("backgroundSliceSize",
        2500);
    public static int BACKGROUND_PERMISSIONS_MINUTES = (Integer)getConfigurationValue(
        "backgroundPermissionsMinutes", 10);
    public static int BACKGROUND_PERMISSIONS_MAX_FORCE_MINUTES = (Integer)getConfigurationValue(
        "backgroundPermissionsMaxForceMinutes", 45);
    public static String PERMISSIONS_LAST_RUN_KEY = "OpenSearch_permissions_last_run_timestamp";
    public static String PERMISSIONS_NEEDED_KEY = "OpenSearch_permissions_needed";
    // kNN match warmup: after startup, load the faiss/kNN native graph for the match index into
    // off-heap memory (and warm the filter/segment paths) so the FIRST user match doesn't pay the
    // cold-load, which can exceed the socket timeout and surface to the user as an empty result.
    public static String WARMUP_INDEX = (String)getConfigurationValue("matchWarmupIndex",
        "annotation");
    // Retry cadence while OpenSearch is still coming up / the index isn't built yet (e.g. a
    // `docker compose up` where both containers start at once). Kept short so we warm as soon as
    // OpenSearch is reachable.
    public static int WARMUP_RETRY_MINUTES = (Integer)getConfigurationValue("matchWarmupRetryMinutes",
        1);
    // Re-warm cadence once warmed, to survive segment merges / native-cache eviction. 0 disables
    // re-warming (warm once at startup only).
    public static int WARMUP_REWARM_MINUTES = (Integer)getConfigurationValue(
        "matchWarmupRewarmMinutes", 20);
    // True while the match kNN graph is warmed into native memory in THIS JVM: set on a successful
    // warm pass, reset if a later (re)warm fails (e.g. OpenSearch restarted under us). In-memory,
    // so correctly starts false on restart. Exposed for health/readiness use; not a gate on matching.
    private static volatile boolean matchWarmupReady = false;
    // Ensures a single self-rescheduling warmup chain, even if backgroundStartup is ever called twice.
    private static final java.util.concurrent.atomic.AtomicBoolean warmupChainStarted =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    public static boolean isMatchWarmupReady() {
        return matchWarmupReady;
    }
    public static String QUERY_STORAGE_DIR = "/tmp"; // FIXME
    static String ACTIVE_TYPE_FOREGROUND = "opensearch_indexing_foreground";
    static String ACTIVE_TYPE_BACKGROUND = "opensearch_indexing_background";


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
        initializeClient(host);
    }

    public static void initializeClient(HttpHost host) {
        // Raise the socket timeout well above the 30s default. A slow query (e.g. a
        // cold/large kNN, or a match over a big candidate set) otherwise throws
        // SocketTimeoutException, which the match path surfaces to users as an empty
        // "0 matches" result -- and a failed match reads as "no match found", which is
        // WORSE for a user than a slow one. 240s gives cold/large matches room to
        // complete rather than silently returning empty. (The batch-load + top-N cap in
        // Annotation.getMatches keep normal matches to a few seconds; this is the safety
        // net for the cold/outlier case.)
        restClient = RestClient.builder(host)
            .setRequestConfigCallback(
                rc -> rc.setConnectTimeout(10000).setSocketTimeout(240000))
            .build();
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
        final ScheduledExecutorService schedExec = Executors.newScheduledThreadPool(8);
        final ScheduledFuture schedFutureIndexing = schedExec.scheduleWithFixedDelay(
            new Runnable() {
                public void run() {
                    updateEncounterIndexes(context);
                }
            }, 2, // initial delay
            BACKGROUND_DELAY_MINUTES, // period delay *after* execution finishes
            TimeUnit.MINUTES); // unit of delays above
        final ScheduledFuture schedFuturePermissions = schedExec.scheduleWithFixedDelay(
            new Runnable() {
                public void run() {
                    updatePermissionsIndex(context);
                }
            }, 8, // initial delay
            BACKGROUND_PERMISSIONS_MINUTES, TimeUnit.MINUTES); // unit of delays above

        try {
            schedExec.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (java.lang.InterruptedException ex) {
            System.out.println("WARNING: OpenSearch.backgroundStartup(" + context +
                ") interrupted: " + ex.toString());
        }
        System.out.println("OpenSearch.backgroundStartup(" + context + ") backgrounded");

        // Warm the match kNN graph so the first user match after this restart isn't cold. Self-
        // reschedules: retry quickly while OpenSearch/the index are still coming up, then re-warm
        // on a slow cadence to survive merges/eviction. Guarded so only one chain ever runs.
        if (warmupChainStarted.compareAndSet(false, true)) {
            scheduleMatchWarmup(schedExec, context, WARMUP_RETRY_MINUTES);
        }
    }

    // Self-rescheduling warmup. Reschedules itself on the same executor: WARMUP_RETRY_MINUTES until
    // the first successful warm (OpenSearch may still be starting on a `docker compose up`), then
    // WARMUP_REWARM_MINUTES thereafter (0 = warm once only). Never throws out of the task.
    private static void scheduleMatchWarmup(final ScheduledExecutorService schedExec,
        final String context, long delayMinutes) {
        try {
            schedExec.schedule(new Runnable() {
                public void run() {
                    boolean warmed = false;
                    try {
                        warmed = warmupMatching(context);
                    } catch (Throwable t) { // belt-and-suspenders: a task must never die silently
                        System.out.println("OpenSearch.warmupMatching threw (will retry): " + t);
                    }
                    long next = warmed ? WARMUP_REWARM_MINUTES : WARMUP_RETRY_MINUTES;
                    if (next > 0) scheduleMatchWarmup(schedExec, context, next);
                }
            }, delayMinutes, TimeUnit.MINUTES);
        } catch (Exception ex) {
            // e.g. executor shutting down on tomcat stop -- nothing to do, warmup simply ends.
            System.out.println("OpenSearch.scheduleMatchWarmup: could not schedule (" + ex + ")");
        }
    }

    // Warm the match index. Returns true only if the graph was actually warmed this pass. Any
    // failure (OpenSearch unreachable, index not built yet) is treated as "not ready, retry" -- it
    // logs and returns false rather than throwing, so a racing startup degrades gracefully.
    public static boolean warmupMatching(String context) {
        OpenSearch os;
        try {
            os = new OpenSearch(); // client is lazy; ctor won't fail if OpenSearch is still down
        } catch (Exception ex) {
            System.out.println("warmupMatching: OpenSearch client not available yet (will retry): " +
                ex);
            matchWarmupReady = false;
            return false;
        }
        String indexName = WARMUP_INDEX;
        try {
            if (!os.existsIndex(indexName)) {
                System.out.println("warmupMatching: '" + indexName +
                    "' index not ready yet (OpenSearch may still be starting / reindexing); will retry");
                matchWarmupReady = false;
                return false;
            }
            long t = System.currentTimeMillis();
            System.out.println("warmupMatching: warming kNN graph for '" + indexName + "' ...");
            os.warmupKnn(indexName);
            // Also warm the filter/segment/doc-value paths a real match query touches (the graph
            // warmup alone doesn't). A cheap _count with the matchAgainst filter is enough.
            try {
                JSONObject warmQuery = new JSONObject(
                    "{\"query\":{\"bool\":{\"filter\":[{\"term\":{\"matchAgainst\":true}}]}}}");
                os.queryCount(indexName, warmQuery);
            } catch (Exception ex) {
                System.out.println("warmupMatching: filter-path warm query failed (non-fatal): " +
                    ex);
            }
            matchWarmupReady = true;
            System.out.println("warmupMatching: kNN warmup complete in " +
                (System.currentTimeMillis() - t) + "ms; matchWarmupReady=true");
            return true;
        } catch (Exception ex) {
            System.out.println("warmupMatching: warmup failed (OpenSearch may still be starting); " +
                "will retry: " + ex);
            matchWarmupReady = false;
            return false;
        }
    }

    // Load an index's kNN native graphs into off-heap memory ahead of the first query.
    // https://docs.opensearch.org/latest/vector-search/api/knn/#warmup
    public void warmupKnn(String indexName) throws IOException {
        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
        Request req = new Request("GET", "/_plugins/_knn/warmup/" + indexName);
        String rtn = getRestResponse(req);
        System.out.println("OpenSearch.warmupKnn(" + indexName + "): " + rtn);
        // Treat the warmup as failed only if a shard actually errored (failed>0) or nothing warmed
        // at all (successful<1). Do NOT require successful==total: on a single-node cluster the
        // index's replica shards are unassigned, so warmup legitimately reports e.g.
        // total=2,successful=1,failed=0 -- the primary (which serves queries) is warmed. Requiring
        // successful==total would make warmup throw forever and never latch on single-node.
        JSONObject shards = new JSONObject(rtn).optJSONObject("_shards");
        int successful = (shards == null) ? 0 : shards.optInt("successful", 0);
        int failed = (shards == null) ? 0 : shards.optInt("failed", 0);
        if ((failed > 0) || (successful < 1)) {
            throw new IOException("kNN warmup incomplete for '" + indexName + "': " + rtn);
        }
    }

    private static void updatePermissionsIndex(String context) {
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("OpenSearch.backgroundPermissions");
        try {
            myShepherd.beginDBTransaction();
            System.out.println("OpenSearch background permissions running...");
            Encounter.opensearchIndexPermissionsBackground(myShepherd);
            System.out.println("OpenSearch background permissions finished.");
            myShepherd.commitDBTransaction(); // need commit since we might have changed SystemValues
            myShepherd.closeDBTransaction();
        } catch (Exception ex) {
            ex.printStackTrace();
            myShepherd.rollbackAndClose();
        }
    }

    public static void updateEncounterIndexes(String context) {
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("OpenSearch.backgroundIndexing");
        try {
            myShepherd.beginDBTransaction();
            System.out.println("OpenSearch background indexing running...");
            Base.opensearchSyncIndex(myShepherd, Encounter.class, BACKGROUND_SLICE_SIZE);
            Base.opensearchSyncIndex(myShepherd, Annotation.class, BACKGROUND_SLICE_SIZE);
            Base.opensearchSyncIndex(myShepherd, MarkedIndividual.class, BACKGROUND_SLICE_SIZE);
            Base.opensearchSyncIndex(myShepherd, Occurrence.class, BACKGROUND_SLICE_SIZE);
            Base.opensearchSyncIndex(myShepherd, MediaAsset.class, BACKGROUND_SLICE_SIZE);
            System.out.println("OpenSearch background indexing finished.");
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            myShepherd.rollbackAndClose();
            unsetActiveIndexingBackground();
        }
    }

    public void createIndex(String indexName, JSONObject mapping)
    throws IOException {
        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
        IndexSettings indexSettings = null;
        // a little hacky but meh
        if (indexName.equals("annotation")) {
            // also? "knn.algo_param.ef_search": 100
            indexSettings = IndexSettings.of(is -> is.knn(true));
        }
        CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder().index(
            indexName).settings(indexSettings).build();

        client.indices().create(createIndexRequest);
        // TODO fold in this settings-change into indexSettings above
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
        // Central gate: objects that opt out (e.g. non-candidate/"trivial" annotations)
        // must not have an index doc. Delete any stale doc (covers matchAgainst->false or
        // last-embedding-removed transitions) and skip indexing. This makes BOTH
        // Base.opensearchIndex() and the reconciler's direct os.index(obj) honor the
        // predicate. Synchronous delete: callers are already on background threads.
        if (!obj.shouldIndexInOpenSearch()) {
            delete(indexName, id);   // delete() no-ops if the index/doc is absent
            return;
        }
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
        query.put("from", numFrom);
        query.put("size", pageSize);
        // "sort": [ {"@timestamp": {"order": "asc"}} ]
        if (sort != null) {
            JSONArray sortArr = new JSONArray();
            if ((sortOrder == null) || !"desc".equals(sortOrder)) sortOrder = "asc";
            sortArr.put(new JSONObject("{\"" + sort + "\":{\"order\":\"" + sortOrder + "\"}}"));
            query.put("sort", sortArr);
        }
        int attempt = 0;
        while (true) {
            String pitId = createPit(indexName);
            JSONObject jpit = new JSONObject();
            jpit.put("id", pitId);
            jpit.put("keep_alive", SEARCH_PIT_TIME);
            query.put("pit", jpit);
            Request searchRequest = new Request("POST", "/_search?track_total_hits=true");
            searchRequest.setJsonEntity(query.toString());
            try {
                return new JSONObject(getRestResponse(searchRequest));
            } catch (ResponseException ex) {
                // always drop OUR cached PIT: a possibly-bad cached id would otherwise
                // poison every subsequent search on this index
                discardPitQuietly(indexName, pitId);
                if (!isStaleSearchContextError(ex.getMessage())) {
                    // deterministic rejection (bad query, result window exceeded, ...):
                    // retrying cannot succeed and would burn a new server-side PIT per attempt
                    throw ex;
                }
                attempt++;
                System.out.println("queryPit() using pitId=" + pitId + " failed[" + attempt +
                    "] with: " + ex);
                if (attempt > 5) {
                    ex.printStackTrace();
                    throw new IOException("queryPit() failed to POST query");
                }
                // we try again with a fresh PIT
            }
        }
    }

    // queryPit()'s cached PIT can expire server-side; ONLY that failure class is worth
    // a retry with a fresh PIT, matched on the typed OpenSearch cause. Anything else
    // (including a bare 404) is deterministic - retrying cannot succeed.
    static boolean isStaleSearchContextError(String message) {
        if (message == null) return false;
        return message.contains("search_context_missing")
            || message.contains("No search context found");
    }

    // best-effort: delete the (likely stale) server-side PIT before dropping it from
    // cache - but only if the cache still holds the pit THIS request used, so we never
    // destroy a fresh PIT installed by a concurrent request
    private void discardPitQuietly(String indexName, String pitId) {
        if ((pitId == null) || !pitId.equals(PIT_CACHE.get(indexName))) return;
        try {
            deletePit(indexName);
        } catch (Exception ex) {
            PIT_CACHE.remove(indexName);
        }
    }

    // the effective pagination ceiling (from + size limit) for queries on this index
    public int getMaxResultWindow(String indexName) {
        if (indexName == null) return DEFAULT_MAX_RESULT_WINDOW;
        long[] cached = MAX_RESULT_WINDOW_CACHE.get(indexName);
        if ((cached != null) && (cached[1] > System.currentTimeMillis())) return (int)cached[0];
        try {
            int window = parseMaxResultWindow(getSettings(indexName));
            MAX_RESULT_WINDOW_CACHE.put(indexName,
                new long[] { window, System.currentTimeMillis() + MAX_RESULT_WINDOW_CACHE_MILLIS });
            return window;
        } catch (Exception ex) {
            // do NOT cache the fallback: retry the settings read on the next request
            System.out.println("OpenSearch.getMaxResultWindow(" + indexName +
                ") settings unavailable, using default: " + ex);
            return DEFAULT_MAX_RESULT_WINDOW;
        }
    }

    static int parseMaxResultWindow(JSONObject indexSettings) {
        if (indexSettings == null) return DEFAULT_MAX_RESULT_WINDOW;
        try {
            // _settings returns values as strings; tolerate numbers too
            int window = Integer.parseInt(indexSettings.opt("max_result_window").toString());
            // a broken/misconfigured value must never lock search out entirely
            return (window < 1) ? DEFAULT_MAX_RESULT_WINDOW : window;
        } catch (Exception ex) {
            return DEFAULT_MAX_RESULT_WINDOW;
        }
    }

    // just return the actual hit results
    // note: each object in the array has _id but actual doc is in _source!!
    public static JSONArray getHits(JSONObject queryResults) {
        JSONArray failed = new JSONArray();

        if (queryResults == null) return failed;
        JSONObject outerHits = queryResults.optJSONObject("hits");
        if (outerHits == null) {
            System.out.println("could not find (outer) hits");
            return failed;
        }
        JSONArray hits = outerHits.optJSONArray("hits");
        if (hits == null) {
            System.out.println("could not find hits");
            return failed;
        }
        return hits;
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

    // ml-service migration v2 (commit #7): force pending writes in `indexName`
    // through Lucene's refresh boundary so they are searchable. Synchronous;
    // returns after targeted shards have completed the refresh. NOT a Wildbook
    // queue drain — IndexingManager may still have unindexed entities queued.
    // Callers (typically waitForVisibility) follow with a visibility poll.
    public void indexRefresh(final String indexName)
    throws IOException {
        if (!isValidIndexName(indexName))
            throw new IOException("invalid index name: " + indexName);
        Request req = new Request("POST", indexName + "/_refresh");
        getRestResponse(req);   // discard body; non-2xx surfaces as IOException
    }

    // ml-service migration v2 (commit #7): bounded poll-and-wait until OpenSearch
    // can see every id in `ids` in `indexName`. Used by MlServiceProcessor
    // (commit #9) post-persist to avoid running findMatchProspects against an
    // index that doesn't yet contain the freshly-written annotations.
    //
    // On entry:
    //   - normalizes `ids` to a Set (drops nulls and duplicates so they can't
    //     prevent the count check from ever succeeding);
    //   - calls _refresh once (synchronous; pushes pending writes through
    //     Lucene's refresh boundary);
    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
    //     every poll return zero hits regardless of how long we wait.
    //
    // Then polls a _count eligibility query with exponential backoff (start
    // 100ms, double, cap 1s) until count >= |normalized ids| OR the total
    // wait reaches timeoutMs. Returns true on visible-success, false on
    // timeout. Caller decides what to do on false (e.g. enqueue a deferred-
    // match job rather than match against a partial index).
    //
    // Does NOT try to drain the Wildbook IndexingManager queue. That queue
    // may contain unrelated entities; queue-depth zero doesn't imply the
    // specific IDs are queryable. Polling visibility IS the correctness gate.
    public boolean waitForVisibility(String indexName, Collection<String> ids,
        long timeoutMs)
    throws IOException {
        if (!isValidIndexName(indexName))
            throw new IOException("invalid index name: " + indexName);
        if (ids == null || ids.isEmpty()) return true;

        // Normalize: drop nulls + duplicates so the count comparison is
        // against the true number of distinct documents we expect to see.
        Set<String> targetIds = new LinkedHashSet<String>();
        for (String id : ids) {
            if (id != null) targetIds.add(id);
        }
        if (targetIds.isEmpty()) return true;

        if (skipAutoIndexing()) {
            System.out.println(
                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
                "— every poll will return zero hits regardless of wait time.");
        }

        indexRefresh(indexName);

        JSONObject query = buildIdEligibilityQuery(targetIds);
        long deadline = System.currentTimeMillis() + timeoutMs;
        long sleepMs = 100;
        while (true) {
            int seen = queryCount(indexName, query);
            if (seen >= targetIds.size()) return true;
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) return false;
            try {
                Thread.sleep(Math.min(sleepMs, remaining));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
            sleepMs = Math.min(sleepMs * 2, 1000);
        }
    }

    // Returns a query body that filters on _id ∈ ids, using OpenSearch's idiomatic `ids` query.
    // Public so the media-resolve endpoint (org.ecocean.api) can reuse the exact id-eligibility shape.
    public static JSONObject buildIdEligibilityQuery(Set<String> ids) {
        JSONArray idArr = new JSONArray();
        for (String id : ids) idArr.put(id);
        JSONObject query = new JSONObject();
        query.put("query",
            new JSONObject().put("ids",
                new JSONObject().put("values", idArr)));
        return query;
    }

    // ml-service migration v2 / empty-match-prospects design Track 2 C8.
    //
    // Stronger visibility predicate than waitForVisibility for the annotation
    // index. A doc that exists by _id but is missing nested
    // embeddings.method/methodVersion would pass _id-only and then knn-fail
    // at match time. This helper polls a predicate that mirrors the matching
    // constraints in Annotation.getMatchQuery: id ∈ ids AND matchAgainst=true
    // AND acmId exists AND a nested embedding for this method/version is
    // indexed. Scope is intentionally narrower than getMatchingSetQuery
    // (no taxonomy/viewpoint/encounter/dead-animal filters) — this helper
    // answers "doc has fresh embedding metadata", which is the visibility
    // race the Track 2 batch gate cares about.
    //
    // method/methodVersion follow the strict-when-present convention of
    // Annotation.getMatchQuery at Annotation.java:1205-1209: if either is
    // null/blank, the corresponding nested predicate is omitted.
    //
    // Like waitForVisibility: _refresh on entry, then exponential-backoff
    // poll of _count until count >= |normalized ids| OR timeout. Empty
    // wait set short-circuits to true.
    public boolean waitForAnnotationMatchableIds(Collection<String> ids,
        String method, String methodVersion, long timeoutMs)
    throws IOException {
        if (ids == null || ids.isEmpty()) return true;

        Set<String> targetIds = new LinkedHashSet<String>();
        for (String id : ids) {
            if (id != null) targetIds.add(id);
        }
        if (targetIds.isEmpty()) return true;

        if (skipAutoIndexing()) {
            System.out.println(
                "WARN: OpenSearch.waitForAnnotationMatchableIds called with " +
                "/tmp/skipAutoIndexing set — every poll will return zero hits " +
                "regardless of wait time.");
        }

        indexRefresh("annotation");

        JSONObject query = buildAnnotationMatchableQuery(targetIds, method,
            methodVersion);
        long deadline = System.currentTimeMillis() + timeoutMs;
        long sleepMs = 100;
        while (true) {
            int seen = queryCount("annotation", query);
            if (seen >= targetIds.size()) return true;
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) return false;
            try {
                Thread.sleep(Math.min(sleepMs, remaining));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
            sleepMs = Math.min(sleepMs * 2, 1000);
        }
    }

    // Package-visible for testing. Returns the _count-shaped query body
    // matching the annotation-matchable predicate documented on
    // waitForAnnotationMatchableIds. Uses the same `ids` query shape as
    // buildIdEligibilityQuery for consistency with queryCount's
    // expectations (no `size`, no `track_total_hits`).
    static JSONObject buildAnnotationMatchableQuery(Set<String> ids,
        String method, String methodVersion) {
        JSONArray idArr = new JSONArray();
        for (String id : ids) idArr.put(id);

        JSONArray filterArr = new JSONArray();
        filterArr.put(new JSONObject().put("ids",
            new JSONObject().put("values", idArr)));
        filterArr.put(new JSONObject().put("term",
            new JSONObject().put("matchAgainst", true)));
        filterArr.put(new JSONObject().put("exists",
            new JSONObject().put("field", "acmId")));

        // Nested embedding clause. Match Annotation.getMatchQuery at
        // Annotation.java:1205-1209 exactly: omit a predicate only when
        // the value is `null`. A non-null blank string would be a strict
        // term on "" (matching no docs), preserving consistency with the
        // matcher rather than silently broadening the wait predicate.
        // Codex round-1 C8 review surfaced this — empty vs null asymmetry
        // would let the gate green-light docs the matcher then rejects.
        JSONArray nestedMust = new JSONArray();
        if (method != null) {
            nestedMust.put(new JSONObject().put("term",
                new JSONObject().put("embeddings.method", method)));
        }
        if (methodVersion != null) {
            nestedMust.put(new JSONObject().put("term",
                new JSONObject().put("embeddings.methodVersion", methodVersion)));
        }
        JSONObject nestedQuery;
        if (nestedMust.length() == 0) {
            // Both null — wait only on the existence of any nested
            // embedding entry. (Legacy api_endpoint-only configs that
            // can't derive a method.)
            nestedQuery = new JSONObject().put("match_all", new JSONObject());
        } else {
            nestedQuery = new JSONObject().put("bool",
                new JSONObject().put("must", nestedMust));
        }
        filterArr.put(new JSONObject().put("nested",
            new JSONObject().put("path", "embeddings").put("query", nestedQuery)));

        JSONObject query = new JSONObject();
        query.put("query",
            new JSONObject().put("bool",
                new JSONObject().put("filter", filterArr)));
        return query;
    }

    // when you only care about how many this would return
    public int queryCount(String indexName, final JSONObject query)
    throws IOException {
        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
        Request searchRequest = new Request("POST", indexName + "/_count");
        JSONObject cleanedQuery = new JSONObject(query.toString());
        cleanedQuery.remove("_source"); // invalid for a _count query
        searchRequest.setJsonEntity(cleanedQuery.toString());
        JSONObject res = new JSONObject();
        try {
            res = new JSONObject(getRestResponse(searchRequest));
        } catch (Exception ex) {
            System.out.println("queryCount() on index " + indexName + " using query=" + query +
                " failed with: " + ex);
            ex.printStackTrace();
            throw new IOException("queryCount() failed");
        }
        return res.optInt("count", -1);
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
            MAX_RESULT_WINDOW_CACHE.remove(indexName);
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

    // updateData is { field0: value0, field1: value1, ... }
    public void indexUpdate(final String indexName, String id, JSONObject updateData)
    throws IOException {
        if (!existsIndex(indexName)) throw new IOException("index does not exist: " + indexName);
        if ((id == null) || (updateData == null)) throw new IOException("missing id or updateData");
        updateData.put("indexTimestamp", System.currentTimeMillis());
        JSONObject doc = new JSONObject();
        doc.put("doc", updateData);
        Request updateRequest = new Request("POST", indexName + "/_update/" + id);
        updateRequest.setJsonEntity(doc.toString());
        getRestResponse(updateRequest);
    }

    // Reads the CURRENT indexed viewUsers array for a single doc. Returns the array
    // (possibly empty) on success, or null if the doc/field cannot be read (missing doc,
    // index not present, parse failure). null means "unknown": the caller decides the
    // policy (opensearchIndexPermissions treats unknown as no-change to avoid storming
    // child reindexes on a degraded read; the reconciler recovers a missed refresh).
    public org.json.JSONArray getIndexedViewUsers(String index, String id) {
        if ((index == null) || (id == null)) return null;
        try {
            if (!existsIndex(index)) return null;
            // _source filtered to just viewUsers keeps the response tiny.
            Request getRequest = new Request("GET", index + "/_doc/" + id + "?_source=viewUsers");
            String body = getRestResponse(getRequest);
            if (body == null) return null;
            org.json.JSONObject parsed = new org.json.JSONObject(body);
            if (!parsed.optBoolean("found", false)) return null;
            org.json.JSONObject source = parsed.optJSONObject("_source");
            if (source == null) return new org.json.JSONArray(); // doc exists, no viewUsers yet -> empty
            org.json.JSONArray arr = source.optJSONArray("viewUsers");
            return (arr == null) ? new org.json.JSONArray() : arr;
        } catch (Exception ex) {
            // 404 (doc not found) surfaces as ResponseException here; treat as unknown.
            return null;
        }
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

    public static long setPermissionsTimestamp(Shepherd myShepherd) {
        long now = System.currentTimeMillis();

        SystemValue.set(myShepherd, PERMISSIONS_LAST_RUN_KEY, now);
        return now;
    }

    public static Long getPermissionsTimestamp(Shepherd myShepherd) {
        return SystemValue.getLong(myShepherd, PERMISSIONS_LAST_RUN_KEY);
    }

    public static void setPermissionsNeeded(Shepherd myShepherd, boolean value) {
        SystemValue.set(myShepherd, PERMISSIONS_NEEDED_KEY, value);
    }

    public static void setPermissionsNeeded(boolean value) {
        Shepherd myShepherd = new Shepherd("context0");

        myShepherd.setAction("OpenSearch.setPermissionsNeeded");
        myShepherd.beginDBTransaction();
        try {
            setPermissionsNeeded(myShepherd, value);
            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();
        } catch (Exception ex) {
            ex.printStackTrace();
            myShepherd.rollbackAndClose();
        }
    }

    public static boolean getPermissionsNeeded(Shepherd myShepherd) {
        Boolean value = SystemValue.getBoolean(myShepherd, PERMISSIONS_NEEDED_KEY);

        if (value == null) return false;
        return value;
    }

    public static JSONObject querySanitize(JSONObject query, User user, Shepherd myShepherd)
    throws IOException {
        if ((query == null) || (user == null)) throw new IOException("empty query or user");
        // see issue 958 - now we let query pass as-is for anyone, results are scrubbed later e.g. sanitizeDoc() below
        return query;
    }

    /**
     * Wrap a search query's top-level "query" clause in a bool whose filter enforces the
     * encounter ACL (mirrors Encounter.opensearchAccess for a non-admin user). Applied on the
     * token-authenticated path BEFORE execution so totals, pagination, and hits are all scoped.
     * Admins are not passed through here (caller skips the call for admins).
     *
     * Decision (documented, safe): a request with no inner "query" yields a filter-only bool,
     * i.e. "all encounters this user may see" — still fully scoped, never a bypass. A truly
     * malformed (non-JSON) body fails earlier in ServletUtilities.jsonFromHttpServletRequest,
     * before reaching this method, so the spec's "fail closed on malformed" is satisfied upstream.
     */
    public static JSONObject applyEncounterAclFilter(JSONObject query, String userId)
    throws IOException {
        return applyAclFilter(query, userId, "encounter");
    }

    public static JSONObject applyAclFilter(JSONObject query, String userId, String indexName)
    throws IOException {
        if ((query == null) || !Util.stringExists(userId))
            throw new IOException("applyAclFilter: null query or userId");
        // encounter docs carry a single submitterUserId; annotation/individual carry the union set submitterUserIds
        String submitterField = "encounter".equals(indexName) ? "submitterUserId" : "submitterUserIds";
        JSONArray should = new JSONArray();
        should.put(new JSONObject().put("term", new JSONObject().put("publiclyReadable", true)));
        should.put(new JSONObject().put("term", new JSONObject().put(submitterField, userId)));
        should.put(new JSONObject().put("term", new JSONObject().put("viewUsers", userId)));
        JSONObject aclBool = new JSONObject();
        aclBool.put("should", should);
        aclBool.put("minimum_should_match", 1);
        JSONObject acl = new JSONObject().put("bool", aclBool);

        JSONObject wrapBool = new JSONObject();
        JSONObject inner = query.optJSONObject("query");
        if (inner != null) {
            JSONArray must = new JSONArray();
            must.put(inner);
            wrapBool.put("must", must);
        }
        wrapBool.put("filter", new JSONArray().put(acl));

        JSONObject out = new JSONObject(query.toString()); // shallow copy via re-parse
        out.put("query", new JSONObject().put("bool", wrapBool));
        return out;
    }

    private static final String[] ACL_FIELDS = {
        "publiclyReadable", "submitterUserId", "submitterUserIds", "viewUsers", "editUsers"
    };
    // identity fields kept for a non-admin token individual hit; everything else dropped (allowlist).
    // NOTE: socialUnits/relationships/cooccurrence*/users/encounterIds/number* and all other
    // cross-encounter aggregates are DELIBERATELY excluded — they reveal data from encounters the
    // viewer may not see. Out of scope for v1 (agent gets per-viewer detail via the scoped encounter index).
    private static final String[] INDIVIDUAL_TOKEN_KEEP = {
        "id", "version", "indexTimestamp", "displayName", "names", "nameMap",
        "sex", "taxonomy", "timeOfBirth", "timeOfDeath"
    };
    // Set form of the individual identity allowlist, for query-side field validation.
    public static final java.util.Set<String> INDIVIDUAL_TOKEN_KEEP_SET =
        new java.util.HashSet<>(java.util.Arrays.asList(INDIVIDUAL_TOKEN_KEEP));

    // Structural/operator keys in the OpenSearch query DSL whose CHILD object keys are NOT field names.
    private static final java.util.Set<String> DSL_STRUCTURAL = new java.util.HashSet<>(java.util.Arrays.asList(
        "query","bool","must","should","filter","must_not","minimum_should_match","boost",
        "match_all","match_none","constant_score","dis_max","queries","tie_breaker",
        "term","terms","match","match_phrase","match_phrase_prefix","range",
        "prefix","wildcard","regexp","fuzzy","ids","exists"));
    // Leaf-operator keys whose CHILD OBJECT's keys ARE field names (e.g. term/range/match -> {field:...}).
    // NOTE: "terms" is intentionally absent — it is handled separately in nodeAllowed because the
    // terms-lookup form (object value) must be rejected while the plain-array form is allowed.
    private static final java.util.Set<String> FIELD_BEARING = new java.util.HashSet<>(java.util.Arrays.asList(
        "term","match","match_phrase","match_phrase_prefix","range","prefix","wildcard","regexp","fuzzy"));
    // Keys that are DISALLOWED outright (can reference arbitrary fields / execute code).
    private static final java.util.Set<String> DENY_FEATURES = new java.util.HashSet<>(java.util.Arrays.asList(
        "script","script_score","aggs","aggregations","sort","_source","fields","docvalue_fields",
        "runtime_mappings","function_score","more_like_this","percolate","field",
        // nested/path let a caller probe nested aggregate fields (socialUnits/relationships) -> deny
        "nested","path",
        // free-text/Lucene operators carry field references the validator can't parse -> fail-closed
        "query_string","simple_query_string","multi_match"));

    // The ONLY top-level body keys a non-admin token individual search may carry.
    // Anything else (post_filter, collapse, rescore, suggest, highlight, aggs, sort, _source,
    // fields, script_fields, search_after, pit, runtime_mappings, ...) is rejected fail-closed.
    private static final java.util.Set<String> ALLOWED_TOP_LEVEL_KEYS =
        new java.util.HashSet<>(java.util.Arrays.asList("query", "from", "size"));

    // ---- Token search aggregations (deny-by-default; see docs/superpowers/specs/2026-06-28-token-search-aggregations.md)
    /** Max terms buckets a caller may request. */
    public static final int AGG_MAX_BUCKETS = 1000;
    // Only these top-level body keys may accompany an aggregation request (blocks runtime_mappings,
    // post_filter, script_fields, fields, _source, body sort/from/size, collapse, rescore, suggest,
    // caller pit, ...). Pagination is via the ?from=/?size= URL params (queryPit ignores body
    // from/size), so reject body from/size on an agg request rather than silently dropping it; for a
    // counts-only request pass ?size=0 on the URL.
    private static final java.util.Set<String> AGG_BODY_TOP_LEVEL = new java.util.HashSet<>(
        java.util.Arrays.asList("query", "aggs", "aggregations"));
    // Per-index allow-list of fields a terms aggregation may bucket on. ONLY fields confirmed to be
    // explicitly keyword-mapped (terms-aggregatable) in opensearchMapping(); never identity/PII/ACL.
    private static final java.util.Map<String, java.util.Set<String>> AGG_ALLOWED_FIELDS;
    static {
        java.util.Map<String, java.util.Set<String>> m = new java.util.HashMap<>();
        m.put("encounter", new java.util.HashSet<>(java.util.Arrays.asList(
            "locationId", "taxonomy", "country", "lifeStage")));
        m.put("annotation", new java.util.HashSet<>(java.util.Arrays.asList(
            "viewpoint", "iaClass", "encounterLocationId", "encounterTaxonomy")));
        m.put("individual", new java.util.HashSet<>(java.util.Arrays.asList(
            "taxonomy", "sex")));
        AGG_ALLOWED_FIELDS = java.util.Collections.unmodifiableMap(m);
    }

    /**
     * Validate an aggregation-bearing token-search body, deny-by-default. Returns null when the body
     * has NO aggregation (a normal search) OR a fully-valid one; otherwise returns a human-readable
     * reason to reject with HTTP 400 (so an unsupported aggregation is an explicit error, never a
     * silent empty result). Permits ONLY a direct `terms` aggregation on an allow-listed keyword field
     * with an optional bounded integer `size` — no wrappers (global/filter/nested/...), no
     * sub-aggregations, no pipeline/metric/script aggregations, no extra terms params, and no other
     * top-level body keys. The caller surfaces `aggregations` from the response only on success.
     */
    public static String aggregationError(JSONObject body, String indexName) {
        if (body == null) return null;
        boolean hasAggs = body.has("aggs");
        boolean hasAggregations = body.has("aggregations");
        if (!hasAggs && !hasAggregations) return null; // no aggregation requested
        if (hasAggs && hasAggregations) return "specify only one of 'aggs' or 'aggregations'";
        // (A) strict whole-body top-level allow-list
        for (String key : body.keySet()) {
            if (!AGG_BODY_TOP_LEVEL.contains(key)) {
                return "top-level field '" + key + "' is not allowed alongside an aggregation";
            }
        }
        JSONObject aggs = body.optJSONObject(hasAggs ? "aggs" : "aggregations");
        if ((aggs == null) || (aggs.length() == 0)) return "aggregation must be a non-empty object";
        java.util.Set<String> allowedFields = AGG_ALLOWED_FIELDS.get(indexName);
        if (allowedFields == null) return "aggregations are not supported for this index";
        for (String name : aggs.keySet()) {
            JSONObject named = aggs.optJSONObject(name);
            if (named == null) return "aggregation '" + name + "' must be an object";
            // (B) exactly one direct 'terms' — no sub-aggs, no wrapper/other agg type
            if ((named.length() != 1) || !named.has("terms")) {
                return "aggregation '" + name
                    + "' must be exactly a 'terms' aggregation (no sub-aggregations or other types)";
            }
            JSONObject terms = named.optJSONObject("terms");
            if (terms == null) return "aggregation '" + name + "' has an invalid 'terms' body";
            // (C) terms params allow-list: field (required) + optional bounded integer size
            for (String tk : terms.keySet()) {
                if (!"field".equals(tk) && !"size".equals(tk)) {
                    return "terms parameter '" + tk + "' is not allowed (only 'field' and 'size')";
                }
            }
            Object field = terms.opt("field");
            if (!(field instanceof String) || !allowedFields.contains((String) field)) {
                return "terms field '" + field + "' is not allowed for index '" + indexName + "'";
            }
            if (terms.has("size")) {
                Object sz = terms.opt("size");
                long size;
                if (sz instanceof Integer) size = (Integer) sz;
                else if (sz instanceof Long) size = (Long) sz;
                else return "terms 'size' must be an integer";
                if ((size < 1) || (size > AGG_MAX_BUCKETS)) {
                    return "terms 'size' must be between 1 and " + AGG_MAX_BUCKETS;
                }
            }
        }
        return null;
    }

    /**
     * Fail-closed: returns true ONLY if every field the query/sort/aggs could reference is in `allowed`.
     * Used to constrain non-admin token individual searches to identity fields (so a caller can't
     * probe hidden cross-encounter aggregates via range/sort/aggs/etc.).
     */
    public static boolean queryReferencesOnlyAllowedFields(JSONObject body, java.util.Set<String> allowed) {
        if (body == null) return true;
        // Strict top-level allowlist (fail-closed): ONLY query/from/size are permitted. Any other
        // top-level key (post_filter, collapse, rescore, suggest, highlight, aggs, sort, _source,
        // fields, script_fields, search_after, pit, ...) is an unvetted field-bearing/feature key.
        for (String key : body.keySet()) {
            if (!ALLOWED_TOP_LEVEL_KEYS.contains(key)) return false;
        }
        // from/size are pagination scalars; only `query` needs recursive field validation.
        return nodeAllowed(body.opt("query"), allowed, false);
    }

    // expectField=true means: the CURRENT object's KEYS are field names to check against the allowlist.
    private static boolean nodeAllowed(Object node, java.util.Set<String> allowed, boolean expectField) {
        if (node == null) return true;
        if (node instanceof org.json.JSONArray) {
            org.json.JSONArray arr = (org.json.JSONArray) node;
            for (int i = 0; i < arr.length(); i++) if (!nodeAllowed(arr.opt(i), allowed, expectField)) return false;
            return true;
        }
        if (!(node instanceof JSONObject)) return true; // scalar value
        JSONObject obj = (JSONObject) node;
        for (String key : obj.keySet()) {
            if (DENY_FEATURES.contains(key)) return false; // script/field/etc. anywhere -> reject
            if (expectField) {
                // current level keys are FIELD NAMES
                if (!allowed.contains(rootField(key))) return false;
                // values under a field (e.g. range bounds) are leaf params; don't recurse for fields
            } else if ("terms".equals(key)) {
                // Special-case: the terms operator has two legal forms:
                //   plain:  {"terms": {"field": ["v1","v2"]}}   -> array/scalar value  -> allowed (if field allowlisted)
                //   lookup: {"terms": {"field": {"index":"...","path":"..."}}} -> object value -> REJECT
                // The lookup form lets a caller probe hidden nested fields via "path"; reject it fail-closed.
                Object tv = obj.opt(key);
                if (!(tv instanceof JSONObject)) return false; // terms body must be a JSONObject
                JSONObject to = (JSONObject) tv;
                for (String f : to.keySet()) {
                    if (!allowed.contains(rootField(f))) return false; // field must be allowlisted
                    if (to.opt(f) instanceof JSONObject) return false;  // object value = terms-lookup -> reject
                }
            } else if (FIELD_BEARING.contains(key)) {
                // the child object's keys are field names
                if (!nodeAllowed(obj.opt(key), allowed, true)) return false;
            } else if (DSL_STRUCTURAL.contains(key)) {
                if (!nodeAllowed(obj.opt(key), allowed, false)) return false;
            } else {
                // unknown key in a structural position -> fail closed
                return false;
            }
        }
        return true;
    }

    // allow nested subfields under an allowlisted root (e.g. nameMap.foo, names.keyword)
    private static String rootField(String field) {
        int dot = field.indexOf('.');
        return (dot >= 0) ? field.substring(0, dot) : field;
    }

    private static void scrubAclFields(JSONObject doc) {
        for (String f : ACL_FIELDS) doc.remove(f);
    }

    // 4-arg overload preserved for the existing caller (non-token path) until SearchApi passes tokenAuth.
    public static JSONObject sanitizeDoc(final JSONObject sourceDoc, String indexName,
        Shepherd myShepherd, User user)
    throws IOException {
        return sanitizeDoc(sourceDoc, indexName, myShepherd, user, false);
    }

    // takes raw search result doc and presents only data user should see
    public static JSONObject sanitizeDoc(final JSONObject sourceDoc, String indexName,
        Shepherd myShepherd, User user, boolean tokenAuth)
    throws IOException {
        if ((user == null) || (sourceDoc == null)) throw new IOException("null user or sourceDoc");
        if ("annotation".equals(indexName)) {
            JSONObject clean = new JSONObject(sourceDoc.toString());
            scrubAclFields(clean);             // never leak the internal ACL fields
            return clean;                      // content (incl. embeddings) returned as-is
        }
        if ("individual".equals(indexName)) {
            boolean admin = user.isAdmin(myShepherd);
            if (tokenAuth && !admin) {
                JSONObject clean = new JSONObject();
                for (String f : INDIVIDUAL_TOKEN_KEEP) {
                    if (sourceDoc.has(f)) clean.put(f, sourceDoc.get(f));
                }
                return clean;                  // allowlist: identity only, aggregates dropped
            }
            JSONObject clean = new JSONObject(sourceDoc.toString());
            scrubAclFields(clean);
            return clean;
        }
        // these we return some kinda cleaned value
        JSONObject clean = new JSONObject();
        if ("encounter".equals(indexName)) {
            boolean hasAccess = Encounter.opensearchAccess(sourceDoc, user, myShepherd);
            if (hasAccess) {
                clean = new JSONObject(sourceDoc.toString());
                scrubAclFields(clean);
                clean.put("access", "full");
                return clean;
            }
            clean.put("access", "none");
            String[] okFields = new String[] {
                "id", "version", "indexTimestamp", "version", "individualId",
                    "individualDisplayName", "occurrenceId", "otherCatalogNumbers", "dateSubmitted",
                    "date", "locationId", "locationName", "taxonomy", "assignedUsername",
                    "numberAnnotations"
            };
            for (String fieldName : okFields) {
                if (sourceDoc.has(fieldName)) clean.put(fieldName, sourceDoc.get(fieldName));
            }
        } else if ("occurrence".equals(indexName)) {
            // right now, we only search occurrences for finding based on name, so we punt on
            // this quite a bit. we basically only show as access=full when the user can
            // (in theory) edit the occurrence
            boolean hasAccess = user.isAdmin(myShepherd) || hasAccessOccurrence(user, sourceDoc);
            if (hasAccess) {
                clean = new JSONObject(sourceDoc.toString());
                scrubAclFields(clean);
                clean.put("access", "full");
            } else {
                clean = new JSONObject();
                clean.put("id", sourceDoc.optString("id", "unknown"));
                clean.put("access", "none");
            }
        }
        // if we fall through (e.g. future classes) clean will just be empty
        return clean;
    }

    private static boolean hasAccessOccurrence(User user, JSONObject doc) {
        if ((user == null) || (doc == null)) return false;
        String username = user.getUsername();
        if (username == null) return false;
        if (username.equals(doc.optString("submitterId", "__FAIL__"))) return true;
        JSONArray earr = doc.optJSONArray("encounters");
        if ((earr == null) || (earr.length() < 1)) return false;
        for (int i = 0; i < earr.length(); i++) {
            JSONObject enc = earr.optJSONObject(i);
            if (enc == null) continue;
            if (username.equals(enc.optString("submitterId", "__FAIL__"))) return true;
        }
        return false;
    }

    public static boolean indexingActive() {
        return indexingActiveBackground() || indexingActiveForeground();
    }

    public static boolean indexingActiveForeground() {
        return getActive(ACTIVE_TYPE_FOREGROUND);
    }

    public static void setActiveIndexingForeground() {
        setActive(ACTIVE_TYPE_FOREGROUND);
    }

    public static void unsetActiveIndexingForeground() {
        unsetActive(ACTIVE_TYPE_FOREGROUND);
    }

    public static boolean indexingActiveBackground() {
        return getActive(ACTIVE_TYPE_BACKGROUND);
    }

    public static void setActiveIndexingBackground() {
        setActive(ACTIVE_TYPE_BACKGROUND);
    }

    public static void unsetActiveIndexingBackground() {
        unsetActive(ACTIVE_TYPE_BACKGROUND);
    }

    static void setActive(String type) {
        // we want our own shepherd as the main shepherd may not persist this til later
        Shepherd myShepherd = new Shepherd("context0");

        myShepherd.setAction("OpenSearch.setActive");
        myShepherd.beginDBTransaction();
        try {
            SystemValue.set(myShepherd, type, true);
            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();
        } catch (Exception ex) {
            ex.printStackTrace();
            myShepherd.rollbackAndClose();
        }
    }

    static void unsetActive(String type) {
        Shepherd myShepherd = new Shepherd("context0");

        myShepherd.setAction("OpenSearch.unsetActive");
        myShepherd.beginDBTransaction();
        try {
            SystemValue.set(myShepherd, type, false);
            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();
        } catch (Exception ex) {
            ex.printStackTrace();
            myShepherd.rollbackAndClose();
        }
    }

    // TODO probably should get in some sort of expire/stale check here
    static boolean getActive(String type) {
        Boolean active = false;
        Shepherd myShepherd = new Shepherd("context0");

        myShepherd.setAction("OpenSearch.getActive");
        myShepherd.beginDBTransaction();
        try {
            active = SystemValue.getBoolean(myShepherd, type);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        myShepherd.rollbackAndClose();
        if (active == null) return false;
        return active;
    }

    // TODO: right now this respects index timestamp and only indexes objects with versions > timestamp.
    // want to make an option to index everything and ignore version/timestamp.
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
            query.closeAll();
            return;
        }
        query.closeAll();
        long initTime = System.currentTimeMillis();
        System.out.println("OpenSearch.indexAll() [" +
            java.time.Instant.ofEpochMilli(now).toString() + "] indexing " + indexName + ": size=" +
            all.size() + " (" + obj.getClass() + ")");
        int ct = 0;
        for (Base item : all) {
            ct++;
            index(indexName, item);
            if (ct % 500 == 0)
                System.out.println("OpenSearch.indexAll() [" +
                    (System.currentTimeMillis() - initTime) + "] indexed " + indexName + ": " + ct +
                    " of " + all.size());
        }
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

    public static Object getConfigurationValue(String key, Object defaultValue) {
        return getConfigurationValue("context0", key, defaultValue);
    }

    public static Object getConfigurationValue(String context, String key, Object defaultValue) {
        if (key == null) return null;
        Properties props = getConfigurationProperties(context);
        if (props == null) {
            System.out.println(
                "OpenSearch.getConfigurationValue(): WARNING could not get properties file; using defaultValue ["
                + defaultValue + "] for " + key);
            return defaultValue;
        }
        String propValue = props.getProperty(key);
        // TODO can we actually set a NULL from a properties file? if so: we need to return that as null here
        if (propValue == null) return defaultValue;
        if (defaultValue instanceof Integer) { // get int from string
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException nfe) {
                return defaultValue;
            }
        }
        if (defaultValue instanceof Double) { // get int from string
            try {
                return Double.parseDouble(propValue);
            } catch (NumberFormatException nfe) {
                return defaultValue;
            }
        }
        // guess we are just a string
        return propValue;
    }

    static Properties getConfigurationProperties(String context) {
        return ShepherdProperties.getProperties("OpenSearch.properties", "", context);
    }
}
