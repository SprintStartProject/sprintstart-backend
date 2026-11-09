/**
 * The Buddy: the hire's AI mentor. Its conversations, memory and suggestions, and the tools and actions it
 * runs on the hire's behalf across path, arrival, starter work, knowledge and the board. An L4 assistant:
 * it may use everything below it, and team mode may use its API, never the other way round.
 */
@ApplicationModule(
        allowedDependencies = {
                "shared :: shared", "shared :: ids", "shared :: ai", "shared :: annotations", "shared :: config",
                "assistant :: api", "user :: api", "user :: api-enum", "user :: api-events",
                "upload :: upload.api", "upload :: upload.model", "ingestion :: api", "ingestion :: api.model", "blueprint :: api-enums",
                "path :: api", "path :: api-model", "path :: api-enums", "path :: api-requests", "path :: api-responses",
                "arrival :: api", "arrival :: api-model", "arrival :: api-enums", "arrival :: api-responses",
                "starterwork :: api", "starterwork :: api-enums", "starterwork :: api-model", "starterwork :: api-responses",
                "knowledge :: api", "knowledge :: api-model", "knowledge :: api-responses",
                "board :: api", "board :: api-enums", "board :: api-model", "board :: api-requests",
                "board :: api-responses",
                "insights :: api", "insights :: api-responses"
        }
)
package com.sprintstart.sprintstartbackend.buddy;

import org.springframework.modulith.ApplicationModule;
