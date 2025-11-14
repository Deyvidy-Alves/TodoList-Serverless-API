package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;

import java.util.HashMap;
import java.util.Map;

public class DeleteItemHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DeleteItemHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().region(Region.SA_EAST_1).build();
        this.tableName = System.getenv("TABLE_NAME");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            // 1. Extrai os IDs da URL
            String listId = event.getPathParameters().get("listId");
            String itemId = event.getPathParameters().get("itemId");

            // 2. Define a chave composta exata do item a ser apagado
            String pk = "LIST#" + listId;
            String sk = "ITEM#" + itemId;

            Map<String, AttributeValue> keyToDelete = new HashMap<>();
            keyToDelete.put("pk", AttributeValue.builder().s(pk).build());
            keyToDelete.put("sk", AttributeValue.builder().s(sk).build());

            // 3. Cria e executa a requisição de exclusão
            DeleteItemRequest deleteReq = DeleteItemRequest.builder()
                    .tableName(this.tableName)
                    .key(keyToDelete)
                    .build();

            dynamoDbClient.deleteItem(deleteReq);

            // 4. Retorna sucesso (204 No Content é comum para DELETE, mas 200 com mensagem também é bom)
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("{\"message\": \"Item excluído com sucesso!\"}");

        } catch (Exception e) {
            context.getLogger().log("ERRO AO EXCLUIR ITEM: " + e.getMessage());
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("{\"error\": \"Erro interno no servidor.\"}");
        }
    }
}