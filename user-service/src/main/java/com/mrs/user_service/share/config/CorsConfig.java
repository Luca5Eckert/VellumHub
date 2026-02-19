package com.mrs.user_service.share.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração de CORS (Cross-Origin Resource Sharing) para o microserviço.
 * <p>
 * Esta classe permite que o frontend (Next.js ou outro) em uma origem diferente
 * faça requisições para este backend, incluindo requisições com credenciais.
 * </p>
 * <p>
 * As origens permitidas são injetadas via variável de ambiente ou arquivo de propriedades,
 * evitando valores hardcoded e permitindo configuração flexível por ambiente.
 * </p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With")
                .allowCredentials(true);
    }
}
