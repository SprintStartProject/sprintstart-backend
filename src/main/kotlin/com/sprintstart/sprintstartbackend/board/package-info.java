/**
 * The hire's board: the cards that show where they stand, live reads of path, arrival and starter work,
 * plus the cards people author and the diagrams drawn for them. An L3 view: it reads the L2 features and
 * below, never the assistants.
 */
@ApplicationModule(
        allowedDependencies = {
                "shared :: shared", "shared :: ids", "shared :: ai", "shared :: annotations", "user :: api", "user :: api-events",
                "ingestion :: api", "ingestion :: api.model",
                "path :: api", "path :: api-model", "path :: api-enums", "path :: api-responses",
                "arrival :: api", "arrival :: api-model", "arrival :: api-enums", "arrival :: api-responses",
                "starterwork :: api", "starterwork :: api-enums", "starterwork :: api-events",
                "starterwork :: api-model", "starterwork :: api-responses", "assistant :: api"
        }
)
package com.sprintstart.sprintstartbackend.board;

import org.springframework.modulith.ApplicationModule;
