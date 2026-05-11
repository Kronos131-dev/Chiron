package com.kronos.chiron.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for OpenAPI (Swagger) documentation generation.
 * This sets up the global metadata for the API documentation and configures
 * the JWT bearer authentication requirement for the endpoints.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Chiron API",
                version = "1.0",
                description = "Documentation officielle de l'API Chiron"
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        description = "Saisissez votre JWT ici. Ne mettez pas le mot 'Bearer ' devant, Swagger le fait automatiquement.",
        scheme = "bearer",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
