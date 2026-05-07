package pl.zzpj.watermark_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response returned after a successful watermark extraction.
 *
 * @param ownerIdentity owner identifier stored in the watermark metadata
 * @param text extracted text payload
 */
@Schema(description = "Extracted watermark payload")
public record ExtractedTextResponse(
        @Schema(description = "Owner identity stored in the watermark (format: username-userId)") String ownerIdentity,
        @Schema(description = "Extracted watermark text") String text
) {
}
