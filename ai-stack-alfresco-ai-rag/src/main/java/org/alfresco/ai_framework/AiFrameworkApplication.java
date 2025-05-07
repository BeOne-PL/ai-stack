package org.alfresco.ai_framework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Main class for the AI Framework Spring Boot application.
 * It initializes the application and optionally sets up a CORS filter.
 */
@SpringBootApplication
public class AiFrameworkApplication {

	/**
	 * Application entry point.
	 *
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(AiFrameworkApplication.class, args);
	}

	/**
	 * Conditionally creates the CORS filter bean based on the application property.
	 *
	 * @return the configured CorsFilter if enabled, null otherwise
	 */
	@Bean
	@ConditionalOnProperty(name = "cors.filter.disabled", havingValue = "true")
	public CorsFilter corsFilter() {
		CorsConfiguration corsConfiguration = new CorsConfiguration();
		corsConfiguration.setAllowCredentials(false);
		corsConfiguration.addAllowedOrigin("*");
		corsConfiguration.addAllowedHeader("*");
		corsConfiguration.addAllowedMethod("*");

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", corsConfiguration);

		return new CorsFilter(source);
	}

}
