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
        return new RoleService(new AdminService(ADMIN_EMAILS));
    }

    @Test
    void superTierEmailsResolveSuper() {
        assertEquals(RoleService.Tier.SUPER, svc().tierOf("apieri@treppides.com"));
        assertEquals(RoleService.Tier.SUPER, svc().tierOf("DKATSIOLAS@treppides.com")); // case-insensitive
        assertEquals(RoleService.Tier.SUPER, svc().tierOf("lpampaka@treppides.com"));
        assertEquals(RoleService.Tier.SUPER, svc().tierOf("syiannaki@treppides.com"));
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
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("kmagou@treppides.com"));
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("afotiou@treppides.com"));
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("rlambrou@treppides.com"));
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("aeleftheriou@treppides.com"));
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("exenophontos@treppides.com"));
    }

    @Test
    void nonAdminsAndNullResolveNone() {
        assertEquals(RoleService.Tier.NONE, svc().tierOf("someone@treppides.com"));
        assertEquals(RoleService.Tier.NONE, svc().tierOf(""));
        assertEquals(RoleService.Tier.NONE, svc().tierOf(null));
    }

    @Test
    void isFullGatesFullAndSuper() {
        RoleService s = svc();
        assertTrue(s.isFull("apieri@treppides.com"));        // SUPER also passes FULL gates
        assertTrue(s.isFull("gpanayiotou@treppides.com"));   // FULL
        assertFalse(s.isFull("kmagou@treppides.com"));       // STANDARD — walled off
        assertFalse(s.isFull("someone@treppides.com"));      // NONE
        assertFalse(s.isFull(null));
    }

    @Test
    void isSuperGatesSuperOnly() {
        RoleService s = svc();
        assertTrue(s.isSuper("apieri@treppides.com"));         // SUPER
        assertTrue(s.isSuper("syiannaki@treppides.com"));      // SUPER
        assertFalse(s.isSuper("gpanayiotou@treppides.com"));   // FULL — no Financials
        assertFalse(s.isSuper("kmagou@treppides.com"));        // STANDARD
        assertFalse(s.isSuper(null));
    }

    @Test
    void featuresMatchTier() {
        RoleService s = svc();
        // Financials is SUPER-only.
        assertTrue(s.features(RoleService.Tier.SUPER).contains("financials"));
        assertTrue(s.features(RoleService.Tier.SUPER).contains("performance"));
        // FULL sees everything the admin section offers EXCEPT Financials.
        assertFalse(s.features(RoleService.Tier.FULL).contains("financials"));
        assertTrue(s.features(RoleService.Tier.FULL).contains("performance"));
        assertTrue(s.features(RoleService.Tier.FULL).contains("simulator"));
        // STANDARD: Performance + Budget KPI (self-scoped), no financials/simulator.
        assertTrue(s.features(RoleService.Tier.STANDARD).contains("performance"));
        assertTrue(s.features(RoleService.Tier.STANDARD).contains("budgetkpi"));
        assertFalse(s.features(RoleService.Tier.STANDARD).contains("financials"));
        assertFalse(s.features(RoleService.Tier.STANDARD).contains("simulator"));
        assertTrue(s.features(RoleService.Tier.STANDARD).contains("kb"));
        assertTrue(s.features(RoleService.Tier.NONE).isEmpty());
    }
}
