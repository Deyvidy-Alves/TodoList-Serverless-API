package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.HashMap;
import java.util.Map;

public class GetItemHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final Gson gson = new Gson();

    public GetItemHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().region(Region.SA_EAST_1).build();
        this.tableName = System.getenv("TABLE_NAME");
    }

    // classe interna para formatar a resposta do item
    private static class ItemResponse {
        private String itemId;
        private String text;
        private String createdAt;
        private boolean completed;

        public ItemResponse(Map<String, AttributeValue> item) {
            this.itemId = item.get("itemId").s();
            this.text = item.get("text").s();
            this.createdAt = item.get("createdAt").s();
            this.completed = item.get("completed").bool();
        }
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String listId = event.getPathParameters().get("listId");
            String itemId = event.getPathParameters().get("itemId");

            String pk = "LIST#" + listId;
            String sk = "ITEM#" + itemId;

            Map<String, AttributeValue> keyToGet = new HashMap<>();
            keyToGet.put("pk", AttributeValue.builder().s(pk).build());
            keyToGet.put("sk", AttributeValue.builder().s(sk).build());

            GetItemRequest getItemRequest = GetItemRequest.builder()
                    .tableName(this.tableName)
                    .key(keyToGet)
                    .build();

            GetItemResponse response = dynamoDbClient.getItem(getItemRequest);

            if (!response.hasItem()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(404)
                        .withBody("{\"message\": \"Item não encontrado.\"}");
            }

            ItemResponse itemResponse = new ItemResponse(response.item());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(gson.toJson(itemResponse));

        } catch (Exception e) {
            context.getLogger().log("ERRO AO OBTER ITEM: " + e.getMessage());
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("{\"error\": \"Erro interno no servidor.\"}");
        }
    }
}