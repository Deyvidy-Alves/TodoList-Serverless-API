# variables.tf

variable "aws_region" {
  description = "Região da AWS onde os recursos serão criados."
  type        = string
  default     = "sa-east-1"
}

variable "lambda_function_name" {
  description = "Nome da função lambda"
  type        = string
  default     = "HelloWorldJava"
}

variable "zip_path" {
  description = "Caminho para o arquivo .zip do código da Lambda."
  type        = string
  default     = "../hello-lambda.zip" # Aponta para o arquivo na raiz do projeto
}

variable "lambda_handler" {
  description = "O handler da função Lambda (pacote.Classe::metodo)."
  type        = string
  default     = "example.Hello::handleRequest" # Altere para o seu handler correto
}

variable "lambda_runtime" {
  description = "O runtime da função Lambda."
  type        = string
  default     = "java21"
}

variable "dynamodb_table_name" {
  description = "O nome da tabela DynamoDB a ser criada."
  type        = string
  default     = "TodoList"
}