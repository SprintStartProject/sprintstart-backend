/**
 * The agent protocol both assistants run: the agent client and its DTOs, the stream vocabulary and its
 * relay, opening a conversation, the step budget and window, folding a conversation into its memory note,
 * the in-app guide, resolving a cited artifact, and the life of a proposed change. The kernel of the assistants: it depends on shared and the platform (user,
 * ingestion, upload), plus user's deletion event so it erases the proposals it stores (P5).
 */
@ApplicationModule(
        allowedDependencies = {
                "shared :: shared", "shared :: ids", "shared :: model", "shared :: ai", "shared :: annotations",
                "user :: api", "user :: api-enum", "user :: api-events", "ingestion :: api", "ingestion :: api.model",
                "upload :: upload.api", "upload :: upload.model"
        }
)
package com.sprintstart.sprintstartbackend.assistant;

import org.springframework.modulith.ApplicationModule;
