package com.treppides.taskmanager.auth;

import com.treppides.taskmanager.services.DatabaseOidcUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@Profile("!dev")   // in the dev profile, DevSecurityConfig is the sole (catch-all) chain
public class SecurityConfig {

    private final DatabaseOidcUserService databaseOidcUserService;

    public SecurityConfig(DatabaseOidcUserService databaseOidcUserService) {
        this.databaseOidcUserService = databaseOidcUserService;
    }

    @Bean
    @Order(2)   // after ChamAuthServerConfig's chain (@Order 1)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/", "/login.html", "/error", "/css/**", "/js/**", "/favicon.ico", "/service-worker.js").permitAll()
                .anyRequest().authenticated()
            )
            .headers(headers -> headers
                .xssProtection(xss -> xss.disable())
            )
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/**")
                )
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login.html")
                .defaultSuccessUrl("/dashboard.html", false)
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(databaseOidcUserService)
                )
            )
            .addFilterAfter(new DomainFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "https://hub.treppides.com"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
