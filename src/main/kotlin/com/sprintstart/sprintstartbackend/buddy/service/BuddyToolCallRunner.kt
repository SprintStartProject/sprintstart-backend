package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEventType
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.HireActionProposal
import com.sprintstart.sprintstartbackend.assistant.external.toolUseEvent
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import kotlinx.coroutines.flow.FlowCollector

/** Runs the tools the AI hands back during one buddy reply, on behalf of the resolved caller. */
internal class BuddyToolCallRunner(
    private val buddyToolExecutor: BuddyToolExecutor,
    private val buddyActionService: BuddyActionService,
) {
    /**
     * Runs one tool the AI asked for, emitting the event(s) the client needs to see, and returns
     * the plain-text result fed back to the model. An action tool never mutates here: it produces
     * a proposal the hire gates behind a confirm button, and the AI is told it was proposed.
     */
    suspend fun run(
        collector: FlowCollector<BuddyStreamEvent>,
        call: BuddyToolCallDto,
        user: UserId,
        currentPage: String?,
    ): String =
        if (buddyActionService.isAction(call.name)) {
            val outcome = buddyActionService.propose(call, user.value)
            outcome.proposal?.let { collector.emit(it.toStreamEvent()) }
            outcome.toolResult
        } else {
            collector.emit(toolUseEvent(call.name))
            buddyToolExecutor.execute(call, user.value, currentPage)
        }
}

/**
 * The `action_proposal` event for a proposal. Every field the proposal carries must be copied
 * through; a dropped one surfaces as the action politely refusing, not as an error.
 */
internal fun HireActionProposal.toStreamEvent(): BuddyStreamEvent =
    BuddyStreamEvent(
        type = BuddyStreamEventType.ACTION_PROPOSAL,
        action = action,
        label = label,
        question = question,
        taskId = taskId?.toString(),
        title = title,
        attesterId = attesterId,
        githubLogin = githubLogin,
        competencyKey = competencyKey,
        level = level,
        stepId = stepId?.toString(),
        questionId = questionId?.toString(),
        phaseId = phaseId?.toString(),
        onboardingTaskId = onboardingTaskId?.toString(),
        answer = answer,
        optionIds = optionIds.takeIf { it.isNotEmpty() }?.map { it.toString() },
        description = description,
        reason = reason,
        waitsOnIds = waitsOnIds.takeIf { it.isNotEmpty() }?.map { it.toString() },
        unlocksIds = unlocksIds.takeIf { it.isNotEmpty() }?.map { it.toString() },
    ).withBoardFields(this)

/** The board edits' share of the `action_proposal` event. */
private fun BuddyStreamEvent.withBoardFields(proposal: HireActionProposal): BuddyStreamEvent =
    copy(
        checklistTitle = proposal.checklistTitle,
        checklistItems = proposal.checklistItems,
        cardId = proposal.cardId?.toString(),
        noteText = proposal.noteText,
        lineBefore = proposal.lineBefore,
        lineAfter = proposal.lineAfter,
        linkUrl = proposal.linkUrl,
        linkLabel = proposal.linkLabel,
        cardIds = proposal.cardIds?.map { it.toString() },
        cardNames = proposal.cardNames,
        basedOn = proposal.basedOn,
        preview = proposal.preview,
    )
