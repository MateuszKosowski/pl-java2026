package pl.zzpj.watermark_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Entry point for the watermark service application.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class Application {

	/**
	 * Starts the Spring Boot application.
	 *
	 * @param args command-line arguments passed to the application
	 */
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
