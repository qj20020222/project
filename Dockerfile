# Use official OpenJDK 17 image as base
FROM eclipse-temurin:17-jre-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy the packaged jar file into the container
COPY target/hello-0.0.1-SNAPSHOT.jar app.jar

# Expose port 8080 (the default port for Spring Boot applications)
EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
