# outputs.tf

output "lambda_function_name" {
  description = "O nome da função Lambda criada."
  value       = aws_lambda_function.hello_lambda.function_name
}

output "lambda_function_arn" {
  description = "O ARN (Amazon Resource Name) da função Lambda."
  value       = aws_lambda_function.hello_lambda.arn
}

output "lambda_iam_role_arn" {
  description = "O ARN da IAM Role criada para a Lambda."
  value       = aws_iam_role.lambda_exec_role.arn
}

output "dynamodb_table_name" {
  description = "O nome da tabela DynamoDB criada."
  value       = aws_dynamodb_table.todo_list_table.name
}

output "dynamodb_table_arn" {
  description = "O ARN da tabela DynamoDB criada."
  value       = aws_dynamodb_table.todo_list_table.arn
}

output "api_invoke_url" {
  description = "A URL de invocação para o stage 'v1' da API REST."
  value       = aws_api_gateway_stage.todo_api_stage.invoke_url
}