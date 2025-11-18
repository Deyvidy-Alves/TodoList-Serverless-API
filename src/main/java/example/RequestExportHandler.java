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
            String listId = event.getPathParameters().get("listId");

            Map<String, Object> authorizer = event.getRequestContext().getAuthorizer();
            String userId = (String) authorizer.get("sub");

            if (userId == null || userId.isEmpty()) {
                userId = (String) authorizer.get("cognito:username");
                if (userId == null || userId.isEmpty()) {
                    context.getLogger().log("ERRO DE AUTORIZACAO: Não foi possível obter o ID do usuário.");
                    return new APIGatewayProxyResponseEvent().withStatusCode(401).withBody("{\"error\": \"Usuário não autenticado ou token inválido.\"}");
                }
            }


            SqsMessage messagePayload = new SqsMessage(listId, userId);
            String messageBody = gson.toJson(messagePayload);

            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(this.queueUrl)
                    .messageBody(messageBody)
                    .build();

            sqsClient.sendMessage(sendMsgRequest);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(202)
                    .withBody("{\"message\": \"Seu relatório está sendo processado. Você receberá por email em breve.\"}");

        } catch (Exception e) {
            context.getLogger().log("ERRO AO SOLICITAR EXPORTAÇÃO: " + e.getMessage());
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("{\"error\": \"Erro interno no servidor.\"}");
        }
    }
}