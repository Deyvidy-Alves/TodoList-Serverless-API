package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

public class RequestExportHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final SqsClient sqsClient;
    private final Gson gson = new Gson();
    private final String queueUrl;

    public RequestExportHandler() {
        this.sqsClient = SqsClient.builder().region(Region.SA_EAST_1).build();
        this.queueUrl = System.getenv("SQS_QUEUE_URL");
    }

    private static class SqsMessage {
        String listId;
        String userId;

        SqsMessage(String listId, String userId) {
            this.listId = listId;
            this.userId = userId;
        }
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            // 1. Pega o listId da URL
            String listId = event.getPathParameters().get("listId");

            // 2. O Authorizer do Cognito joga os "claims" direto no mapa 'authorizer'.
            Map<String, Object> authorizer = event.getRequestContext().getAuthorizer();
            String userId = (String) authorizer.get("cognito:username");

            // 3. Prepara a mensagem para a fila
            SqsMessage messagePayload = new SqsMessage(listId, userId);
            String messageBody = gson.toJson(messagePayload);

            // 4. Envia a mensagem para a fila SQS
            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(this.queueUrl)
                    .messageBody(messageBody)
                    .build();

            sqsClient.sendMessage(sendMsgRequest);

            // 5. Retorna 202 (Aceito)
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(202)
                    .withBody("{\"message\": \"Seu relatório está sendo processado. Você receberá por email em breve.\"}");

        } catch (Exception e) {
            context.getLogger().log("ERRO AO SOLICITAR EXPORTAÇÃO: " + e.getMessage());
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("{\"error\": \"Erro interno no servidor.\"}");
        }
    }
}