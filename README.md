# 📚 API Serverless para Lista de Tarefas (To-Do List)

## 1. Visão Geral do Projeto

Este projeto implementa uma API serverless completa de To-Do List. Toda a infraestrutura é provisionada via **Terraform (Infrastructure as Code - IaC)** e o código de negócio é escrito em **Java 17 (AWS Lambda)**.

O projeto demonstra um fluxo de **Entrega Contínua (CD)** automatizado pelo GitHub Actions e utiliza uma arquitetura assíncrona robusta para processamento de relatórios CSV via SQS.

---
## 2. Arquitetura e Componentes

A arquitetura é 100% serverless e desacoplada:

| Serviço AWS | Função Principal |
| :--- | :--- |
| **AWS Lambda (Java 17)** | Lógica de negócio e processamento de dados (CRUD e fluxo assíncrono). |
| **AWS Cognito** | **Autorizador JWT** para proteger todos os endpoints da API. |
| **Amazon DynamoDB** | Persistência de dados (Single-Table Design). |
| **Amazon SQS** | **Fila de Mensagens** para desacoplar a solicitação de exportação. |
| **Amazon S3** | Armazenamento de relatórios CSV gerados. |
| **Amazon SES** | Serviço de e-mail para entrega do link de download do relatório final. |
| **API Gateway (HTTP API)** | Exposição pública dos endpoints REST. |

---
## 3. Modelo de Dados (Single-Table Design)

A tabela `TodoList` utiliza um design otimizado para performance:

| Entidade | PK (Chave de Partição) | SK (Chave de Ordenação) |
| :--- | :--- | :--- |
| **Lista** | `USER#<userId>` | `LIST#<listId>` |
| **Item/Tarefa** | `LIST#<listId>` | `ITEM#<itemId>` |

---
## 4. Pipeline de CI/CD (GitHub Actions)

O deploy é automatizado através de um workflow no GitHub Actions.

#### **Fluxo de Deploy**
* **CI (Integração Contínua):** O pipeline compila o código (`mvn package`) e executa o `terraform plan` em Pull Requests.
* **CD (Entrega Contínua):** O `terraform apply` é executado automaticamente no merge para a branch `develop`.

#### **Alinhamento de Versões**
* **Desenvolvimento Local:** Java JDK 17+
* **Runtime Lambda (AWS):** `java17` (Definido no Terraform)

---
## 5. Guia de Uso e Teste

A URL base da API (`<api-url>`) é retornada pelo `terraform apply`. Todas as requisições **exigem autenticação**.

### **5.1. Obter Token de Autenticação (JWT)**

Você deve gerar um token JWT válido toda vez que o antigo expirar (dura 1 hora).

* **Comando CLI:**
    ```bash
    aws cognito-idp initiate-auth --auth-flow USER_PASSWORD_AUTH --client-id "28229sdm71m3s9hj4j35rgql84" --auth-parameters USERNAME="deyvidy",PASSWORD="SuperSenha#2025"
    ```
* **Uso:** O valor do `IdToken` é colado no cabeçalho `Authorization: Bearer <TOKEN_LIMPO>`.

### **5.2. Teste Final: Fluxo Assíncrono (Exportação CSV)**

Este é o teste de ponta a ponta que valida SQS, S3, SES e as Lambdas.

| # | Ação | Método | Endpoint (Exemplo) | Resultado Esperado |
| :--- | :--- | :--- | :--- | :--- |
| **1** | **Disparar Exportação** | `POST` | `<api-url>/lists/<listId>/export` | **202 Accepted** (Pedido aceito pela fila SQS). |
| **2** | **Verificação Final** | | **Checar Caixa de Entrada (Email)** | Recebimento do e-mail com o link público do CSV (via S3). |

---
## 6. Guia de Instalação e Deploy

#### **Pré-requisitos**

* [AWS CLI](https://aws.amazon.com/cli/) (`aws configure`)
* [Terraform](https://www.terraform.io/downloads.html)
* **Java JDK 17** (Versão mínima)
* [Apache Maven](https://maven.apache.org/download.cgi)

#### **Passos para o Deploy Manual (Primeira Vez)**

1.  **Clone e Compile o Projeto:**
    ```bash
    git clone [https://github.com/Deyvidy-Alves/TodoList-Serverless-API.git](https://github.com/Deyvidy-Alves/TodoList-Serverless-API.git)
    cd TodoList-Serverless-API
    mvn clean package -DskipTests
    ```
2.  **Implante a Infraestrutura:**
    ```bash
    cd infra
    terraform init
    terraform apply --auto-approve
    ```

---
## 7. Limpeza (Destroy)

Para remover todos os recursos da sua conta da AWS:
```bash
cd infra
terraform destroy