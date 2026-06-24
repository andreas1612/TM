package com.treppides.taskmanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * DEV ONLY: serve the REAL treppides-hub frontend verbatim from disk so the
 * actual Performance and Manager KPI pages — with the manager team view and the
 * built-in dev impersonation switcher — run against this app on localhost:8080
 * with zero re-porting. The source of truth stays in the treppides-hub folder.
 *
 *   http://localhost:8080/hub/performance.html
 *   http://localhost:8080/hub/manager-kpi.html
 *
 * Production is unaffected: the hub is deployed separately and proxies to /projects.
 */
@Configuration
@Profile("dev")
public class DevHubResourceConfig implements WebMvcConfigurer {

    // Absolute path to the sibling treppides-hub directory (file: URL, forward slashes).
    private static final String HUB_DIR =
        "file:c:/Users/andreas.pi/OneDrive - K.Treppides & Co/Desktop/planner/treppides-hub/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/hub/**")
                .addResourceLocations(HUB_DIR)
                .setCachePeriod(0);
    }
}
