@ApplicationModule(
        allowedDependencies = {"shared :: shared", "shared :: config", "shared :: model", "shared :: ids", "user :: api", "upload :: api", "connectors :: github-events", "connectors :: api", "shared :: annotations", "upload :: upload.api", "connectors :: jira.events.initial", "connectors :: jira.events.issues", "connectors :: bitbucket-events", "connectors :: confluence-events", "connectors :: jira-events", "connectors :: notion-events", "user :: api-events", "connectors :: confluence-ingestion", "connectors :: notion-ingestion"}
)
package com.sprintstart.sprintstartbackend.ingestion;

import org.springframework.modulith.ApplicationModule;
