/**
 * Starter work: the first real tasks offered to a hire, matched against their competencies and goals, with
 * an orientation packet for the task they take. An L2 feature: it depends on path, the platform and shared
 * only.
 */
@ApplicationModule(
        allowedDependencies = {
                "shared :: shared", "shared :: ids", "shared :: ai", "shared :: annotations", "path :: api-model",
                "user :: api", "user :: api-enum", "user :: api-events", "ingestion :: api", "ingestion :: api.model", "connectors :: api",
                "connectors :: github-events", "connectors :: jira.events.issues", "assistant :: api"
        }
)
package com.sprintstart.sprintstartbackend.starterwork;

import org.springframework.modulith.ApplicationModule;
