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

# Cria a IAM Role para a função Lambda
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

# Anexa a política de logs do CloudWatch
resource "aws_iam_role_policy_attachment" "lambda_logs" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# Cria a função Lambda
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

# Cria a tabela DynamoDB
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
  tags = {
    ManagedBy = "Terraform"
    Project   = "TodoList"
  }
}

# Cria uma política de permissão para a tabela DynamoDB
resource "aws_iam_policy" "dynamodb_policy" {
  name        = "${var.lambda_function_name}-dynamodb-policy"
  description = "Política que permite à Lambda acessar a tabela DynamoDB"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Action = [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:DeleteItem",
        "dynamodb:Query"
      ],
      Effect   = "Allow",
      Resource = aws_dynamodb_table.todo_list_table.arn
    }]
  })
}

# Anexa a política do DynamoDB à role da Lambda
resource "aws_iam_role_policy_attachment" "dynamodb_attachment" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = aws_iam_policy.dynamodb_policy.arn
}

# main.tf

# --- RECURSOS DA API GATEWAY (VERSÃO V1 - REST API) ---

# 1. Cria o recurso da API REST (V1)
resource "aws_api_gateway_rest_api" "todo_api" {
  name        = "${var.lambda_function_name}-api-rest"
  description = "API REST para o projeto To-Do List"
}

# 2. Cria o recurso de caminho "/lists"
resource "aws_api_gateway_resource" "lists_resource" {
  rest_api_id = aws_api_gateway_rest_api.todo_api.id
  parent_id   = aws_api_gateway_rest_api.todo_api.root_resource_id
  path_part   = "lists"
}

# 3. Define o método POST para o recurso "/lists"
resource "aws_api_gateway_method" "post_list_method" {
  rest_api_id   = aws_api_gateway_rest_api.todo_api.id
  resource_id   = aws_api_gateway_resource.lists_resource.id
  http_method   = "POST"
  authorization = "NONE"
}

# 4. Cria a integração entre o método POST e a função Lambda
resource "aws_api_gateway_integration" "lambda_integration" {
  rest_api_id             = aws_api_gateway_rest_api.todo_api.id
  resource_id             = aws_api_gateway_resource.lists_resource.id
  http_method             = aws_api_gateway_method.post_list_method.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.hello_lambda.invoke_arn
}

# 5. Configuração Manual do CORS (para o método OPTIONS)
resource "aws_api_gateway_method" "options_method" {
  rest_api_id   = aws_api_gateway_rest_api.todo_api.id
  resource_id   = aws_api_gateway_resource.lists_resource.id
  http_method   = "OPTIONS"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "options_integration" {
  rest_api_id = aws_api_gateway_rest_api.todo_api.id
  resource_id = aws_api_gateway_resource.lists_resource.id
  http_method = aws_api_gateway_method.options_method.http_method
  type        = "MOCK" # MOCK não chama a Lambda, responde direto da API

  request_templates = {
    "application/json" = "{\"statusCode\": 200}"
  }
}

resource "aws_api_gateway_method_response" "options_response_200" {
  rest_api_id = aws_api_gateway_rest_api.todo_api.id
  resource_id = aws_api_gateway_resource.lists_resource.id
  http_method = aws_api_gateway_method.options_method.http_method
  status_code = "200"

  response_models = {
    "application/json" = "Empty"
  }

  response_parameters = {
    "method.response.header.Access-Control-Allow-Headers" = true,
    "method.response.header.Access-Control-Allow-Methods" = true,
    "method.response.header.Access-Control-Allow-Origin"  = true
  }
}

resource "aws_api_gateway_integration_response" "options_integration_response" {
  rest_api_id = aws_api_gateway_rest_api.todo_api.id
  resource_id = aws_api_gateway_resource.lists_resource.id
  http_method = aws_api_gateway_method.options_method.http_method
  status_code = aws_api_gateway_method_response.options_response_200.status_code

  response_parameters = {
    "method.response.header.Access-Control-Allow-Headers" = "'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token'",
    "method.response.header.Access-Control-Allow-Methods" = "'POST,GET,OPTIONS'",
    "method.response.header.Access-Control-Allow-Origin"  = "'*'"
  }

  depends_on = [aws_api_gateway_integration.options_integration]
}

# 6. Cria o "deployment" e o "stage"
resource "aws_api_gateway_deployment" "todo_api_deployment" {
  rest_api_id = aws_api_gateway_rest_api.todo_api.id

  # Este gatilho força um novo deployment sempre que a configuração da API mudar
  triggers = {
    redeployment = sha1(jsonencode([
      aws_api_gateway_resource.lists_resource.id,
      aws_api_gateway_method.post_list_method.id,
      aws_api_gateway_integration.lambda_integration.id,
      # Adicione outros recursos da API aqui se eles mudarem
    ]))
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_stage" "todo_api_stage" {
  deployment_id = aws_api_gateway_deployment.todo_api_deployment.id
  rest_api_id   = aws_api_gateway_rest_api.todo_api.id
  stage_name    = "v1"
}

# 7. Atualiza a permissão da Lambda com o formato de ARN correto para API REST V1
resource "aws_lambda_permission" "api_gw_permission" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.hello_lambda.function_name
  principal     = "apigateway.amazonaws.com"

  # O formato do ARN é diferente para API REST
  source_arn = "${aws_api_gateway_rest_api.todo_api.execution_arn}/*/${aws_api_gateway_method.post_list_method.http_method}${aws_api_gateway_resource.lists_resource.path}"
}