FROM maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:8-jre
RUN apt-get update && apt-get install -y --no-install-recommends default-mysql-client postgresql-client && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/backupbeacon-0.1.0.jar app.jar
RUN mkdir -p /app/data /backup-target
VOLUME ["/app/data", "/backup-target"]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
