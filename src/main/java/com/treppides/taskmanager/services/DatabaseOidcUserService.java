package com.treppides.taskmanager.services;

import com.treppides.taskmanager.repositories.EmployeeRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class DatabaseOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();
    private final EmployeeRepository employeeRepository;

    public DatabaseOidcUserService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        String email = getEmail(oidcUser);

        if (email == null || !employeeRepository.existsByEmailIgnoreCase(email)) {
            OAuth2Error error = new OAuth2Error(
                    "access_denied",
                    "Your Microsoft account is not registered for Task Manager.",
                    null
            );

            throw new OAuth2AuthenticationException(error);
        }

        return oidcUser;
    }

    private String getEmail(OidcUser oidcUser) {
        String email = oidcUser.getEmail();

        if (email == null || email.isBlank()) {
            email = oidcUser.getClaimAsString("preferred_username");
        }

        if (email == null || email.isBlank()) {
            email = oidcUser.getClaimAsString("upn");
        }

        return email == null ? null : email.trim();
    }
}
