package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

// Imports do AWS SDK v2 para DynamoDB
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

// Imports para JSON, UUID e Mapas
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Hello implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final Gson gson = new Gson();

    public Hello() {
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.SA_EAST_1)
                .build();
    }

    public Hello(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {

        String tableName = System.getenv().getOrDefault("TABLE_NAME", "TodoList");
        String httpMethod = event.getHttpMethod();

        try {
            if ("POST".equalsIgnoreCase(httpMethod)) {
                return createList(event, tableName);
            } else if ("GET".equalsIgnoreCase(httpMethod)) {
                return getAllLists(tableName);
            } else if ("PUT".equalsIgnoreCase(httpMethod)) {
                return updateList(event, tableName);
            } else if ("DELETE".equalsIgnoreCase(httpMethod)) { // <-- NOVA CONDIÇÃO
                return deleteList(event, tableName);
            } else {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(405)
                        .withBody("{\"error\": \"Método não suportado.\"}");
            }
        } catch (Exception e) {
            context.getLogger().log("ERRO AO PROCESSAR REQUISIÇÃO: " + e.getMessage());
            e.printStackTrace();
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Ocorreu um erro interno no servidor.\"}");
        }
    }

    // --- LÓGICA PARA APAGAR UMA LISTA (DELETE) ---
    private APIGatewayProxyResponseEvent deleteList(APIGatewayProxyRequestEvent event, String tableName) {
        String listId = event.getPathParameters().get("listId");

        Map<String, AttributeValue> keyToDelete = new HashMap<>();
        keyToDelete.put("PK", AttributeValue.builder().s("USER#guest").build());
        keyToDelete.put("SK", AttributeValue.builder().s("LIST#" + listId).build());

        DeleteItemRequest deleteItemRequest = DeleteItemRequest.builder()
                .tableName(tableName)
                .key(keyToDelete)
                .build();

        dynamoDbClient.deleteItem(deleteItemRequest);

        String jsonResponse = "{\"message\": \"Lista apagada com sucesso!\", \"listId\": \"" + listId + "\"}";
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody(jsonResponse);
    }

    // --- LÓGICA PARA ATUALIZAR UMA LISTA (PUT) ---
    private APIGatewayProxyResponseEvent updateList(APIGatewayProxyRequestEvent event, String tableName) {
        String listId = event.getPathParameters().get("listId");
        String requestBody = event.getBody();
        InputData inputData = gson.fromJson(requestBody, InputData.class);
        String newListName = inputData.getName();

        if (newListName == null || newListName.isEmpty()) {
            throw new IllegalArgumentException("O novo nome da lista não pode ser vazio.");
        }

        Map<String, AttributeValue> keyToUpdate = new HashMap<>();
        keyToUpdate.put("PK", AttributeValue.builder().s("USER#guest").build());
        keyToUpdate.put("SK", AttributeValue.builder().s("LIST#" + listId).build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", AttributeValue.builder().s(newListName).build());

        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(keyToUpdate)
                .updateExpression("SET listName = :newName")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        dynamoDbClient.updateItem(updateItemRequest);

        String jsonResponse = "{\"message\": \"Lista atualizada com sucesso!\", \"listId\": \"" + listId + "\"}";
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody(jsonResponse);
    }

    // --- LÓGICA PARA CRIAR UMA LISTA (POST) ---
    private APIGatewayProxyResponseEvent createList(APIGatewayProxyRequestEvent event, String tableName) {
        String requestBody = event.getBody();
        InputData inputData = gson.fromJson(requestBody, InputData.class);
        String listName = inputData.getName();

        if (listName == null || listName.isEmpty()) {
            throw new IllegalArgumentException("O nome da lista não pode ser vazio.");
        }

        String listId = UUID.randomUUID().toString();
        String pk = "USER#guest";
        String sk = "LIST#" + listId;

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", AttributeValue.builder().s(pk).build());
        item.put("SK", AttributeValue.builder().s(sk).build());
        item.put("listName", AttributeValue.builder().s(listName).build());
        item.put("createdAt", AttributeValue.builder().s(java.time.Instant.now().toString()).build());
        item.put("GSI1PK", AttributeValue.builder().s("LISTS").build());
        item.put("GSI1SK", AttributeValue.builder().s("METADATA#" + listId).build());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();
        dynamoDbClient.putItem(putItemRequest);

        String jsonResponse = "{\"message\": \"Lista criada com sucesso!\", \"listId\": \"" + listId + "\"}";
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(201)
                .withBody(jsonResponse);
    }

    // --- LÓGICA PARA LISTAR AS LISTAS (GET) ---
    private APIGatewayProxyResponseEvent getAllLists(String tableName) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":pkval", AttributeValue.builder().s("LISTS").build());

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .indexName("gsi1-ListsIndex")
                .keyConditionExpression("GSI1PK = :pkval")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        QueryResponse queryResponse = dynamoDbClient.query(queryRequest);
        List<Map<String, AttributeValue>> items = queryResponse.items();

        List<Map<String, String>> simplifiedItems = new ArrayList<>();
        for (Map<String, AttributeValue> item : items) {
            Map<String, String> simplifiedItem = new HashMap<>();
            simplifiedItem.put("listId", item.get("SK").s().replace("LIST#", ""));
            simplifiedItem.put("listName", item.get("listName").s());
            simplifiedItem.put("createdAt", item.get("createdAt").s());
            simplifiedItems.add(simplifiedItem);
        }

        String jsonResponse = gson.toJson(simplifiedItems);

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody(jsonResponse);
    }

    // Classe auxiliar para parsear o JSON de entrada
    private static class InputData {
        private String name;
        public String getName() {
            return name;
        }
    }
}