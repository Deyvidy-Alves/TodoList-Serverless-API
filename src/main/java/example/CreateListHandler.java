package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateListHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final Gson gson = new Gson();

    // construtor para a Lambda
    public CreateListHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().region(Region.SA_EAST_1).build();
        this.tableName = System.getenv("TABLE_NAME");
    }

    // construtor para os teste
    public CreateListHandler(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String userId = event.getPathParameters().get("userId");
            InputData inputData = gson.fromJson(event.getBody(), InputData.class);
            String listName = inputData.getName();

            if (listName == null || listName.isEmpty()) {
                throw new IllegalArgumentException("O nome da lista não pode ser vazio.");
            }

            String listId = UUID.randomUUID().toString();
            String pk = "USER#" + userId;
            String sk = "LIST#" + listId;

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("pk", AttributeValue.builder().s(pk).build());
            item.put("sk", AttributeValue.builder().s(sk).build());
            item.put("userId", AttributeValue.builder().s(userId).build());
            item.put("name", AttributeValue.builder().s(listName).build());
            item.put("createdAt", AttributeValue.builder().s(java.time.Instant.now().toString()).build());

            PutItemRequest putItemRequest = PutItemRequest.builder()
                    .tableName(this.tableName)
                    .item(item)
                    .build();
            dynamoDbClient.putItem(putItemRequest);

            String jsonResponse = "{\"message\": \"Lista criada com sucesso!\", \"listId\": \"" + listId + "\"}";
            return new APIGatewayProxyResponseEvent().withStatusCode(201).withBody(jsonResponse);

        } catch (Exception e) {
            context.getLogger().log("ERRO AO CRIAR LISTA: " + e.getMessage());
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("{\"error\": \"Erro interno no servidor.\"}");
        }
    }

    private static class InputData {
        private String name;
        public String getName() { return name; }
    }
}