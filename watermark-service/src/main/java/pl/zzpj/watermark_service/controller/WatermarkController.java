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

@RestController
@RequestMapping("/api/watermark")
@Tag(name = "Watermark", description = "Operations for embedding, detecting, and extracting invisible image watermarks")
public class WatermarkController {

    private static final Logger log = LoggerFactory.getLogger(WatermarkController.class);

    private final SteganographyService steganographyService;
    private final AiServiceFeignClient aiServiceFeignClient;

    public WatermarkController(SteganographyService steganographyService,
                               AiServiceFeignClient aiServiceFeignClient) {
        this.steganographyService = steganographyService;
        this.aiServiceFeignClient = aiServiceFeignClient;
    }

    @PostMapping(
            value = "/embed",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.IMAGE_PNG_VALUE
    )
    @Operation(
            summary = "Embed watermark",
            description = "Embeds an invisible watermark into an uploaded image and returns the processed PNG. "
                    + "The image is also classified by ai-service; classification metadata is exposed via response headers."
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

        ClassificationResult classification = aiServiceFeignClient.classify(image);
        log.info("Classification: category={}, label={}, confidence={}",
                classification.category(), classification.label(), classification.confidence());

        byte[] watermarkedImage = steganographyService.embedMessage(image, text, ownerId);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("X-Image-Category", classification.category())
                .header("X-Image-Label", classification.label())
                .header("X-Image-Confidence", String.valueOf(classification.confidence()))
                .body(watermarkedImage);
    }

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
