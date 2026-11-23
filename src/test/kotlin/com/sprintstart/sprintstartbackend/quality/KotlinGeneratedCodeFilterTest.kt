package com.sprintstart.sprintstartbackend.quality

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pitest.classinfo.ClassName
import org.pitest.mutationtest.engine.Location
import org.pitest.mutationtest.engine.MutationDetails
import org.pitest.mutationtest.engine.MutationIdentifier

class KotlinGeneratedCodeFilterTest {
    private val interceptor = KotlinGeneratedCodeFilter().createInterceptor(null)

    private fun mutant(description: String) = MutationDetails(
        MutationIdentifier(Location.location(ClassName.fromString("a.Service"), "run", "()V"), 0, "mutator"),
        "Service.kt",
        description,
        1,
        0,
    )

    @Test
    fun `drops removed null checks and coroutine result unwrapping`() {
        val generated = listOf(
            mutant("removed call to kotlin/jvm/internal/Intrinsics::checkNotNull"),
            mutant("removed call to kotlin/jvm/internal/Intrinsics::checkNotNullExpressionValue"),
            mutant("removed call to kotlin/ResultKt::throwOnFailure"),
        )

        assertThat(interceptor.intercept(generated, null)).isEmpty()
    }

    @Test
    fun `keeps every mutant on code somebody wrote`() {
        val written = listOf(
            mutant("removed call to com/sprintstart/Repository::save"),
            mutant("replaced return value with null for a/Service::run"),
            mutant("negated conditional"),
        )

        assertThat(interceptor.intercept(written, null)).containsExactlyElementsOf(written)
    }

    @Test
    fun `is on without being named in the build`() {
        assertThat(KotlinGeneratedCodeFilter().provides().isOnByDefault).isTrue()
    }
}
