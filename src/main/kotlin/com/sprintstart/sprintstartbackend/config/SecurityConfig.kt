package com.sprintstart.sprintstartbackend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/actuator/health",
                    ).permitAll()

                it
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/users",
                    ).authenticated()

                // TODO: remove after frontend auth is implemented
                it
                    .requestMatchers(
                        "/api/v1/users/**",
                        "/api/v1/onboarding/**",
                        "/api/v1/chats/**",
                    ).permitAll()

                it.anyRequest().authenticated()
            }.oauth2ResourceServer { it.jwt {} }
            .build()
    }
}
