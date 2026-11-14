# Job Processing System

An asynchronous job API built with Spring Boot, MySQL, Kafka, Prometheus, and Grafana. The Compose stack uses Linux containers for x86-64 and ARM64 hosts, including Apple Silicon.

## What it does

- Creates and persists jobs through a REST API.
- Dispatches pending jobs through Kafka after the database transaction commits.
- Claims jobs under a database lock, so duplicate Kafka messages do not execute a job twice.
- Retries failed jobs up to a configurable limit, then records the job as failed and sends its ID to a dead-letter topic.
- Recovers pending jobs and expired processing leases after a restart.
- Exposes health and Prometheus metrics.

`type: "fail"` is a deliberate test mode: it exercises retry and DLQ behavior. Any other non-blank type completes successfully.

## Requirements

- Docker Desktop running in Linux container mode (Windows/macOS), or Docker Engine with the Docker Compose plugin (Linux)
- At least 4 GB of memory available to Docker
- Internet access on first startup to download service images. Building from source also downloads Maven dependencies.

No host installation of Java, Maven, MySQL, Kafka, Prometheus, or Grafana is required.

## Deploy on another computer

Extract `job-scheduler-deploy.zip` and open a terminal in the extracted `job-scheduler` folder. Start Docker, then load the scheduler image and start the full stack.

```sh
docker image load --input job-scheduler-image.tar
docker compose up --no-build --wait --wait-timeout 300
```

The archive contains the scheduler image for `linux/amd64` and `linux/arm64`. Compose starts MySQL, Kafka, ZooKeeper, Prometheus, and Grafana alongside it and downloads their images when needed.

## Build and run from source

Open a terminal in the project folder containing `Dockerfile` and `docker-compose.yml`. Docker builds the scheduler for the host architecture and runs the Maven tests as part of the build.

```sh
docker compose up --build --wait --wait-timeout 300
```

Before either startup command, you can copy `.env.example` to `.env` to set the database passwords and choose free host ports. If a port is already allocated, set `JOB_API_PORT`, `PROMETHEUS_PORT`, or `GRAFANA_PORT` in `.env` and run the startup command again.

Check the stack and its API in a browser or terminal.

```sh
docker compose ps
curl http://localhost:8080/actuator/health
```

For startup errors, run `docker compose logs --tail 100`. Stop the stack with `docker compose down`. The named volumes retain MySQL, Kafka, ZooKeeper, Prometheus, and Grafana data. `docker compose down -v` permanently deletes the Compose-managed volumes.

## CI/CD with GitHub Actions

The [workflow](.github/workflows/ci-cd.yml) runs on pull requests targeting `main` and pushes to `main`. A GitHub-hosted runner uses Java 21 to run Maven tests, starts all six services with Compose, then submits an `email` job and checks that its status reaches `DONE`. Check the repository's **Actions** tab for each step's output. If a check fails, the workflow skips publishing and collects any available container logs.

On a successful push to `main`, the workflow publishes a Linux x86-64 and ARM64 image to GitHub Container Registry, tagged with the full Git commit SHA. New packages are private by default. For a private package, [sign in to `ghcr.io`](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry#authenticating-with-a-personal-access-token-classic) on the deployment machine with a token that has `read:packages` access.

To deploy a published build, set `JOB_SCHEDULER_IMAGE=ghcr.io/devgup/job-processing-system:<commit-sha>` in the target machine's `.env`, replacing `<commit-sha>` with the SHA from the workflow run. From the project directory on that machine, run:

```sh
docker compose pull springboot
docker compose up --no-build --wait --wait-timeout 300
```

Image publication is the delivery step. Updating a running stack is a separate deployment step. To roll back to an earlier published build, set `JOB_SCHEDULER_IMAGE` to its tag and run the same commands.

### Keep data from an earlier Compose setup

Run the updated Compose file with the same project name as your previous deployment. Before recreating the old ZooKeeper container, find the names of the volumes it already uses.

```sh
docker compose ps -a -q zookeeper
docker inspect --format '{{json .Mounts}}' ZOOKEEPER_CONTAINER_ID
```

Use the volume `Name` for `/var/lib/zookeeper/data` as `ZOOKEEPER_DATA_VOLUME` and the one for `/var/lib/zookeeper/log` as `ZOOKEEPER_LOG_VOLUME` in `.env`. Set `ZOOKEEPER_VOLUMES_EXTERNAL=true` so Compose reuses those volumes with the existing Kafka data.

MySQL only applies its user and password settings when it initializes an empty volume. For an existing volume, use the database and application credentials already provisioned in MySQL. If the older setup only created a root account, create a dedicated MySQL user with access to the existing database before starting the updated stack.

## Services

| Service | Address |
| --- | --- |
| Job API | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| Prometheus metrics | http://localhost:8080/actuator/prometheus |
| Prometheus UI | http://localhost:9090 |
| Grafana UI | http://localhost:3000 |

Kafka, ZooKeeper, and MySQL remain internal to Docker. This prevents host-port conflicts and avoids exposing development credentials.

## API examples

Create a job:

```sh
curl -i -X POST http://localhost:8080/jobs \
  -H "Content-Type: application/json" \
  -d '{"type":"email","userId":1}'
```

The response is `201 Created`; the `Location` header contains the job URL. Check a job:

```sh
curl http://localhost:8080/jobs/1
```

Create a job that will reach the DLQ after retries:

```sh
curl -X POST http://localhost:8080/jobs \
  -H "Content-Type: application/json" \
  -d '{"type":"fail","userId":1}'
```

List failed jobs and resubmit one:

```sh
curl http://localhost:8080/jobs/failed
curl -X POST http://localhost:8080/jobs/retry-dlq/2
```

The status endpoint is an administrative override. It accepts only valid state transitions and should not be used as the normal job-processing path.

## Configuration

The defaults are suitable for the included Compose stack. Override values in `.env` for Compose or with environment variables when running the JAR elsewhere.

| Variable | Default | Purpose |
| --- | --- | --- |
| `MYSQL_DATABASE` | `jobdb` | MySQL database name |
| `MYSQL_USER` | `jobapp` | Application database user |
| `MYSQL_PASSWORD` | `jobapp-local-password` | Application database password |
| `MYSQL_ROOT_PASSWORD` | `local-root-password` | Local MySQL root password |
| `APP_KAFKA_CONCURRENCY` | `3` | Maximum parallel Kafka consumers |
| `JOB_API_PORT` | `8080` | Host port for the job API |
| `PROMETHEUS_PORT` | `9090` | Host port for Prometheus |
| `GRAFANA_PORT` | `3000` | Host port for Grafana |
| `APP_JOBS_MAX_RETRIES` | `3` | Retry attempts before a job is marked failed |
| `APP_JOBS_PROCESSING_TIMEOUT_SECONDS` | `300` | Lease duration before an abandoned processing job is requeued |

For a non-Docker deployment, set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and `SPRING_KAFKA_BOOTSTRAP_SERVERS` to reachable services.

## Test and build without Docker

The Maven Wrapper works on Windows, macOS, and Linux when Java 21 is installed:

```sh
# macOS/Linux
./mvnw test

# Windows PowerShell
.\mvnw.cmd test
```

The Docker image runs the same test suite during its build.
