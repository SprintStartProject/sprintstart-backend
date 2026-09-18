package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OnboardingGeneratedContentMappingTest {
    @Test
    fun `AI-populated free-form fields use text columns`() {
        val fields = listOf(
            OnboardingPhase::class.java to "title",
            OnboardingPhase::class.java to "description",
            OnboardingSubGraphNode::class.java to "title",
            OnboardingTask::class.java to "title",
            OnboardingTask::class.java to "description",
            OnboardingResource::class.java to "title",
            OnboardingResource::class.java to "description",
            OnboardingResource::class.java to "url",
            PhaseCheckOption::class.java to "label",
        )

        fields.forEach { (entity, fieldName) ->
            val column = entity.getDeclaredField(fieldName).getAnnotation(Column::class.java)
            assertEquals(
                "TEXT",
                column.columnDefinition,
                "${entity.simpleName}.$fieldName must support generated content longer than 255 characters",
            )
        }
    }
}
