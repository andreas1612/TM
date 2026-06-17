package com.treppides.taskmanager.config;

import com.treppides.taskmanager.services.DatabaseOidcUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;

@Configuration
public class SecurityConfig {

    private final DatabaseOidcUserService databaseOidcUserService;

    public SecurityConfig(DatabaseOidcUserService databaseOidcUserService) {
        this.databaseOidcUserService = databaseOidcUserService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/", "/error", "/css/**", "/js/**", "/favicon.ico").permitAll()
                .anyRequest().authenticated()
            )
            .headers(headers -> headers
                .xssProtection(xss -> xss.disable())
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(databaseOidcUserService)
                )
            );

        return http.build();
    }
}
