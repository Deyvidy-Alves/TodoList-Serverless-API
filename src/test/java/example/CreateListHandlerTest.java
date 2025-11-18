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
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
public class CreateListHandlerTest {

    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private Context context;
    @Mock
    private LambdaLogger logger;

    private CreateListHandler handler;

    @BeforeEach
    void setUp() {
        when(context.getLogger()).thenReturn(logger);
        handler = new CreateListHandler(dynamoDbClient, "FakeTable");
    }

    @Test
    void testHandleRequest_Success() {
        String requestBody = new Gson().toJson(Map.of("name", "Minha Nova Lista"));
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("userId", "user123"))
                .withBody(requestBody);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(201, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(dynamoDbClient).putItem(any(PutItemRequest.class));
    }

    @Test
    void testHandleRequest_EmptyName() {
        String requestBody = new Gson().toJson(Map.of("name", ""));
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("userId", "user123"))
                .withBody(requestBody);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(500, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(logger).log(any(String.class));
    }
}