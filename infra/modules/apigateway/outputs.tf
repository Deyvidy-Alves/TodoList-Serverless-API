output "api_id" {
  description = "ID da API Gateway"
  value       = aws_api_gateway_rest_api.main.id
}

output "invoke_url" {
  description = "URL de invocação da API"
  value       = "${aws_api_gateway_stage.main.invoke_url}/"
}

output "execution_arn" {
  description = "ARN de execução da API"
  value       = aws_api_gateway_rest_api.main.execution_arn
}