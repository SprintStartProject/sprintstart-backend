package com.sprintstart.sprintstartbackend.arrival.external

import com.sprintstart.sprintstartbackend.arrival.external.enums.ArrivalDerivation
import com.sprintstart.sprintstartbackend.arrival.external.model.ArrivalStepChange
import com.sprintstart.sprintstartbackend.arrival.external.model.NewArrivalStep
import com.sprintstart.sprintstartbackend.arrival.model.response.arrival.ArrivalStepResponse
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId

/**
 * A project's arrival steps as team mode authors them. Steps are known by their key, unique within the
 * project; a project step replaces the company-wide step of the same key for everybody on the project.
 *
 * A write that breaks a rule (a malformed or taken key, an unknown step) throws a
 * [org.springframework.web.server.ResponseStatusException] whose reason is a sentence to relay.
 */
interface ArrivalAuthoringApi {
    /** The steps authored on [projectId], or the company-wide ones for null. */
    fun authoredOn(projectId: ProjectId?): List<ArrivalStepResponse>

    /** The step [key] names on [projectId], or null. */
    fun stepOn(key: String, projectId: ProjectId): ArrivalStepResponse?

    /** Each derivation the system can settle a step by, and whether it can on [projectId]. */
    fun derivable(projectId: ProjectId): List<Pair<ArrivalDerivation, Boolean>>

    /** Creates [steps] on [projectId], after the steps already there. */
    fun createAll(projectId: ProjectId, steps: List<NewArrivalStep>): List<ArrivalStepResponse>

    /** Applies [change] to the step [key] on [projectId]. */
    fun update(key: String, projectId: ProjectId, change: ArrivalStepChange): ArrivalStepResponse

    /** Puts the steps on [projectId] in [orderedKeys]' order. */
    fun reorder(projectId: ProjectId, orderedKeys: List<String>): List<ArrivalStepResponse>

    /** Deletes the step [key] on [projectId]; a company-wide step of that key shows again. */
    fun delete(key: String, projectId: ProjectId)

    companion object {
        /**
         * The shape of a step key.
         *
         * Exposed so a caller that wants to refuse a malformed key in its own words answers by the
         * same rule arrival enforces, instead of keeping a copy that can drift from it.
         */
        val KEY = Regex("^[a-z\\d][a-z\\d_-]{0,63}$")
    }
}
