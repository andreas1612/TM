package com.treppides.taskmanager.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** tierOf + features (plain unit test, no Spring context). */
class RoleServiceTest {

    // A representative admin set (feeds AdminService → isAdmin). SUPER/FULL come from the
    // hard-coded sets in RoleService, independent of this fixture.
    private static final String ADMIN_EMAILS =
        "gpanayiotou@treppides.com,syiannaki@treppides.com,msourmeli@treppides.com,"
      + "aparaskeva@treppides.com,apieri@treppides.com,dkatsiolas@treppides.com,"
      + "lpampaka@treppides.com,etheodorou@treppides.com,lsofokleous@treppides.com,"
      + "stavrostimotheou@treppides.com,kmagou@treppides.com,"
      + "afotiou@treppides.com,rlambrou@treppides.com,"
      + "aeleftheriou@treppides.com,exenophontos@treppides.com";

    private RoleService svc() {
        return new RoleService(new AdminService(ADMIN_EMAILS), new HrService(""));
    }

    @Test
    void superTierEmailsResolveSuper() {
        assertEquals(RoleService.Tier.SUPER, svc().tierOf("apieri@treppides.com"));
        assertEquals(RoleService.Tier.SUPER, svc().tierOf("DKATSIOLAS@treppides.com")); // case-insensitive
        // lpampaka temporarily moved to STANDARD
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("lpampaka@treppides.com"));
        assertEquals(RoleService.Tier.SUPER, svc().tierOf("syiannaki@treppides.com"));
    }

    @Test
    void supervisorTierEmailsResolveSupervisor() {
        assertEquals(RoleService.Tier.SUPERVISOR, svc().tierOf("kmagou@treppides.com"));
        assertEquals(RoleService.Tier.SUPERVISOR, svc().tierOf("ekasieri@treppides.com"));
    }

    @Test
    void fullTierEmailsResolveFull() {
        assertEquals(RoleService.Tier.FULL, svc().tierOf("gpanayiotou@treppides.com"));
        assertEquals(RoleService.Tier.FULL, svc().tierOf("msourmeli@treppides.com"));
        assertEquals(RoleService.Tier.FULL, svc().tierOf("etheodorou@treppides.com"));
        assertEquals(RoleService.Tier.FULL, svc().tierOf("lsofokleous@treppides.com")); // promoted 2026-07-10
        assertEquals(RoleService.Tier.FULL, svc().tierOf("stavrostimotheou@treppides.com")); // promoted 2026-07-21
    }

    @Test
    void standardAdminsResolveStandard() {
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("afotiou@treppides.com"));
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("rlambrou@treppides.com"));
        // aeleftheriou promoted to SUPERVISOR 2026-07-31
        assertEquals(RoleService.Tier.SUPERVISOR, svc().tierOf("aeleftheriou@treppides.com"));
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("exenophontos@treppides.com"));
    }

    @Test
    void nonAdminsAndNullResolveNone() {
        assertEquals(RoleService.Tier.NONE, svc().tierOf("someone@treppides.com"));
        assertEquals(RoleService.Tier.NONE, svc().tierOf(""));
        assertEquals(RoleService.Tier.NONE, svc().tierOf(null));
    }

    @Test
    void isFullGatesFullSupervisorAndSuper() {
        RoleService s = svc();
        assertTrue(s.isFull("apieri@treppides.com"));        // SUPER also passes FULL gates
        assertTrue(s.isFull("gpanayiotou@treppides.com"));   // FULL
        assertTrue(s.isFull("etheodorou@treppides.com"));    // FULL
        assertFalse(s.isFull("kmagou@treppides.com"));       // SUPERVISOR — walled off
        assertFalse(s.isFull("someone@treppides.com"));      // NONE
        assertFalse(s.isFull(null));
    }

    @Test
    void isSuperGatesSuperOnly() {
        RoleService s = svc();
        assertTrue(s.isSuper("apieri@treppides.com"));         // SUPER
        assertTrue(s.isSuper("syiannaki@treppides.com"));      // SUPER
        assertFalse(s.isSuper("gpanayiotou@treppides.com"));   // SUPERVISOR — no Financials
        assertFalse(s.isSuper("kmagou@treppides.com"));        // STANDARD
        assertFalse(s.isSuper(null));
    }

    @Test
    void featuresMatchTier() {
        RoleService s = svc();
        // SUPER = everything including Financials + CRM.
        assertTrue(s.features(RoleService.Tier.SUPER).contains("financials"));
        assertTrue(s.features(RoleService.Tier.SUPER).contains("crm"));
        assertTrue(s.features(RoleService.Tier.SUPER).contains("performance"));
        // SUPERVISOR = STANDARD + CRM.
        assertTrue(s.features(RoleService.Tier.SUPERVISOR).contains("crm"));
        assertTrue(s.features(RoleService.Tier.SUPERVISOR).contains("performance"));
        assertTrue(s.features(RoleService.Tier.SUPERVISOR).contains("budgetkpi"));
        assertFalse(s.features(RoleService.Tier.SUPERVISOR).contains("simulator"));
        assertFalse(s.features(RoleService.Tier.SUPERVISOR).contains("financials"));
        // FULL sees everything EXCEPT Financials.
        assertFalse(s.features(RoleService.Tier.FULL).contains("financials"));
        assertTrue(s.features(RoleService.Tier.FULL).contains("crm"));
        assertTrue(s.features(RoleService.Tier.FULL).contains("performance"));
        assertTrue(s.features(RoleService.Tier.FULL).contains("simulator"));
        // STANDARD: Performance + Budget KPI (self-scoped), no financials/simulator/crm.
        assertTrue(s.features(RoleService.Tier.STANDARD).contains("performance"));
        assertTrue(s.features(RoleService.Tier.STANDARD).contains("budgetkpi"));
        assertFalse(s.features(RoleService.Tier.STANDARD).contains("financials"));
        assertFalse(s.features(RoleService.Tier.STANDARD).contains("simulator"));
        assertFalse(s.features(RoleService.Tier.STANDARD).contains("crm"));
        assertTrue(s.features(RoleService.Tier.STANDARD).contains("kb"));
        assertTrue(s.features(RoleService.Tier.NONE).isEmpty());
    }
}
