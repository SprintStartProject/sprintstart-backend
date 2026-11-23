/**
 * What a team loses when someone leaves: which files lose the people who know them, ranked by how much
 * that would hurt. A view over the contribution evidence ingestion stores; it depends on ingestion and
 * shared only.
 */
@ApplicationModule(
        allowedDependencies = {"ingestion :: api", "ingestion :: api.model", "shared :: ids"}
)
package com.sprintstart.sprintstartbackend.offboarding;

import org.springframework.modulith.ApplicationModule;
