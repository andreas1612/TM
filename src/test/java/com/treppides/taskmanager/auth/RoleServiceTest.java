package com.treppides.taskmanager.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** S1 test — tierOf + features (plain unit test, no Spring context). */
class RoleServiceTest {

    // All 13 admins (FULL 8 + STANDARD 5) as they appear in app.admin.emails.
    private static final String ADMIN_EMAILS =
        "gpanayiotou@treppides.com,syiannaki@treppides.com,msourmeli@treppides.com,"
      + "aparaskeva@treppides.com,apieri@treppides.com,dkatsiolas@treppides.com,"
      + "lpampaka@treppides.com,etheodorou@treppides.com,"
      + "stavrostimotheou@treppides.com,kmagou@treppides.com,"
      + "afotiou@treppides.com,lsofokleous@treppides.com,rlambrou@treppides.com"; // HR — STANDARD

    private RoleService svc() {
        return new RoleService(new AdminService(ADMIN_EMAILS));
    }

    @Test
    void fullTierEmailsResolveFull() {
        assertEquals(RoleService.Tier.FULL, svc().tierOf("apieri@treppides.com"));
        assertEquals(RoleService.Tier.FULL, svc().tierOf("GPANAYIOTOU@treppides.com")); // case-insensitive
        assertEquals(RoleService.Tier.FULL, svc().tierOf("lpampaka@treppides.com"));
        assertEquals(RoleService.Tier.FULL, svc().tierOf("etheodorou@treppides.com"));
    }

    @Test
    void standardAdminsResolveStandard() {
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("stavrostimotheou@treppides.com"));
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("kmagou@treppides.com"));
        // HR team — admins (in app.admin.emails) but not FULL.
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("afotiou@treppides.com"));
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("lsofokleous@treppides.com"));
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("rlambrou@treppides.com"));
    }

    @Test
    void isFullGatesFullOnly() {
        RoleService s = svc();
        assertTrue(s.isFull("apieri@treppides.com"));       // FULL
        assertTrue(s.isFull("lpampaka@treppides.com"));     // FULL (restored)
        assertFalse(s.isFull("kmagou@treppides.com"));      // STANDARD admin — walled off
        assertFalse(s.isFull("someone@treppides.com"));     // NONE
        assertFalse(s.isFull(null));
    }

    @Test
    void nonAdminsAndNullResolveNone() {
        assertEquals(RoleService.Tier.NONE, svc().tierOf("someone@treppides.com"));
        assertEquals(RoleService.Tier.NONE, svc().tierOf(""));
        assertEquals(RoleService.Tier.NONE, svc().tierOf(null));
    }

    @Test
    void featuresMatchTier() {
        RoleService s = svc();
        assertTrue(s.features(RoleService.Tier.FULL).contains("financials"));
        assertTrue(s.features(RoleService.Tier.FULL).contains("simulator"));
        // STANDARD now sees Performance + Budget KPI (self-scoped), but NOT financials/simulator.
        assertTrue(s.features(RoleService.Tier.STANDARD).contains("performance"));
        assertTrue(s.features(RoleService.Tier.STANDARD).contains("budgetkpi"));
        assertFalse(s.features(RoleService.Tier.STANDARD).contains("financials"));
        assertFalse(s.features(RoleService.Tier.STANDARD).contains("simulator"));
        assertTrue(s.features(RoleService.Tier.STANDARD).contains("kb"));
        assertTrue(s.features(RoleService.Tier.NONE).isEmpty());
    }
}
