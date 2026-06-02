FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY . .
RUN chmod +x gradlew && ./gradlew :subscription-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /workspace/subscription-service/build/libs/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
