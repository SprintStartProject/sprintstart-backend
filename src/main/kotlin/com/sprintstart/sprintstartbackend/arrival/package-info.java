/**
 * Arrival: the first-week steps a hire settles (accounts, access, a first contribution), the evidence
 * that settles them, and the attestations a teammate gives. An L2 feature: it depends on the platform
 * and shared only.
 */
@ApplicationModule(
        allowedDependencies = {
                "shared :: ids", "shared :: annotations", "user :: api", "user :: api-enum", "user :: api-events",
                "ingestion :: api", "ingestion :: api.model", "connectors :: api"
        }
)
package com.sprintstart.sprintstartbackend.arrival;

import org.springframework.modulith.ApplicationModule;
