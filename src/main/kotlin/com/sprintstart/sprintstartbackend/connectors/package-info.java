/**
 * Connectors: the GitHub, Bitbucket, Jira, Confluence and Notion integrations, and the events their
 * syncs publish for ingestion. An L1 platform module: it depends on user and shared only.
 */
@ApplicationModule(
        allowedDependencies = {
                "shared :: shared", "shared :: annotations", "shared :: config", "shared :: crypto", "shared :: git",
                "shared :: ids",
                "user :: api", "user :: api-events"
        }
)
package com.sprintstart.sprintstartbackend.connectors;

import org.springframework.modulith.ApplicationModule;
