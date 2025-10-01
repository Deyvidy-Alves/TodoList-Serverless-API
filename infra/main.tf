# Bloco para configurar o Terraform e o backend remoto
terraform {
  backend "s3" {
    bucket         = "deyvidy-terraform-state-repojava2"
    key            = "repo-java2/terraform.tfstate"
    region         = "sa-east-1"
    dynamodb_table = "terraform-state-lock"
    encrypt        = true
  }
}

# Configura o provedor da AWS
provider "aws" {
  region = var.aws_region
}


# --- RECURSOS DA LAMBDA E IAM ---
resource "aws_iam_role" "lambda_exec_role" {
  name = "${var.lambda_function_name}-role"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17",
    Statement = [{
      Action    = "sts:AssumeRole",
      Effect    = "Allow",
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_logs" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_lambda_function" "hello_lambda" {
  filename         = var.zip_path
  function_name    = var.lambda_function_name
  role             = aws_iam_role.lambda_exec_role.arn
  handler          = var.lambda_handler
  runtime          = var.lambda_runtime
  source_code_hash = filebase64sha256(var.zip_path)
  timeout          = 30
  memory_size      = 256
  tags             = { ManagedBy = "Terraform" }
}

# --- RECURSOS DO DYNAMODB E IAM ---
resource "aws_dynamodb_table" "todo_list_table" {
  name         = var.dynamodb_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"
  range_key    = "SK"

  attribute {
    name = "PK"
    type = "S"
  }

  attribute {
    name = "SK"
    type = "S"
  }

  attribute {
    name = "GSI1PK"
    type = "S"
  }

  attribute {
    name = "GSI1SK"
    type = "S"
  }

  global_secondary_index {
    name            = "gsi1-ListsIndex"
    hash_key        = "GSI1PK"
    range_key       = "GSI1SK"
    projection_type = "ALL"
  }
  tags = {
    ManagedBy = "Terraform"
    Project   = "TodoList"
  }
}

resource "aws_iam_policy" "dynamodb_policy" {
  name        = "${var.lambda_function_name}-dynamodb-policy"
  description = "Política que permite à Lambda acessar a tabela DynamoDB"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Action = [
        "dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem",
        "dynamodb:DeleteItem", "dynamodb:Query"
      ],
      Effect   = "Allow",
      Resource = [
        aws_dynamodb_table.todo_list_table.arn,
        "${aws_dynamodb_table.todo_list_table.arn}/index/*",
      ]
    }]
  })
}

resource "aws_iam_role_policy_attachment" "dynamodb_attachment" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = aws_iam_policy.dynamodb_policy.arn
}

# --- RECURSOS DA API GATEWAY (VERSÃO V1 - REST API) ---

# 1. Definições Principais
resource "aws_api_gateway_rest_api" "todo_api" {
  name        = "${var.lambda_function_name}-api-rest"
  description = "API REST para o projeto To-Do List"
}

resource "aws_api_gateway_resource" "lists_resource" {
  rest_api_id = aws_api_gateway_rest_api.todo_api.id
  parent_id   = aws_api_gateway_rest_api.todo_api.root_resource_id
  path_part   = "lists"
}

resource "aws_api_gateway_resource" "list_item_resource" {
  rest_api_id = aws_api_gateway_rest_api.todo_api.id
  parent_id   = aws_api_gateway_resource.lists_resource.id
  path_part   = "{listId}"
}

# 2. Métodos e Integrações
# POST /lists
resource "aws_api_gateway_method" "post_lists" {
  rest_api_id   = aws_api_gateway_rest_api.todo_api.id
  resource_id   = aws_api_gateway_resource.lists_resource.id
  http_method   = "POST"
  authorization = "NONE"
}
resource "aws_api_gateway_integration" "post_lists_integration" {
  rest_api_id             = aws_api_gateway_rest_api.todo_api.id
  resource_id             = aws_api_gateway_resource.lists_resource.id
  http_method             = aws_api_gateway_method.post_lists.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.hello_lambda.invoke_arn
}

