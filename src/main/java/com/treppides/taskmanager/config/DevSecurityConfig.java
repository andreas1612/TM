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
            // Simulator identity resolution (dev/test only). Priority:
            //  1) session SIM_USER_EMAIL (set by /api/sim/login) — persists across SPA requests
            //  2) legacy X-Dev-User-Code header (esoft code → email) — back-compat
            //  3) default = apieri (FULL admin)
            // Identity comes from InternalTools EMPLOYEES (the source prod authenticates against),
            // NOT eSoft — eSoft is only the KPI/performance datamart.
            String email = null;
            var session = request.getSession(false);
            if (session != null && session.getAttribute("SIM_USER_EMAIL") instanceof String se && !se.isBlank()) {
                email = se;
            }
            if (email == null) {
                String code = request.getHeader("X-Dev-User-Code");
                if (code != null && !code.isBlank()) {
                    List<Map<String, Object>> r = jdbcTemplate.queryForList(
                        "SELECT email FROM dbo.esoft_employees WHERE employee_code = ?", code);
                    if (!r.isEmpty()) email = (String) r.get(0).get("email");
                }
            }
            if (email == null || email.isBlank()) {
                email = "apieri@treppides.com"; // default dev/sim identity = FULL admin; NOT Ioanna
            }

            String name = email;
            List<Map<String, Object>> nr = jdbcTemplate.queryForList(
                "SELECT FULLNAME FROM dbo.EMPLOYEES WHERE LOWER(EMAIL) = LOWER(?)", email);
            if (!nr.isEmpty() && nr.get(0).get("FULLNAME") != null) name = (String) nr.get(0).get("FULLNAME");

            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(email, null, List.of());
            auth.setDetails(name);
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
        }
    }
}
