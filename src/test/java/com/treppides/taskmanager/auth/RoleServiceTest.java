package com.treppides.taskmanager.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** S1 test — tierOf + features (plain unit test, no Spring context). */
class RoleServiceTest {

    // All 9 admins (FULL 7 + STANDARD 2) as they appear in app.admin.emails.
    private static final String ADMIN_EMAILS =
        "gpanayiotou@treppides.com,syiannaki@treppides.com,msourmeli@treppides.com,"
      + "aparaskeva@treppides.com,apieri@treppides.com,dkatsiolas@treppides.com,"
      + "lpampaka@treppides.com,stavrostimotheou@treppides.com,kmagou@treppides.com";

    private RoleService svc() {
        return new RoleService(new AdminService(ADMIN_EMAILS));
    }

    @Test
    void fullTierEmailsResolveFull() {
        assertEquals(RoleService.Tier.FULL, svc().tierOf("apieri@treppides.com"));
        assertEquals(RoleService.Tier.FULL, svc().tierOf("gpanayiotou@treppides.com"));
        assertEquals(RoleService.Tier.FULL, svc().tierOf("LPAMPAKA@treppides.com")); // case-insensitive
    }

    @Test
    void standardAdminsResolveStandard() {
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("stavrostimotheou@treppides.com"));
        assertEquals(RoleService.Tier.STANDARD, svc().tierOf("kmagou@treppides.com"));
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
        assertFalse(s.features(RoleService.Tier.STANDARD).contains("financials"));
        assertTrue(s.features(RoleService.Tier.STANDARD).contains("kb"));
        assertTrue(s.features(RoleService.Tier.NONE).isEmpty());
    }
}
