package com.treppides.taskmanager.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Board-member / financials allow-list.
 *
 * Mirrors {@link AdminService}: a flat, hard-coded list of emails from
 * application.properties ({@code app.board.emails}). No DB, no Azure write access
 * needed — Azure only supplies the identity; this config decides who may see the
 * financials reports.
 *
 * Example:
 *   app.board.emails=director1@treppides.com,director2@treppides.com
 */
@Service
public class BoardService {

    private final Set<String> boardEmails;

    public BoardService(@Value("${app.board.emails:}") String emails) {
        this.boardEmails = List.of(emails.split(",")).stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    }

    public boolean isBoard(String email) {
        return email != null && boardEmails.contains(email.toLowerCase());
    }
}
