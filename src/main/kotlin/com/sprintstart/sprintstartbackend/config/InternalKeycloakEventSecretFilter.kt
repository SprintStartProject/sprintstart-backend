package com.sprintstart.sprintstartbackend.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class InternalKeycloakEventSecretFilter(
    @Value("\${sprintstart.internal-event-secret}")
    private val expectedSecret: String,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return request.requestURI != "/api/v1/internal/keycloak/events"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val actualSecret = request.getHeader("X-SprintStart-Keycloak-Secret")

        if (actualSecret != expectedSecret) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid internal secret")
            return
        }

        filterChain.doFilter(request, response)
    }
}
