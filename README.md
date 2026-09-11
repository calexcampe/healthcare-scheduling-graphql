# Backend Hospitalar

Backend hospitalar para gerenciamento de consultas, histórico de pacientes e notificações, com autenticação/autorização (RBAC), GraphQL e comunicação assíncrona via RabbitMQ.

## Objetivo

Permitir que médicos, enfermeiros e pacientes interajam com um sistema de agendamento de consultas, cada um com permissões distintas, e que o paciente receba um lembrete assíncrono sempre que uma consulta for criada ou alterada.

## Arquitetura

```
Cliente
   |
   v
Spring Security (HTTP Basic + BCrypt)
   |
   v
Scheduling Service (GraphQL) --- H2 (banco em memoria)
   |
   | publica AppointmentChangedEvent
   v
RabbitMQ (exchange "appointments.exchange")
   |
   v
Notification Service (consumidor) --- loga/simula envio de lembrete
```

Dois serviços Spring Boot **independentes** (processos e JARs separados), sem biblioteca compartilhada — o contrato entre eles é apenas o formato JSON do evento publicado no RabbitMQ.

### Serviços

- **scheduling-service** (porta `8081`): autenticação/autorização, GraphQL (consulta e edição de consultas), persistência em H2, publica eventos de consulta criada/alterada.
- **notification-service** (porta `8082`): consome os eventos do RabbitMQ e simula o envio de um lembrete ao paciente (log estruturado). Não expõe HTTP.

Um serviço de histórico separado não foi criado: o histórico é apenas uma query GraphQL sobre a mesma tabela de consultas do scheduling-service, e um serviço adicional só para isso adicionaria complexidade sem necessidade real.

## Tecnologias

- Java 21
- Spring Boot 3.3.5
- Spring Security (HTTP Basic, `BCryptPasswordEncoder`, RBAC via `@PreAuthorize`)
- Spring Data JPA + H2 (em memória)
- Spring for GraphQL
- Spring AMQP (RabbitMQ)
- Maven (multi-módulo)
- Docker Compose (RabbitMQ)
- JUnit 5, Mockito, AssertJ, `spring-graphql-test`

## Estrutura do projeto

```
tech-challenge-fase3-parent (pom pai)
├── scheduling-service
│   └── src/main/java/.../scheduling
│       ├── config          (Security, RabbitMQ, seed de dados)
│       ├── domain           (User, Patient, Appointment + enums)
│       ├── repository        (Spring Data JPA)
│       ├── security          (UserDetailsService, principal customizado)
│       ├── service            (regras de negocio e autorizacao)
│       ├── graphql             (resolvers, DTOs, tratamento de erros)
│       └── messaging            (producer, evento)
└── notification-service
    └── src/main/java/.../notification
        ├── config           (RabbitMQ)
        ├── event             (contrato do evento)
        ├── listener           (consumidor)
        └── service             (simulacao do envio)
```

Camadas seguem `graphql/controller -> service -> repository -> domain`; regra de negócio e autorização ficam no `service`, nunca no resolver GraphQL.

## Requisitos para execução

- JDK 21
- Maven 3.9+
- Docker (para subir o RabbitMQ) — ou uma instância RabbitMQ acessível em `localhost:5672`

## Como subir o RabbitMQ

```bash
docker compose up -d
```

Painel de administração do RabbitMQ: http://localhost:15672 (usuário/senha: `guest`/`guest`).

## Como iniciar cada aplicação

Em dois terminais separados, a partir da raiz do projeto:

```bash
cd scheduling-service
mvn spring-boot:run
```

```bash
cd notification-service
mvn spring-boot:run
```

Ou, após `mvn package` na raiz, execute os JARs gerados:

```bash
java -jar scheduling-service/target/scheduling-service.jar
java -jar notification-service/target/notification-service.jar
```

- GraphiQL (IDE de testes GraphQL): http://localhost:8081/graphiql
- Console H2: http://localhost:8081/h2-console (JDBC URL `jdbc:h2:mem:scheduling`, usuário `sa`, sem senha)

## Usuários de teste

Criados automaticamente na inicialização do scheduling-service (`DataSeeder`):

| Usuário       | Senha       | Role        | Paciente vinculado |
|---------------|-------------|-------------|---------------------|
| `medico1`     | `senha123`  | MEDICO      | -                   |
| `enfermeiro1` | `senha123`  | ENFERMEIRO  | -                   |
| `paciente1`   | `senha123`  | PACIENTE    | Joao Silva (id 1)   |
| `paciente2`   | `senha123`  | PACIENTE    | Maria Souza (id 2)  |

