package pl.zzpj.ai_service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Service
public class ClassificationService {

    private static final int INPUT_SIZE = 224;
    private static final int RESIZE_SIZE = 256;
    private static final int TOP_K = 3;
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD  = {0.229f, 0.224f, 0.225f};

    @Value("${ai.model.path}")
    private String modelPath;

    private OrtEnvironment environment;
    private OrtSession session;
    private List<String> labels;
    private String[] labelCategories;
    private String inputName;

    /**
     * Loads the MobileNetV2 ONNX model and ImageNet class labels from disk.
     * The synset file is expected to reside in the same directory as the model file.
     */
    @PostConstruct
    void loadModel() throws OrtException, IOException {
        log.info("Loading MobileNetV2 ONNX model from {}", modelPath);

        Path modelFile = Path.of(modelPath);
        Path parent = modelFile.getParent();
        Path synsetFile = (parent != null) ? parent.resolve("synset.txt") : Path.of("synset.txt");

        if (!Files.exists(synsetFile)) {
            throw new IOException("Labels file not found: " + synsetFile.toAbsolutePath());
        }

        labels = Files.readAllLines(synsetFile);
        labelCategories = labels.stream().map(CategoryMapper::map).toArray(String[]::new);
        log.info("Loaded {} class labels from {}", labels.size(), synsetFile.getFileName());

        environment = OrtEnvironment.getEnvironment();
        try {
            session = environment.createSession(modelPath, new OrtSession.SessionOptions());
        } catch (OrtException e) {
            log.error("Failed to create ONNX session for {}: {}", modelPath, e.getMessage());
            throw e;
        }
        inputName = session.getInputNames().iterator().next();

        log.info("Model loaded successfully. Input tensor name: '{}'", inputName);
    }

    /**
     * Classifies the given image using MobileNetV2 and maps the result to a broad category.
     *
     * @param file uploaded image file
     * @return classification result with the top label, category, confidence, and top-3 predictions
     */
    public ClassificationResult classify(MultipartFile file) throws IOException, OrtException {
        float[] inputData = preprocess(file.getInputStream());

        long[] shape = {1, 3, INPUT_SIZE, INPUT_SIZE};
        OnnxTensor inputTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(inputData), shape);

        float[] probs;
        try (OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor))) {
            float[][] raw = (float[][]) result.get(0).getValue();
            probs = softmax(raw[0]);
        }

        int[] topIndices = IntStream.range(0, probs.length)
                .boxed()
                .sorted((a, b) -> Float.compare(probs[b], probs[a]))
                .mapToInt(Integer::intValue)
                .limit(TOP_K)
                .toArray();

        String bestLabel = labels.get(topIndices[0]);
        double bestConfidence = round4(probs[topIndices[0]]);
        String category = labelCategories[topIndices[0]];

        double categoryConfidence = 0.0;
        if (!"other".equals(category)) {
            for (int i = 0; i < probs.length; i++) {
                if (category.equals(labelCategories[i])) {
                    categoryConfidence += probs[i];
                }
            }
        }

        List<ClassificationResult.TopPrediction> top3 = Arrays.stream(topIndices)
                .mapToObj(i -> new ClassificationResult.TopPrediction(labels.get(i), round4(probs[i])))
                .toList();

        double clampedCategoryConfidence = Math.min(1.0, categoryConfidence);
        return new ClassificationResult(bestLabel, category, bestConfidence, round4(clampedCategoryConfidence), top3);
    }

    private float[] preprocess(InputStream is) throws IOException {
        BufferedImage orig = ImageIO.read(is);
        if (orig == null) throw new IOException("Failed to decode image — unsupported format or corrupted file");

        // Resize shorter edge to RESIZE_SIZE
        int w = orig.getWidth();
        int h = orig.getHeight();
        double scale = (double) RESIZE_SIZE / Math.min(w, h);
        int newW = (int) Math.round(w * scale);
        int newH = (int) Math.round(h * scale);

        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(orig, 0, 0, newW, newH, null);
        g.dispose();

        // Center crop to INPUT_SIZE x INPUT_SIZE
        int x = (newW - INPUT_SIZE) / 2;
        int y = (newH - INPUT_SIZE) / 2;
        BufferedImage cropped = resized.getSubimage(x, y, INPUT_SIZE, INPUT_SIZE);

        // Convert to CHW float32 with ImageNet normalization
        float[] data = new float[3 * INPUT_SIZE * INPUT_SIZE];
        for (int row = 0; row < INPUT_SIZE; row++) {
            for (int col = 0; col < INPUT_SIZE; col++) {
                int pixel = cropped.getRGB(col, row);
                float r = ((pixel >> 16) & 0xFF) / 255.0f;
                float green = ((pixel >> 8) & 0xFF) / 255.0f;
                float b = (pixel & 0xFF) / 255.0f;
                int base = row * INPUT_SIZE + col;
                data[base]                           = (r  - MEAN[0]) / STD[0];
                data[INPUT_SIZE * INPUT_SIZE + base] = (green - MEAN[1]) / STD[1];
                data[2 * INPUT_SIZE * INPUT_SIZE + base] = (b - MEAN[2]) / STD[2];
            }
        }
        return data;
    }

    private static float[] softmax(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : logits) if (v > max) max = v;
        double sum = 0;
        float[] exp = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exp[i] = (float) Math.exp(logits[i] - max);
            sum += exp[i];
        }
        for (int i = 0; i < exp.length; i++) exp[i] /= (float) sum;
        return exp;
    }

    @PreDestroy
    void close() {
        try {
            if (session != null) session.close();
            if (environment != null) environment.close();
        } catch (OrtException e) {
            log.warn("Error closing ORT session: {}", e.getMessage());
        }
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
