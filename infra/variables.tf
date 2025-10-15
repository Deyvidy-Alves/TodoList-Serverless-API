variable "aws_region" {
  description = "Região da AWS onde os recursos serão criados."
  type        = string
  default     = "sa-east-1"
}

variable "project_name" {
  description = "Nome base para os recursos do projeto."
  type        = string
  default     = "TodoList"
}

variable "zip_path" {
  description = "Caminho para o arquivo .jar do código da Lambda."
  type        = string
  default     = "../target/todo-lambdas-1.0-SNAPSHOT.jar"
}

variable "lambda_runtime" {
  description = "O runtime da função Lambda."
  type        = string
  default     = "java17"
}

variable "dynamodb_table_name" {
  description = "O nome da tabela DynamoDB."
  type        = string
  default     = "TodoList"
}