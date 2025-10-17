terraform {
  backend "s3" {
    bucket         = "deyvidy-terraform-state-repojava2"
    key            = "repo-java2/terraform.tfstate"
    region         = "sa-east-1"
    dynamodb_table = "terraform-state-lock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region
}

# --- RECURSOS COMUNS (IAM Role e DynamoDB Table) ---

resource "aws_dynamodb_table" "todo_list_table" {
  name         = var.dynamodb_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "pk"
  range_key    = "sk"

  attribute {
    name = "pk"
    type = "S"
  }

  attribute {
    name = "sk"
    type = "S"
  }

  tags = {
    Project = var.project_name
  }
}

resource "aws_iam_role" "lambda_exec_role" {
  name = "${var.project_name}-lambda-role"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17",
    Statement = [{
      Action    = "sts:AssumeRole",
      Effect    = "Allow",
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })
}

resource "aws_iam_policy" "dynamodb_policy" {
  name        = "${var.project_name}-dynamodb-policy"
  description = "Política que permite à Lambda acessar a tabela DynamoDB"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Action = [
        "dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem",
        "dynamodb:DeleteItem", "dynamodb:Query"
      ],
      Effect   = "Allow",
      Resource = aws_dynamodb_table.todo_list_table.arn
    }]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_logs" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "dynamodb_attachment" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = aws_iam_policy.dynamodb_policy.arn
}


# --- LAMBDA 1: Criar Lista ---

resource "aws_lambda_function" "create_list_lambda" {
  filename         = var.zip_path
  function_name    = "${var.project_name}-CreateList"
  role             = aws_iam_role.lambda_exec_role.arn
  handler          = "example.CreateListHandler::handleRequest"
  runtime          = var.lambda_runtime
  source_code_hash = filebase64sha256(var.zip_path)
  timeout          = 30

  environment {
    variables = {
      TABLE_NAME = aws_dynamodb_table.todo_list_table.name
    }
  }
}

# --- LAMBDA 2: Obter Lista ---

resource "aws_lambda_function" "get_list_lambda" {
  filename         = var.zip_path
  function_name    = "${var.project_name}-GetList"
  role             = aws_iam_role.lambda_exec_role.arn
  handler          = "example.GetListHandler::handleRequest"
  runtime          = var.lambda_runtime
  source_code_hash = filebase64sha256(var.zip_path)
  timeout          = 30

  environment {
    variables = {
      TABLE_NAME = aws_dynamodb_table.todo_list_table.name
    }
  }
}

# --- LAMBDA 3: Atualizar/Apagar Lista ---

resource "aws_lambda_function" "update_delete_list_lambda" {
  filename         = var.zip_path
  function_name    = "${var.project_name}-UpdateDeleteList"
  role             = aws_iam_role.lambda_exec_role.arn
  handler          = "example.UpdateDeleteListHandler::handleRequest"
  runtime          = var.lambda_runtime
  source_code_hash = filebase64sha256(var.zip_path)
  timeout          = 30

  environment {
    variables = {
      TABLE_NAME = aws_dynamodb_table.todo_list_table.name
    }
  }
}

resource "aws_lambda_function" "create_item_lambda" {
  filename         = var.zip_path
  function_name    = "${var.project_name}-CreateItem"
  role             = aws_iam_role.lambda_exec_role.arn
  handler          = "example.CreateItemHandler::handleRequest"
  runtime          = var.lambda_runtime
  source_code_hash = filebase64sha256(var.zip_path)
  timeout          = 30

  environment {
    variables = {
      TABLE_NAME = aws_dynamodb_table.todo_list_table.name
    }
  }
}

# --- LAMBDA 5: Listar Itens da Lista ---

resource "aws_lambda_function" "list_items_lambda" {
  filename         = var.zip_path
  function_name    = "${var.project_name}-ListItems"
  role             = aws_iam_role.lambda_exec_role.arn
  # IMPORTANTE: O handler aponta para a nova classe Java
  handler          = "example.ListItemsHandler::handleRequest"
  runtime          = var.lambda_runtime
  source_code_hash = filebase64sha256(var.zip_path)
  timeout          = 30

  environment {
    variables = {
      TABLE_NAME = aws_dynamodb_table.todo_list_table.name
    }
  }
}


# --- API GATEWAY ---

resource "aws_apigatewayv2_api" "http_api" {
  name          = "${var.project_name}-api-rest"
  protocol_type = "HTTP"
  cors_configuration {
    allow_origins = ["*"]
    allow_methods = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
    allow_headers = ["Content-Type", "Authorization"]
  }
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.http_api.id
  name        = "$default"
  auto_deploy = true
}

# --- AWS COGNITO (Autenticação de Usuários) ---

resource "aws_cognito_user_pool" "user_pool" {
  name = "${var.project_name}-user-pool"

  # Configura como os usuários podem se registrar e fazer login (usaremos email como username)
  alias_attributes       = ["email"]
  auto_verified_attributes = ["email"]

  # Define a política de senha
  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_numbers   = true
    require_symbols   = true
    require_uppercase = true
  }

  tags = {
    Project = var.project_name
  }
}

resource "aws_cognito_user_pool_client" "app_client" {
  name         = "${var.project_name}-app-client"
  user_pool_id = aws_cognito_user_pool.user_pool.id

  # Desabilita a geração de um "client secret", comum para aplicações web/mobile
  generate_secret = false

  # Habilita o fluxo de autenticação via usuário e senha, necessário para nossos testes
  # ADMIN_NO_SRP_AUTH permite que o backend (ou AWS CLI) autentique o usuário diretamente.
  explicit_auth_flows = [
    "ALLOW_ADMIN_USER_PASSWORD_AUTH",
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH"
  ]
}



