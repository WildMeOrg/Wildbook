package org.ecocean;

/*
   import java.net.URL;
   import java.util.ArrayList;
   import java.util.Arrays;
   import java.util.HashSet;
   import java.util.List;
   import java.util.Properties;
   import java.util.Random;
   import javax.servlet.http.HttpServletRequest;
   import org.ecocean.datacollection.*;
   import org.ecocean.media.Feature;
   import org.ecocean.media.MediaAsset;
   import org.ecocean.media.MediaAssetFactory;
   import org.ecocean.media.URLAssetStore;
   import org.ecocean.movement.*;
   import org.ecocean.servlet.ServletUtilities;
   import org.joda.time.DateTime;
   import org.json.JSONArray;
   import org.json.JSONObject;

   import java.io.IOException;
   import java.net.MalformedURLException;
   import java.security.InvalidKeyException;
   import java.security.NoSuchAlgorithmException;
   import org.apache.shiro.crypto.hash.Sha256Hash;
 */
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/*
   import org.apache.hc.client5.http.auth.AuthScope;
   import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
   import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
   import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
   import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
   import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
   import org.apache.hc.core5.function.Factory;
   import org.apache.hc.core5.http.HttpHost;
   import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
   import org.apache.hc.core5.reactor.ssl.TlsDetails;
   import org.apache.hc.core5.ssl.SSLContextBuilder;
 */

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
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

// https://opensearch.org/docs/latest/clients/java/
// https://github.com/opensearch-project/opensearch-java/blob/main/USER_GUIDE.md

public class OpenSearch {
    public static OpenSearchClient client = null;
    public static RestClient restClient = null;
    public static Map<String, Boolean> INDEX_EXISTS_CACHE = new HashMap<String, Boolean>();
    // public static Properties props = null; // will be set by init()

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
        System.out.println("got client???? " + client);
    }

// http://localhost:9200/encounter/_search?pretty=true&q=*:*
// http://localhost:9200/_cat/indices?v

    public void createIndex(String indexName)
    throws java.io.IOException {
        CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder().index(
            indexName).build();

        client.indices().create(createIndexRequest);
        System.out.println(indexName + " OpenSearch index created");
    }

    public void ensureIndex(String indexName)
    throws java.io.IOException {
        if (existsIndex(indexName)) return;
        createIndex(indexName);
    }

    public void deleteIndex(String indexName)
    throws java.io.IOException {
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest.Builder().index(
            indexName).build();

        // DeleteIndexResponse deleteIndexResponse = client.indices().delete(deleteIndexRequest);
        client.indices().delete(deleteIndexRequest);
        System.out.println(indexName + " OpenSearch index deleted");
    }

    public boolean existsIndex(String indexName) {
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
    throws java.io.IOException {
        String id = obj.getId();

        if (id == null) throw new RuntimeException("must have id property to index");
        ensureIndex(indexName);
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

    // https://github.com/opensearch-project/opensearch-java/issues/824
    // https://forum.opensearch.org/t/how-can-i-create-a-simple-match-query-using-java-client/7748/2
    // https://forum.opensearch.org/t/java-client-searchrequest-query-building-for-neural-plugin/15895/4
    public List<Base> queryx(String indexName, String query)
    throws java.io.IOException {
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

    public List<Base> queryRaw(String indexName, String query)
    throws java.io.IOException {
        // final RestClient restClient = RestClient.builder(host).build();

        Request searchRequest = new Request("POST", indexName + "/_search");

        // Set the query in the Json Entity, you can set your neural query here.
        searchRequest.setJsonEntity("{\"query\": { \"match_all\": {} }}");
        // Response response = restClient.performRequest(searchRequest);
        Response response = restClient.performRequest(searchRequest);
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            response.getEntity().getContent(), "UTF-8"), 8);
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        System.out.println(sb);
        return null;
    }

    public void delete(String indexName, String id)
    throws java.io.IOException {
        client.delete(b -> b.index(indexName).id(id));
    }
}