## Roles e permissões (RBAC)

| Ação                                   | MEDICO | ENFERMEIRO | PACIENTE (apenas o próprio) |
|-----------------------------------------|:------:|:----------:|:----------------------------:|
| `patientHistory` / `futureAppointments` |   ✅   |     ✅     |              ✅               |
| `createAppointment`                     |   ✅   |     ✅     |              ❌               |
| `updateAppointment`                     |   ✅   |     ✅     |              ❌               |

A restrição "paciente só vê os próprios dados" é aplicada na **camada de serviço** (`AppointmentService`), comparando o `patientId` da query com o paciente vinculado ao usuário autenticado — não depende apenas do que o cliente envia na requisição, então trocar o `patientId` na query não dá acesso a dados de outro paciente.

## Exemplos de queries e mutations GraphQL

Autenticação: HTTP Basic (header `Authorization: Basic base64(usuario:senha)`).

**Histórico completo de um paciente:**
```graphql
query {
  patientHistory(patientId: 1) {
    id
    professionalName
    dateTime
    description
    status
  }
}
```

**Consultas futuras:**
```graphql
query {
  futureAppointments(patientId: 1) {
    id
    professionalName
    dateTime
    status
  }
}
```

**Registrar consulta (usuário `medico1` ou `enfermeiro1`):**
```graphql
mutation {
  createAppointment(input: {
    patientId: 1
    professionalName: "Dr. Carlos Lima"
    dateTime: "2026-12-01T09:00:00"
    description: "Consulta de retorno"
  }) {
    id
    status
  }
}
```

**Editar consulta (usuário `medico1` ou `enfermeiro1`):**
```graphql
mutation {
  updateAppointment(id: 1, input: {
    patientId: 1
    status: REALIZADA
    description: "Consulta concluida"
  }) {
    id
    status
  }
}
```

Tentativas fora do papel permitido (ex.: `paciente1` chamando `createAppointment` ou `updateAppointment`) retornam um erro GraphQL do tipo `FORBIDDEN`.

## Fluxo de mensageria

1. `createAppointment`/`updateAppointment` persistem a consulta e, em seguida, `AppointmentEventProducer` publica um `AppointmentChangedEvent` (JSON) na exchange `appointments.exchange` (tipo topic) com routing key `appointment.changed`.
2. O evento carrega apenas os dados necessários para a notificação (`appointmentId`, `patientId`, `patientEmail`, `patientName`, `appointmentDateTime`, `eventType`) — nunca a entidade JPA.
3. O `notification-service` declara a fila `appointment.notifications.queue`, faz o bind na mesma exchange/routing key, e o `AppointmentEventListener` consome cada mensagem de forma assíncrona e desacoplada do scheduling-service.
4. `NotificationSenderService` simula o envio do lembrete (log estruturado); a integração real com um provedor de e-mail/SMS está fora do escopo deste desafio.

## Decisões arquiteturais relevantes

- **HTTP Basic em vez de JWT**: atende ao requisito de autenticação/autorização com Spring Security sem adicionar a complexidade de emissão/renovação de tokens, que não foi pedida.
- **RBAC alinhado ao enunciado**: `createAppointment` e `updateAppointment` são permitidos para MEDICO e ENFERMEIRO, enquanto PACIENTE fica restrito à leitura das próprias consultas. A regra é aplicada via `@PreAuthorize` no `AppointmentService` (camada de negócio), não apenas no resolver.
- **`dateTime` como `String` no schema GraphQL**: evita a necessidade de um scalar customizado de data/hora para o escopo deste desafio; o valor trafega em ISO-8601 (`yyyy-MM-ddTHH:mm:ss`).
- **DTO de evento duplicado entre os dois serviços** (sem biblioteca compartilhada): mantém os serviços deployáveis e evoluíveis de forma independente, ao custo de manter os dois DTOs sincronizados manualmente caso o contrato mude.
- **Sem serviço de histórico separado**: o histórico é uma query sobre a mesma base de consultas; um terceiro serviço só para isso adicionaria complexidade sem necessidade.
- **Testes de mensageria em nível de unidade**: o produtor é testado verificando a chamada ao `RabbitTemplate` (mock) e o consumidor testado chamando o listener diretamente (mock do serviço de envio), sem depender de um broker real durante o build.

## Testes

```bash
mvn test
```

