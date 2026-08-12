package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModuleStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyModuleRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.VerificationRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The buddy's plan-and-material tools: what the hire should learn next, and the content to teach
 * it from.
 *
 * The competency graph stopped being a hire-facing UI and became the buddy's working memory. With
 * its structure retired it is a **learning area**, not a curriculum: [GET_LEARNING_PLAN] reads what
 * this project teaches — the competencies with a published module — against the hire's own ledger,
 * and reports what they already hold and where the gaps are. Reasons, never scores.
 *
 * There is deliberately **no ordering**. Prerequisite edges ranked rather than gated, were mined
 * from a code corpus, and reached the hire through a `PathEdge` that had dropped the edge kind — so
 * a `RELATED` edge, the kind that explicitly means *not* ordering, could be spoken as "usually comes
 * after". Sequencing a hire's learning is now the model's judgement in conversation, where it can be
 * questioned, rather than a DAG's assertion that could not be.
 *
 * [GET_MODULE] hands the buddy a published module's pages with their citations, so teaching stays
 * grounded in real material rather than improvised. Both run strictly on behalf of the resolved
 * caller, like every buddy tool.
 */
@Component
class BuddyPlanTools(
    private val competencyModuleRepository: CompetencyModuleRepository,
    private val competencyRepository: CompetencyRepository,
    private val userCompetencyStateRepository: UserCompetencyStateRepository,
    private val userGoalService: UserGoalService,
    private val verificationRepository: VerificationRepository,
    private val userApi: UserApi,
) {
    /** The tool specs this component owns, aggregated into the buddy's catalog by the executor. */
    fun toolSpecs(): List<BuddyToolSpecDto> = listOf(GET_LEARNING_PLAN_SPEC, GET_MODULE_SPEC)

    /** Whether [toolName] is one of this component's tools. */
    fun handles(toolName: String): Boolean = toolName == GET_LEARNING_PLAN || toolName == GET_MODULE

    /** Executes [call] on behalf of [userId], returning a plain-text result for the model. */
    fun execute(call: BuddyToolCallDto, userId: UUID): String =
        when (call.name) {
            GET_LEARNING_PLAN -> getLearningPlan(userId)
            GET_MODULE -> getModule(userId, call.stringArg("competency_key"))
            else -> "Unknown tool: ${call.name}."
        }

    private fun getLearningPlan(userId: UUID): String {
        val projects = userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
        if (projects.isEmpty()) {
            return "You are not a member of any project yet, so there is no learning plan."
        }
        val ledger = userCompetencyStateRepository
            .findAllByUserId(userId)
            .associate { it.competencyKey to it.level }

        return projects
            .map { project -> describePlan(userId, project.projectId, project.name, ledger) }
            .joinToString("\n\n")
    }

    /**
     * What this project teaches, against what the hire already holds.
     *
     * The learning area is exactly the competencies with a **published module** on this project.
     * Naming a gap with no material behind it would be telling a hire they are missing something and
     * then having nothing to offer; anything else they need to know is what `search_docs` is for.
     */
    private fun describePlan(
        userId: UUID,
        projectId: UUID,
        projectName: String,
        ledger: Map<String, Int>,
    ): String {
        val modules = competencyModuleRepository.findAllByProjectIdAndStatus(projectId, ModuleStatus.ACTIVE)
        val goal = userGoalService.findForUser(userId, projectId)
        val goalLine = goal
            ?.let { "- Working toward: ${it.title}" }
            ?: "- Working toward: nothing claimed yet — offer to claim a goal."

        if (modules.isEmpty()) {
            return "On $projectName nothing has a published module yet, so there is no learning " +
                "area to teach from — answer from the docs with search_docs instead.\n$goalLine"
        }

        val moduleByKey = modules.associateBy { it.competencyKey }
        val competencies = competencyRepository
            .findAllByKeyIn(moduleByKey.keys.toList())
            .sortedBy { it.label }
        val (held, gaps) = competencies.partition { (ledger[it.key] ?: 0) >= it.targetLevel }

        return buildString {
            appendLine("Learning area on $projectName:")
            appendLine(goalLine)

            if (gaps.isEmpty()) {
                appendLine("No gaps — they already meet everything this project teaches.")
            } else {
                appendLine("Gaps:")
                appendGaps(gaps, ledger, moduleByKey)
            }

            if (held.isNotEmpty()) {
                appendLine("Already met: ${held.joinToString(", ") { it.label }}.")
            }
            appendLine(
                "This is a shelf, not an order to follow. Bring one up when it would help with " +
                    "what they are actually working on, and say why it is relevant then. The " +
                    "headings group what each one is about, so if they are working in one area you " +
                    "can offer its neighbours — they are related subjects, not steps in a sequence.",
            )
        }.trim()
    }

    /**
     * The gaps, grouped by what they are *about*.
     *
     * Grouping is what replaced prerequisite structure, and it exists to answer the question the
     * buddy actually gets asked — *"what else is about auth?"* — which ordering never did. It is
     * presented as headings rather than a flat list so the model can offer a neighbour of what a
     * hire is already touching, instead of reading a shelf out in alphabetical order.
     *
     * **Headings only appear once something is grouped.** A hand-authored vocabulary is mostly
     * ungrouped, and wrapping every gap in an "Ungrouped" heading would add a layer that carries no
     * information. Ungrouped gaps are listed plainly, after the
     * grouped ones — they are not a category, they are the ones nobody has said anything about yet.
     */
    private fun StringBuilder.appendGaps(
        gaps: List<Competency>,
        ledger: Map<String, Int>,
        moduleByKey: Map<String, CompetencyModule>,
    ) {
        val (grouped, ungrouped) = gaps.partition { it.area != null }

        grouped
            .groupBy { it.area.orEmpty() }
            .toSortedMap()
            .forEach { (area, inArea) ->
                appendLine("$area:")
                inArea.forEach { appendLine("- ${describeGap(it, ledger, moduleByKey)}") }
            }

        if (ungrouped.isNotEmpty()) {
            if (grouped.isNotEmpty()) appendLine("Not grouped into an area yet:")
            ungrouped.forEach { appendLine("- ${describeGap(it, ledger, moduleByKey)}") }
        }
    }

    /**
     * One gap: what it is, how far along they are, and the material that covers it.
     *
     * Stated as a level against its bar rather than as a score — the #74 rule is that a hire hears
     * reasons, never a number standing in for one.
     */
    private fun describeGap(
        competency: Competency,
        ledger: Map<String, Int>,
        moduleByKey: Map<String, CompetencyModule>,
    ): String {
        val level = ledger[competency.key] ?: 0
        val progress = if (level == 0) {
            "no evidence yet"
        } else {
            "at level $level of ${competency.targetLevel}"
        }
        val material = moduleByKey[competency.key]
            ?.let { " Module: “${it.title}” — offer to teach it." }
            .orEmpty()
        return "${competency.label} ($progress).$material"
    }

    private fun getModule(userId: UUID, competencyKey: String): String {
        if (competencyKey.isBlank()) {
            return "No competency_key was provided. Ask the plan which competency to teach first."
        }
        val projects = userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
        if (projects.isEmpty()) {
            return "The hire is not a member of any project yet, so there is no module to teach."
        }
        val described = projects.mapNotNull { project ->
            competencyModuleRepository
                .findByCompetencyKeyAndProjectIdAndStatus(competencyKey, project.projectId, ModuleStatus.ACTIVE)
                ?.let { module -> describeModule(project.name, module) }
        }
        return described
            .ifEmpty {
                listOf(
                    "No published module teaches '$competencyKey' on the hire's project(s). Say so " +
                        "plainly, teach from the docs with search_docs instead, and never invent " +
                        "module content.",
                )
            }.joinToString("\n\n")
    }

    /**
     * The module as teaching material: pages in order, each with its citations, plus what the
     * check *asks* (never the rubric or expected answer — that is what the hire is graded
     * against, so it is not the buddy's to reveal).
     */
    private fun describeModule(projectName: String, module: CompetencyModule): String = buildString {
        appendLine("Module “${module.title}” (project: $projectName, id: ${module.id})")
        module.summary?.let { appendLine(it) }

        val check = verificationRepository.findByModuleId(module.id)
        if (check != null) {
            appendLine("Check to pass (${check.type}): “${check.prompt}”")
            appendLine("When the hire has done the work, offer to submit their answer with submit_verification.")
        } else {
            appendLine("No check is configured for this module — it teaches, it does not gate.")
        }

        appendLine("Pages:")
        module.pages.forEachIndexed { index, page ->
            appendLine("${index + 1}. [${page.kind}] ${page.title}")
            page.body?.takeIf { it.isNotBlank() }?.let { body ->
                appendLine(body.take(MAX_PAGE_BODY_CHARS) + if (body.length > MAX_PAGE_BODY_CHARS) " …" else "")
            }
            if (page.citations.isNotEmpty()) {
                val sources = page.citations.joinToString("; ") { citation ->
                    citation.filename + (citation.sourceUrl?.let { " ($it)" } ?: "")
                }
                appendLine("   Sources: $sources")
            }
        }
    }.trim()

    /** Reads a string argument the model passed to a tool, or "" when it is missing/non-text. */
    private fun BuddyToolCallDto.stringArg(name: String): String =
        (arguments[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

    /** Not private for the reason `BuddyToolExecutor`'s is not: the suggestion chips bind to these. */
    companion object {
        const val GET_LEARNING_PLAN = "get_learning_plan"
        const val GET_MODULE = "get_module"

        // Page bodies are capped so one tool result can't crowd out the whole prompt; the buddy
        // teaches, it does not need to quote the page verbatim end to end.
        const val MAX_PAGE_BODY_CHARS = 1500

        val GET_LEARNING_PLAN_SPEC = BuddyToolSpecDto(
            name = GET_LEARNING_PLAN,
            description = "The hire's learning area on their project(s): what they are working " +
                "toward (the goal they claimed), which competencies this project teaches, how far " +
                "along they are against each one's target level, and which module covers it. " +
                "Consult this BEFORE offering to teach something, so you only offer material that " +
                "exists. It is a shelf, not a sequence — there is no prescribed order, so bring a " +
                "gap up when it is relevant to what the hire is actually doing and say why it is " +
                "relevant. Never read a level out as a score. Takes no arguments — it always reads " +
                "the caller.",
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { })
            },
        )

        val GET_MODULE_SPEC = BuddyToolSpecDto(
            name = GET_MODULE,
            description = "The published module that teaches one competency: its ordered pages " +
                "with their cited sources, and what its check asks. Use this to teach a " +
                "competency from the shared, PM-approved material, and cite the sources it gives. " +
                "If it answers that no module exists, teach from the docs instead — never " +
                "fabricate module content.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("competency_key") {
                        put("type", "string")
                        put(
                            "description",
                            "The stable key of the competency to teach, as the learning plan names it.",
                        )
                    }
                }
                putJsonArray("required") { add("competency_key") }
            },
        )
    }
}
