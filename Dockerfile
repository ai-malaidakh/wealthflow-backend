# Multi-stage build — Spring Boot 3.2 on JDK 17
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
# Cache dependencies separately from source
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Runtime image — JRE only (smaller)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/finance-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
