variable "api_name" {
  description = "Nome da API Gateway"
  type        = string
}

variable "api_description" {
  description = "Descrição da API Gateway"
  type        = string
  default     = "API Gateway criada via Terraform"
}

variable "stage_name" {
  description = "Nome do stage de deploy"
  type        = string
  default     = "v1"
}

variable "resources" {
  description = "Mapa de recursos da API"
  type = map(object({
    path_part = string
    parent_id = string
  }))
  default = {}
}

variable "methods" {
  description = "Mapa de métodos HTTP para cada recurso"
  type = map(object({
    resource_key       = string
    http_method        = string
    authorization      = string
    lambda_invoke_arn  = string
    lambda_function_name = string
  }))
  default = {}
}

variable "enable_cors" {
  description = "Habilitar configuração CORS"
  type        = bool
  default     = true
}

variable "cors_origin" {
  description = "Origem permitida para CORS"
  type        = string
  default     = "*"
}

variable "allowed_methods" {
  description = "Métodos HTTP permitidos para CORS"
  type        = list(string)
  default     = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
}