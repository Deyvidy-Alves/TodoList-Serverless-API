package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.Map;

public class UpdateDeleteListHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final Gson gson = new Gson();

    public UpdateDeleteListHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().region(Region.SA_EAST_1).build();
        this.tableName = System.getenv("TABLE_NAME");
    }

    // <<< MUDANÇA: Construtor para os Testes >>>
    public UpdateDeleteListHandler(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    // ... o resto do código permanece igual
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String httpMethod = event.getHttpMethod();

        try {
            if ("PUT".equalsIgnoreCase(httpMethod)) {
                return updateList(event);
            } else if ("DELETE".equalsIgnoreCase(httpMethod)) {
                return deleteList(event);
            } else {
                return new APIGatewayProxyResponseEvent().withStatusCode(405).withBody("{\"error\": \"Método não suportado.\"}");
            }
        } catch (Exception e) {
            context.getLogger().log("ERRO AO PROCESSAR REQUISIÇÃO: " + e.getMessage());
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("{\"error\": \"Erro interno no servidor.\"}");
        }
    }

    private APIGatewayProxyResponseEvent updateList(APIGatewayProxyRequestEvent event) {
        String userId = event.getPathParameters().get("userId");
        String listId = event.getPathParameters().get("listId");
        InputData inputData = gson.fromJson(event.getBody(), InputData.class);
        String newListName = inputData.getName();

        Map<String, AttributeValue> keyToUpdate = new HashMap<>();
        keyToUpdate.put("pk", AttributeValue.builder().s("USER#" + userId).build());
        keyToUpdate.put("sk", AttributeValue.builder().s("LIST#" + listId).build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", AttributeValue.builder().s(newListName).build());

        UpdateItemRequest updateReq = UpdateItemRequest.builder()
                .tableName(this.tableName)
                .key(keyToUpdate)
                .updateExpression("SET #nm = :newName")
                .expressionAttributeNames(Map.of("#nm", "name"))
                .expressionAttributeValues(expressionAttributeValues)
                .build();
        dynamoDbClient.updateItem(updateReq);

        return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody("{\"message\": \"Lista atualizada com sucesso!\"}");
    }

    private APIGatewayProxyResponseEvent deleteList(APIGatewayProxyRequestEvent event) {
        String userId = event.getPathParameters().get("userId");
        String listId = event.getPathParameters().get("listId");

        Map<String, AttributeValue> keyToDelete = new HashMap<>();
        keyToDelete.put("pk", AttributeValue.builder().s("USER#" + userId).build());
        keyToDelete.put("sk", AttributeValue.builder().s("LIST#" + listId).build());

        DeleteItemRequest deleteReq = DeleteItemRequest.builder()
                .tableName(this.tableName)
                .key(keyToDelete)
                .build();
        dynamoDbClient.deleteItem(deleteReq);

        return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody("{\"message\": \"Lista apagada com sucesso!\"}");
    }

    private static class InputData {
        private String name;
        public String getName() { return name; }
    }
}