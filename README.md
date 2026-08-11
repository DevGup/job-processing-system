# 🚀 Job Processing System

A fault-tolerant asynchronous job processing system built with **Java, Spring Boot, MySQL, Apache Kafka, Docker, Prometheus, and Grafana**.

## ✨ Features

- 🌐 REST API for job management
- ⚡ Asynchronous processing with Kafka
- 🔀 Kafka partitions & concurrent consumers
- 🔁 Retry mechanism & Dead Letter Queue (DLQ)
- ♻️ Crash recovery
- 🗄️ MySQL persistence
- 📊 Prometheus & Grafana monitoring
- 🐳 Fully Dockerized

## 🏗️ Architecture

```text
                  Client
                    |
                    v
              Spring Boot
               /        \
              v          v
           MySQL       Kafka
                         |
                         v
                    Job Worker
                    /        \
                 Success    Failure
                              |
                         Retry / DLQ

       Spring Boot ---> Prometheus ---> Grafana


🛠️ Tech Stack
Java 21 • Spring Boot • MySQL • Kafka • Docker • Prometheus • Grafana

🚀 Run Locally
Requirements
Git
Docker Desktop

No need to install Java, Maven, MySQL, or Kafka separately.

Start the Application
git clone https://github.com/DevGup/job-processing-system.git
cd job-processing-system
docker compose up -d --build

🌐 Services
Service	URL
🚀 API	http://localhost:8080
🗄️ MySQL	localhost:3307
📊 Prometheus	http://localhost:9090
📈 Grafana	http://localhost:3000

🔌 API Endpoints

| Method   | Endpoint               | Description       |
| -------- | ---------------------- | ----------------- |
| `POST`   | `/jobs`                | Create a job      |
| `GET`    | `/jobs`                | Get all jobs      |
| `GET`    | `/jobs/{id}`           | Get a job         |
| `GET`    | `/jobs/failed`         | Get failed jobs   |
| `PUT`    | `/jobs/{id}/status`    | Update job status |
| `DELETE` | `/jobs/{id}`           | Delete a job      |
| `POST`   | `/jobs/retry-dlq/{id}` | Retry DLQ job     |


👨‍💻 Author
Dev Gupta
⭐ If you find this project useful, consider starring the repository.
