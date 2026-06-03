# Stage 1: Build the JAR
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :ai-service:bootJar --no-daemon

# Stage 2: Final lightweight image - JRE only, no Python, no PyTorch.
# The ONNX model is checked into the repository to keep Docker builds reproducible.
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /workspace/ai-service/build/libs/*.jar app.jar
COPY ai-service/src/test/resources/model/mobilenetv2.onnx /app/model/mobilenetv2.onnx
COPY ai-service/src/test/resources/model/synset.txt /app/model/synset.txt

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
