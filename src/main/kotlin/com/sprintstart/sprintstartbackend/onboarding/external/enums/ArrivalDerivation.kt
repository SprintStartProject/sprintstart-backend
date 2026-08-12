package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * The arrival steps the system can check for itself, rather than taking the hire's word for.
 *
 * ⚠️ **A closed catalog, not a field on the row.** A derivation is code, so a derived step cannot
 * be authored freely — the step's own [stepKey] binds a row to its derivation. There is no column
 * pointing at a deriver, because a column could name one that does not exist.
 *
 * ⚠️ Nothing here is seeded; an admin adds the ones their organisation wants.
 *
 * ⚠️ **Observing settles; failing to observe never refutes.** Every derivation answers *"can I see
 * that this is done?"*, and **no** is always "not that I can see". That is why [selfConfirmable] is
 * per derivation rather than following from being derived at all.
 */
enum class ArrivalDerivation(
    val stepKey: String,
    val suggestedTitle: String,
    val suggestedDescription: String,
    val selfConfirmable: Boolean,
) {
    /**
     * The hire has declared a GitHub login, and GitHub confirms an account with that name exists.
     *
     * ⚠️ Checked rather than trusted: this value is what artifact verification compares a pull
     * request's author against, and a typo silently stops crediting work the hire really did.
     *
     * ⚠️ **Not self-confirmable.** The check is definitive when it answers, so letting somebody
     * tick it would let them declare away the one fact their credit depends on.
     */
    GITHUB_ACCOUNT(
        stepKey = "github-account",
        suggestedTitle = "Add your GitHub username",
        suggestedDescription =
            "So work you push can be recognised as yours. Add it in Settings, or from this card.",
        selfConfirmable = false,
    ),

    /**
     * The hire has produced work on a project, which means their environment evidently runs.
     *
     * ⚠️ Derived, never stored separately: a contribution already proves the environment worked.
     *
     * ⚠️ **Self-confirmable, and that is the important half.** The evidence arrives *late* — by the
     * time somebody has opened a pull request, getting set up is days behind them — so the hire's
     * own "it builds" is what lands on day one and the derivation is a backstop.
     */
    ENVIRONMENT_READY(
        stepKey = "environment-ready",
        suggestedTitle = "Get the project running on your machine",
        suggestedDescription =
            "Clone it, install what it needs, and run the build once. Stuck? Ask your buddy.",
        selfConfirmable = true,
    ),
    ;

    companion object {
        fun forStepKey(stepKey: String): ArrivalDerivation? = entries.firstOrNull { it.stepKey == stepKey }
    }
}
