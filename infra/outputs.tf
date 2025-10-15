# outputs.tf

output "dynamodb_table_name" {
  description = "O nome da tabela DynamoDB criada."
  value       = aws_dynamodb_table.todo_list_table.name
}

output "api_invoke_url" {
  description = "A URL de invocação para a API REST."
  value       = aws_apigatewayv2_stage.default.invoke_url
}

# --- ADICIONADO: Outputs para o Cognito ---

output "cognito_user_pool_id" {
  description = "O ID do Cognito User Pool."
  value       = aws_cognito_user_pool.user_pool.id
}

output "cognito_app_client_id" {
  description = "O ID do Cognito App Client."
  value       = aws_cognito_user_pool_client.app_client.id
}