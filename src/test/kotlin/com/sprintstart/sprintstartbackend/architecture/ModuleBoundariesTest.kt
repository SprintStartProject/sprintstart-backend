package com.sprintstart.sprintstartbackend.architecture

import com.sprintstart.sprintstartbackend.SprintStartBackendApplication
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/**
 * Verifies the Spring Modulith module boundaries against the known violations.
 *
 * A module may use another module only through its `external` package (declared with `@NamedInterface`
 * in `package-info.java`) or through events; `shared` is open to everyone. Cycles between modules are
 * violations too.
 */
class ModuleBoundariesTest {
    @Test
    fun `no module boundary is crossed beyond the known violations`() {
        val violations = ApplicationModules
            .of(SprintStartBackendApplication::class.java)
            .detectViolations()
            .messages
            .map(::violationKey)

        KnownViolations("module-violations").assertMatches(
            violations,
            "use the other module's `external` API or an event instead.",
        )
    }
}

private val SLICE = Regex("""Slice (\S+)""")
private val LAMBDA_SUFFIX = Regex("""\$[\w$]*""")

/** A violation message reduced to a line that stays stable while unrelated code changes. */
internal fun violationKey(message: String): String {
    val firstLine = message.lineSequence().first().trim()
    if (firstLine.startsWith("Cycle detected")) {
        val slices = message.substringBefore("1. Dependencies")
        return "cycle: " + SLICE.findAll(slices).joinToString(" -> ") { it.groupValues[1] }
    }
    return firstLine
        .substringBefore(". Allowed targets:")
        .replace(LAMBDA_SUFFIX, "")
}
