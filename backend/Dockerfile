# Run the application
FROM eclipse-temurin:21-jre-alpine

# Create a non-root user for security
RUN addgroup -S ktor && adduser -S ktor -G ktor

USER ktor
WORKDIR /app

# The JAR should be built before running docker build
# e.g., ./gradlew backend:build
ARG JAR_PATH=backend/build/libs/*-all.jar
COPY --chown=ktor:ktor ${JAR_PATH} /app/kdiab-profiles-backend.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/kdiab-profiles-backend.jar"]
