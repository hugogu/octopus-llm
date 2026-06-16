ARG DOCKER_IMAGE_PREFIX=""
ARG BACKEND_BUILDER_IMAGE=""
ARG BACKEND_RUNNER_IMAGE=""
FROM ${BACKEND_BUILDER_IMAGE:-${DOCKER_IMAGE_PREFIX}eclipse-temurin:21-jdk-alpine} AS builder
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon -q || true
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM ${BACKEND_RUNNER_IMAGE:-${DOCKER_IMAGE_PREFIX}eclipse-temurin:21-jre-alpine}
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
