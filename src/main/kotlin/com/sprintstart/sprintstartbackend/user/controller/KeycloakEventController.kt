package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.dto.KeycloakEventRequest
import com.sprintstart.sprintstartbackend.user.service.KeycloakEventService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("api/v1/internal/keycloak/events")
class KeycloakEventController(
    private val keycloakEventService: KeycloakEventService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    fun handleEvent(@RequestBody request: KeycloakEventRequest) {
        keycloakEventService.handleEvent(request)
    }
}
