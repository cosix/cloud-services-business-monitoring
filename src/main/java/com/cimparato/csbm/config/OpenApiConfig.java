package com.cimparato.csbm.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Value("${springdoc.swagger-ui.oauth.authorizationUrl}")
    private String authorizationUrl;

    @Value("${springdoc.swagger-ui.oauth.tokenUrl}")
    private String tokenUrl;

    @Value("${keycloak.realm:cloud-services-business-monitoring}")
    private String realm;

    @Value("${keycloak.resource:cloud-services-business-monitoring-client}")
    private String clientId;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cloud Services Business Monitoring API")
                        .version("1.0")
                        .description("API for monitoring business cloud services. " +
                                "This API requires authentication via Keycloak."))
                .addSecurityItem(new SecurityRequirement().addList("oauth2"))
                .components(new Components()
                        .addSecuritySchemes("oauth2",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.OAUTH2)
                                        .description("""                                                
                                                ## Authentication
                                                
                                                1. Click on "Authorize" button
                                                2. Access by using one of the following Keycloak credentials: "admin/password", "uploader/password", "analyst/password"
    
                                                It is not necessary to enter the Client Secret.
                                                The required scopes are already preselected.
                                                """)
                                        .flows(new OAuthFlows()
                                                .authorizationCode(new OAuthFlow()
                                                        .authorizationUrl(authorizationUrl)
                                                        .tokenUrl(tokenUrl)
                                                        .scopes(new Scopes()
                                                                .addString("openid", "OpenID Connect")
                                                                .addString("profile", "User Profile")
                                                        )
                                                )
                                        )
                        )
                );
    }
}
