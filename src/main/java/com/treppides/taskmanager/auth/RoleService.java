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
 *   SUPER      — FULL + CRM (a small, hand-picked set).
 *   SUPERVISOR — STANDARD + CRM.
 *   FULL       — sees everything, incl. Financials, CRM and simulator.
 *   STANDARD   — an admin who sees only the released base hub (no admin/reporting section).
 *   NONE       — not eligible (Access Restricted).
 */
@Service
public class RoleService {

    public enum Tier { SUPER, SUPERVISOR, FULL, STANDARD, NONE }

    /** SUPER-tier emails — a hand-picked superset of FULL (adds CRM). */
    private static final Set<String> SUPER_EMAILS = Set.of(
        "apieri@treppides.com",       // Andreas Pieri
        "lpampaka@treppides.com",    // Lygia Pampaka — restored to SUPER
        "syiannaki@treppides.com",    // Stelios Yiannaki
        "dkatsiolas@treppides.com"    // Daniel Katsiolas — promoted to SUPER 2026-08-05
    );

    /** SUPERVISOR-tier emails — STANDARD + CRM. */
    private static final Set<String> SUPERVISOR_EMAILS = Set.of(
        "mkourtella@treppides.com",    // Maria Kourtella
        "etheodorou@treppides.com",    // Eleni Theodorou
        "kphotiou@treppides.com",      // Kyriaki Photiou
        "ciacovides@treppides.com",    // Constantinos Iacovides
        "ckallis@treppides.com",       // Constantinos Kallis
        "cmesimeri@treppides.com",     // Christina Mesimeri
        "iagathokleous@treppides.com", // Ioanna Agathokleous
        "ibeiti@treppides.com",        // Irene Beiti
        "mefstathiou@treppides.com",   // Maria Efstathiou
        "alouca@treppides.com",        // Nana Louca
        "ninicolaou@treppides.com",    // Nicolina Nicolaou
        "cchrysanthou@treppides.com",  // Chara Chrysanthou
        "fkaikiti@treppides.com",      // Fani Kaikiti
        "anikolaou@treppides.com",     // Andri Nikolaou
        "mchartzioti@treppides.com",   // Marina Chartzioti
        "pgenethliou@treppides.com",   // Periklis Genethliou
        "dellina@treppides.com",       // Despina Ellina
        "acharalambous@treppides.com", // Andreas D. Charalambous
        "aeleftheriou@treppides.com",  // Andrea Eleftheriou
        "ngeorgiou@treppides.com",     // Nikos Georgiou
        "aphivou@treppides.com",       // Athineos Phivou
        "ekasieri@treppides.com",      // Eleni Kasieri
        "skyprianou@treppides.com",    // Stefanos Kyprianou
        "kmagou@treppides.com",        // Korina Magou
        "gpitsillidou@treppides.com",  // Georgia Pitsillidou
        "skaramouzas@treppides.com",   // Symeon Karamouzas
        "egeorgiou@treppides.com",     // Elpida Georgiou
        "kmosfili@treppides.com",      // Katerina Mosfili
        "makyriacou@treppides.com",    // Marios Kyriakou
        "edalitou@treppides.com",      // Evelyn Dalitou
        "aandreou@treppides.com",      // Andreas Andreou
        "khadjiefrem@treppides.com"    // Kypros Hadjiefrem
    );

    /** FULL-tier emails (hard-coded for now; migrate to EMPLOYEES.hub_role later). */
    private static final Set<String> FULL_EMAILS = Set.of(
        "gnicolaou@treppides.com",        // George Nicolaou
        "alexis.d@treppides.com",         // Alexis Dalitis
        "syiannaki@treppides.com",        // Stelios Yiannaki
        "gstrati@treppides.com",          // Giorgos Strati
        "msourmeli@treppides.com",        // Maria Sourmeli
        "avladimerou@treppides.com",      // Andreas Vladimerou
        "kherakleous@treppides.com",      // Kyriakos Herakleous
        "gpanayiotou@treppides.com",      // George Panayiotou
        "nklappis@treppides.com",         // Nicolas Klappis
        "cacheriotou@treppides.com",      // Chara Acheriotou
        "aparaskeva@treppides.com",       // Andreas Paraskeva
        "mapapanicolaou@treppides.com",   // Marios Papanicolaou
        "mxenophontos@treppides.com",     // Maria Xenophontos
        "cmerakli@treppides.com",         // Pambina Merakli
        "czampa@treppides.com",           // Chara Zampa
        "meleftheriades@treppides.com",   // Marios Eleftheriades
        "chadjineophytou@treppides.com",  // Charalambos Hadjineophytou
        "stavrostimotheou@treppides.com", // Stavros Timotheou
        "afotiou@treppides.com"           // Antrea Fotiou
    );

    // Feature keys align with the hub sidebar sections.
    private static final Set<String> BASE = Set.of("home", "kb", "staff", "tools", "support");
    // STANDARD also sees Performance + Budget KPI, but SELF-scoped (own card or a
    // "not applicable" message) — the backend /me endpoints are ungated, while the
    // view-anyone endpoints stay FULL-only. NOT financials.
    private static final Set<String> STANDARD_FEATURES = Set.of(
        "home", "kb", "staff", "tools", "support",
        "performance", "budgetkpi");
    // FULL = everything the admin section offers, including Financials
    // (Financials opened up to FULL tier 2026-08-06; previously SUPER-only).
    private static final Set<String> FULL_FEATURES = Set.of(
        "home", "kb", "staff", "tools", "support",
        "performance", "budgetkpi", "crm", "simulator", "financials");
    // SUPERVISOR = STANDARD + CRM.
    private static final Set<String> SUPERVISOR_FEATURES = Set.of(
        "home", "kb", "staff", "tools", "support",
        "performance", "budgetkpi", "crm");
    // SUPER = same hub feature set as FULL (financials now included in both);
    // SUPER remains distinct for board/Chamilo-admin checks, not the feature list.
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

    /** True only for SUPER users. (Financials is gated by {@link #isFull}, not this.) */
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
