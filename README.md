# Job Processing System

A backend system built with **Spring Boot** that demonstrates **asynchronous job processing**, job lifecycle management, and REST API design.

This project simulates how real systems handle long-running tasks like **report generation, email sending, or background data processing**.

---

## Architecture

Client → REST API → Async Worker Thread → Database

1. Client sends a request to create a job
2. Job is stored in the database with status **PENDING**
3. A background worker picks up the job asynchronously
4. Job status transitions through different states until completion

---

## Job Lifecycle

Every job goes through a defined lifecycle:

PENDING → PROCESSING → DONE

| Status     | Meaning                                 |
| ---------- | --------------------------------------- |
| PENDING    | Job created and waiting to be processed |
| PROCESSING | Worker is currently processing the job  |
| DONE       | Job completed successfully              |

---

## Async Worker

The worker runs in a background thread using Spring Boot's **@Async** support.

Example:

```java
@Async
public void processJob(Job job) {
    Thread.sleep(2000);
    job.setStatus(JobStatus.PROCESSING);

    Thread.sleep(10000);
    job.setStatus(JobStatus.DONE);
}
```

This allows the API to **respond immediately** while the job continues processing in the background.

---

## REST API Endpoints

### Create Job

POST /jobs

Request Body

```json
{
  "type": "generate_report",
  "userId": 42
}
```

Response

```json
{
  "id": 1,
  "type": "generate_report",
  "userId": 42,
  "status": "PENDING"
}
```

---

### Get Job

GET /jobs/{id}

Returns the job details and current status.

---

### Update Job Status

PUT /jobs/{id}/status

Request

```json
{
  "status": "DONE"
}
```

---

### Delete Job

DELETE /jobs/{id}

Deletes a job from the system.

---

## Technologies Used

* Java
* Spring Boot
* Spring Data JPA
* MySQL / H2
* Maven
* SLF4J Logging

---

## Logging

The system logs the complete job lifecycle:

```
Job 1 created with status PENDING
Job 1 status updated to PROCESSING
Job 1 completed successfully
```

This helps debug asynchronous processing flows.

---

## Future Improvements

Planned upgrades to evolve this system into a distributed architecture:

* Kafka-based job queue
* Separate worker microservice
* Retry and failure handling
* Job priority support
* Monitoring and metrics

Target Architecture:

Client → API Service → Kafka → Worker Service → Database

---

## How to Run

Clone the repository:

```
git clone https://github.com/your-username/job-processing-system.git
```

Run the application:

```
mvn spring-boot:run
```

The API will start on:

```
http://localhost:8080
```

---

## Purpose of This Project

This project demonstrates key backend concepts:

* REST API design
* Async background processing
* Job lifecycle management
* Logging and observability
* Clean service architecture

It serves as a foundation for learning **distributed job processing systems** used in modern backend platforms.
