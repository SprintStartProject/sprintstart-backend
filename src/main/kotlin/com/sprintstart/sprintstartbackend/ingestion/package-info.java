@ApplicationModule(
        allowedDependencies = {"github::github-events", "github",
        "shared :: shared"}
)
package com.sprintstart.sprintstartbackend.ingestion;

import org.springframework.modulith.ApplicationModule;
