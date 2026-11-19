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

Use the scheduler image published by GitHub Actions. The image supports Linux containers on x86-64 and ARM64. Docker Compose starts the scheduler, MySQL, Kafka, ZooKeeper, Prometheus, and Grafana together.

1. Start Docker with Linux containers on the target computer. See [Requirements](#requirements) and [Docker installation](https://docs.docker.com/get-started/get-docker/).
2. Choose a push to `main` whose **Publish to GitHub Container Registry** job succeeded and whose commit includes the authentication changes. Open that commit, copy its full SHA, and download the source ZIP for that same commit. Extract it and open a terminal in the folder containing `docker-compose.yml`. [Scheduler CI/CD runs](https://github.com/DevGup/job-processing-system/actions/workflows/ci-cd.yml)
3. Copy `.env.example` to `.env` in that folder. Set `JOB_API_USERNAME` and `JOB_API_PASSWORD` to your own login. Add the image setting below, replacing `FULL_COMMIT_SHA` with the SHA you copied.

   ```dotenv
   JOB_SCHEDULER_IMAGE=ghcr.io/devgup/job-processing-system:FULL_COMMIT_SHA
   ```

4. Pull the scheduler image and start the full stack.

   ```sh
   docker compose pull springboot
   docker compose up --no-build --wait --wait-timeout 300
   ```

5. Open [the API health endpoint](http://localhost:8080/actuator/health) on the target computer. A healthy scheduler returns `{"status":"UP"}`. Use `docker compose ps` to check all six services.

The first startup on a new computer creates an empty MySQL database. To bring over existing job records, restore a MySQL backup from the original machine.

### Deploy from the image archive

If you have `job-scheduler-deploy.zip`, extract it and open a terminal in the extracted `job-scheduler` folder. Use an archive built from a version that includes authentication to run the secured API. Set `JOB_API_USERNAME` and `JOB_API_PASSWORD` in a local `.env` next to its Compose file. Start Docker, then load the bundled image and start the stack.

```sh
docker image load --input job-scheduler-image.tar
docker compose up --no-build --wait --wait-timeout 300
```

The archive contains the scheduler image for `linux/amd64` and `linux/arm64`. Compose starts MySQL, Kafka, ZooKeeper, Prometheus, and Grafana alongside it and downloads their images when needed.

## Build and run from source

Open a terminal in the project folder containing `Dockerfile` and `docker-compose.yml`. On first setup, copy `.env.example` to `.env` and fill in `JOB_API_PASSWORD`. For an existing `.env`, add `JOB_API_USERNAME` and `JOB_API_PASSWORD` to that file. Docker builds the scheduler for the host architecture and runs the Maven tests as part of the build.

```sh
docker compose up --build --wait --wait-timeout 300
```

Before startup, you can add database passwords and host port settings to `.env`. See [`.env.example`](.env.example) for the available settings. If a port is already allocated, set `JOB_API_PORT`, `PROMETHEUS_PORT`, or `GRAFANA_PORT` in `.env` and run the startup command again.

Check the stack and its API in a browser or terminal.

```sh
docker compose ps
curl http://localhost:8080/actuator/health
```

For startup errors, run `docker compose logs --tail 100`. Stop the stack with `docker compose down`. The named volumes retain MySQL, Kafka, ZooKeeper, Prometheus, and Grafana data. `docker compose down -v` permanently deletes the Compose-managed volumes.

## CI/CD with GitHub Actions

The [workflow](.github/workflows/ci-cd.yml) runs on pull requests targeting `main` and pushes to `main`. A GitHub-hosted runner runs Maven tests, starts all six services with Compose, and signs in with its own test credentials. It obtains a CSRF token, submits jobs, and checks that an email job completes, a failing job exhausts three retries, and a report returns the expected counts. Check the repository's **Actions** tab for each step's output. If a check fails, the workflow skips publishing and collects any available container logs.

On a successful push to `main`, the workflow publishes a Linux x86-64 and ARM64 image to GitHub Container Registry, tagged with the full Git commit SHA. New packages are private by default. For a private package, [sign in to `ghcr.io`](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry#authenticating-with-a-personal-access-token-classic) on the deployment machine with a token that has `read:packages` access.

Image publication is the delivery step. To deploy a later build or roll back to an earlier one, set `JOB_SCHEDULER_IMAGE` to that build's tag in the target machine's `.env` and follow the [deployment steps](#deploy-on-another-computer).

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

Run these examples in one Bash session with `curl` and `jq` installed. Enter the API login you set in `.env`. For a remote deployment, use HTTPS before sending credentials.

```bash
api=http://localhost:8080
read -rp "API username: " api_user
read -rsp "API password: " api_password
echo
cookie_jar=$(mktemp)
jobs_api() {
  curl --silent --show-error --fail --user "$api_user:$api_password" \
    --cookie "$cookie_jar" --cookie-jar "$cookie_jar" "$@"
}
csrf=$(jobs_api "$api/csrf")
csrf_header=$(jq -er '.headerName + ": " + .token' <<< "$csrf")
```

`GET /csrf` provides a token for the current session. Send the session cookie and the token header together on requests that change jobs. Create a job with:

```sh
jobs_api -i "$api/jobs" -H "$csrf_header" \
  -H "Content-Type: application/json" \
  -d '{"type":"email","userId":1}'
```

The response is `201 Created`; the `Location` header contains the job URL. Check a job:

```sh
jobs_api "$api/jobs/1"
```

Create a job that will reach the DLQ after retries:

```sh
jobs_api "$api/jobs" -H "$csrf_header" \
  -H "Content-Type: application/json" \
  -d '{"type":"fail","userId":1}'
```

List failed jobs and resubmit one:

```sh
jobs_api "$api/jobs/failed"
jobs_api -X POST "$api/jobs/retry-dlq/2" -H "$csrf_header"
```

After trying the examples, run `rm -f "$cookie_jar"` and `unset api_password csrf csrf_header` in that shell.

The status endpoint is an administrative override. It accepts only valid state transitions and should not be used as the normal job-processing path.

## Configuration

Set the required API login in `.env` for Compose or in environment variables when running the JAR directly. Keep the password private.

| Variable | Default | Purpose |
| --- | --- | --- |
| `JOB_API_USERNAME` | Required | Username for the job API |
| `JOB_API_PASSWORD` | Required | Password for the job API |
| `MYSQL_DATABASE` | `jobdb` | MySQL database name |
| `MYSQL_USER` | `jobapp` | Application database user |
| `MYSQL_PASSWORD` | `jobapp-local-password` | Application database password |
| `MYSQL_ROOT_PASSWORD` | `local-root-password` | Local MySQL root password |
| `JOB_SCHEDULER_IMAGE` | `job-scheduler:local` | Scheduler image used by Compose |
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