# GET /lists
resource "aws_api_gateway_method" "get_lists" {
  rest_api_id   = aws_api_gateway_rest_api.todo_api.id
  resource_id   = aws_api_gateway_resource.lists_resource.id
  http_method   = "GET"
  authorization = "NONE"
}
resource "aws_api_gateway_integration" "get_lists_integration" {
  rest_api_id             = aws_api_gateway_rest_api.todo_api.id
  resource_id             = aws_api_gateway_resource.lists_resource.id
  http_method             = aws_api_gateway_method.get_lists.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.hello_lambda.invoke_arn
}

# PUT /lists/{listId}
resource "aws_api_gateway_method" "put_list_item" {
  rest_api_id   = aws_api_gateway_rest_api.todo_api.id
  resource_id   = aws_api_gateway_resource.list_item_resource.id
  http_method   = "PUT"
  authorization = "NONE"
}
resource "aws_api_gateway_integration" "put_list_item_integration" {
  rest_api_id             = aws_api_gateway_rest_api.todo_api.id
  resource_id             = aws_api_gateway_resource.list_item_resource.id
  http_method             = aws_api_gateway_method.put_list_item.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.hello_lambda.invoke_arn
}

# DELETE /lists/{listId}
resource "aws_api_gateway_method" "delete_list_item" {
  rest_api_id   = aws_api_gateway_rest_api.todo_api.id
  resource_id   = aws_api_gateway_resource.list_item_resource.id
  http_method   = "DELETE"
  authorization = "NONE"
}
resource "aws_api_gateway_integration" "delete_list_item_integration" {
  rest_api_id             = aws_api_gateway_rest_api.todo_api.id
  resource_id             = aws_api_gateway_resource.list_item_resource.id
  http_method             = aws_api_gateway_method.delete_list_item.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.hello_lambda.invoke_arn
}

# 3. Configurações de CORS
# CORS para /lists
resource "aws_api_gateway_method" "options_lists" {
  rest_api_id   = aws_api_gateway_rest_api.todo_api.id
  resource_id   = aws_api_gateway_resource.lists_resource.id
  http_method   = "OPTIONS"
  authorization = "NONE"
}
resource "aws_api_gateway_integration" "options_lists_integration" {
  rest_api_id       = aws_api_gateway_rest_api.todo_api.id
  resource_id       = aws_api_gateway_resource.lists_resource.id
  http_method       = aws_api_gateway_method.options_lists.http_method
  type              = "MOCK"
  request_templates = { "application/json" = "{\"statusCode\": 200}" }
}
resource "aws_api_gateway_method_response" "options_lists_response" {
  rest_api_id = aws_api_gateway_rest_api.todo_api.id
  resource_id = aws_api_gateway_resource.lists_resource.id
  http_method = aws_api_gateway_method.options_lists.http_method
  status_code = "200"
  response_models = { "application/json" = "Empty" }
  response_parameters = {
    "method.response.header.Access-Control-Allow-Headers" = true,
    "method.response.header.Access-Control-Allow-Methods" = true,
    "method.response.header.Access-Control-Allow-Origin"  = true
  }
}
resource "aws_api_gateway_integration_response" "options_lists_response" {
  rest_api_id = aws_api_gateway_rest_api.todo_api.id
  resource_id = aws_api_gateway_resource.lists_resource.id
  http_method = aws_api_gateway_method.options_lists.http_method
  status_code = aws_api_gateway_method_response.options_lists_response.status_code
  response_parameters = {
    "method.response.header.Access-Control-Allow-Headers" = "'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token'",
    "method.response.header.Access-Control-Allow-Methods" = "'POST,GET,OPTIONS'",
    "method.response.header.Access-Control-Allow-Origin"  = "'*'"
  }
}

