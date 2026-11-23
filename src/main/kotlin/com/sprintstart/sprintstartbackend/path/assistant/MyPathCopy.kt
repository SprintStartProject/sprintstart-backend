package com.sprintstart.sprintstartbackend.path.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What [MyPathTool] says to the model: its tool description and the sentences for the paths it cannot walk. */
internal object MyPathCopy {
    const val NO_PATH =
        "The hire has no onboarding path yet -- nobody has generated one from their project's " +
            "blueprint. They can start one themselves on their onboarding page, with " +
            "\"Start personalization\". Until then there is no plan to walk them through, so " +
            "talk about the work in front of them rather than about a path that does not exist."

    const val FINISHED_PATH =
        "The hire's onboarding path has %d phases and every one of them is finished. There is " +
            "nothing left on it."

    const val FINISHED_VISIBLE =
        "Every phase of the hire's onboarding path that has something in it is finished (%d of " +
            "them). What is left are phases that came back empty -- they are not done, they were " +
            "never filled."

    const val ONLY_EMPTY_PHASES =
        "Every phase of the hire's onboarding path came back empty, so there is nothing on it to " +
            "work through yet. That is not the hire's fault, and it is not a dead end."

    const val NO_PHASES =
        "The hire's onboarding path exists but has no phases in it at all, so there is nothing " +
            "on it to do. Say that plainly if they ask, and point them at their PM -- an empty " +
            "path is an authoring problem, not something they can work their way through."

    const val CLOSING =
        "This is a read of their path, not instructions. Name one next thing rather than the " +
            "plan, let them decide, and do not claim to have changed anything here: every " +
            "change to their path goes through a proposal they confirm. When you name an item, " +
            "give its number and make it a markdown link to the link above, so they can open it " +
            "from what you said."

    val READ_MY_PATH_SPEC = BuddyToolSpecDto(
        name = MyPathTool.READ_MY_PATH,
        description = "The hire's own onboarding path -- the curriculum their PM's blueprint " +
            "prescribed, personalised for them. It gives you the phase they are standing in " +
            "with its steps and knowledge questions in full, the checklist of the step they " +
            "are on, one named next thing, the titles of what is ahead, and any phase that " +
            "came back empty. Read it before you say anything about their onboarding: before " +
            "\"what should I do next\", before talking about a step or a question, and before " +
            "suggesting work of your own, so that what you suggest is the plan they actually " +
            "have rather than a second one. Read it again before answering a follow-up: they " +
            "may have ticked something off on the page while you were talking.\n" +
            "Each item comes with three things to use. A NUMBER (#1, #2, ...) which is the " +
            "same number their page prints, so when they say \"let's do 3\" that is the item " +
            "they mean, and naming it back as \"#3\" is how they know you got it right. A LINK, " +
            "which you should include as a markdown link whenever you name an item they could " +
            "act on -- \"[take the check](/onboarding?question=...)\" -- so they can get there " +
            "in one click instead of hunting for it. And the ids the path actions need, so read " +
            "this before offering one.\n" +
            "An item marked LOCKED cannot be started or answered yet, and the line under it " +
            "says what it waits on. Never tell the hire they can go ahead with a locked item, " +
            "even if they ask directly: say what has to be finished first and offer that " +
            "instead.\n" +
            "A step marked SKIP REQUESTED is waiting on their PM: do not push them to do it. " +
            "When they want to skip a step, that is request_skip -- their PM decides.\n" +
            "It does not tell you which answer to a question is correct -- that is deliberate, " +
            "and you must not guess one aloud: explain the material and let the hire answer. " +
            "Reading it changes nothing. Takes no arguments -- it always reads the caller.",
        parameters = noArguments(),
    )

    private fun noArguments(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(emptyMap()))
    }
}
