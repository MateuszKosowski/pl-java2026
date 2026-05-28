package pl.zzpj.watermark_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Client-side DTO mirroring the classification result returned by ai-service.
 *
 * @param label              the most specific ImageNet class label (e.g. "golden retriever")
 * @param category           the broad category mapped from the label (e.g. "dog")
 * @param confidence         probability of the top prediction in range [0, 1]
 * @param categoryConfidence aggregated probability over all labels mapped to the category, in [0, 1]
 * @param top3               the three highest-scoring predictions with their labels and confidences
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClassificationResult(
        String label,
        String category,
        double confidence,
        double categoryConfidence,
        List<TopPrediction> top3
) {
    /**
     * A single prediction with its label and confidence score.
     *
     * @param label      ImageNet class label
     * @param confidence probability in range [0, 1]
     */
    public record TopPrediction(String label, double confidence) {}
}
