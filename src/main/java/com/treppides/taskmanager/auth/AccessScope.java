package com.treppides.taskmanager.auth;

import java.util.Collections;
import java.util.Set;

/**
 * The result of {@link AccessScopeResolver}: "given who is logged in, which rows
 * may they see?" — the reusable access primitive shared by every report
 * (Performance, Financials, ...).
 *
 * A report repository applies only the dimension(s) it cares about:
 *   - chargeability / timesheet reports filter on {@link #employeeCodes()}
 *   - revenue / budget reports filter on {@link #elCodes()} / {@link #departmentCodes()}
 *   - if {@link #unrestricted()} is true, no row filter is applied (board / admin).
 *
 * Dimensions are populated lazily: DEPARTMENT and EL stay empty until a grant
 * activates them (see report_grants), so adding "X sees their whole department"
 * is a data change, not a code change.
 */
public record AccessScope(
        Level level,
        boolean unrestricted,
        String ownEsoftCode,
        Set<String> employeeCodes,
        Set<String> departmentCodes,
        Set<String> elCodes) {

    public enum Level { SELF, TEAM, DEPARTMENT, EL, ALL }

    /** Board / admin — sees everything, no row filter. */
    public static AccessScope all(String ownEsoftCode) {
        return new AccessScope(Level.ALL, true, ownEsoftCode,
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    }

    public boolean canSeeEmployee(String code) {
        return unrestricted || (code != null && employeeCodes.contains(code));
    }

    public boolean canSeeEl(String elCode) {
        return unrestricted || (elCode != null && elCodes.contains(elCode));
    }

    public boolean canSeeDepartment(String deptCode) {
        return unrestricted || (deptCode != null && departmentCodes.contains(deptCode));
    }
}
