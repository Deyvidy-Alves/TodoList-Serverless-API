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

public class GetListHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final Gson gson = new Gson();

    public GetListHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().region(Region.SA_EAST_1).build();
        this.tableName = System.getenv("TABLE_NAME");
    }

    public GetListHandler(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    // Classe interna para formatar a resposta JSON
    private static class ListResponse {
        private String userId;
        private String listId;
        private String name;
        private String createdAt;

        public ListResponse(String userId, String listId, String name, String createdAt) {
            this.userId = userId;
            this.listId = listId;
            this.name = name;
            this.createdAt = createdAt;
        }
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String userId = event.getPathParameters().get("userId");
            String listId = event.getPathParameters().get("listId");

            Map<String, AttributeValue> keyToGet = new HashMap<>();
            keyToGet.put("pk", AttributeValue.builder().s("USER#" + userId).build());
            keyToGet.put("sk", AttributeValue.builder().s("LIST#" + listId).build());

            GetItemRequest getItemRequest = GetItemRequest.builder()
                    .tableName(this.tableName)
                    .key(keyToGet)
                    .build();

            GetItemResponse response = dynamoDbClient.getItem(getItemRequest);

            if (!response.hasItem()) {
                return new APIGatewayProxyResponseEvent().withStatusCode(404).withBody("{\"message\": \"Lista não encontrada.\"}");
            }

            Map<String, AttributeValue> item = response.item();

            // Montamos nosso objeto de resposta limpo
            ListResponse listResponse = new ListResponse(
                    item.get("userId").s(),
                    item.get("sk").s().replace("LIST#", ""), // Extrai o ID do SK
                    item.get("name").s(),
                    item.get("createdAt").s()
            );

            // Convertemos o objeto de resposta para JSON
            return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody(gson.toJson(listResponse));

        } catch (Exception e) {
            context.getLogger().log("ERRO AO OBTER LISTA: " + e.getMessage());
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("{\"error\": \"Erro interno no servidor.\"}");
        }
    }
}