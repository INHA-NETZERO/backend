package com.netzero.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("ZeroWave Backend API")
                .description("발주 최적화·탄소 절감 SaaS 백엔드")
                .version("1.0.0"))
            .addSecurityItem(new SecurityRequirement().addList("X-API-Key"))
            .components(new Components()
                .addSecuritySchemes("X-API-Key",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")));
    }
}
