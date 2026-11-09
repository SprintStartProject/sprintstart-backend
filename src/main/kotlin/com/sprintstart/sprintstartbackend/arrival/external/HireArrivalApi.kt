package com.sprintstart.sprintstartbackend.arrival.external

import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId

/** What a hire's assistant files in arrival on the hire's behalf. */
interface HireArrivalApi {
    /**
     * Asks [attesterId] to confirm that the hire did the work [title] names on [projectId], without an
     * evidence link. Answers with the title as it was stored, which is all the assistant repeats back.
     *
     * @throws org.springframework.web.server.ResponseStatusException 400 for a blank title or when the hire
     *   asks themselves, 404 when either is not on the project.
     */
    fun requestAttestation(hireId: UserId, projectId: ProjectId, title: String, attesterId: UserId): String
}
