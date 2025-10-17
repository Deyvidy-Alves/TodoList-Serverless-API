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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateItemHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final Gson gson = new Gson();

    public CreateItemHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().region(Region.SA_EAST_1).build();
        this.tableName = System.getenv("TABLE_NAME");
    }

    // Classe interna para desserializar o corpo da requisição JSON
    private static class InputData {
        private String text;
        public String getText() { return text; }
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String listId = event.getPathParameters().get("listId");

            InputData inputData = gson.fromJson(event.getBody(), InputData.class);
            String itemText = inputData.getText();

            if (itemText == null || itemText.trim().isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("{\"error\": \"O texto do item não pode ser vazio.\"}");
            }

            String itemId = UUID.randomUUID().toString();

            // 4. Define o modelo de dados para o item da lista
            //    PK = LIST#{listId} nos permite buscar todos os itens de uma lista de uma vez
            //    SK = ITEM#{itemId} garante a unicidade de cada item
            String pk = "LIST#" + listId;
            String sk = "ITEM#" + itemId;

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("pk", AttributeValue.builder().s(pk).build());
            item.put("sk", AttributeValue.builder().s(sk).build());
            item.put("itemId", AttributeValue.builder().s(itemId).build());
            item.put("text", AttributeValue.builder().s(itemText).build());
            item.put("createdAt", AttributeValue.builder().s(Instant.now().toString()).build());
            item.put("completed", AttributeValue.builder().bool(false).build());

            PutItemRequest putItemRequest = PutItemRequest.builder()
                    .tableName(this.tableName)
                    .item(item)
                    .build();

            dynamoDbClient.putItem(putItemRequest);

            String jsonResponse = "{\"message\": \"Item adicionado com sucesso!\", \"itemId\": \"" + itemId + "\"}";
            return new APIGatewayProxyResponseEvent().withStatusCode(201).withBody(jsonResponse);

        } catch (Exception e) {
            context.getLogger().log("ERRO AO CRIAR ITEM: " + e.getMessage());
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("{\"error\": \"Erro interno no servidor.\"}");
        }
    }
}