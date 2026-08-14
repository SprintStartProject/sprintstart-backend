package com.sprintstart.sprintstartbackend.config

import com.sprintstart.sprintstartbackend.user.service.SessionActivityService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain

@Profile("!local")
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {
    // ObjectProvider rather than a required constructor argument: narrow @WebMvcTest slices that
    // @Import(SecurityConfig::class) without the rest of the application context (the common
    // pattern across this codebase's controller tests) don't provide a SessionActivityService
    // bean, and shouldn't need to just to exercise a controller.
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        sessionActivityServiceProvider: ObjectProvider<SessionActivityService>,
    ): SecurityFilterChain {
        val chain = http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/actuator/health",
                        "/api/v1/health",
                    ).permitAll()
                    .requestMatchers(
                        "/api/v1/internal/keycloak/events",
                    ).permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer {
                it.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(KeycloakJwtAuthenticationConverter())
                }
            }

        sessionActivityServiceProvider.ifAvailable { sessionActivityService ->
            chain.addFilterAfter(
                SessionBoundaryFilter(sessionActivityService),
                BearerTokenAuthenticationFilter::class.java,
            )
        }

        return chain.build()
    }
}
