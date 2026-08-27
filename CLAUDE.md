# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.3.6 / Java 17 learning-and-demo project (`my-spring-ai`) that mixes several real-world integrations. It is not a single coherent product — treat it as a collection of independent modules:

- **预警处理 (Alarm processing)** — the most developed module: receives alerts from Hikvision ISC over HTTP, persists them, and fans out notifications/SMS. See "Alarm Module" below.
- **Dify 知识库集成** — uploads documents to a Dify knowledge base and tracks upload status.
- **跨切面关注点演示** — a demo stack of `Filter → Interceptor → AOP` logging/auth (not real auth).
- **杂项 demo/test 类** — scattered experiments under `test/`, `paicha/` (OOM/deadlock/CPU tests), `tools/`, `excel/`.

## Build & Run

```bash
mvn compile          # compile main sources
mvn test-compile     # compile main + test
mvn spring-boot:run  # start the app on port 9999
```

Run the alarm load-test tool (no Spring context needed, plain `main`):

```bash
mvn test-compile -q
java -cp "target/test-classes;target/classes" com.example.myspringai.bench.AlarmLoadTest --qps=1000 --duration=60
```

### Known issue: project does not compile cleanly

`mvn compile` currently fails on `src/main/java/com/example/myspringai/controller/DifyDocumentController.java` (a pre-existing switch-expression / source-encoding problem, unrelated to the alarm module). The alarm module itself compiles. When verifying alarm changes, filter errors to files other than `DifyDocumentController.java`.

## Runtime Dependencies

All connection settings are env-var driven with localhost defaults (`application.yaml`):

- **MySQL** (`test` database) — required by the alarm module and gun-info module
- **RabbitMQ** — required by the alarm module (`spring-boot-starter-amqp`)
- **Redis** — no longer used by the alarm module; `spring-boot-starter-data-redis` was removed from the alarm path
- **DashScope / 通义千问** (`OPENAI_API_KEY` env) — for Spring AI chat

## Alarm Module (预警处理) — Architecture

This is the part most likely to be worked on. Current design is a **hybrid: DB as source of truth + MQ only for urgent events + scheduled-task batch fallback**.

Flow:

```
Hikvision ISC → POST /api/hikvision/alarm → AlertController
    → AlertServiceImpl.handleAlarm()
        → INSERT alert_info (idempotent via unique index on alert_id)
        → Level 3: publish AlarmEvent to RabbitMQ (fast path)
        → Level 1/2: no MQ, handled entirely by scheduled tasks
    → returns "success" / "duplicate" (~5ms)
```

Downstream:

- **MQ consumers** (`consumer/AlertNotifyConsumer`, `AlertSmsConsumer`) — only handle Level 3, with **MANUAL ACK**: send → `basicAck` → mark DB `SUCCESS`. Failure throws → `RetryOperationsInterceptor` retries 3× → DLQ.
- **Scheduled tasks** (`service/AlertNotifyService`, `AlertSmsService`) — scan `alert_info` for `notify_status`/`sms_status = PENDING` older than a window, batch-send Level 1/2, and fallback-recover Level 3 that never got sent.

Key files:

| File | Role |
|------|------|
| `config/RabbitMQConfig.java` | 1 topic exchange + 2 queues (notify, sms) + DLX/DLQ + retry interceptors + MANUAL-ACK listener factories |
| `service/impl/AlertServiceImpl.java` | Entry: insert + conditional MQ publish |
| `consumer/AlertNotifyConsumer.java`, `consumer/AlertSmsConsumer.java` | Level 3 MANUAL-ACK consumers |
| `service/AlertNotifyService.java`, `AlertSmsService.java` | DB-driven batch + fallback |
| `domain/AlertInfo.java` | Entity (`alert_info`), `deviceName` auto-encrypted |
| `jiami/CryptoTypeHandler.java` + `AesUtil.java` | AES-256-GCM transparent field encryption (MyBatis type handler) |

### Reliability invariants (do not regress)

- **Zero message loss** relies on `alert_info` being the single source of truth: once an alert is INSERTed with `PENDING` status, the scheduled tasks guarantee eventual send. There is deliberately **no in-memory buffering** in the send path (previous in-memory batch buffers were removed to avoid crash loss).
- **Idempotency** is via the `alert_id` unique index + `DuplicateKeyException`, not Redis.
- The **DDL for `alert_info`** (columns `alert_level`, `receive_time`, `notify_status`, `sms_status`, unique index `uk_alert_id`) is not checked into the repo — if the table doesn't match `AlertInfo.java`, inserts will fail.

## Persistence Stack

- **MyBatis-Plus** (`mybatis-plus-spring-boot3-starter`) is the primary ORM; mappers are in `mapper/`, `@MapperScan("com.example.myspringai.mapper")` is on the main class.
- `dynamic-datasource-spring-boot-starter` and `spring-boot-starter-data-jpa` are present in `pom.xml` but the datasource config in `application.yaml` is a single plain `spring.datasource` (not the dynamic `@DS` style). Do not assume multi-datasource is actually wired up.
- `pom.xml` has duplicate `spring-boot-starter-web` declarations and other demo leftovers — treat dependencies as illustrative, not authoritative.
