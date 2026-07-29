package com.treppides.taskmanager.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

/**
 * Spring Authorization Server — makes TM an OAuth2/OIDC provider for Chamilo LMS.
 *
 * <p>This is a SEPARATE security filter chain (@Order 1) that handles ONLY the
 * authorization server endpoints under {@code /oauth2/chamilo/*}. It does NOT
 * interfere with the existing Azure AD OAuth2 client flow in SecurityConfig
 * (@Order 2).</p>
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>User clicks "Sign in with Treppides Hub" on learn.treppides.com</li>
 *   <li>Chamilo redirects browser to {@code https://tasks.treppides.com/oauth2/chamilo/authorize}</li>
 *   <li>If user has no TM session → Spring redirects to Azure AD login first</li>
 *   <li>TM issues auth code → redirects to Chamilo callback</li>
 *   <li>Chamilo exchanges code for tokens at {@code /oauth2/chamilo/token}</li>
 *   <li>Chamilo calls {@code /oauth2/chamilo/userinfo} → gets email, name, role</li>
 *   <li>Chamilo auto-provisions user as STUDENT or TEACHER based on role claim</li>
 * </ol>
 *
 * <h3>Role mapping</h3>
 * <ul>
 *   <li>SUPER-tier + HR team → {@code role: "admin"} → Chamilo global administrator</li>
 *   <li>Everyone else → {@code role: "student"} → Chamilo STUDENT</li>
 * </ul>
 */
@Configuration
@Profile("!dev")
public class ChamAuthServerConfig {

    @Bean
    @Order(1)
    @SuppressWarnings("removal")   // applyDefaultSecurity — deprecated in 1.5, replacement TBD
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
            .oidc(oidc -> oidc
                .userInfoEndpoint(userInfo -> userInfo
                    .userInfoMapper(context -> {
                        var authorization = context.getAuthorization();
                        var idToken = authorization.getToken(
                            org.springframework.security.oauth2.core.oidc.OidcIdToken.class);

                        var builder = OidcUserInfo.builder()
                            .subject(idToken.getToken().getSubject());

                        var claims = idToken.getClaims();
                        if (claims != null) {
                            if (claims.containsKey("email"))
                                builder.claim("email", claims.get("email"));
                            if (claims.containsKey("given_name"))
                                builder.claim("given_name", claims.get("given_name"));
                            if (claims.containsKey("family_name"))
                                builder.claim("family_name", claims.get("family_name"));
                            if (claims.containsKey("role"))
                                builder.claim("role", claims.get("role"));
                        }

                        return builder.build();
                    })
                )
            );

        // Bearer token support — required for the OIDC userinfo endpoint.
        // Without this, userinfo can't validate access tokens from Chamilo.
        http.oauth2ResourceServer(oauth2 -> oauth2
            .jwt(Customizer.withDefaults())
        );

        // If the user isn't authenticated (no Azure AD session), redirect to
        // login.html which triggers the Azure AD flow. After login, Spring
        // will return to the authorize endpoint and issue the code to Chamilo.
        http.exceptionHandling(ex -> ex
            .defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login.html"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
            )
        );

        return http.build();
    }

    /** Single registered OAuth2 client: Chamilo LMS. */
    @Bean
    public RegisteredClientRepository registeredClientRepository(
            @Value("${chamilo.oauth2.client-id}") String clientId,
            @Value("${chamilo.oauth2.client-secret}") String clientSecret) {

        RegisteredClient chamilo = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId(clientId)
            .clientSecret("{noop}" + clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://learn.treppides.com/connect/generic/check")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(false)   // trusted first-party client
                .build())
            .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(10))  // short-lived, used once for userinfo
                .build())
            .build();

        return new InMemoryRegisteredClientRepository(chamilo);
    }

    /**
     * Customizes the ID token claims with user email, name, and Chamilo role.
     * The OIDC userinfo endpoint returns these same claims.
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> idTokenCustomizer(RoleService roleService) {
        return context -> {
            // Only customize ID tokens — access tokens stay standard.
            if (!"id_token".equals(context.getTokenType().getValue())) {
                return;
            }

            var principal = context.getPrincipal();
            if (principal.getPrincipal() instanceof OidcUser oidc) {
                String email = oidc.getPreferredUsername() != null
                    ? oidc.getPreferredUsername().toLowerCase()
                    : oidc.getEmail() != null ? oidc.getEmail().toLowerCase() : "";

                String role = roleService.isChamAdmin(email) ? "admin" : "student";

                context.getClaims()
                    .subject(email)
                    .claim("email", email)
                    .claim("role", role);

                String givenName = oidc.getGivenName();
                String familyName = oidc.getFamilyName();

                // Azure AD v2.0 often omits given_name/family_name unless configured
                // as optional claims. Fall back to the "name" claim (always present).
                if ((givenName == null || familyName == null) && oidc.getFullName() != null) {
                    String[] parts = oidc.getFullName().trim().split("\\s+", 2);
                    if (givenName == null) givenName = parts[0];
                    if (familyName == null && parts.length > 1) familyName = parts[1];
                }

                if (givenName != null) {
                    context.getClaims().claim("given_name", givenName);
                }
                if (familyName != null) {
                    context.getClaims().claim("family_name", familyName);
                }
            }
        };
    }

    /** RSA key pair for signing JWTs. Generated once per JVM lifetime. */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = generateRsaKey();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    /**
     * Namespace all auth server endpoints under {@code /oauth2/chamilo/} to avoid
     * any conflict with the Azure AD client endpoints at
     * {@code /oauth2/authorization/azure} and {@code /login/oauth2/code/azure}.
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
            .issuer("https://tasks.treppides.com")
            .authorizationEndpoint("/oauth2/chamilo/authorize")
            .tokenEndpoint("/oauth2/chamilo/token")
            .jwkSetEndpoint("/oauth2/chamilo/jwks")
            .oidcUserInfoEndpoint("/oauth2/chamilo/userinfo")
            .tokenRevocationEndpoint("/oauth2/chamilo/revoke")
            .tokenIntrospectionEndpoint("/oauth2/chamilo/introspect")
            .build();
    }

    private static RSAKey generateRsaKey() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair keyPair = kpg.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair", ex);
        }
    }
}
