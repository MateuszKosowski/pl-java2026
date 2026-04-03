package pl.zzpj.watermark_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the generated OpenAPI description for the watermark service.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Creates the OpenAPI metadata exposed through the Swagger UI.
     *
     * @return configured OpenAPI model for the service
     */
    @Bean
    OpenAPI watermarkServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Watermark Service API")
                        .version("1.0")
                        .description("Embeds, detects, and extracts invisible image watermarks.")
                        .contact(new Contact().name("Java2026 team")));
    }
}
