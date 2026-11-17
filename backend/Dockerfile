# syntax=docker/dockerfile:1.7
# One Dockerfile for all four Spring Boot services:
#   docker build -f backend/Dockerfile --build-arg MODULE=core-lms-service -t oep/core-lms-service backend
ARG JAVA_VERSION=21

FROM maven:3.9-eclipse-temurin-${JAVA_VERSION} AS build
ARG MODULE
WORKDIR /src
COPY pom.xml .
COPY core-model/pom.xml core-model/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY core-lms-service/pom.xml core-lms-service/pom.xml
COPY assessment-service/pom.xml assessment-service/pom.xml
COPY worker-service/pom.xml worker-service/pom.xml
COPY core-model/src core-model/src
COPY ${MODULE}/src ${MODULE}/src
# BuildKit cache keeps the ~/.m2 repository between builds
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${MODULE} -am package -DskipTests \
 && cp ${MODULE}/target/${MODULE}-*.jar /app.jar

FROM eclipse-temurin:${JAVA_VERSION}-jre AS runtime
ARG MODULE
# worker-service transcodes videos to HLS and needs ffmpeg; the other services stay lean
ARG INSTALL_FFMPEG=false
RUN if [ "$INSTALL_FFMPEG" = "true" ]; then apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*; fi
RUN useradd --system --uid 10001 --create-home oep
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER oep
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom -Dsun.net.inetaddr.ttl=10 -Dsun.net.inetaddr.negative.ttl=1"
# 8080-8083 = service port, 9090 = internal management port (health / metrics)
EXPOSE 8080 8081 8082 8083 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
