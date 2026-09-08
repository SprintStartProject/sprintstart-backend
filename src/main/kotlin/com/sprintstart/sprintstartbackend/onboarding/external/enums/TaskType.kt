package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * What kind of work a task is.
 *
 * R7 found that the first issues one newcomer resolves cluster by task type, so this is a ranking
 * signal rather than decoration. Derived from an issue's own labels — the project already labels
 * its work, and inferring the type from the title would be guessing where evidence exists.
 *
 * [OTHER] is the honest answer for an unlabelled issue: it means *we do not know*, and it must not
 * be treated as a type a hire has or has not worked on.
 */
enum class TaskType(
    val label: String,
) {
    BUG("bug fix"),
    FEATURE("feature"),
    DOCS("documentation"),
    TEST("testing"),
    CHORE("maintenance"),
    OTHER("general"),
    ;

    companion object {
        // Substring matches, because label vocabularies vary ("bug", "type: bug", "kind/bug").
        private val PATTERNS: List<Pair<TaskType, List<String>>> = listOf(
            TaskType.DOCS to listOf("doc", "readme", "typo"),
            TaskType.TEST to listOf("test", "coverage"),
            TaskType.BUG to listOf("bug", "defect", "fix", "regression"),
            TaskType.FEATURE to listOf("feature", "enhancement", "improvement"),
            TaskType.CHORE to listOf("chore", "refactor", "cleanup", "dependencies", "maintenance"),
        )

        /**
         * The task type a set of labels indicates, or [OTHER] when none do.
         *
         * Order matters: an issue labelled both `bug` and `documentation` is a docs task, because
         * the narrower label is the more informative one about what somebody will actually do.
         */
        fun fromLabels(labels: Collection<String>): TaskType {
            val normalized = labels.map { it.lowercase() }
            return PATTERNS
                .firstOrNull { (_, patterns) ->
                    normalized.any { label -> patterns.any { label.contains(it) } }
                }?.first ?: OTHER
        }
    }
}
