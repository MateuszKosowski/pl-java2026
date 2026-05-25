package pl.zzpj.watermark_service.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign configuration for {@link pl.zzpj.watermark_service.client.AiServiceFeignClient}.
 *
 * <p>ai-service secures every endpoint, so the caller's JWT must travel with the
 * inter-service classify call. This interceptor copies the incoming request's
 * {@code Authorization} header onto the outgoing Feign request.
 *
 * <p>Intentionally <strong>not</strong> annotated with {@code @Configuration} so it is not
 * picked up by component scanning; it applies only to the Feign client that references it
 * via {@code @FeignClient(configuration = ...)}, leaving other clients untouched.
 */
public class AiServiceFeignConfig {

    @Bean
    public RequestInterceptor authForwardingInterceptor() {
        return template -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && !authorization.isBlank()) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        };
    }
}