# CORS para /lists/{listId}
resource "aws_api_gateway_method" "options_list_item" {
  rest_api_id   = aws_api_gateway_rest_api.todo_api.id
  resource_id   = aws_api_gateway_resource.list_item_resource.id
  http_method   = "OPTIONS"
  authorization = "NONE"
}
resource "aws_api_gateway_integration" "options_list_item_integration" {
  rest_api_id       = aws_api_gateway_rest_api.todo_api.id
  resource_id       = aws_api_gateway_resource.list_item_resource.id
  http_method       = aws_api_gateway_method.options_list_item.http_method
  type              = "MOCK"
  request_templates = { "application/json" = "{\"statusCode\": 200}" }
}
resource "aws_api_gateway_method_response" "options_list_item_response" {
  rest_api_id = aws_api_gateway_rest_api.todo_api.id
  resource_id = aws_api_gateway_resource.list_item_resource.id
  http_method = aws_api_gateway_method.options_list_item.http_method
  status_code = "200"
  response_models = { "application/json" = "Empty" }
  response_parameters = {
    "method.response.header.Access-Control-Allow-Headers" = true,
    "method.response.header.Access-Control-Allow-Methods" = true,
    "method.response.header.Access-Control-Allow-Origin"  = true
  }
}
resource "aws_api_gateway_integration_response" "options_list_item_response" {
  rest_api_id = aws_api_gateway_rest_api.todo_api.id
  resource_id = aws_api_gateway_resource.list_item_resource.id
  http_method = aws_api_gateway_method.options_list_item.http_method
  status_code = aws_api_gateway_method_response.options_list_item_response.status_code
  response_parameters = {
    "method.response.header.Access-Control-Allow-Headers" = "'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token'",
    "method.response.header.Access-Control-Allow-Methods" = "'PUT,DELETE,OPTIONS'",
    "method.response.header.Access-Control-Allow-Origin"  = "'*'"
  }
}


# 4. Deployment e Stage
resource "aws_api_gateway_deployment" "todo_api_deployment" {
  rest_api_id = aws_api_gateway_rest_api.todo_api.id
  lifecycle { create_before_destroy = true }
  depends_on = [
    aws_api_gateway_integration.post_lists_integration,
    aws_api_gateway_integration.get_lists_integration,
    aws_api_gateway_integration.put_list_item_integration,
    aws_api_gateway_integration.delete_list_item_integration,
    aws_api_gateway_integration.options_lists_integration,
    aws_api_gateway_integration.options_list_item_integration,
  ]
}
resource "aws_api_gateway_stage" "todo_api_stage" {
  deployment_id = aws_api_gateway_deployment.todo_api_deployment.id
  rest_api_id   = aws_api_gateway_rest_api.todo_api.id
  stage_name    = "v1"
}

# 5. Permissões da Lambda
resource "aws_lambda_permission" "post_permission" {
  statement_id  = "AllowAPIGatewayInvokePOST"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.hello_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.todo_api.execution_arn}/*/${aws_api_gateway_method.post_lists.http_method}${aws_api_gateway_resource.lists_resource.path}"
}
resource "aws_lambda_permission" "get_permission" {
  statement_id  = "AllowAPIGatewayInvokeGET"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.hello_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.todo_api.execution_arn}/*/${aws_api_gateway_method.get_lists.http_method}${aws_api_gateway_resource.lists_resource.path}"
}
resource "aws_lambda_permission" "put_permission" {
  statement_id  = "AllowAPIGatewayInvokePUT"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.hello_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.todo_api.execution_arn}/*/${aws_api_gateway_method.put_list_item.http_method}${aws_api_gateway_resource.list_item_resource.path}"
}
resource "aws_lambda_permission" "delete_permission" {
  statement_id  = "AllowAPIGatewayInvokeDELETE"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.hello_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.todo_api.execution_arn}/*/${aws_api_gateway_method.delete_list_item.http_method}${aws_api_gateway_resource.list_item_resource.path}"
}