@ApplicationModule(
        allowedDependencies = {"shared :: shared", "user :: api", "upload :: api", "connectors :: github-events", "connectors :: api", "shared :: annotations", "upload :: upload.api"}
)
package com.sprintstart.sprintstartbackend.ingestion;

import org.springframework.modulith.ApplicationModule;
