package pl.zzpj.watermark_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import pl.zzpj.watermark_service.config.AiServiceFeignConfig;
import pl.zzpj.watermark_service.dto.ClassificationResult;

/**
 * Feign client for the AI classification microservice.
 * Service discovery is handled by Eureka using the logical name {@code ai-service}.
 *
 * <p>{@link AiServiceFeignConfig} forwards the caller's {@code Authorization} header so the
 * JWT-secured {@code /api/classify} endpoint accepts the inter-service call.
 */
@FeignClient(name = "ai-service", configuration = AiServiceFeignConfig.class)
public interface AiServiceFeignClient {

    @PostMapping(value = "/api/classify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ClassificationResult classify(@RequestPart("file") MultipartFile file);
}
