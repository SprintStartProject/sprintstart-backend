package com.sprintstart.sprintstartbackend.onboarding.model

import java.util.UUID

/**
 * Translates between the scope names the backend stores and the project-qualified form the AI
 * service expects.
 *
 * The AI service identifies a blueprint by `project:<projectId>|<scope>` and ignores any blueprint
 * whose scope does not carry the requesting project — its steps were drafted from a corpus that
 * project may not be allowed to see.
 *
 * The backend deliberately stores the *bare* scope (`global`, `area:backend`) alongside a
 * `projectId` column rather than the qualified string: the scope is a path segment in the
 * blueprint routes, and neither `|` nor `:` survives there unencoded.
 *
 * Mirrors `src/onboarding/scope.py` in the AI service — keep the two in sync.
 */
object BlueprintScope {
    private const val PROJECT_PREFIX = "project:"
    private const val PROJECT_SEPARATOR = "|"

    /**
     * Qualifies [scope] with [projectId]. Already-qualified scopes are re-qualified rather than
     * nested, so passing a value that made a round trip through the AI service is safe.
     */
    fun qualify(projectId: UUID, scope: String): String =
        "$PROJECT_PREFIX$projectId$PROJECT_SEPARATOR${bare(scope)}"

    /**
     * Strips any project qualification, yielding the bare scope name the backend stores. Scopes
     * that carry no qualification — everything generated before project separation — pass through
     * unchanged.
     */
    fun bare(scope: String): String =
        if (scope.startsWith(PROJECT_PREFIX) && scope.contains(PROJECT_SEPARATOR)) {
            scope.substringAfter(PROJECT_SEPARATOR)
        } else {
            scope
        }
}
