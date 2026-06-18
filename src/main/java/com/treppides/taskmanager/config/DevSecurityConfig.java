package com.treppides.taskmanager.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Configuration
@Profile("dev")
public class DevSecurityConfig {

    private final JdbcTemplate jdbcTemplate;

    public DevSecurityConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(request -> request.getHeader("X-Dev-User-Code") != null)
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .addFilterBefore(new DevAuthFilter(jdbcTemplate), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    static class DevAuthFilter extends OncePerRequestFilter {

        private final JdbcTemplate jdbcTemplate;

        DevAuthFilter(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String esoftCode = request.getHeader("X-Dev-User-Code");
            if (esoftCode != null && !esoftCode.isBlank()) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT azure_email, employee_name FROM dbo.performance_targets WHERE esoft_code = ?",
                    esoftCode
                );
                if (!rows.isEmpty()) {
                    String azureEmail   = (String) rows.get(0).get("azure_email");
                    String employeeName = (String) rows.get(0).get("employee_name");
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(azureEmail, null, List.of());
                    auth.setDetails(employeeName);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}
