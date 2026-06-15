package com.sprintstart.sprintstartbackend.config

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

class KeycloakJwtAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val realmRoles = extractRealmRoles(jwt)

        val authorities = KeycloakRoleMapper
            .mapRealmRoles(realmRoles)
            .map { SimpleGrantedAuthority("ROLE_${it.name}") }

        return JwtAuthenticationToken(jwt, authorities, jwt.subject)
    }

    @Suppress("ReturnCount")
    private fun extractRealmRoles(jwt: Jwt): Set<String> {
        val realmAccess = jwt.claims["realm_access"] as? Map<*, *>
            ?: return emptySet()

        val roles = realmAccess["roles"] as? Collection<*>
            ?: return emptySet()

        return roles
            .filterIsInstance<String>()
            .toSet()
    }
}
