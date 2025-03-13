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
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexResponse;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.client.transport.OpenSearchTransport;
*/
//import org.opensearch.client.IndicesClient;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.indices.OpenSearchIndicesClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;

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

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import java.nio.charset.StandardCharsets;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

public class OpenSearchMockingTest {

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

        HttpResponse mockHttpResponse = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        String jsonResponse = "{\"mock\": \"response\"}";
        mockHttpResponse.setEntity(new StringEntity(jsonResponse, StandardCharsets.UTF_8));

        Response mockResponse = mock(Response.class);

        when(mockResponse.getEntity()).thenReturn(new StringEntity(jsonResponse, StandardCharsets.UTF_8));
        when(restClient.performRequest(any(Request.class))).thenReturn(mockResponse);
/*
        when(restClient.performRequest(any(Request.class))).thenReturn(new Response(
                null, null,
                mockHttpResponse
        ));
*/


        os.createIndex("encounter", null);
    }

/*
    @Test
    void canCreateSearcherWithES_8_15() throws SQLException, IOException{
        // when
        when(esClient.esql()).thenReturn(esql);
        when(esql.query(eq(ResultSetEsqlAdapter.INSTANCE), anyString())).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        // here you need to know which inner stuff you need to mock
        // and what will happen if the implementation is changed to "major" and "minor"?
        when(mockResultSet.getInt(1)).thenReturn(8);
        when(mockResultSet.getInt(2)).thenReturn(15);

        // then
        Assertions.assertDoesNotThrow(() -> new BookSearcher(esClient));
    }

    @Test
    void cannotCreateSearcherWithoutES_8_15() throws SQLException, IOException {
        // when
        when(esClient.esql()).thenReturn(esql);
        when(esql.query(eq(ResultSetEsqlAdapter.INSTANCE), anyString())).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getInt(1)).thenReturn(8);
        when(mockResultSet.getInt(2)).thenReturn(16);

        // then
        Assertions.assertThrows(UnsupportedOperationException.class, () -> new BookSearcher(esClient));
    }

    @Test
    void cannotCreateSearcherWithoutESVersion() throws SQLException, IOException {
        // when
        when(esClient.esql()).thenReturn(esql);
        when(esql.query(eq(ResultSetEsqlAdapter.INSTANCE), anyString())).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        // then
        Assertions.assertThrows(RuntimeException.class, () -> new BookSearcher(esClient));
    }

    @Test
    void shouldNotAllowSearchingForMostPublishedAuthorsWithIncorrectDates() throws SQLException, IOException {
        // when
        when(esClient.esql()).thenReturn(esql);
        when(esql.query(eq(ResultSetEsqlAdapter.INSTANCE), anyString())).thenReturn(mockResultSet);

        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getInt(1)).thenReturn(8);
        when(mockResultSet.getInt(2)).thenReturn(15);

        BookSearcher systemUnderTest = new BookSearcher(esClient);

        // then
        Assertions.assertThrows(
            AssertionError.class,
            () -> systemUnderTest.mostPublishedAuthorsInYears(2012, 2000)
        );

    }
*/


}
