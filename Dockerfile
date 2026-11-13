# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM --platform=$BUILDPLATFORM maven:3.9.11-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -ntp clean package

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-noble

WORKDIR /app

RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

COPY --from=builder /app/target/*.jar /app/app.jar

EXPOSE 8080

USER appuser

HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
    CMD ["curl", "--fail", "--silent", "--show-error", "--max-time", "4", "http://127.0.0.1:8080/actuator/health"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
