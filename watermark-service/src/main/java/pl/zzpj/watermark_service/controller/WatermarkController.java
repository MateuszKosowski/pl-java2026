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
import pl.zzpj.watermark_service.dto.DetectWatermarkResponse;
import pl.zzpj.watermark_service.dto.ExtractedTextResponse;
import pl.zzpj.watermark_service.service.SteganographyService;

/**
 * Exposes watermark-related endpoints for image processing clients.
 */
@RestController
@RequestMapping("/api/watermark")
@Tag(name = "Watermark", description = "Operations for embedding, detecting, and extracting invisible image watermarks")
public class WatermarkController {
    private final SteganographyService steganographyService;

    /**
     * Creates a new controller instance.
     *
     * @param steganographyService watermark service used by the API layer
     */
    public WatermarkController(SteganographyService steganographyService) {
        this.steganographyService = steganographyService;
    }

    /**
     * Embeds a protected watermark into the provided image.
     *
     * @param image source image (PNG, JPG, BMP, or any format supported by Java ImageIO)
     * @param text text payload to embed
     * @param ownerId owner identifier stored in the watermark metadata
     * @return generated PNG image containing the watermark
     */
    @PostMapping(
            value = "/embed",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.IMAGE_PNG_VALUE
    )
    @Operation(
            summary = "Embed watermark",
            description = "Embeds an invisible watermark into an uploaded image and returns the processed PNG."
    )
    @ApiResponse(responseCode = "200", description = "Watermark embedded successfully",
            content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE))
    public ResponseEntity<byte[]> embed(
            @RequestParam("image") MultipartFile image,
            @RequestParam("text") String text,
            @RequestParam("ownerId") String ownerId
    ) {
        byte[] watermarkedImage = steganographyService.embedMessage(image, text, ownerId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(watermarkedImage);
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
     * @param requesterId identifier of the requesting user
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
    public ExtractedTextResponse extract(
            @RequestParam("image") MultipartFile image,
            @RequestParam("requesterId") String requesterId
    ) {
        return steganographyService.extractMessage(image, requesterId);
    }

    /**
     * Returns a PNG visualization of watermark block locations in the provided image.
     *
     * <p>Each 4×4 block (displayed as 8×8 after scaling from the DWT subband) that
     * carries embedded data is highlighted with a semi-transparent red overlay.
     * If no watermark is detected, the image is returned without any highlights.</p>
     *
     * @param image uploaded image to visualize
     * @return PNG image with highlighted watermark blocks
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
