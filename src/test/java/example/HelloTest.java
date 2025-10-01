package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HelloTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private Context context;

    private Hello handler;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new Hello(dynamoDbClient);
    }

    @Test
    public void testHandleRequest_GetLists_Success() {
        // --- Arrange ---
        APIGatewayProxyRequestEvent getRequest = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET");

        Map<String, AttributeValue> fakeItem = Map.of(
                "SK", AttributeValue.builder().s("LIST#1234").build(),
                "listName", AttributeValue.builder().s("Minha Lista de Teste").build(),
                "createdAt", AttributeValue.builder().s("2025-10-01T12:00:00Z").build()
        );
        QueryResponse fakeQueryResponse = QueryResponse.builder()
                .items(Collections.singletonList(fakeItem))
                .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(fakeQueryResponse);

        // --- Act ---
        APIGatewayProxyResponseEvent response = handler.handleRequest(getRequest, context);

        // --- Assert ---
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    public void testHandleRequest_PostList_Success() {
        // --- Arrange ---
        Map<String, String> requestBody = Map.of("name", "Nova Lista");
        APIGatewayProxyRequestEvent postRequest = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody(new Gson().toJson(requestBody));

        // --- Act ---
        APIGatewayProxyResponseEvent response = handler.handleRequest(postRequest, context);

        // --- Assert ---
        assertEquals(201, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    // ================== NOVO TESTE PARA O PUT ==================
    @Test
    public void testHandleRequest_UpdateList_Success() {
        // --- Arrange ---
        String fakeListId = "abc-123";
        Map<String, String> requestBody = Map.of("name", "Nome Atualizado");

        APIGatewayProxyRequestEvent putRequest = new APIGatewayProxyRequestEvent()
                .withHttpMethod("PUT")
                .withPathParameters(Map.of("listId", fakeListId)) // Simula o {listId} da URL
                .withBody(new Gson().toJson(requestBody));

        // --- Act ---
        APIGatewayProxyResponseEvent response = handler.handleRequest(putRequest, context);

        // --- Assert ---
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains(fakeListId)); // Verifica se a resposta contém o ID da lista
    }

    // ================== NOVO TESTE PARA O DELETE ==================
    @Test
    public void testHandleRequest_DeleteList_Success() {
        // --- Arrange ---
        String fakeListId = "xyz-789";

        APIGatewayProxyRequestEvent deleteRequest = new APIGatewayProxyRequestEvent()
                .withHttpMethod("DELETE")
                .withPathParameters(Map.of("listId", fakeListId)); // Simula o {listId} da URL

        // --- Act ---
        APIGatewayProxyResponseEvent response = handler.handleRequest(deleteRequest, context);

        // --- Assert ---
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains(fakeListId));
    }
}