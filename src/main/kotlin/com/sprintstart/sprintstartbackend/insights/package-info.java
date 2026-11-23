/**
 * Insights for PMs across the features: onboarding metrics and attention, the FAQ built from what hires
 * ask, knowledge gaps and project analysis. An L3 view: it reads the L2 features and below, never the
 * assistants.
 */
@ApplicationModule(
        allowedDependencies = {
                "shared :: shared", "shared :: ids", "shared :: config", "shared :: annotations",
                "user :: api", "user :: api-events", "ingestion :: api", "ingestion :: ingestion.events",
                "knowledge :: api", "knowledge :: api-events",
                "arrival :: api", "arrival :: api-model", "arrival :: api-enums",
                "starterwork :: api"
        }
)
package com.sprintstart.sprintstartbackend.insights;

import org.springframework.modulith.ApplicationModule;
