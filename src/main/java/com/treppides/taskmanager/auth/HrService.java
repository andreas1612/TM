package com.treppides.taskmanager.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HR team email list — used to determine Chamilo TEACHER role assignment.
 * HR staff + SUPER-tier users get TEACHER; everyone else gets STUDENT.
 */
@Service
public class HrService {

    private final Set<String> hrEmails;

    public HrService(@Value("${app.hr.emails:}") String emails) {
        this.hrEmails = List.of(emails.split(",")).stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    }

    public boolean isHr(String email) {
        return email != null && hrEmails.contains(email.toLowerCase());
    }
}
