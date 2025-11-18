package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
public class UpdateDeleteListHandlerTest {

    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private Context context;
    @Mock
    private LambdaLogger logger;

    private UpdateDeleteListHandler handler;

    @BeforeEach
    void setUp() {
        when(context.getLogger()).thenReturn(logger);
        handler = new UpdateDeleteListHandler(dynamoDbClient, "FakeTable");
    }

    @Test
    void testHandleRequest_Put_Success() {
        String requestBody = new Gson().toJson(Map.of("name", "Nome Atualizado"));
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("PUT")
                .withPathParameters(Map.of("userId", "user123", "listId", "list456"))
                .withBody(requestBody);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());
        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void testHandleRequest_Delete_Success() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("DELETE")
                .withPathParameters(Map.of("userId", "user123", "listId", "list456"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());
        verify(dynamoDbClient).deleteItem(any(DeleteItemRequest.class));
    }

    @Test
    void testHandleRequest_InvalidMethod() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("PATCH")
                .withPathParameters(Map.of("userId", "user123", "listId", "list456"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(405, response.getStatusCode());
    }
}