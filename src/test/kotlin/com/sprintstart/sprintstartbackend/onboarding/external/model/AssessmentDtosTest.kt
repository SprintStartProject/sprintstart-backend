package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Regression coverage for a real production failure: the AI service's own schema declares
 * `targets`/`coverage`/`assessments` as `list[...] | None` (see `sprintstart-ai`'s
 * `AssessmentTurnResponse`), sent as explicit JSON `null` depending on `done`. kotlinx.serialization
 * field defaults only apply when a key is *absent*, not when it's present with value `null`, so a
 * non-nullable `List` type here throws `JsonDecodingException` on an otherwise valid response
 * (confirmed via a real `/onboarding/me/assessment/start` 500).
 */
class AssessmentDtosTest {
    @Test
    fun `decodes a still-interviewing turn where assessments is explicitly null`() {
        val json =
            """{"done":false,"question":"Next question?","targets":["kotlin"],"coverage":[],"assessments":null}"""

        val result = Json.decodeFromString<AssessmentTurnResponse>(json)

        assertThat(result.done).isFalse()
        assertThat(result.question).isEqualTo("Next question?")
        assertThat(result.targets).containsExactly("kotlin")
        assertThat(result.coverage).isEmpty()
        assertThat(result.assessments).isNull()
    }

    @Test
    fun `decodes a final turn where targets and coverage are explicitly null`() {
        val json =
            """{"done":true,"targets":null,"coverage":null,""" +
                """"assessments":[{"key":"kotlin","level":"advanced","confidence":0.8,"evidence":"e"}]}"""

        val result = Json.decodeFromString<AssessmentTurnResponse>(json)

        assertThat(result.done).isTrue()
        assertThat(result.targets).isNull()
        assertThat(result.coverage).isNull()
        assertThat(result.assessments).hasSize(1)
    }
}
