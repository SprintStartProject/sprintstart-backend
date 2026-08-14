package com.sprintstart.sprintstartbackend.config

import com.sprintstart.sprintstartbackend.user.service.SessionActivityService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

class SessionBoundaryFilterTest {
    private val sessionActivityService: SessionActivityService = mockk(relaxed = true)
    private val filter = SessionBoundaryFilter(sessionActivityService)
    private val request: HttpServletRequest = mockk(relaxed = true)
    private val response: HttpServletResponse = mockk(relaxed = true)
    private val filterChain: FilterChain = mockk(relaxed = true)

    @BeforeEach
    fun stubOncePerRequestBookkeeping() {
        // OncePerRequestFilter.doFilter() short-circuits doFilterInternal() when
        // request.getAttribute(<already-filtered marker>) is non-null. A relaxed mock fabricates
        // a non-null default for unstubbed Object-returning calls, which trips that guard.
        every { request.getAttribute(any()) } returns null
    }

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    private fun jwtWithSubject(subject: String): Jwt =
        Jwt
            .withTokenValue("token")
            .header("alg", "none")
            .subject(subject)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

    @Test
    fun `records activity for the authenticated JWT subject`() {
        val jwt = jwtWithSubject("auth|test-user")
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt, emptyList())

        filter.doFilter(request, response, filterChain)

        verify(exactly = 1) { sessionActivityService.recordActivity("auth|test-user") }
        verify(exactly = 1) { filterChain.doFilter(request, response) }
    }

    @Test
    fun `does nothing when there is no authenticated principal`() {
        filter.doFilter(request, response, filterChain)

        verify(exactly = 0) { sessionActivityService.recordActivity(any()) }
        verify(exactly = 1) { filterChain.doFilter(request, response) }
    }

    @Test
    fun `does nothing for a non-JWT principal`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("mock-user", "credentials", emptyList())

        filter.doFilter(request, response, filterChain)

        verify(exactly = 0) { sessionActivityService.recordActivity(any()) }
    }
}
