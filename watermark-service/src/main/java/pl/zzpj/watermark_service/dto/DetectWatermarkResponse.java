package pl.zzpj.watermark_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response returned when the service checks whether an image contains a watermark.
 *
 * @param watermarked indicates whether the image contains a watermark created by this service
 * @param ownerIdentity owner identity embedded in the watermark metadata
 * @param version watermark payload format version
 */
@Schema(description = "Result of watermark detection")
public record DetectWatermarkResponse(
        @Schema(description = "Whether the image contains a supported watermark") boolean watermarked,
        @Schema(description = "Owner identity stored in the watermark (format: username-userId)", nullable = true) String ownerIdentity,
        @Schema(description = "Watermark format version", nullable = true) Integer version
) {
}
