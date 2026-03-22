package com.example.dreamdiary.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.util.StringUtils;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class).oidc(Customizer.withDefaults());
        http.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http.securityMatcher("/api/**", "/actuator/**");
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/dream-entries", "/api/dream-entries/*")
                .hasAuthority("SCOPE_dream.read")
                .requestMatchers(HttpMethod.POST, "/api/dream-entries")
                .hasAuthority("SCOPE_dream.write")
                .requestMatchers(HttpMethod.PATCH, "/api/dream-entries/*/text")
                .hasAuthority("SCOPE_dream.write")
                .requestMatchers(HttpMethod.DELETE, "/api/dream-entries/*")
                .hasAuthority("SCOPE_dream.write")
                .anyRequest().authenticated());
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/actuator/**"));
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain appSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                        "/",
                        "/error",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/openapi.yaml"
                ).permitAll()
                .anyRequest().authenticated());
        http.formLogin(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(AppSecurityProperties properties, PasswordEncoder passwordEncoder) {
        List<UserDetails> users = parseUsers(properties.getUsers(), passwordEncoder);
        log.info("Loaded {} user(s) from DREAM_DIARY_USERS.", users.size());
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(
            AppSecurityProperties properties,
            PasswordEncoder passwordEncoder) {
        AppSecurityProperties.OAuth oauth = properties.getOauth();
        assertOAuthProperty("OAUTH_CLIENT_ID", oauth.getClientId());
        assertOAuthProperty("OAUTH_CLIENT_SECRET", oauth.getClientSecret());
        assertOAuthProperty("OAUTH_REDIRECT_URI", oauth.getRedirectUri());

        Set<String> scopes = parseScopes(oauth.getScopes());
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(oauth.getClientId())
                .clientSecret(passwordEncoder.encode(oauth.getClientSecret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(oauth.getRedirectUri())
                .scopes(scopeSet -> scopeSet.addAll(scopes))
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        log.info("Registered OAuth client '{}'.", oauth.getClientId());
        return new InMemoryRegisteredClientRepository(client);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService() {
        return new org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService();
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService() {
        return new org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(AppSecurityProperties properties) {
        String issuer = properties.getOauth().getIssuer();
        assertOAuthProperty("OAUTH_ISSUER", issuer);
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = generateRsa();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    private static void assertOAuthProperty(String name, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " must not be empty.");
        }
    }

    private static List<UserDetails> parseUsers(String rawUsers, PasswordEncoder passwordEncoder) {
        if (!StringUtils.hasText(rawUsers)) {
            throw new IllegalStateException("DREAM_DIARY_USERS must contain at least one username:password pair.");
        }

        List<UserDetails> users = new ArrayList<>();
        Set<String> seenUsernames = new LinkedHashSet<>();
        for (String part : rawUsers.split(",")) {
            String candidate = part.trim();
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            int separatorIndex = candidate.indexOf(':');
            boolean invalid = separatorIndex <= 0
                    || separatorIndex == candidate.length() - 1
                    || candidate.indexOf(':', separatorIndex + 1) >= 0;
            if (invalid) {
                throw new IllegalStateException(
                        "Invalid DREAM_DIARY_USERS entry '" + candidate + "'. Expected format username:password.");
            }

            String username = candidate.substring(0, separatorIndex).trim();
            String password = candidate.substring(separatorIndex + 1).trim();
            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                throw new IllegalStateException(
                        "Invalid DREAM_DIARY_USERS entry '" + candidate + "'. Username and password are required.");
            }
            if (!seenUsernames.add(username.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("Duplicate username in DREAM_DIARY_USERS: " + username);
            }

            users.add(User.withUsername(username)
                    .password(passwordEncoder.encode(password))
                    .roles("USER")
                    .build());
        }

        if (users.isEmpty()) {
            throw new IllegalStateException("DREAM_DIARY_USERS did not contain a valid username:password pair.");
        }
        return users;
    }

    private static Set<String> parseScopes(String rawScopes) {
        if (!StringUtils.hasText(rawScopes)) {
            return Set.of("openid", "profile", "dream.read", "dream.write");
        }
        Set<String> scopes = Arrays.stream(rawScopes.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (scopes.isEmpty()) {
            throw new IllegalStateException("OAUTH_SCOPES must contain at least one scope.");
        }
        return scopes;
    }

    private static RSAKey generateRsa() {
        KeyPair keyPair;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not generate RSA key pair for JWT signing.", exception);
        }

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
    }
}
