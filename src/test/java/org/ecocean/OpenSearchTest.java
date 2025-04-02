package testing_opensearch;

/*
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.transport.rest_client.RestClientTransport;

import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.DeleteIndexResponse;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.client.transport.OpenSearchTransport;
*/
//import org.opensearch.client.IndicesClient;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.OpenSearchIndicesClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.opensearch.client.opensearch.indices.GetIndexRequest;

import org.ecocean.OpenSearch;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

public class OpenSearchTest {

    //ResultSet mockResultSet;
    RestClient restClient;
    OpenSearchClient osClient;
    OpenSearch os;

    @BeforeEach
    void setUp() {
        //mockResultSet = mock(ResultSet.class);
        osClient = mock(OpenSearchClient.class);
        OpenSearch.client = osClient;
        restClient = mock(RestClient.class);
        OpenSearch.restClient = restClient;
        os = new OpenSearch();
    }

    @Test
    void verifyOpenSearch() {
        assertNotNull(os);
    }

    @Test
    void indexManipulation() throws IOException {
        Exception ex = assertThrows(IOException.class, () -> {
            os.createIndex("THIS_IS_AN_INVALID_INDEX_NAME", null);
        });
        assertTrue(ex.getMessage().contains("invalid index name"));

        CreateIndexResponse response = null;
        OpenSearchIndicesClient mockedIndicesClient = mock(OpenSearchIndicesClient.class);
        when(osClient.indices()).thenReturn(mockedIndicesClient);
        when(mockedIndicesClient.create(mock(CreateIndexRequest.class))).thenReturn(response);

        Response mockResponse = mock(Response.class);
        when(mockResponse.getEntity()).thenReturn(new StringEntity("{}", StandardCharsets.UTF_8));
        when(restClient.performRequest(any(Request.class))).thenReturn(mockResponse);
        // all the sub-calls to os.getRestResponse() dont care about response, so the empty one works (!)
        os.createIndex("encounter", null);

        // this should be true just via the cache
        assertTrue(os.existsIndex("encounter"));
        // now unset cache so it has to ask OpenSearch about index
        OpenSearch.INDEX_EXISTS_CACHE.remove("encounter");
        assertTrue(os.existsIndex("encounter"));  // ok cuz we previously mocked osClient.indices() so it "finds it"
        assertTrue(OpenSearch.INDEX_EXISTS_CACHE.get("encounter"));
        os.ensureIndex("encounter", null);

        // indexUpdate
        ex = assertThrows(IOException.class, () -> {
            os.indexUpdate("THIS_IS_AN_INVALID_INDEX_NAME", null, null);
        });
        assertTrue(ex.getMessage().startsWith("index does not exist:"));
        ex = assertThrows(IOException.class, () -> {
            os.indexUpdate("encounter", null, null);
        });
        assertEquals(ex.getMessage(), "missing id or updateData");
        ex = assertThrows(IOException.class, () -> {
            os.indexUpdate("encounter", "id", null);
        });
        assertEquals(ex.getMessage(), "missing id or updateData");

        // this will still piggyback on our restClient mockResponse above, to stimulate success
        os.indexUpdate("encounter", "id", new JSONObject());

        // fake delete (doesnt care about return, so just give null)
        when(osClient.indices().delete(any(DeleteIndexRequest.class))).thenReturn(null);
        os.deleteIndex("encounter");
        assertFalse(OpenSearch.INDEX_EXISTS_CACHE.containsKey("encounter"));

        // this will test osClient throwing exception (which gets swallowed but returns false)
        doThrow(new RuntimeException("intentional fail")).when(osClient).indices();
        OpenSearch.INDEX_EXISTS_CACHE.remove("encounter");  // force OpenSearch check (which will throw above)
        assertFalse(os.existsIndex("encounter"));

        ex = assertThrows(IOException.class, () -> {
            os.ensureIndex("THIS_IS_AN_INVALID_INDEX_NAME", null);
        });
        assertTrue(ex.getMessage().contains("invalid index name"));
        ex = assertThrows(IOException.class, () -> {
            os.deleteIndex("THIS_IS_AN_INVALID_INDEX_NAME");
        });
        assertTrue(ex.getMessage().contains("invalid index name"));

    }

}
