# Stage 1: Build the application
FROM gradle:9.3.1-jdk21 AS build
WORKDIR /app

# Cache dependencies first
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY backend/build.gradle.kts backend/
# Ignore failures if it tries to configure plugins that require other files
RUN gradle backend:dependencies --no-daemon || true

COPY . .
RUN gradle backend:build --no-daemon -x check

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
# Create a non-root user for security
RUN addgroup -S ktor && adduser -S ktor -G ktor
USER ktor
WORKDIR /app
# Copy the fat jar from the build stage
COPY --from=build --chown=ktor:ktor /app/backend/build/libs/*-all.jar /app/kdiab-profiles-backend.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/kdiab-profiles-backend.jar"]
