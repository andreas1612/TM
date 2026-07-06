package com.treppides.taskmanager.auth;

import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Hub access tiers — the single decision point for "what does this person see".
 *
 * Today the FULL set is hard-coded (7 emails); tomorrow the source moves to an
 * EMPLOYEES.hub_role column with NO change to callers (see HUB_ROLES_PLAN.md §5).
 * Never scatter role checks around the code — always ask this service.
 *
 * Tiers:
 *   FULL     — sees everything, incl. hidden/WIP features + the simulator.
 *   STANDARD — an admin who sees only the released base hub (no admin/reporting section).
 *   NONE     — not eligible (Access Restricted).
 */
@Service
public class RoleService {

    public enum Tier { FULL, STANDARD, NONE }

    /** FULL-tier emails (hard-coded for now; migrate to EMPLOYEES.hub_role later). */
    private static final Set<String> FULL_EMAILS = Set.of(
        "gpanayiotou@treppides.com",
        "syiannaki@treppides.com",
        "msourmeli@treppides.com",
        "aparaskeva@treppides.com",
        "apieri@treppides.com",
        "dkatsiolas@treppides.com",
        "lpampaka@treppides.com"
    );

    // Feature keys align with the hub sidebar sections.
    private static final Set<String> BASE = Set.of("home", "kb", "staff", "tools", "support");
    private static final Set<String> FULL = Set.of(
        "home", "kb", "staff", "tools", "support",
        "performance", "budgetkpi", "financials", "simulator");

    private final AdminService adminService;

    public RoleService(AdminService adminService) {
        this.adminService = adminService;
    }

    /** Resolve a person's tier. Admins not in the FULL set are STANDARD; non-admins are NONE. */
    public Tier tierOf(String email) {
        if (email == null || email.isBlank()) return Tier.NONE;
        String e = email.toLowerCase();
        if (FULL_EMAILS.contains(e)) return Tier.FULL;
        if (adminService.isAdmin(e)) return Tier.STANDARD;
        return Tier.NONE;
    }

    /** The set of hub sections a tier may see. */
    public Set<String> features(Tier tier) {
        return switch (tier) {
            case FULL -> FULL;
            case STANDARD -> BASE;
            case NONE -> Set.of();
        };
    }
}
