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
 *   SUPER      — FULL + Financials + CRM (a small, hand-picked set).
 *   SUPERVISOR — STANDARD + CRM.
 *   FULL       — sees everything EXCEPT Financials, incl. CRM and simulator.
 *   STANDARD   — an admin who sees only the released base hub (no admin/reporting section).
 *   NONE       — not eligible (Access Restricted).
 */
@Service
public class RoleService {

    public enum Tier { SUPER, SUPERVISOR, FULL, STANDARD, NONE }

    /** SUPER-tier emails — the ONLY people who see Financials. Superset of FULL. */
    private static final Set<String> SUPER_EMAILS = Set.of(
        "apieri@treppides.com",       // Andreas Pieri
        // "lpampaka@treppides.com",  // Lygia Pampaka — temporarily moved to STANDARD
        "syiannaki@treppides.com",    // Stelios Yiannaki
        "dkatsiolas@treppides.com"    // Daniel Katsiolas — promoted to SUPER 2026-08-05
    );

    /** SUPERVISOR-tier emails — STANDARD + CRM. */
    private static final Set<String> SUPERVISOR_EMAILS = Set.of(
        "kmagou@treppides.com",       // Korina Magou
        "ekasieri@treppides.com",     // Eleni Kasieri
        "skyprianou@treppides.com",   // Stefanos Kyprianou — added 2026-07-29
        "skaramouzas@treppides.com",  // Symeon Karamouzas — added 2026-07-29 (was STANDARD)
        "egeorgiou@treppides.com",    // Elpida Georgiou — added 2026-07-29
        "kmosfili@treppides.com",     // Katerina Mosfili — added 2026-07-29
        "makyriacou@treppides.com",   // Marios Kyriakou — added 2026-07-29
        "edalitou@treppides.com",     // Evelyn Dalitou — added 2026-07-29
        "aandreou@treppides.com",     // Andreas Andreou — added 2026-07-29
        "khadjiefrem@treppides.com",  // Kypros Hadjiefrem — added 2026-07-29
        "aeleftheriou@treppides.com", // A. Eleftheriou — added 2026-07-31
        "ckallis@treppides.com",      // C. Kallis — added 2026-07-31
        "ibeiti@treppides.com",       // I. Beiti — added 2026-07-31
        "cchrysanthou@treppides.com"  // C. Chrysanthou — added 2026-07-31
    );

    /** FULL-tier emails (hard-coded for now; migrate to EMPLOYEES.hub_role later). */
    private static final Set<String> FULL_EMAILS = Set.of(
        "gpanayiotou@treppides.com",
        "syiannaki@treppides.com",
        "msourmeli@treppides.com",
        "aparaskeva@treppides.com",
        "apieri@treppides.com",
        // "dkatsiolas@treppides.com", // Daniel Katsiolas — moved to SUPERVISOR 2026-08-05
        // "lpampaka@treppides.com", // temporarily moved to STANDARD
        "etheodorou@treppides.com",  // Eleni Theodorou — added 2026-07-06 (full access)
        "lsofokleous@treppides.com", // Loukia Sofokleous — upgraded to FULL 2026-07-10
        "cacheriotou@treppides.com",
        "avladimerou@treppides.com",
        "stavrostimotheou@treppides.com", // Stavros Timotheou — promoted STANDARD→FULL 2026-07-21
        "czampa@treppides.com",           // Christiana Zampa — added 2026-07-23
        "kherakleous@treppides.com",      // K. Herakleous — added 2026-07-27
        "alexis.d@treppides.com"          // Alexis D. — added 2026-07-31
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
    private static final Set<String> FULL_FEATURES = Set.of(
        "home", "kb", "staff", "tools", "support",
        "performance", "budgetkpi", "crm", "simulator");
    // SUPERVISOR = STANDARD + CRM.
    private static final Set<String> SUPERVISOR_FEATURES = Set.of(
        "home", "kb", "staff", "tools", "support",
        "performance", "budgetkpi", "crm");
    // SUPER = FULL + Financials + CRM.
    private static final Set<String> SUPER_FEATURES = Set.of(
        "home", "kb", "staff", "tools", "support",
        "performance", "budgetkpi", "crm", "financials", "simulator");

    private final AdminService adminService;
    private final HrService hrService;

    public RoleService(AdminService adminService, HrService hrService) {
        this.adminService = adminService;
        this.hrService = hrService;
    }

    /** Resolve a person's tier. SUPER → SUPERVISOR → FULL → STANDARD → NONE. */
    public Tier tierOf(String email) {
        if (email == null || email.isBlank()) return Tier.NONE;
        String e = email.toLowerCase();
        if (SUPER_EMAILS.contains(e)) return Tier.SUPER;
        if (SUPERVISOR_EMAILS.contains(e)) return Tier.SUPERVISOR;
        if (FULL_EMAILS.contains(e)) return Tier.FULL;
        if (adminService.isAdmin(e)) return Tier.STANDARD;
        return Tier.NONE;
    }

    /**
     * True for FULL and SUPER users. This is the gate for all-employee Performance /
     * Budget KPI (view anyone). SUPERVISOR and STANDARD see self-scoped only.
     */
    public boolean isFull(String email) {
        Tier t = tierOf(email);
        return t == Tier.FULL || t == Tier.SUPER;
    }

    /** True for SUPERVISOR, FULL, and SUPER — gate for fee-adjustment CRUD. */
    public boolean isSupervisorOrAbove(String email) {
        Tier t = tierOf(email);
        return t == Tier.SUPERVISOR || t == Tier.FULL || t == Tier.SUPER;
    }

    /** True only for SUPER users — the single gate for Financials data. */
    public boolean isSuper(String email) {
        return tierOf(email) == Tier.SUPER;
    }

    /**
     * True for users who should be global administrators in Chamilo (SUPER-tier + HR team).
     * Everyone else gets STUDENT. Used by the authorization server token customizer.
     */
    public boolean isChamAdmin(String email) {
        if (email == null || email.isBlank()) return false;
        String e = email.toLowerCase();
        return SUPER_EMAILS.contains(e) || hrService.isHr(e);
    }

    /** The set of hub sections a tier may see. */
    public Set<String> features(Tier tier) {
        return switch (tier) {
            case SUPER -> SUPER_FEATURES;
            case SUPERVISOR -> SUPERVISOR_FEATURES;
            case FULL -> FULL_FEATURES;
            case STANDARD -> STANDARD_FEATURES;
            case NONE -> Set.of();
        };
    }
}
