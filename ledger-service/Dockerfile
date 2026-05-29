# Stage 1: Build the JAR using Gradle container
FROM gradle:8.7.0-jdk21-alpine AS builder
WORKDIR /build

# Copy gradle configurations and source code
COPY settings.gradle.kts build.gradle.kts /build/
COPY shared /build/shared
COPY ledger-service /build/ledger-service

# Compile the ledger module directly
RUN gradle :ledger-service:bootJar --no-daemon

# Stage 2: Tiny production runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/ledger-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
