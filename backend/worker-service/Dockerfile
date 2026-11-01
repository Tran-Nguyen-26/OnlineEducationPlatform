# Multi-stage Dockerfile for OEP Worker Service (Target: worker-service)
# Security & Sizing Compliance:
# - Minimal JRE image: eclipse-temurin:21-jre-alpine (Total image size <= 300MB limit)
# - Runs as dedicated non-root user (appuser, UID 10001)
# - No secrets embedded in image; configurations injected via AWS Secrets Manager / Env vars

# Stage 1: Build & Package
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Install maven
RUN apk add --no-cache maven

# Copy root POM and module definitions for layer caching
COPY pom.xml .
COPY core-model/pom.xml core-model/
COPY api-gateway/pom.xml api-gateway/
COPY core-lms-service/pom.xml core-lms-service/
COPY assessment-service/pom.xml assessment-service/
COPY worker-service/pom.xml worker-service/

# Cache Maven dependencies for core-model and worker-service
RUN mvn dependency:go-offline -B -pl core-model,worker-service -am || true

# Copy source code
COPY core-model/src core-model/src
COPY worker-service/src worker-service/src

# Compile and package worker-service, copy executable jar
RUN mvn clean package -pl worker-service -am -DskipTests -B && \
    cp worker-service/target/worker-service-*.jar /workspace/app.jar

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

# Worker service port
EXPOSE 8083

# Healthcheck checking Actuator endpoint on port 8083
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q --spider http://localhost:8083/actuator/health || exit 1

# Optimized JVM memory, GC, and diagnostic flags for container environments
ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/tmp/heapdump.hprof", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", \
  "app.jar"]
