package pl.zzpj.watermark_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Defines the minimal security configuration required by the watermark API.
 */
@Configuration
public class SecurityConfig {

    /**
     * Builds the security filter chain for the service.
     *
     * @param http security builder provided by Spring Security
     * @return configured security filter chain
     * @throws Exception when Spring Security fails to build the chain
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll()
                )
                .build();
    }
}
