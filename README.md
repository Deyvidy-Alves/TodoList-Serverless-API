# 📚 API Serverless para Lista de Tarefas (To-Do List)

## 1. Visão Geral do Projeto

Este projeto implementa uma API serverless completa de To-Do List. Toda a infraestrutura é gerenciada via **Terraform (Infrastructure as Code - IaC)** e o código de negócio é escrito em **Java 21 (AWS Lambda)**.

O projeto é dividido em dois fluxos principais, demonstrando uma arquitetura desacoplada e robusta:
1.  **Síncrono (CRUD):** Operações imediatas para manipulação de listas e itens.
2.  **Assíncrono (CSV Export):** Um fluxo de processamento em segundo plano para gerar relatórios e enviá-los por e-mail, utilizando filas de mensagens (SQS).

A estabilidade é garantida por um pipeline de **Entrega Contínua (CI/CD)**.

---
## 2. Arquitetura e Componentes

A arquitetura é 100% serverless, utilizando os seguintes serviços da AWS:

| Serviço AWS | Função |
| :--- | :--- |
| **AWS Lambda (10 Funções)** | Lógica de negócio (Java 21) e processamento assíncrono. |
| **AWS Cognito** | Autorizador JWT para proteger todos os endpoints da API. |
| **Amazon DynamoDB** | Persistência de listas e itens (Single-Table Design). |
| **Amazon SQS** | Fila de mensagens para desacoplar a solicitação de relatório do processamento (lento). |
| **Amazon S3** | Armazenamento de relatórios CSV gerados. |
| **Amazon SES** | Serviço de e-mail utilizado para entregar o relatório final ao usuário (e-mail verificado: `deyvidyalves03@gmail.com`). |
| **API Gateway (HTTP API)** | Exposição pública dos endpoints REST. |

---
## 3. Modelo de Dados (Single-Table Design)

A tabela `TodoList` utiliza um design otimizado para consultas diretas:

| Entidade | PK (Chave de Partição) | SK (Chave de Ordenação) |
| :--- | :--- | :--- |
| **Lista** | `USER#<userId>` | `LIST#<listId>` |
| **Item/Tarefa** | `LIST#<listId>` | `ITEM#<itemId>` |

---
## 4. Pipeline de CI/CD (GitHub Actions)

O deploy é automatizado através de um workflow no GitHub Actions.

#### **Fluxo de Deploy**
* **CI (Integração Contínua):** Toda vez que um Pull Request é aberto para a branch `develop`, o pipeline compila o código e executa o `terraform plan`.
* **CD (Entrega Contínua):** O deploy é acionado automaticamente pelo `push` (merge) na branch `develop`, executando o `terraform apply`.

#### **Alinhamento de Versões**
* **Desenvolvimento Local:** Java JDK 21
* **Runtime Lambda (AWS):** `java21` (Definido no Terraform)

---
## 5. Guia de Teste e Uso da API

A URL base da API (`<api-url>`) é fornecida na saída do `terraform apply`. Todas as requisições **exigem autenticação**.

### **5.1. Obter Token de Autenticação (JWT)**

Você deve gerar um novo token JWT toda vez que o antigo expirar (dura 1 hora).

* **Comando CLI:**
    ```bash
    aws cognito-idp initiate-auth --auth-flow USER_PASSWORD_AUTH --client-id "28229sdm71m3s9hj4j35rgql84" --auth-parameters USERNAME="deyvidy",PASSWORD="SuperSenha#2025"
    ```
* **Uso:** O valor do `IdToken` é colado no cabeçalho `Authorization: Bearer <TOKEN_LIMPO>`.

### **5.2. Teste do Fluxo de Exportação CSV (Assíncrono)**

Este é o teste de ponta a ponta que valida SQS, S3, SES e as Lambdas.

| # | Ação | Método | Endpoint (Exemplo) | Resultado Esperado |
| :--- | :--- | :--- | :--- | :--- |
| **1** | **Disparar Exportação** | `POST` | `<api-url>/lists/<listId>/export` | **202 Accepted** (Pedido aceito pela fila SQS). |
| **2** | **Verificação Final** | | **Checar Caixa de Entrada (Email)** | Recebimento do e-mail com o link público do CSV (via S3). |

### **5.3. Teste do CRUD de Itens (GET Específico)**

| Endpoint | Método | Descrição |
| :--- | :--- | :--- |
| `/lists/{listId}/items/{itemId}` | **GET** | Busca um item específico. |

* **Exemplo (`curl` - substitua IDs e TOKEN):**
    ```bash
    curl -X GET '<api-url>/lists/46304619-.../items/5824cefb-...' \
      -H 'Authorization: Bearer <TOKEN_FRESCO>'
    ```
* **Resultado esperado:** `200 OK` com o JSON do item específico.

---
## 6. Guia de Instalação e Deploy

#### **Pré-requisitos**

* [AWS CLI](https://aws.amazon.com/cli/) (`aws configure`)
* [Terraform](https://www.terraform.io/downloads.html)
* **Java JDK 21**
* [Apache Maven](https://maven.apache.org/download.cgi)

#### **Passos para o Deploy Manual (Primeira Vez)**

1.  **Clone e Compile o Projeto:**
    ```bash
    git clone [https://github.com/Deyvidy-Alves/TodoList-Serverless-API.git](https://github.com/Deyvidy-Alves/TodoList-Serverless-API.git)
    cd TodoList-Serverless-API
    # Compila o JAR
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

Para remover toda a infraestrutura da sua conta da AWS:
```bash
cd infra
terraform destroy