# Multi-stage Dockerfile for OEP Backend Services (Target: core-lms-service)
# Security & Sizing Compliance:
# - Minimal JRE image: eclipse-temurin:21-jre-alpine (Total image size ~180MB <= 300MB limit)
# - Runs as dedicated non-root user (appuser, UID 10001)
# - No secrets embedded in image; configurations injected via AWS Secrets Manager / Env vars

# Stage 1: Build & Package
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Install maven
RUN apk add --no-cache maven

# Copy root POM and module definitions for optimal layer caching
COPY pom.xml .
COPY core-model/pom.xml core-model/
COPY api-gateway/pom.xml api-gateway/
COPY core-lms-service/pom.xml core-lms-service/
COPY assessment-service/pom.xml assessment-service/
COPY worker-service/pom.xml worker-service/

# Cache Maven dependencies
RUN mvn dependency:go-offline -B -pl core-model,core-lms-service -am || true

# Copy source code and build
COPY core-model/src core-model/src
COPY core-lms-service/src core-lms-service/src

# Compile and package core-lms-service, copy executable jar
RUN mvn clean package -pl core-lms-service -am -DskipTests -B && \
    cp core-lms-service/target/core-lms-service-*.jar /workspace/app.jar

# Stage 2: Lightweight, secure runtime
FROM eclipse-temurin:21-jre-alpine AS runner

# Create non-root system user and group
RUN addgroup -S -g 10001 appgroup && \
    adduser -S -u 10001 -G appgroup -s /sbin/nologin appuser

WORKDIR /app

# Copy executable jar from builder
COPY --from=builder --chown=appuser:appgroup /workspace/app.jar app.jar

# Switch to non-root user
USER appuser:appgroup

# Service port
EXPOSE 8081

# Healthcheck checking Actuator endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q --spider http://localhost:8081/actuator/health || exit 1

# Optimized JVM memory, GC, and diagnostic flags for container environments
ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/tmp/heapdump.hprof", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", \
  "app.jar"]
