# Job Processing System 🚀

A fault-tolerant asynchronous job processing system built using **Spring Boot, MySQL, Apache Kafka, and Docker**.

This project simulates a real-world distributed job processing architecture with support for asynchronous execution, retries, recovery after application crashes, and parallel processing using Kafka partitions.

---

## 📌 Features

### Job Lifecycle Management

* Create Jobs via REST API
* Track Job Status
* Job States:

  * PENDING
  * PROCESSING
  * DONE
  * FAILED

### Asynchronous Processing

* Job requests are published to Kafka.
* API responds immediately without waiting for job completion.
* Background consumers process jobs asynchronously.

### Kafka Integration

* Kafka Producer
* Kafka Consumer
* Consumer Groups
* Topic Partitions
* Concurrent Processing

### Fault Tolerance

* Automatic Recovery on Application Restart
* Retry Mechanism
* Configurable Maximum Retry Limit
* Failure Handling

### Parallel Processing

* Topic configured with multiple partitions.
* Multiple Kafka consumer threads process jobs concurrently.

---

# 🏗️ Architecture

```text
                +------------------+
                |      User        |
                +--------+---------+
                         |
                         v
                +------------------+
                |  Spring Boot API |
                +--------+---------+
                         |
                         v
                +------------------+
                |   Kafka Topic    |
                |  job-requests    |
                +--------+---------+
                         |
                         v
                +------------------+
                |   Job Worker     |
                +--------+---------+
                         |
            +------------+-------------+
            |                          |
            v                          v
         SUCCESS                    FAILURE
            |                          |
            v                          v
          DONE                     RETRY
                                      |
                                      v
                                 MAX RETRIES
                                      |
                                      v
                                   FAILED
```

---

# 🔄 Job Processing Flow

## Successful Job

```text
PENDING
   ↓
PROCESSING
   ↓
DONE
```

## Failed Job

```text
PENDING
   ↓
PROCESSING
   ↓
FAILURE
   ↓
RETRY 1
   ↓
RETRY 2
   ↓
RETRY 3
   ↓
FAILED
```

---

# 🛠️ Tech Stack

* Java 21
* Spring Boot
* Spring Data JPA
* MySQL
* Apache Kafka
* Docker
* Lombok
* Maven

---

# 📂 Project Structure

```text
src/main/java
│
├── controller
│   └── JobController
│
├── entity
│   ├── Job
│   └── JobStatus
│
├── repo
│   └── JobRepository
│
├── service
│   ├── JobService
│   ├── JobWorker
│   └── JobRecovery
│
└── Phase1Application
```

---

# 🚀 API Endpoints

## Create Job

```http
POST /jobs
```

Request:

```json
{
  "type": "generate_report",
  "userId": 101
}
```

Response:

```json
{
  "id": 1,
  "status": "PENDING"
}
```

---

## Get Job

```http
GET /jobs/{id}
```

---

## Get All Jobs

```http
GET /jobs
```

---

# ⚙️ Kafka Configuration

Topic:

```text
job-requests
```

Partitions:

```text
3
```

Consumer Concurrency:

```properties
spring.kafka.listener.concurrency=3
```

---

# 🔁 Recovery Mechanism

When the application starts:

* All jobs with status:

  * PENDING
  * PROCESSING

are automatically detected and republished to Kafka for processing.

This ensures jobs are not lost during application crashes or restarts.

---

# 🧪 Example Test Results

| Job Type        | Status | Retry Count |
| --------------- | ------ | ----------- |
| generate_report | DONE   | 0           |
| fail            | FAILED | 3           |
| generate_report | DONE   | 0           |

---

# 📚 Key Learnings

* Apache Kafka Producer & Consumer
* Kafka Partitions
* Consumer Groups
* Parallel Processing
* Fault Tolerance
* Retry Strategies
* Recovery Patterns
* Distributed Systems Fundamentals
* Event-Driven Architecture

---

# 🔮 Future Enhancements

* Dead Letter Queue (DLQ)
* Exponential Backoff Retries
* Kafka Monitoring
* Prometheus Metrics
* Grafana Dashboards
* Email Notifications
* Kubernetes Deployment

---

# 👨‍💻 Author

Dev Gupta

GitHub:
https://github.com/DevGup

If you found this project interesting, feel free to star the repository ⭐
