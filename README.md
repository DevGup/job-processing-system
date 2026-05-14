# ⚙️ Distributed Job Processing System

A production-oriented backend system built with **Spring Boot**, evolving from a simple synchronous API to a fully **fault-tolerant, Kafka-powered distributed job processing architecture** — built incrementally across three phases.

> Each phase was driven by a real limitation discovered in the previous one.

---

## 🗺️ Project Evolution

| Phase | Focus | Key Technology |
|---|---|---|
| Phase 1 | Synchronous job processing + REST API | Spring Boot, MySQL |
| Phase 2 | Async background processing + lifecycle tracking | Spring `@Async`, Spring Data JPA |
| Phase 3 | Fault-tolerant, resumable job processing | Apache Kafka, Docker |

---

## Architecture

### Phase 1 — Synchronous
```
Client → REST Controller → Job Service → MySQL
                                ↑
                         (blocks until done)
```

### Phase 2 — Asynchronous
```
Client → REST Controller → Job Service → MySQL
                                │
                          @Async Worker Thread
                          (non-blocking response)
```

### Phase 3 — Event-Driven + Fault-Tolerant
```
Client → REST API → Kafka Producer
                         │
                   Kafka Topic (job-events)
                         │
                   Kafka Consumer (Worker)
                         │
                  ┌──────┴──────┐
                  ▼             ▼
               MySQL        On Failure:
            (status update)  Job offset NOT committed
                             → resumes on restart
```

---

## Job Lifecycle

```
PENDING ──► PROCESSING ──► DONE
                │
                └──────────► FAILED
```

| Status | Meaning |
|---|---|
| `PENDING` | Job created, waiting to be picked up |
| `PROCESSING` | Worker is actively executing the job |
| `DONE` | Job completed successfully |
| `FAILED` | Job failed — will resume on server restart (Phase 3) |

---

## Phase 1 — Synchronous Job Processing

### What was built
- REST API to create, read, update, and delete jobs
- Jobs processed **synchronously** — the HTTP thread waits until the job finishes
- MySQL persistence via Spring Data JPA

### The limitation
Synchronous processing blocks the request thread during long-running tasks. Under load, threads pile up and the API becomes unresponsive.

**→ This pushed toward async processing in Phase 2.**

### API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/jobs` | Create a new job |
| `GET` | `/jobs/{id}` | Get job by ID |
| `PUT` | `/jobs/{id}/status` | Update job status |
| `DELETE` | `/jobs/{id}` | Delete a job |

#### Create a Job
```http
POST /jobs
Content-Type: application/json

{
  "type": "generate_report",
  "userId": 42
}
```

**Response** `201 Created`
```json
{
  "id": 1,
  "type": "generate_report",
  "userId": 42,
  "status": "PENDING"
}
```

---

## Phase 2 — Async Background Processing

### What was built
- Background job execution using Spring `@Async`
- Non-blocking API — responds immediately while job runs in background
- Full lifecycle tracking: `PENDING → PROCESSING → DONE / FAILED`
- Structured logging for end-to-end job visibility

### How it works

```java
@Async
public void processJob(Job job) {
    try {
        job.setStatus(JobStatus.PROCESSING);
        jobRepository.save(job);

        // Simulate long-running work
        Thread.sleep(10000);

        job.setStatus(JobStatus.DONE);
    } catch (Exception e) {
        job.setStatus(JobStatus.FAILED);
        log.error("Job {} failed: {}", job.getId(), e.getMessage());
    } finally {
        jobRepository.save(job);
    }
}
```

The API returns `PENDING` immediately. Clients poll `GET /jobs/{id}` to track progress.

### The limitation
In-memory async has no durability. If the server crashes while a job is `PROCESSING`, that job is lost — there's no way to recover or resume it.

**→ This pushed toward Kafka-based durable processing in Phase 3.**

---

## Phase 3 — Kafka-Based Fault-Tolerant Processing

### What was built
- Jobs published as **Kafka events** on creation
- Kafka consumer picks up and processes jobs
- **Crash recovery**: if the server crashes mid-processing, the Kafka offset is not committed — so the job **automatically resumes from where it left off** on the next server start
- Docker Compose setup for Kafka + Zookeeper + MySQL

### Why Kafka solves the crash problem

With `@Async`, once a job is pulled off the thread pool, there's no record of it outside the JVM. A crash = silent data loss.

