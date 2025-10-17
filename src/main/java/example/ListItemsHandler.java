package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ListItemsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final Gson gson = new Gson();

    public ListItemsHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().region(Region.SA_EAST_1).build();
        this.tableName = System.getenv("TABLE_NAME");
    }

    // Classe interna para formatar a resposta de cada item
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
            //  Extrai o ID da lista dos parâmetros da URL
            String listId = event.getPathParameters().get("listId");
            String pk = "LIST#" + listId;

            //    A query busca todos os itens que têm a mesma Chave de Partição
            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(this.tableName)
                    .keyConditionExpression("pk = :pkVal")
                    .expressionAttributeValues(Map.of(":pkVal", AttributeValue.builder().s(pk).build()))
                    .build();

            QueryResponse response = dynamoDbClient.query(queryRequest);

            // Transforma a lista de itens do DynamoDB em uma lista de objetos de resposta limpos
            List<ItemResponse> items = response.items().stream()
                    .map(ItemResponse::new)
                    .collect(Collectors.toList());

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(gson.toJson(items));

        } catch (Exception e) {
            context.getLogger().log("ERRO AO LISTAR ITENS: " + e.getMessage());
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("{\"error\": \"Erro interno no servidor.\"}");
        }
    }
}