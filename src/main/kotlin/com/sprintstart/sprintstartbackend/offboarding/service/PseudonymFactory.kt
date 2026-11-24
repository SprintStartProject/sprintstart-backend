package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.offboarding.scoring.Pseudonyms
import com.sprintstart.sprintstartbackend.shared.config.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Component

/** Hands out the pseudonyms of one project's people. */
@Component
class PseudonymFactory(
    config: ApplicationConfig,
) {
    private val key: ByteArray = Pseudonyms.keyFrom(config.crypto.masterKey)

    /** The pseudonyms of [projectId]'s people; unique to the project and stable as long as the secret is. */
    fun forProject(projectId: ProjectId): Pseudonyms = Pseudonyms(key, projectId.value.toString())
}
