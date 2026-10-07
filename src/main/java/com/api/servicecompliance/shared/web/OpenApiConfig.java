package com.api.servicecompliance.shared.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI serviceComplianceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Service Compliance API")
                        .version("v1")
                        .description("API REST para la trazabilidad de obligaciones, ejecuciones y evidencias de servicios de limpieza.")
                        .contact(new Contact().name("Opervia")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
