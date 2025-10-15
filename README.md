# API Serverless para Lista de Tarefas (To-Do List)

## 1. Visão Geral

Este projeto implementa uma API serverless completa para uma aplicação de "To-Do List". Toda a infraestrutura na AWS é provisionada e gerenciada como código utilizando Terraform, garantindo automação, repetibilidade e segurança.

A aplicação consiste em uma API REST que permite aos usuários criar, listar, editar e apagar listas de tarefas (CRUD). A lógica de negócio é executada por uma função AWS Lambda (Java) e os dados são persistidos em uma tabela Amazon DynamoDB, seguindo as melhores práticas de design de dados NoSQL (Single Table Design).

## 2. Arquitetura

A arquitetura é 100% serverless, utilizando os seguintes serviços da AWS:

```mermaid
graph TD
    subgraph Cliente
        A[Postman / Front-End]
    end

    subgraph "AWS API Gateway (REST API)"
        B("POST /v1/lists")
        C("GET /v1/lists")
        D("PUT /v1/lists/{listId}")
        H("DELETE /v1/lists/{listId}")
    end

    subgraph "AWS Lambda"
        E{Função Principal (Java)}
    end

    subgraph "Amazon DynamoDB"
        F[(Tabela: TodoList)]
        G[GSI1: Índice Secundo Global]
    end

    A --> B & C & D & H

    B --> E
    C --> E
    D --> E
    H --> E

    E --> F
    F -- "Query para Listagem" --> G
```

## 3. Modelo de Dados - Single Table Design

A tabela `TodoList` utiliza o padrão Single Table Design para otimizar as consultas.

| Entidade | PK (Chave de Partição) | SK (Chave de Ordenação) | GSI1PK | GSI1SK | Atributos Adicionais |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Lista** | `USER#<userId>` | `LIST#<listId>` | `LISTS` | `METADATA#<listId>` | `listName`, `createdAt` |
| **Tarefa** | `USER#<userId>` | `LIST#<listId>#TASK#<taskId>` | - | - | `taskDescription`, `isComplete` |

* **GSI1 (Global Secondary Index):** Permite a consulta `Query(GSI1PK = "LISTS")` para obter todas as listas de forma eficiente, atendendo ao requisito de "NÃO UTILIZAR SCAN".

## 4. Roadmap de Desenvolvimento![img.png](img.png)

- [x] **Spike Terraform:** Estudo dos comandos e armazenamento de estado no S3.
- [x] **Spike Lambda via Terraform:** Criação de uma função Lambda "Hello World".
- [x] **Criar DynamoDB via Terraform:** Criação da tabela `TodoList` com `PK`, `SK` e GSI.
- [x] **Subir ApiGateway via terraform (REST API V1):** Implantação da API Gateway base.
- [x] **Estória: Cadastrar uma lista (`POST /lists`):** Implementada e testada.
- [x] **Estória: Listar todas as listas (`GET /lists`):** Implementada e testada.
- [x] **Estória: Editar uma lista (`PUT /lists/{listId}`):** Implementada e testada.
- [x] **Estória: Apagar uma lista (`DELETE /lists/{listId}`):** Implementada e testada.
- [x] **Qualidade:** Adicionar cobertura de testes unitários para todas as operações do CRUD.
- [ ] **Refatoração:** Converter recursos para Módulos Terraform reutilizáveis (próximo passo sugerido).

## 5. Guia de Instalação e Deploy

#### **Pré-requisitos**

* [AWS CLI](https://aws.amazon.com/cli/) (`aws configure`)
* [Terraform](https://www.terraform.io/downloads.html)
* [Java JDK](https://www.oracle.com/java/technologies/downloads/) (v17+)
* [Apache Maven](https://maven.apache.org/download.cgi)
* [Postman](https://www.postman.com/downloads/)

#### **Passos para o Deploy**

1.  **Clone o repositório e compile o projeto Java:**
    ```bash
    git clone <url-do-repositorio>
    cd <nome-do-repositorio>
    mvn clean package 
    ```

2.  **Implante a infraestrutura com Terraform:**
    ```bash
    cd infra
    terraform init
    terraform apply
    ```

## 6. Uso da API (Endpoints)

A URL base da API (`<api-url>`) é fornecida na saída do `terraform apply`.

---
#### **Criar uma Lista**
Cria uma nova lista de tarefas.

* **Endpoint:** `POST <api-url>/v1/lists`
* **Exemplo (`curl`):**
  ```bash
  curl -X POST \
    '<api-url>/v1/lists' \
    -H 'Content-Type: application/json' \
    -d '{"name": "Compras do Supermercado"}'
  ```
* **Resposta de Sucesso (`201 Created`):**
  ```json
  {
      "message": "Lista criada com sucesso!",
      "listId": "a1b2c3d4-e5f6-7890-1234-567890abcdef"
  }
  ```

---
#### **Listar todas as Listas**
Retorna um array com todas as listas de tarefas cadastradas.

* **Endpoint:** `GET <api-url>/v1/lists`
* **Exemplo (`curl`):**
  ```bash
  curl -X GET '<api-url>/v1/lists'
  ```
* **Resposta de Sucesso (`200 OK`):**
  ```json
  [
      {
          "listId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
          "createdAt": "2025-10-01T19:05:17.891Z",
          "listName": "Compras do Supermercado"
      },
      {
          "listId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
          "createdAt": "2025-10-01T19:10:25.123Z",
          "listName": "Tarefas da Casa"
      }
  ]
  ```

---
#### **Editar uma Lista**
Atualiza o nome de uma lista de tarefas existente.

* **Endpoint:** `PUT <api-url>/v1/lists/{listId}`
* **Exemplo (`curl`):**
  ```bash
  curl -X PUT \
    '<api-url>/v1/lists/a1b2c3d4-e5f6-7890-1234-567890abcdef' \
    -H 'Content-Type: application/json' \
    -d '{"name": "Compras de Sábado"}'
  ```
* **Resposta de Sucesso (`200 OK`):**
  ```json
  {
      "message": "Lista atualizada com sucesso!",
      "listId": "a1b2c3d4-e5f6-7890-1234-567890abcdef"
  }
  ```

---
#### **Apagar uma Lista**
Apaga uma lista de tarefas específica.

* **Endpoint:** `DELETE <api-url>/v1/lists/{listId}`
* **Exemplo (`curl`):**
  ```bash
  curl -X DELETE '<api-url>/v1/lists/a1b2c3d4-e5f6-7890-1234-567890abcdef'
  ```
* **Resposta de Sucesso (`200 OK`):**
  ```json
  {
      "message": "Lista apagada com sucesso!",
      "listId": "a1b2c3d4-e5f6-7890-1234-567890abcdef"
  }
  ```

## 7. Limpeza

Para remover toda a infraestrutura da sua conta da AWS, execute:
```bash
cd infra
terraform destroy
```