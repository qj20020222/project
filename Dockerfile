FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /workspace/target/hello-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /app/uploads && chown -R spring:spring /app

USER spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
