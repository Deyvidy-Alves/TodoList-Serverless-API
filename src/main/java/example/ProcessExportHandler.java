package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProcessExportHandler implements RequestHandler<SQSEvent, Void> {

    private final DynamoDbClient dynamoDbClient;
    private final S3Client s3Client;
    private final SesClient sesClient;
    private final CognitoIdentityProviderClient cognitoClient;

    private final Gson gson = new Gson();
    private final String tableName;
    private final String bucketName;
    private final String userPoolId;
    private final String senderEmail;

    public ProcessExportHandler() {
        Region region = Region.SA_EAST_1;
        this.dynamoDbClient = DynamoDbClient.builder().region(region).build();
        this.s3Client = S3Client.builder().region(region).build();
        this.sesClient = SesClient.builder().region(region).build();
        this.cognitoClient = CognitoIdentityProviderClient.builder().region(region).build();

        this.tableName = System.getenv("TABLE_NAME");
        this.bucketName = System.getenv("BUCKET_NAME");
        this.userPoolId = System.getenv("USER_POOL_ID");
        this.senderEmail = System.getenv("SENDER_EMAIL");
    }

    private static class SqsMessage {
        String listId;
        String userId;
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage msg : event.getRecords()) {
            try {
                String messageBody = msg.getBody();
                context.getLogger().log("Processando mensagem: " + messageBody);
                SqsMessage request = gson.fromJson(messageBody, SqsMessage.class);

                if (request.userId == null || request.userId.trim().isEmpty()) {
                    context.getLogger().log("ALERTA: userId está NULL na mensagem. Pulando chamada ao Cognito/SES.");
                    continue;
                }

                // busca os dados da lista no DynamoDB
                List<Map<String, AttributeValue>> items = getItemsFromDynamoDB(request.listId);

                // gera o CSV e salva no S3
                String csvContent = generateCsv(items);
                String csvFileName = "relatorio-" + request.listId + "-" + Instant.now().toEpochMilli() + ".csv";
                String s3Url = saveCsvToS3(csvContent, csvFileName);

                // busca o email do usuário no Cognito
                String userEmail = getUserEmail(request.userId);

                // envia o email
                sendEmail(userEmail, s3Url);
                context.getLogger().log("SUCESSO: Email enviado para: " + userEmail);

            } catch (Exception e) {
                context.getLogger().log("ERRO CRÍTICO NO PROCESSAMENTO: " + e.getMessage());
                // Lança a exceção para que a SQS tente de novo.
                throw new RuntimeException("Falha ao processar mensagem SQS", e);
            }
        }
        return null;
    }

    private List<Map<String, AttributeValue>> getItemsFromDynamoDB(String listId) {
        String pk = "LIST#" + listId;
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(this.tableName)
                .keyConditionExpression("pk = :pkVal")
                .expressionAttributeValues(Map.of(":pkVal", AttributeValue.builder().s(pk).build()))
                .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);
        return response.items();
    }

    private String generateCsv(List<Map<String, AttributeValue>> items) {
        StringBuilder csv = new StringBuilder();
        csv.append("itemId,text,createdAt,completed\n");
        for (Map<String, AttributeValue> item : items) {
            csv.append(item.get("itemId").s()).append(",");
            csv.append("\"").append(item.get("text").s()).append("\",");
            csv.append(item.get("createdAt").s()).append(",");
            csv.append(item.get("completed").bool()).append("\n");
        }
        return csv.toString();
    }

    private String saveCsvToS3(String csvContent, String fileName) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(this.bucketName)
                .key("reports/" + fileName)
                .contentType("text/csv")
                .build();
        s3Client.putObject(putObjectRequest, RequestBody.fromString(csvContent));
        return String.format("https://%s.s3.%s.amazonaws.com/reports/%s",
                this.bucketName, Region.SA_EAST_1.id(), fileName);
    }

    private String getUserEmail(String userId) {
        AdminGetUserRequest getUserRequest = AdminGetUserRequest.builder()
                .userPoolId(this.userPoolId)
                .username(userId)
                .build();

        AdminGetUserResponse response = cognitoClient.adminGetUser(getUserRequest);

        return response.userAttributes().stream()
                .filter(attr -> attr.name().equals("email"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Email não encontrado para o usuário: " + userId))
                .value();
    }

    private void sendEmail(String toEmail, String s3Url) {
        String subject = "Seu Relatório de Lista de Tarefas está pronto!";
        String bodyHtml = String.format(
                "<h1>Relatório de Exportação</h1>" +
                        "<p>Olá!</p>" +
                        "<p>Seu relatório da lista de tarefas foi gerado com sucesso.</p>" +
                        "<p>Você pode baixá-lo clicando no link abaixo:</p>" +
                        "<a href=\"%s\">Baixar CSV</a>" +
                        "<p>Obrigado!</p>", s3Url
        );

        Destination destination = Destination.builder().toAddresses(toEmail).build();
        Content subjectContent = Content.builder().data(subject).build();
        Content bodyContent = Content.builder().data(bodyHtml).build();
        Body emailBody = Body.builder().html(bodyContent).build();

        Message message = Message.builder()
                .subject(subjectContent)
                .body(emailBody)
                .build();

        SendEmailRequest emailRequest = SendEmailRequest.builder()
                .destination(destination)
                .message(message)
                .source(this.senderEmail)
                .build();

        sesClient.sendEmail(emailRequest);
    }
}