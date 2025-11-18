package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
public class GetListHandlerTest {

    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private Context context;
    @Mock
    private LambdaLogger logger;

    private GetListHandler handler;

    @BeforeEach
    void setUp() {
        when(context.getLogger()).thenReturn(logger);
        handler = new GetListHandler(dynamoDbClient, "FakeTable");
    }

    @Test
    void testHandleRequest_Found() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("userId", "user123", "listId", "list456"));

        Map<String, AttributeValue> fakeItem = Map.of(
                "pk", AttributeValue.builder().s("USER#user123").build(),
                "sk", AttributeValue.builder().s("LIST#list456").build(),
                "name", AttributeValue.builder().s("Lista de Teste").build(),
                "userId", AttributeValue.builder().s("123").build(),
                "createdAt", AttributeValue.builder().s("2025-01-01T00:00:00Z").build()
        );
        GetItemResponse fakeResponse = GetItemResponse.builder().item(fakeItem).build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(fakeResponse);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());
    }

    @Test
    void testHandleRequest_NotFound() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("userId", "user123", "listId", "list456"));

        GetItemResponse fakeResponse = GetItemResponse.builder().build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(fakeResponse);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(404, response.getStatusCode());
    }
}