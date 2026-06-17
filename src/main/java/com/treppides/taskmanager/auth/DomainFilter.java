package com.treppides.taskmanager.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects authenticated users whose preferred_username is not @treppides.com.
 * This blocks Azure AD B2B guest users (e.g. @finalogic.com) from accessing the app.
 */
public class DomainFilter extends OncePerRequestFilter {

    private static final String ALLOWED_DOMAIN = "@treppides.com";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OidcUser oidc) {
            String username = oidc.getPreferredUsername();
            if (username == null || !username.toLowerCase().endsWith(ALLOWED_DOMAIN)) {
                request.getSession().invalidate();
                SecurityContextHolder.clearContext();
                response.setStatus(403);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Access restricted to treppides.com accounts\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