# --- INTEGRAÇÃO API GATEWAY COM COGNITO ---

resource "aws_apigatewayv2_authorizer" "cognito_authorizer" {
  api_id           = aws_apigatewayv2_api.http_api.id
  name             = "${var.project_name}-cognito-authorizer"
  authorizer_type  = "JWT"

  # Define onde o API Gateway deve procurar o token na requisição (no header 'Authorization')
  identity_sources = ["$request.header.Authorization"]

  # Configuração do JWT
  jwt_configuration {
    # 'audience' deve ser o ID do nosso App Client
    audience = [aws_cognito_user_pool_client.app_client.id]

    # 'issuer' é a URL do nosso User Pool
    issuer   = "https://${aws_cognito_user_pool.user_pool.endpoint}"
  }
}


# --- Rotas e Integrações ---

# POST /users/{userId}/lists
resource "aws_apigatewayv2_integration" "create_list_integration" {
  api_id           = aws_apigatewayv2_api.http_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.create_list_lambda.invoke_arn
}
resource "aws_apigatewayv2_route" "create_list_route" {
  api_id    = aws_apigatewayv2_api.http_api.id
  route_key = "POST /users/{userId}/lists"
  target    = "integrations/${aws_apigatewayv2_integration.create_list_integration.id}"

  # --- MODIFICADO: Protegendo a rota ---
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito_authorizer.id
}

# GET /users/{userId}/lists/{listId}
resource "aws_apigatewayv2_integration" "get_list_integration" {
  api_id           = aws_apigatewayv2_api.http_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.get_list_lambda.invoke_arn
}
resource "aws_apigatewayv2_route" "get_list_route" {
  api_id    = aws_apigatewayv2_api.http_api.id
  route_key = "GET /users/{userId}/lists/{listId}"
  target    = "integrations/${aws_apigatewayv2_integration.get_list_integration.id}"

  # --- MODIFICADO: Protegendo a rota ---
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito_authorizer.id
}

# PUT /users/{userId}/lists/{listId}
resource "aws_apigatewayv2_integration" "update_list_integration" {
  api_id           = aws_apigatewayv2_api.http_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.update_delete_list_lambda.invoke_arn
}
resource "aws_apigatewayv2_route" "update_list_route" {
  api_id    = aws_apigatewayv2_api.http_api.id
  route_key = "PUT /users/{userId}/lists/{listId}"
  target    = "integrations/${aws_apigatewayv2_integration.update_list_integration.id}"

  # --- MODIFICADO: Protegendo a rota ---
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito_authorizer.id
}

# DELETE /users/{userId}/lists/{listId}
resource "aws_apigatewayv2_integration" "delete_list_integration" {
  api_id           = aws_apigatewayv2_api.http_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.update_delete_list_lambda.invoke_arn
}
resource "aws_apigatewayv2_route" "delete_list_route" {
  api_id    = aws_apigatewayv2_api.http_api.id
  route_key = "DELETE /users/{userId}/lists/{listId}"
  target    = "integrations/${aws_apigatewayv2_integration.delete_list_integration.id}"

  # --- MODIFICADO: Protegendo a rota ---
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito_authorizer.id
}

# POST /lists/{listId}/items
resource "aws_apigatewayv2_integration" "create_item_integration" {
  api_id           = aws_apigatewayv2_api.http_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.create_item_lambda.invoke_arn
}

resource "aws_apigatewayv2_route" "create_item_route" {
  api_id    = aws_apigatewayv2_api.http_api.id
  route_key = "POST /lists/{listId}/items"
  target    = "integrations/${aws_apigatewayv2_integration.create_item_integration.id}"

  # Rota também protegida pelo Cognito
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito_authorizer.id
}

# GET /lists/{listId}/items
resource "aws_apigatewayv2_integration" "list_items_integration" {
  api_id           = aws_apigatewayv2_api.http_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.list_items_lambda.invoke_arn
}

resource "aws_apigatewayv2_route" "list_items_route" {
  api_id    = aws_apigatewayv2_api.http_api.id
  route_key = "GET /lists/{listId}/items"
  target    = "integrations/${aws_apigatewayv2_integration.list_items_integration.id}"

  # Rota também protegida pelo Cognito
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito_authorizer.id
}

# Permissões para API Gateway invocar as Lambdas
resource "aws_lambda_permission" "api_gtw_permission_create" {
  statement_id  = "AllowAPIGatewayToInvokeCreate"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.create_list_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.http_api.execution_arn}/*/*"
}
resource "aws_lambda_permission" "api_gtw_permission_get" {
  statement_id  = "AllowAPIGatewayToInvokeGet"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.get_list_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.http_api.execution_arn}/*/*"
}
resource "aws_lambda_permission" "api_gtw_permission_update_delete" {
  statement_id  = "AllowAPIGatewayToInvokeUpdateDelete"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.update_delete_list_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.http_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gtw_permission_create_item" {
  statement_id  = "AllowAPIGatewayToInvokeCreateItem"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.create_item_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.http_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gtw_permission_list_items" {
  statement_id  = "AllowAPIGatewayToInvokeListItems"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.list_items_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.http_api.execution_arn}/*/*"
}