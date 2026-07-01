package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.dto.KeycloakEventRequest
import com.sprintstart.sprintstartbackend.user.service.KeycloakEventService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Receives Keycloak events for user synchronization.
 *
 * This controller exposes an internal webhook endpoint that forwards incoming
 * Keycloak event payloads to [KeycloakEventService]. It does not implement event
 * handling logic itself.
 */
@Tag(
    name = "Keycloak Events",
    description = "Internal endpoint for forwarding Keycloak user synchronization events.",
)
@RestController
@RequestMapping("api/v1/internal/keycloak/events")
class KeycloakEventController(
    private val keycloakEventService: KeycloakEventService,
) {
    /**
     * Forwards a Keycloak event payload to the user synchronization service.
     *
     * The request body is passed through unchanged so the service can route the
     * event based on its resource type and event type.
     *
     * @param request Incoming Keycloak event payload.
     */
    @Operation(
        summary = "Handle Keycloak event",
        description = "Accepts an internal Keycloak event payload and forwards it to the user synchronization service.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Event accepted and processed"),
            ApiResponse(responseCode = "400", description = "Required user fields are missing in the event payload"),
            ApiResponse(responseCode = "404", description = "Referenced local user projection was not found"),
        ],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    fun handleEvent(@RequestBody request: KeycloakEventRequest) {
        keycloakEventService.handleEvent(request)
    }
}
