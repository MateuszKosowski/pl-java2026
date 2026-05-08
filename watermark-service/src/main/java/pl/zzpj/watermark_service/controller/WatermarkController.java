package pl.zzpj.watermark_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.zzpj.watermark_service.client.AiServiceFeignClient;
import pl.zzpj.watermark_service.dto.ClassificationResult;
import pl.zzpj.watermark_service.dto.DetectWatermarkResponse;
import pl.zzpj.watermark_service.dto.ExtractedTextResponse;
import pl.zzpj.watermark_service.service.SteganographyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/watermark")
@Tag(name = "Watermark", description = "Operations for embedding, detecting, and extracting invisible image watermarks")
public class WatermarkController {

    private static final Logger log = LoggerFactory.getLogger(WatermarkController.class);

    private static final ClassificationResult UNKNOWN_CLASSIFICATION =
            new ClassificationResult("unknown", "unknown", 0.0, List.of());

    private final SteganographyService steganographyService;
    private final AiServiceFeignClient aiServiceFeignClient;

    /**
     * Creates a new controller instance.
     *
     * @param steganographyService watermark service used by the API layer
     * @param aiServiceFeignClient feign client used to call the AI classification service
     */
    public WatermarkController(SteganographyService steganographyService,
                               AiServiceFeignClient aiServiceFeignClient) {
        this.steganographyService = steganographyService;
        this.aiServiceFeignClient = aiServiceFeignClient;
    }

    /**
     * Embeds a protected watermark into the provided image.
     *
     * <p>The image is also sent to {@code ai-service} for classification; the result is exposed
     * via response headers. If the AI service is unreachable, watermarking still proceeds and
     * the headers fall back to {@code unknown} values.
     *
     * @param image source image (PNG, JPG, BMP, or any format supported by Java ImageIO)
     * @param text text payload to embed
     * @param principal injected security principal containing the validated user identifier
     * @return generated PNG image containing the watermark
     */
    @PostMapping(
            value = "/embed",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.IMAGE_PNG_VALUE
    )
    @Operation(
            summary = "Embed watermark",
            description = "Embeds an invisible watermark into an uploaded image and returns the processed PNG. "
                    + "The image is also classified by ai-service; classification metadata is exposed via response headers. "
                    + "If ai-service is unavailable, watermarking still succeeds and the metadata headers contain 'unknown'."
    )
    @ApiResponse(responseCode = "200", description = "Watermark embedded successfully",
            content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE))
    @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token")
    public ResponseEntity<byte[]> embed(
            @RequestParam("image") MultipartFile image,
            @RequestParam("text") String text,
            Principal principal
    ) {
        String ownerId = principal != null ? principal.getName() : "Unknown-0";
        log.info("Watermark embed requested. Resolved principal ownerId: {}", ownerId);

        ClassificationResult classification = classifyOrFallback(image);

        byte[] watermarkedImage = steganographyService.embedMessage(image, text, ownerId);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("X-Image-Category", classification.category())
                .header("X-Image-Label", classification.label())
                .header("X-Image-Confidence", String.valueOf(classification.confidence()))
                .body(watermarkedImage);
    }

    private ClassificationResult classifyOrFallback(MultipartFile image) {
        try {
            ClassificationResult result = aiServiceFeignClient.classify(image);
            log.info("Classification: category={}, label={}, confidence={}",
                    result.category(), result.label(), result.confidence());
            return result;
        } catch (Exception ex) {
            log.warn("AI classification failed, continuing without metadata: {}", ex.getMessage());
            return UNKNOWN_CLASSIFICATION;
        }
    }

    /**
     * Detects whether an uploaded image contains a watermark created by this service.
     *
     * @param image uploaded image to inspect
     * @return detection result containing watermark presence and metadata
     */
    @PostMapping(
            value = "/detect",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Detect watermark",
            description = "Detects whether an image contains a watermark created by this application. This endpoint is best treated as administrative or internal unless authentication is enforced."
    )
    @ApiResponse(responseCode = "200", description = "Detection completed",
            content = @Content(schema = @Schema(implementation = DetectWatermarkResponse.class)))
    public DetectWatermarkResponse detect(@RequestParam("image") MultipartFile image) {
        return steganographyService.detectWatermark(image);
    }

    /**
     * Extracts the embedded text when the requester is authorized to read it.
     *
     * @param image image containing a watermark
     * @param principal injected security principal containing the validated user identifier
     * @return extracted watermark data
     */
    @PostMapping(
            value = "/extract",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Extract watermark",
            description = "Extracts watermark text for the owner of the watermark."
    )
    @ApiResponse(responseCode = "200", description = "Watermark extracted successfully",
            content = @Content(schema = @Schema(implementation = ExtractedTextResponse.class)))
    @ApiResponse(responseCode = "403", description = "Requester is not allowed to read this watermark")
    @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token")
    public ExtractedTextResponse extract(
            @RequestParam("image") MultipartFile image,
            Principal principal
    ) {
        String requesterId = principal != null ? principal.getName() : "Unknown-0";
        log.info("Watermark extract requested. Resolved principal requesterId: {}", requesterId);

        return steganographyService.extractMessage(image, requesterId);
    }

    /**
     * Returns a PNG visualization of watermark block locations in the provided image.
     *
     * @param image uploaded image to inspect
     * @return PNG with red-highlighted 8×8 blocks where watermark data resides
     */
    @PostMapping(
            value = "/visualize",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.IMAGE_PNG_VALUE
    )
    @Operation(
            summary = "Visualize watermark blocks",
            description = "Returns an image with highlighted 8×8 blocks that carry embedded watermark data. "
                    + "Useful for administrative inspection of watermarked images."
    )
    @ApiResponse(responseCode = "200", description = "Visualization generated",
            content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE))
    public ResponseEntity<byte[]> visualize(@RequestParam("image") MultipartFile image) {
        byte[] visualization = steganographyService.visualizeWatermark(image);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(visualization);
    }
}