With Kafka:
- The job event sits in the Kafka topic until the consumer **explicitly commits the offset**
- If the server crashes before committing, Kafka re-delivers the message on restart
- The job resumes automatically — no manual intervention needed

### Kafka Configuration

```java
@KafkaListener(topics = "job-events", groupId = "job-processor")
public void consumeJob(JobEvent event) {
    Job job = jobRepository.findById(event.getJobId()).orElseThrow();

    try {
        job.setStatus(JobStatus.PROCESSING);
        jobRepository.save(job);

        // Process the job
        processJobLogic(job);

        job.setStatus(JobStatus.DONE);
        jobRepository.save(job);

        // Offset committed ONLY after successful processing
    } catch (Exception e) {
        job.setStatus(JobStatus.FAILED);
        jobRepository.save(job);
        log.error("Job {} failed, will retry on restart: {}", job.getId(), e.getMessage());
        throw e; // prevents offset commit → Kafka re-delivers on restart
    }
}
```

### Key Kafka settings

```properties
# Disable auto-commit — control offset manually
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.listener.ack-mode=record

# Consumer group — ensures each job is processed once
spring.kafka.consumer.group-id=job-processor
```

### Docker Compose

```yaml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    depends_on: [zookeeper]
    ports:
      - "9092:9092"
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  mysql:
    image: mysql:8
    environment:
      MYSQL_DATABASE: job_processing_db
      MYSQL_ROOT_PASSWORD: root
    ports:
      - "3306:3306"
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17+ |
| Framework | Spring Boot |
| Async (Phase 2) | Spring `@Async` + `ThreadPoolTaskExecutor` |
| Messaging (Phase 3) | Apache Kafka |
| Persistence | Spring Data JPA / Hibernate |
| Database | MySQL |
| Containerization | Docker + Docker Compose |
| Build | Maven |
| Logging | SLF4J |
| Boilerplate | Lombok |

---

## Logging

Every phase emits structured logs for full observability:

```
INFO  - Job 1 created with status PENDING
INFO  - Job 1 event published to Kafka topic: job-events
INFO  - Job 1 picked up by Kafka consumer
INFO  - Job 1 status updated to PROCESSING
INFO  - Job 1 completed successfully — duration: 10.4s
INFO  - Kafka offset committed for Job 1
```

On failure/crash recovery:
```
INFO  - Server restarted — resuming uncommitted Kafka offsets
INFO  - Job 3 re-delivered by Kafka (was PROCESSING before crash)
INFO  - Job 3 status updated to PROCESSING
INFO  - Job 3 completed successfully
```

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/jobprocessor/
│   │   ├── controller/        # REST endpoints
│   │   ├── service/           # Job logic, @Async methods, Kafka producer
│   │   ├── consumer/          # Kafka consumer / job worker
│   │   ├── repository/        # Spring Data JPA repositories
│   │   ├── model/             # Job entity, JobStatus enum, JobEvent
│   │   └── config/            # Kafka config, Async executor config
│   └── resources/
│       └── application.properties
├── docker-compose.yml
└── pom.xml
```

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker + Docker Compose

### Run with Docker

```bash
git clone https://github.com/your-username/distributed-job-processing.git
cd distributed-job-processing

# Start Kafka + Zookeeper + MySQL
docker-compose up -d

# Run the application
mvn spring-boot:run
```

API available at: `http://localhost:8080`

### Test Crash Recovery

```bash
# 1. Create a job
curl -X POST http://localhost:8080/jobs \
  -H "Content-Type: application/json" \
  -d '{"type": "generate_report", "userId": 1}'

# 2. Kill the server while job is PROCESSING

# 3. Restart the server — job resumes automatically
mvn spring-boot:run
```

---

## Key Engineering Decisions

| Decision | Reasoning |
|---|---|
| Start with synchronous (Phase 1) | Simplest working system; establishes the baseline |
| Move to `@Async` (Phase 2) | Decouples request handling from job execution without operational overhead |
| Move to Kafka (Phase 3) | `@Async` has no durability; Kafka's offset model gives crash recovery for free |
| Manual offset commit | Auto-commit would mark jobs done before they finish — defeats fault tolerance |
| Docker Compose | Reproducible local environment; mirrors production deployment topology |

---

## Roadmap

- [ ] Dead-letter topic for permanently failed jobs
- [ ] Job retry with exponential backoff
- [ ] Job priority queue
- [ ] Separate worker microservice
- [ ] Prometheus metrics + Grafana dashboard
- [ ] Kubernetes deployment

---

## License

[MIT](LICENSE)
