package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * The proficiency scale a competency level is expressed on, in words.
 *
 * The ledger stores a rank (`UserCompetencyState.level`, 0..4); this is the same scale named. The
 * words are the AI service's `SKILL_LEVELS` verbatim, and the ranks are its mapping, so a level
 * placed in conversation and a level read back out of the ledger mean the same thing.
 *
 * Words rather than a number wherever a model supplies the level: "intermediate" is a judgement a
 * reasoner can make about somebody it has just talked to, and 3-out-of-4 is not. Rank 0 has no word
 * on purpose — it means *not yet placed*, which is a state the ledger records and nobody asserts.
 */
enum class ProficiencyLevel(
    val word: String,
    val rank: Int,
) {
    BEGINNER("beginner", 1),
    INTERMEDIATE("intermediate", 2),
    ADVANCED("advanced", 3),
    EXPERT("expert", 4),
    ;

    companion object {
        /** The level [word] names, case- and space-insensitively, or null when it names none. */
        fun fromWord(word: String): ProficiencyLevel? =
            entries.firstOrNull { it.word == word.trim().lowercase() }

        /** The words a model may use, for a tool description and a refusal message to list. */
        val WORDS: List<String> = entries.map { it.word }
    }
}
