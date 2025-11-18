package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.Map;

public class UpdateItemHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final Gson gson = new Gson();

    public UpdateItemHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().region(Region.SA_EAST_1).build();
        this.tableName = System.getenv("TABLE_NAME");
    }

    // classe interna para o corpo da requisição
    private static class InputData {
        private String text;
        private Boolean completed; // Permite atualizar o texto ou o status

        public String getText() { return text; }
        public Boolean isCompleted() { return completed; }
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {

            String listId = event.getPathParameters().get("listId");
            String itemId = event.getPathParameters().get("itemId");

            InputData input = gson.fromJson(event.getBody(), InputData.class);

            String pk = "LIST#" + listId;
            String sk = "ITEM#" + itemId;
            Map<String, AttributeValue> keyToUpdate = new HashMap<>();
            keyToUpdate.put("pk", AttributeValue.builder().s(pk).build());
            keyToUpdate.put("sk", AttributeValue.builder().s(sk).build());

            String updateExpression = "SET ";
            Map<String, String> expressionAttributeNames = new HashMap<>();
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();

            if (input.getText() != null && !input.getText().trim().isEmpty()) {
                updateExpression += "#txt = :newText, ";
                expressionAttributeNames.put("#txt", "text");
                expressionAttributeValues.put(":newText", AttributeValue.builder().s(input.getText()).build());
            }

            if (input.isCompleted() != null) {
                updateExpression += "#comp = :newCompleted, ";
                expressionAttributeNames.put("#comp", "completed");
                expressionAttributeValues.put(":newCompleted", AttributeValue.builder().bool(input.isCompleted()).build());
            }

            updateExpression = updateExpression.substring(0, updateExpression.length() - 2);

            UpdateItemRequest updateReq = UpdateItemRequest.builder()
                    .tableName(this.tableName)
                    .key(keyToUpdate)
                    .updateExpression(updateExpression)
                    .expressionAttributeNames(expressionAttributeNames)
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();

            dynamoDbClient.updateItem(updateReq);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("{\"message\": \"Item atualizado com sucesso!\"}");

        } catch (Exception e) {
            context.getLogger().log("ERRO AO ATUALIZAR ITEM: " + e.getMessage());
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("{\"error\": \"Erro interno no servidor.\"}");
        }
    }
}