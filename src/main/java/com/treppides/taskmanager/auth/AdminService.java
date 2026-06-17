package com.treppides.taskmanager.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final Set<String> adminEmails;

    public AdminService(@Value("${app.admin.emails:}") String emails) {
        this.adminEmails = List.of(emails.split(",")).stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    }

    public boolean isAdmin(String email) {
        return email != null && adminEmails.contains(email.toLowerCase());
    }
}
