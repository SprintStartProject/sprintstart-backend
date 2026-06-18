package com.sprintstart.sprintstartbackend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Provides a place for general-purpose, application-custom configurations.
 */
@Configuration
class CustomsConfig {
    /**
     * Provides a unit-testable, injectable `Clock` attribute for `Instant.now()` calls.
     *
     * Unit tests can inject a `Clock.fixed()` to pinpoint a time to assert on,
     * and this default value does not actually change behaviour e.g. timezone handling
     * at production runtime.
     */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
