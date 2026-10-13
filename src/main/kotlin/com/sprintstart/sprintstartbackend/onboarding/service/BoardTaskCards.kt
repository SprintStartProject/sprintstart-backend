package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardPoolTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardSuggestedTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.SuggestedTasksContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.TaskPoolContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.RankedStarterWorkTaskResponse
import java.util.UUID

/**
 * The two board cards built from the ranked starter-work pool: good next tasks, and the whole pool.
 *
 * Pure projections of one ranking, which [BoardService] reads once per board and hands to both, so
 * the two cards cannot disagree about order or reasons. Neither carries the score.
 */
internal object BoardTaskCards {
    /** Matches the buddy tool's cap, so the card and the conversation list the same tasks. */
    const val MAX_SUGGESTED_TASKS = 3

    /**
     * Good next tasks: the top three, whatever they score.
     *
     * The pool's `bestFit` also asks for a score above zero, and that difference is deliberate. This
     * card answers "what should I pick up" and must not come up empty while the pool holds work;
     * `bestFit` answers "does this fit me", and a zero score is not a fit. So a task can be
     * suggested here without being marked there.
     */
    fun suggested(matches: List<RankedStarterWorkTaskResponse>): SuggestedTasksContent =
        SuggestedTasksContent(
            tasks = matches
                .take(MAX_SUGGESTED_TASKS)
                .map { match ->
                    BoardSuggestedTaskResponse(
                        taskId = match.task.id,
                        title = match.task.title,
                        url = match.task.sourceUrl,
                        reasons = match.reasons,
                    )
                },
        )

    /**
     * The whole pool, for the hire to pick from themselves.
     *
     * Uncapped on purpose — the point of the card is that nothing is hidden behind "ask your buddy".
     * The client scrolls and filters.
     */
    fun pool(matches: List<RankedStarterWorkTaskResponse>, currentTaskId: UUID?): TaskPoolContent =
        TaskPoolContent(
            tasks = matches.mapIndexed { index, match ->
                BoardPoolTaskResponse(
                    taskId = match.task.id,
                    title = match.task.title,
                    summary = match.task.summary,
                    rationale = match.task.rationale,
                    url = match.task.sourceUrl,
                    taskType = match.taskType,
                    reasons = match.reasons,
                    bestFit = index < MAX_SUGGESTED_TASKS && match.score > 0,
                    sourceHasAssignee = match.task.sourceHasAssignee,
                )
            },
            currentTaskId = currentTaskId,
        )
}
