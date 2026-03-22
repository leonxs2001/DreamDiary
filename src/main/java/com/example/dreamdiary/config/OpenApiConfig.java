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
    public OpenAPI dreamDiaryOpenApi(
            @Value("${app.openapi.server-url}") String serverUrl,
            @Value("${app.openapi.authorization-url}") String authorizationUrl,
            @Value("${app.openapi.token-url}") String tokenUrl) {
        SecurityScheme oauth2Scheme = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                        .authorizationUrl(authorizationUrl)
                        .tokenUrl(tokenUrl)
                        .scopes(new Scopes()
                                .addString("openid", "OpenID Connect scope")
                                .addString("profile", "Basic profile scope")
                                .addString("dream.read", "Read dream entries")
                                .addString("dream.write", "Create, update and delete dream entries"))));

        return new OpenAPI()
                .info(new Info()
                        .title("Dream Diary API")
                        .version("1.0.0")
                        .description("REST API for managing dream entries"))
                .servers(List.of(new Server().url(serverUrl)))
                .components(new Components().addSecuritySchemes("oauth2", oauth2Scheme))
                .addSecurityItem(new SecurityRequirement().addList("oauth2"));
    }
}
