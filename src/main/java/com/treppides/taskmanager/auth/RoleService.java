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
 *   SUPER    — FULL + the Financials reports (a small, hand-picked set).
 *   FULL     — sees everything EXCEPT Financials, incl. hidden/WIP features + the simulator.
 *   STANDARD — an admin who sees only the released base hub (no admin/reporting section).
 *   NONE     — not eligible (Access Restricted).
 */
@Service
public class RoleService {

    public enum Tier { SUPER, FULL, STANDARD, NONE }

    /** SUPER-tier emails — the ONLY people who see Financials. Superset of FULL. */
    private static final Set<String> SUPER_EMAILS = Set.of(
        "apieri@treppides.com",       // Andreas Pieri
        "dkatsiolas@treppides.com",   // Daniel Katsiolas
        "lpampaka@treppides.com",     // Lygia Pampaka
        "syiannaki@treppides.com"     // Stelios Yiannaki
    );

    /** FULL-tier emails (hard-coded for now; migrate to EMPLOYEES.hub_role later). */
    private static final Set<String> FULL_EMAILS = Set.of(
        "gpanayiotou@treppides.com",
        "syiannaki@treppides.com",
        "msourmeli@treppides.com",
        "aparaskeva@treppides.com",
        "apieri@treppides.com",
        "dkatsiolas@treppides.com",
        "lpampaka@treppides.com",
        "etheodorou@treppides.com",  // Eleni Theodorou — added 2026-07-06 (full access)
        "lsofokleous@treppides.com", // Loukia Sofokleous — upgraded to FULL 2026-07-10
        "cacheriotou@treppides.com",
        "avladimerou@treppides.com"
    );

    // Feature keys align with the hub sidebar sections.
    private static final Set<String> BASE = Set.of("home", "kb", "staff", "tools", "support");
    // STANDARD also sees Performance + Budget KPI, but SELF-scoped (own card or a
    // "not applicable" message) — the backend /me endpoints are ungated, while the
    // view-anyone endpoints stay FULL-only. NOT financials.
    private static final Set<String> STANDARD_FEATURES = Set.of(
        "home", "kb", "staff", "tools", "support",
        "performance", "budgetkpi");
    // FULL = everything the admin section offers EXCEPT Financials.
    private static final Set<String> FULL = Set.of(
        "home", "kb", "staff", "tools", "support",
        "performance", "budgetkpi", "simulator");
    // SUPER = FULL + Financials (the only tier that sees Financials).
    private static final Set<String> SUPER_FEATURES = Set.of(
        "home", "kb", "staff", "tools", "support",
        "performance", "budgetkpi", "financials", "simulator");

    private final AdminService adminService;

    public RoleService(AdminService adminService) {
        this.adminService = adminService;
    }

    /** Resolve a person's tier. SUPER first, then FULL, then admins are STANDARD; non-admins NONE. */
    public Tier tierOf(String email) {
        if (email == null || email.isBlank()) return Tier.NONE;
        String e = email.toLowerCase();
        if (SUPER_EMAILS.contains(e)) return Tier.SUPER;
        if (FULL_EMAILS.contains(e)) return Tier.FULL;
        if (adminService.isAdmin(e)) return Tier.STANDARD;
        return Tier.NONE;
    }

    /**
     * True for FULL and SUPER users. This is the gate for all-employee Performance /
     * Budget KPI (view anyone). STANDARD admins fail it — being in app.admin.emails
     * makes you an admin, NOT necessarily FULL.
     */
    public boolean isFull(String email) {
        Tier t = tierOf(email);
        return t == Tier.FULL || t == Tier.SUPER;
    }

    /** True only for SUPER users — the single gate for Financials data. */
    public boolean isSuper(String email) {
        return tierOf(email) == Tier.SUPER;
    }

    /** The set of hub sections a tier may see. */
    public Set<String> features(Tier tier) {
        return switch (tier) {
            case SUPER -> SUPER_FEATURES;
            case FULL -> FULL;
            case STANDARD -> STANDARD_FEATURES;
            case NONE -> Set.of();
        };
    }
}
