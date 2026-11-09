/**
 * Team mode: the assistant for PMs and the team. Its sessions, the areas it opens, and the tools and
 * actions it offers across path, arrival, starter work, knowledge and insights. An L4 assistant at the
 * top: it may use everything below it, including the Buddy's API, and nothing depends on it.
 */
@ApplicationModule(
        allowedDependencies = {
                "shared :: ids", "assistant :: api", "user :: api", "user :: api-events",
                "ingestion :: api", "ingestion :: api.model", "blueprint :: api-enums",
                "path :: api", "path :: api-model", "path :: api-enums", "path :: api-requests", "path :: api-responses",
                "arrival :: api", "arrival :: api-model", "arrival :: api-enums", "arrival :: api-responses",
                "starterwork :: api", "starterwork :: api-enums", "starterwork :: api-model", "starterwork :: api-requests",
                "starterwork :: api-responses",
                "knowledge :: api", "knowledge :: api-model", "knowledge :: api-responses", "insights :: api", "insights :: api-responses",
                "buddy :: api", "buddy :: api-enums", "buddy :: api-requests",
                "buddy :: api-responses"
        }
)
package com.sprintstart.sprintstartbackend.teamspace;

import org.springframework.modulith.ApplicationModule;
