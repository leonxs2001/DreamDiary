package com.example.dreamdiary.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dreamDiaryOpenApi(@Value("${app.openapi.server-url}") String serverUrl) {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("API Key");
        Scopes oauthScopes = new Scopes()
                .addString("dreamdiary.read", "Read dream entries")
                .addString("dreamdiary.write", "Create, update and delete dream entries");
        SecurityScheme oauthScheme = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                        .authorizationUrl("/oauth/authorize")
                        .tokenUrl("/oauth/token")
                        .scopes(oauthScopes)));

        return new OpenAPI()
                .info(new Info()
                        .title("Dream Diary API")
                        .version("1.2.0")
                        .description("REST API and MCP endpoint for managing dream entries"))
                .servers(List.of(new Server().url(serverUrl)))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerScheme)
                        .addSecuritySchemes("oauth2", oauthScheme))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .addSecurityItem(new SecurityRequirement().addList("oauth2", List.of("dreamdiary.read", "dreamdiary.write")));
    }
}
