# Stage 1: Build the JAR
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :ai-service:bootJar --no-daemon

# Stage 2: Export MobileNetV2 to ONNX format and generate synset labels
# This stage is discarded after build — Python/PyTorch not in final image
FROM python:3.12-slim AS model-exporter
RUN pip install --no-cache-dir \
    torch==2.5.1+cpu \
    torchvision==0.20.1+cpu \
    --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir onnx

RUN mkdir -p /model && python - <<'EOF'
import torch
import torchvision.models as m

weights = m.MobileNet_V2_Weights.DEFAULT
model = m.mobilenet_v2(weights=weights)
model.eval()

dummy = torch.randn(1, 3, 224, 224)
torch.onnx.export(
    model, dummy, "/model/mobilenetv2.onnx",
    input_names=["input"],
    output_names=["output"],
    opset_version=13,
    dynamic_axes={"input": {0: "batch_size"}, "output": {0: "batch_size"}},
)
print("Model exported: /model/mobilenetv2.onnx")

categories = weights.meta["categories"]
with open("/model/synset.txt", "w") as f:
    f.write("\n".join(categories))
print(f"Synset saved: {len(categories)} classes")
EOF

# Stage 3: Final lightweight image — JRE only, no Python, no PyTorch
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /workspace/ai-service/build/libs/*.jar app.jar
COPY --from=model-exporter /model/ /app/model/

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
