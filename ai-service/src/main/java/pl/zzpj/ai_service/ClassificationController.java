package pl.zzpj.ai_service;

import ai.onnxruntime.OrtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Classification", description = "AI Image Classification API")
public class ClassificationController {

  private final ClassificationService classificationService;

  @PostMapping(value = "/classify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Classify image",
      description = "Uses an ONNX model to classify the content of the provided image file.")
  public ResponseEntity<ClassificationResult> classify(@RequestPart("file") MultipartFile file)
      throws IOException, OrtException {
    log.info("Classifying image: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());
    ClassificationResult result = classificationService.classify(file);
    log.info(
        "Result: category={}, label={}, confidence={}",
        result.category(),
        result.label(),
        result.confidence());
    return ResponseEntity.ok(result);
  }
}
