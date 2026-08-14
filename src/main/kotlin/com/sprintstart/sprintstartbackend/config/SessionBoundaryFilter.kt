package com.sprintstart.sprintstartbackend.config

import com.sprintstart.sprintstartbackend.user.service.SessionActivityService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Records session activity for authenticated requests, once the JWT has been resolved.
 *
 * Wired via [SecurityConfig] with `addFilterAfter(..., BearerTokenAuthenticationFilter::class.java)`
 * rather than component-scanned, so it reliably runs after Spring Security's OAuth2 resource
 * server filter has populated the security context -- and only registers on this one filter
 * chain, not a second time as a generically auto-registered servlet filter.
 */
class SessionBoundaryFilter(
    private val sessionActivityService: SessionActivityService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        if (principal is Jwt) {
            sessionActivityService.recordActivity(principal.subject)
        }

        filterChain.doFilter(request, response)
    }
}
