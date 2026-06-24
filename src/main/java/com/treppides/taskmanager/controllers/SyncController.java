package com.treppides.taskmanager.controllers;

import com.treppides.taskmanager.auth.AdminService;
import com.treppides.taskmanager.services.EsoftSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Admin-only manual trigger + status for the eSoft -> datamart sync. */
@RestController
@RequestMapping("/api/admin")
public class SyncController {

    private final EsoftSyncService syncService;
    private final AdminService adminService;
    private final JdbcTemplate jdbc;

    public SyncController(EsoftSyncService syncService, AdminService adminService, JdbcTemplate jdbcTemplate) {
        this.syncService = syncService;
        this.adminService = adminService;
        this.jdbc = jdbcTemplate;
    }

    /** Run a full datamart refresh now. */
    @PostMapping("/sync")
    public Map<String, Object> sync(Authentication auth) {
        requireAdmin(auth);
        return syncService.syncAll();
    }

    /** Most recent sync_log entries. */
    @GetMapping("/sync/status")
    public List<Map<String, Object>> status(Authentication auth) {
        requireAdmin(auth);
        return jdbc.queryForList("""
            SELECT TOP 20 table_name, rows_loaded, started_at, finished_at, status, message
            FROM   dbo.sync_log
            ORDER  BY id DESC
            """);
    }

    private void requireAdmin(Authentication auth) {
        if (auth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String email = (auth.getPrincipal() instanceof OidcUser oidc)
            ? oidc.getPreferredUsername().toLowerCase()
            : auth.getName().toLowerCase();
        if (!adminService.isAdmin(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }
}
