package com.sprintstart.sprintstartbackend.board.assistant

import com.sprintstart.sprintstartbackend.assistant.external.projectsOf
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.UserApi

/**
 * The one project this hire is onboarding on, or the reason there is no answer.
 *
 * Scoped like every action the buddy takes: a board belongs to one project, and guessing which
 * one somebody meant is how a card lands on the wrong board — or, for a read, how the mentor
 * describes a board the hire is not looking at.
 */
internal fun UserApi.soleProjectOf(userId: UserId): ProjectChoice {
    val projects = projectsOf(userId)

    return when (projects.size) {
        0 -> ProjectChoice.Refused("The hire is not on a project yet, so there is no board.")
        1 -> ProjectChoice.One(ProjectId(projects.first().projectId), projects.first().name)
        else -> ProjectChoice.Refused(
            "The hire is onboarding on more than one project. Ask which one before saying " +
                "anything about their board.",
        )
    }
}

internal sealed interface ProjectChoice {
    data class One(
        val projectId: ProjectId,
        val name: String,
    ) : ProjectChoice

    data class Refused(
        val reason: String,
    ) : ProjectChoice
}

/**
 * How many cards one list here may name.
 *
 * It was six, on the reasoning that a mentor reading out fourteen titles has turned a
 * conversation into a listing. That reasoning is right and this was the wrong place to act
 * on it: what a mentor *says* is governed by the tool's description, which tells it not to
 * read the list back; what this cap governs is what the mentor is allowed to *know*. Six
 * meant that on a board of twenty-four cards it could see six, and a hire asking "do I have
 * a card about the pipeline" got a guess — a confident one, about a card that was there.
 *
 * High enough that a real board fits under it, and still a cap, because a runaway board
 * should cost a long answer rather than an unbounded prompt.
 */
internal const val LIST_LIMIT = 40
