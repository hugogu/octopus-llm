ARG DOCKER_IMAGE_PREFIX=""
ARG BACKEND_BUILDER_IMAGE=""
ARG BACKEND_RUNNER_IMAGE=""
FROM --platform=$BUILDPLATFORM ${BACKEND_BUILDER_IMAGE:-${DOCKER_IMAGE_PREFIX}gradle:8.8-jdk21-jammy} AS builder
USER root
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN if command -v gradle >/dev/null 2>&1; then gradle dependencies --no-daemon -q; else ./gradlew dependencies --no-daemon -q; fi || true
COPY src ./src
RUN if command -v gradle >/dev/null 2>&1; then gradle bootJar --no-daemon -x test; else ./gradlew bootJar --no-daemon -x test; fi

FROM ${BACKEND_RUNNER_IMAGE:-${DOCKER_IMAGE_PREFIX}eclipse-temurin:21-jre-alpine}
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
