package io.carranza.jpeg_trust_orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper; // <-- Importante
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}