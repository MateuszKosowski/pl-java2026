package pl.zzpj.watermark_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response returned after a successful watermark extraction.
 *
 * @param ownerId owner identifier stored in the watermark metadata
 * @param text extracted text payload
 */
@Schema(description = "Extracted watermark payload")
public record ExtractedTextResponse(
        @Schema(description = "Owner identifier stored in the watermark") String ownerId,
        @Schema(description = "Extracted watermark text") String text
) {
}
