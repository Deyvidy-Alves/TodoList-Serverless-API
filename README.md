# API Serverless para Lista de Tarefas (To-Do List)

## 1\. Visão Geral

Este projeto tem como objetivo desenvolver uma API serverless completa para uma aplicação de "To-Do List". Toda a infraestrutura na AWS é provisionada e gerenciada como código utilizando Terraform, garantindo automação, repetibilidade e segurança.

A aplicação consiste em uma API REST que permite aos usuários criar, listar e editar listas de tarefas. A lógica de negócio é executada por uma função AWS Lambda (Java) e os dados são persistidos em uma tabela Amazon DynamoDB, seguindo as melhores práticas de design de dados NoSQL (Single Table Design).

## 2\. Arquitetura Alvo

A arquitetura final planejada é 100% serverless, utilizando os seguintes serviços da AWS:

```mermaid
graph TD
    subgraph Cliente
        A[Postman / Front-End]
    end

    subgraph "AWS API Gateway (REST API)"
        B(POST /v1/lists)
        C(GET /v1/lists)
        D(PUT /v1/lists/{listId})
    end

    subgraph "AWS Lambda"
        E{Função Principal (Java)}
    end

    subgraph "Amazon DynamoDB"
        F[(Tabela: TodoList)]
        G[GSI1: Índice Secundário Global]
    end

    A --> B
    A --> C
    A --> D

    B --> E
    C --> E
    D --> E

    E --> F
    F -- Query para Listagem --> G
```

* **API Gateway (REST API V1):** Serve como o ponto de entrada seguro para todas as requisições.
* **AWS Lambda:** Executa a lógica de negócio principal. Uma única função é usada para lidar com as diferentes rotas, lendo o evento para determinar a ação a ser tomada.
* **Amazon DynamoDB:** Armazena os dados da aplicação. Um **Índice Secundário Global (GSI)** será utilizado para permitir a listagem eficiente de todas as listas sem usar a operação `Scan`.

## 3\. Modelo de Dados - Single Table Design

Para otimizar as consultas e escalar de forma eficiente, a tabela `TodoList` utilizará o padrão Single Table Design.

| Entidade | PK (Chave de Partição) | SK (Chave de Ordenação) | GSI1PK | GSI1SK | Atributos Adicionais |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Lista** | `USER#<userId>` | `LIST#<listId>` | `LISTS` | `METADATA#<listId>` | `listName`, `createdAt`, `updatedAt` |
| **Tarefa** | `USER#<userId>` | `LIST#<listId>#TASK#<taskId>` | - | - | `taskDescription`, `isComplete` |

* **GSI1 (Global Secondary Index):**
    * **GSI1PK:** Um valor estático (`LISTS`) para todos os itens do tipo "Lista".
    * **GSI1SK:** Metadados como o ID ou a data de criação.
    * **Objetivo:** Permitir a consulta `Query(GSI1PK = "LISTS")` para obter todas as listas de forma eficiente, atendendo ao requisito de "NÃO UTILIZAR SCAN".

## 4\. Roadmap de Desenvolvimento

Este é o plano de tarefas para a conclusão do projeto.

- [x] **Spike Terraform:** Estudo dos comandos e armazenamento de estado no S3.
- [x] **Spike Lambda via Terraform:** Criação de uma função Lambda "Hello World" com deploy via Terraform.
- [x] **Criar DynamoDB via Terraform:** Criação da tabela `TodoList` com `PK` e `SK`.
- [x] **Subir ApiGateway via terraform (REST API V1):** Implantação da API Gateway base.
- [x] **Estória: Cadastrar uma lista (`POST /lists`):**
    - [x] Infraestrutura do endpoint criada.
    - [ ] Implementar a lógica na função Lambda.
- [ ] **Estória: Listar todas as listas (`GET /lists`):**
    - [ ] Adicionar a rota `GET /lists` no Terraform.
    - [ ] Adicionar o GSI na tabela DynamoDB via Terraform.
    - [ ] Implementar a lógica de `Query` no GSI na função Lambda.
- [ ] **Estória: Editar uma lista (`PUT /lists/{listId}`):**
    - [ ] Adicionar a rota `PUT /lists/{listId}` no Terraform.
    - [ ] Implementar a lógica de `UpdateItem` na função Lambda.
- [ ] **Refatoração:** Converter recursos para Módulos Terraform reutilizáveis.

## 5\. Guia de Instalação e Deploy

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
    terraform plan
    terraform apply
    ```

## 6\. Uso da API (Endpoints Planejados)

A URL base da API será fornecida na saída do `terraform apply`.

#### Criar uma Lista

* **Endpoint:** `POST /v1/lists`
* **Descrição:** Cria uma nova lista de tarefas.
* **Corpo da Requisição:**
  ```json
  {
      "name": "Compras do Supermercado"
  }
  ```
* **Resposta de Sucesso (Exemplo):**
  ```json
  {
      "listId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "listName": "Compras do Supermercado",
      "createdAt": "2025-09-29T18:00:00Z"
  }
  ```

#### Listar todas as Listas

* **Endpoint:** `GET /v1/lists`
* **Descrição:** Retorna todas as listas de tarefas cadastradas.

#### Editar uma Lista

* **Endpoint:** `PUT /v1/lists/{listId}`
* **Descrição:** Atualiza o nome de uma lista de tarefas existente.
* **Corpo da Requisição:**
  ```json
  {
      "name": "Compras de Sábado"
  }
  ```

## 7\. Limpeza

Para remover toda a infraestrutura da sua conta da AWS e evitar custos, execute:

```bash
cd infra
terraform destroy
```