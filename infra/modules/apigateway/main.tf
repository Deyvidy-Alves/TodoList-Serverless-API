# 1. Definições Principais
resource "aws_api_gateway_rest_api" "main" {
  name        = var.api_name
  description = var.api_description
}

# 2. Criação dinâmica de recursos
resource "aws_api_gateway_resource" "root_resources" {
  for_each = { for k, v in var.resources : k => v if v.parent_id == "root" }

  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_rest_api.main.root_resource_id
  path_part   = each.value.path_part
}

resource "aws_api_gateway_resource" "child_resources" {
  for_each = { for k, v in var.resources : k => v if v.parent_id != "root" }

  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.root_resources[each.value.parent_id].id
  path_part   = each.value.path_part
}

# 3. Local para combinar todos os recursos
locals {
  all_resources = merge(aws_api_gateway_resource.root_resources, aws_api_gateway_resource.child_resources)
}

# 4. Métodos e Integrações
resource "aws_api_gateway_method" "methods" {
  for_each = var.methods

  rest_api_id   = aws_api_gateway_rest_api.main.id
  resource_id   = local.all_resources[each.value.resource_key].id
  http_method   = each.value.http_method
  authorization = each.value.authorization
}

resource "aws_api_gateway_integration" "integrations" {
  for_each = var.methods

  rest_api_id             = aws_api_gateway_rest_api.main.id
  resource_id             = local.all_resources[each.value.resource_key].id
  http_method             = aws_api_gateway_method.methods[each.key].http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = each.value.lambda_invoke_arn
}

# 5. Configurações de CORS (automático)
resource "aws_api_gateway_method" "cors_methods" {
  for_each = var.enable_cors ? local.all_resources : {}

  rest_api_id   = aws_api_gateway_rest_api.main.id
  resource_id   = each.value.id
  http_method   = "OPTIONS"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "cors_integrations" {
  for_each = var.enable_cors ? local.all_resources : {}

  rest_api_id = aws_api_gateway_rest_api.main.id
  resource_id = each.value.id
  http_method = aws_api_gateway_method.cors_methods[each.key].http_method
  type        = "MOCK"

  request_templates = {
    "application/json" = "{\"statusCode\": 200}"
  }
}

resource "aws_api_gateway_method_response" "cors_responses" {
  for_each = var.enable_cors ? local.all_resources : {}

  rest_api_id = aws_api_gateway_rest_api.main.id
  resource_id = each.value.id
  http_method = aws_api_gateway_method.cors_methods[each.key].http_method
  status_code = "200"

  response_parameters = {
    "method.response.header.Access-Control-Allow-Headers" = true,
    "method.response.header.Access-Control-Allow-Methods" = true,
    "method.response.header.Access-Control-Allow-Origin"  = true
  }

  response_models = {
    "application/json" = "Empty"
  }
}

resource "aws_api_gateway_integration_response" "cors_integration_responses" {
  for_each = var.enable_cors ? local.all_resources : {}

  rest_api_id = aws_api_gateway_rest_api.main.id
  resource_id = each.value.id
  http_method = aws_api_gateway_method.cors_methods[each.key].http_method
  status_code = aws_api_gateway_method_response.cors_responses[each.key].status_code

  response_parameters = {
    "method.response.header.Access-Control-Allow-Headers" = "'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token'",
    "method.response.header.Access-Control-Allow-Methods" = "'${join(",", var.allowed_methods)}'",
    "method.response.header.Access-Control-Allow-Origin"  = "'${var.cors_origin}'"
  }
}

# 6. Deployment e Stage
resource "aws_api_gateway_deployment" "main" {
  rest_api_id = aws_api_gateway_rest_api.main.id

  lifecycle {
    create_before_destroy = true
  }

  depends_on = [
    aws_api_gateway_integration.integrations,
    aws_api_gateway_integration.cors_integrations
  ]
}

resource "aws_api_gateway_stage" "main" {
  deployment_id = aws_api_gateway_deployment.main.id
  rest_api_id   = aws_api_gateway_rest_api.main.id
  stage_name    = var.stage_name
}

# 7. Permissões da Lambda (dinâmico)
resource "aws_lambda_permission" "api_gateway" {
  for_each = var.methods

  statement_id  = "AllowAPIGatewayInvoke-${each.key}"
  action        = "lambda:InvokeFunction"
  function_name = each.value.lambda_function_name
  principal     = "apigateway.amazonaws.com"

  source_arn = "${aws_api_gateway_rest_api.main.execution_arn}/*/${each.value.http_method}${local.all_resources[each.value.resource_key].path}"
}