Cobrem:
- Autorização de leitura (paciente só acessa o próprio histórico; médico/enfermeiro acessam qualquer paciente).
- Criação de consulta (regra de negócio + publicação do evento `CREATED`).
- Alteração de consulta (regra de negócio + publicação do evento `UPDATED`).
- RBAC de ponta a ponta via GraphQL (HTTP + Security + resolver), incluindo requisição não autenticada (401), médico/enfermeiro criando e editando consultas, e mutação fora do papel permitido para paciente (erro `FORBIDDEN`).
- Publicação do evento pelo produtor (verifica exchange/routing key/payload).
- Processamento do evento pelo consumidor (listener delega corretamente ao serviço de notificação).

## Collection de testes

Ver [`postman/tech-challenge-fase3.postman_collection.json`](postman/tech-challenge-fase3.postman_collection.json): inclui autenticação, histórico, consultas futuras, criação, edição e tentativas de acesso sem permissão (RBAC).

## Auditoria de requisitos

| Requisito | Implementado | Onde |
|---|:---:|---|
| Autenticação com Spring Security | ✅ | `scheduling-service/.../config/SecurityConfig.java`, `security/CustomUserDetailsService.java` |
| RBAC (MEDICO/ENFERMEIRO/PACIENTE) | ✅ | `@PreAuthorize` em `service/AppointmentService.java`; roles em `domain/Role.java` |
| PasswordEncoder (BCrypt) | ✅ | `config/SecurityConfig.java#passwordEncoder` |
| Autorização também na camada de negócio (não só na URL) | ✅ | `service/AppointmentService.java#assertCanReadPatient` e `@PreAuthorize` nos métodos |
| Paciente não acessa dados de outro paciente trocando o ID | ✅ | `service/AppointmentService.java#assertCanReadPatient` + teste `AppointmentServiceTest.patientCannotReadAnotherPatientHistory` e `AppointmentGraphQLIntegrationTest.patientCannotReadAnotherPatientHistory` |
| GraphQL para histórico e consultas futuras | ✅ | `graphql/schema.graphqls`, `graphql/AppointmentGraphQLController.java` |
| Mutations de criação e edição de consulta | ✅ | `createAppointment`/`updateAppointment` em `AppointmentGraphQLController.java` + `AppointmentService.java` |
| Dois serviços independentes | ✅ | módulos Maven `scheduling-service` e `notification-service`, cada um com seu próprio `main` e JAR |
| Publicação de evento ao criar/alterar consulta | ✅ | `messaging/AppointmentEventProducer.java`, chamado em `AppointmentService.createAppointment/updateAppointment` |
| Consumo desacoplado do evento | ✅ | `notification-service/.../listener/AppointmentEventListener.java` |
| Simulação de envio de lembrete | ✅ | `notification-service/.../service/NotificationSenderService.java` |
| Comunicação assíncrona via RabbitMQ | ✅ | `docker-compose.yml`, `config/RabbitMQConfig.java` em ambos os serviços |
| Evento não expõe a entidade JPA | ✅ | `messaging/event/AppointmentChangedEvent.java` (record dedicado, sem referência a `Appointment`) |
| H2 para dev/teste | ✅ | `scheduling-service/src/main/resources/application.yml` |
| Maven | ✅ | `pom.xml` (multi-módulo) |
| Docker Compose para RabbitMQ | ✅ | `docker-compose.yml` |
| Separação producer/consumer/event/config na mensageria | ✅ | pacotes `messaging` (scheduling) e `config`/`event`/`listener` (notification) |
| Sem regra de negócio em controller/resolver/repository | ✅ | resolvers e repositórios são finos; regras em `AppointmentService` |
| Testes de autorização | ✅ | `AppointmentServiceTest`, `AppointmentGraphQLIntegrationTest` |
| Teste de criação de consulta | ✅ | `AppointmentServiceTest.createAppointmentSavesAndPublishesCreatedEvent`, `AppointmentGraphQLIntegrationTest.enfermeiroCanCreateAppointment`, `AppointmentGraphQLIntegrationTest.medicoCanCreateAppointment` |
| Teste de alteração de consulta | ✅ | `AppointmentServiceTest.updateAppointmentSavesAndPublishesUpdatedEvent`, `AppointmentGraphQLIntegrationTest.medicoCanUpdateAppointment`, `AppointmentGraphQLIntegrationTest.enfermeiroCanUpdateAppointment` |
| Teste de publicação do evento | ✅ | `AppointmentEventProducerTest` |
| Teste de processamento do evento pelo consumidor | ✅ | `AppointmentEventListenerTest` |
| README com arquitetura, tecnologias, execução, usuários, exemplos, mensageria, decisões | ✅ | este arquivo |
| Diagrama de arquitetura | ✅ | seção "Arquitetura" acima |
| Collection de testes HTTP/GraphQL | ✅ | `postman/tech-challenge-fase3.postman_collection.json` |
