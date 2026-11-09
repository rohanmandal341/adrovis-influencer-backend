package com.adrovis.influencer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Documents Bearer authentication without placing secrets or account data into the public schema. */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("adrovisBearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .info(new Info()
                        .title("Influencer Campaign Core API")
                        .version("1.0.0")
                        .description("REST API service for campaign discovery, smart filtering, and agency CRM management.")
                        .contact(new Contact()
                                .name("ADROVIS Technologies")
                                .email("contact@adrovis.com")));
    }
}