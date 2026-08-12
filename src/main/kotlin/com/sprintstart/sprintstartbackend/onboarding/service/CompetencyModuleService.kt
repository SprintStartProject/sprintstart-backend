package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContentProvenance
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModulePageKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModuleStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.VerificationType
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.ModuleProposalOutcome
import com.sprintstart.sprintstartbackend.onboarding.external.model.ProposedModuleSchema
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ModulePage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ModulePageCitation
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Verification
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.module.CreateCompetencyModuleRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.module.CreateModulePageRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.module.ReorderModulePagesRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.module.UpdateCompetencyModuleRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.module.UpdateModulePageRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.module.CompetencyModuleResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.module.CompetencyModulesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.module.ModulePageResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyModuleRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.ModulePageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.VerificationRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Authoring and lifecycle for [CompetencyModule] — the shared content a competency teaches.
 *
 * ⚠️ Proposal-only: authoring never changes what a hire sees until it is approved. A module is
 * written as a [ModuleStatus.DRAFT], offered for review as
 * [ModuleStatus.PROPOSED], and only [approve] makes one live — archiving whatever was live before,
 * so exactly one version per `(competency, project)` is ever [ModuleStatus.ACTIVE].
 *
 * ⚠️ Editing a live module means creating a new version from it, never mutating it — the
 * superseded version is the record of what earlier hires were taught.
 */
@Suppress("TooManyFunctions")
@Service
class CompetencyModuleService(
    private val competencyModuleRepository: CompetencyModuleRepository,
    private val modulePageRepository: ModulePageRepository,
    private val competencyRepository: CompetencyRepository,
    private val verificationRepository: VerificationRepository,
    private val userApi: UserApi,
    private val onboardingAiClient: OnboardingAiClient,
    private val json: Json,
    transactionManager: PlatformTransactionManager,
) {
    // The AI call is a long-running suspend operation, so it must not run inside a
    // transaction (a DB connection would be pinned for its whole duration).
    private val txTemplate = TransactionTemplate(transactionManager)
    private val readTxTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Starts a new module version for `(competencyKey, projectId)`.
     *
     * @param copyFromActive When true, the current ACTIVE version's pages and check are copied in.
     * @throws ResponseStatusException 404 if the competency does not exist — a module teaches one,
     * so there has to be one.
     */
    @Transactional
    fun create(request: CreateCompetencyModuleRequest): CompetencyModuleResponse {
        val competency = competencyRepository.findByKey(request.competencyKey)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No competency found with key: ${request.competencyKey}",
            )

        val nextVersion = competencyModuleRepository
            .findAllByCompetencyKeyAndProjectIdOrderByVersionDesc(request.competencyKey, request.projectId)
            .firstOrNull()
            ?.version
            ?.plus(1)
            ?: 1

        val module = CompetencyModule(
            competencyKey = request.competencyKey,
            projectId = request.projectId,
            version = nextVersion,
            status = ModuleStatus.DRAFT,
            origin = ContentProvenance.PM,
            title = request.title,
            summary = request.summary,
        )

        if (request.copyFromActive) {
            copyActiveInto(module)
        }

        competencyModuleRepository.save(module)
        return module.toResponse(competency.label, verificationRepository.findByModuleId(module.id)?.type)
    }

    /**
     * Asks the AI to draft the module for one competency, and stores it as a proposal.
     *
     * Proposal-only, like every other AI-authored artifact here: this never touches what is live.
     * The draft lands as [ModuleStatus.PROPOSED] with [ContentProvenance.AI] pages, so a later
     * re-synthesis can replace what the AI wrote while leaving anything a PM edited alone.
     *
     * ⚠️ The AI call runs outside any transaction; the surrounding reads and the write are their
     * own short ones. Idempotent per competency: the fingerprint of the last proposal for this
     * `(competency, project)` is sent, so an unchanged corpus returns `unchanged` and nothing new
     * is written.
     *
     * @return The stored proposal, or null when the AI had nothing to propose (empty corpus, no
     * grounded pages, or an unchanged corpus).
     * @throws ResponseStatusException 404 if the competency does not exist.
     */
    suspend fun proposeFromCorpus(competencyKey: String, projectId: UUID): CompetencyModuleResponse? {
        val context = withContext(Dispatchers.IO) {
            readTxTemplate.execute { loadProposalContext(competencyKey, projectId) }!!
        }

        val outcome = onboardingAiClient.proposeModule(
            competencyKey = competencyKey,
            competencyLabel = context.label,
            competencyDescription = context.description,
            level = context.level,
            lastFingerprint = context.lastFingerprint,
        )
        val proposed = outcome.module
        if (outcome.status != "proposed" || proposed == null) {
            logger.info(
                "No module proposed for {} in project {}: {}",
                competencyKey,
                projectId,
                outcome.notes.firstOrNull() ?: outcome.status,
            )
            return null
        }

        return withContext(Dispatchers.IO) {
            txTemplate.execute { persistProposal(competencyKey, projectId, proposed, outcome) }
        }
    }

    /**
     * Streams the AI drafting a module as [AiProgressEvent]s, persisting the proposal on `done`.
     *
     * The live twin of [proposeFromCorpus]. The AI's stage/item/warning events are relayed as-is;
     * on the terminal `done` the outcome is persisted with the very same [persistProposal] the
     * non-streaming path uses, before the event reaches the browser, so the stored proposal is
     * identical whether or not anyone watched. Nothing is stored for an empty corpus, an unchanged
     * corpus, or an ungroundable competency.
     *
     * @throws ResponseStatusException 404 if the competency does not exist.
     */
    suspend fun streamProposalFromCorpus(competencyKey: String, projectId: UUID): Flow<AiProgressEvent> {
        val context = withContext(Dispatchers.IO) {
            readTxTemplate.execute { loadProposalContext(competencyKey, projectId) }!!
        }
        return onboardingAiClient
            .streamModule(
                competencyKey = competencyKey,
                competencyLabel = context.label,
                competencyDescription = context.description,
                level = context.level,
                lastFingerprint = context.lastFingerprint,
            ).onEach { event ->
                if (event.type == AiProgressEvent.DONE) {
                    val outcome = decodeOutcome(event) ?: return@onEach
                    val proposed = outcome.module
                    if (outcome.status == "proposed" && proposed != null) {
                        withContext(Dispatchers.IO) {
                            txTemplate.execute { persistProposal(competencyKey, projectId, proposed, outcome) }
                        }
                    } else {
                        logger.info(
                            "No module proposed for {} in project {}: {}",
                            competencyKey,
                            projectId,
                            outcome.notes.firstOrNull() ?: outcome.status,
                        )
                    }
                }
            }.catch { cause ->
                logger.warn(
                    "Module stream failed for {} on project {}: {}",
                    competencyKey,
                    projectId,
                    cause.message,
                )
                emit(AiProgressEvent.error(MODULE, "Module generation is temporarily unavailable"))
            }
    }

    private fun decodeOutcome(event: AiProgressEvent): ModuleProposalOutcome? =
        event.result?.let {
            runCatching { json.decodeFromJsonElement<ModuleProposalOutcome>(it) }
                .onFailure { e -> logger.warn("Could not decode streamed module result: {}", e.message) }
                .getOrNull()
        }

    private data class ProposalContext(
        val label: String,
        val description: String,
        val level: String,
        val lastFingerprint: String?,
    )

    private fun loadProposalContext(competencyKey: String, projectId: UUID): ProposalContext {
        val competency = competencyRepository.findByKey(competencyKey)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No competency found with key: $competencyKey",
            )
        val previous = competencyModuleRepository
            .findAllByCompetencyKeyAndProjectIdOrderByVersionDesc(competencyKey, projectId)
            .firstOrNull()
        return ProposalContext(
            label = competency.label,
            description = competency.description.orEmpty(),
            // ⚠️ Teach to the competency's target level, so the module's depth and its check
            // agree.
            level = LEVEL_NAMES[competency.targetLevel] ?: LEVEL_NAMES.getValue(Competency.DEFAULT_TARGET_LEVEL),
            lastFingerprint = previous?.corpusFingerprint,
        )
    }

    private fun persistProposal(
        competencyKey: String,
        projectId: UUID,
        proposed: ProposedModuleSchema,
        outcome: ModuleProposalOutcome,
    ): CompetencyModuleResponse {
        val nextVersion = competencyModuleRepository
            .findAllByCompetencyKeyAndProjectIdOrderByVersionDesc(competencyKey, projectId)
            .firstOrNull()
            ?.version
            ?.plus(1)
            ?: 1

        val module = CompetencyModule(
            competencyKey = competencyKey,
            projectId = projectId,
            version = nextVersion,
            status = ModuleStatus.PROPOSED,
            origin = ContentProvenance.AI,
            title = proposed.title,
            summary = proposed.summary.takeIf { it.isNotBlank() },
            corpusFingerprint = outcome.provenance?.corpusFingerprint,
        )

        proposed.pages
            // ⚠️ A kind the enum does not know is dropped: the backend re-validates rather than
            // trusting the AI's own check against the same set.
            .mapNotNull { page -> parsePageKind(page.kind)?.let { it to page } }
            .forEachIndexed { index, (kind, page) ->
                val modulePage = ModulePage(
                    module = module,
                    kind = kind,
                    title = page.title,
                    body = page.body,
                    position = index,
                    provenance = ContentProvenance.AI,
                )
                // ⚠️ Citations must be persisted with the page — a page whose citations were
                // dropped reads as content somebody made up.
                page.citations.forEachIndexed { citationIndex, citation ->
                    modulePage.citations.add(
                        ModulePageCitation(
                            page = modulePage,
                            filename = citation.filename.trim(),
                            chunkId = citation.chunkId.trim(),
                            sourceUrl = citation.sourceUrl?.trim()?.takeIf { it.isNotBlank() },
                            position = citationIndex,
                        ),
                    )
                }
                module.pages.add(modulePage)
            }

        competencyModuleRepository.save(module)

        // ⚠️ Only real proof is persisted -- see HONEST_CHECK_TYPES. Anything else the AI
        // proposes as a check is dropped, and the module ships as content without a gate.
        proposed.verification
            ?.let { check -> parseVerificationType(check.type) to check }
            ?.takeIf { (type, _) -> type in HONEST_CHECK_TYPES }
            ?.let { (type, check) ->
                verificationRepository.save(
                    Verification(
                        moduleId = module.id,
                        type = type,
                        prompt = check.prompt,
                        rubric = check.rubric,
                        competencyKey = competencyKey,
                        level = LEVEL_NAMES[
                            competencyRepository.findByKey(competencyKey)?.targetLevel
                                ?: Competency.DEFAULT_TARGET_LEVEL,
                        ] ?: LEVEL_NAMES.getValue(Competency.DEFAULT_TARGET_LEVEL),
                    ),
                )
            }

        return module.toResponseWithJoins()
    }

    private fun parsePageKind(raw: String): ModulePageKind? =
        ModulePageKind.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }

    private fun parseVerificationType(raw: String): VerificationType =
        VerificationType.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
            ?: VerificationType.KNOWLEDGE

    /** Lists a project's modules in one status, for the authoring surface. */
    @Transactional(readOnly = true)
    fun list(projectId: UUID, status: ModuleStatus): CompetencyModulesResponse {
        val modules = competencyModuleRepository
            .findAllByProjectIdAndStatusOrderByCompetencyKeyAsc(projectId, status)
        return CompetencyModulesResponse(modules = modules.map { it.toResponseWithJoins() })
    }

    @Transactional(readOnly = true)
    fun get(moduleId: UUID): CompetencyModuleResponse = findModule(moduleId).toResponseWithJoins()

    /**
     * Returns a live module for the hire opening it.
     *
     * ⚠️ The access rule is project membership, not "this row belongs to you" — the module is
     * shared, so there is no per-user copy to match a hire against. A module that is not live is
     * reported as absent rather than forbidden.
     *
     * @throws ResponseStatusException 404 if no live module has that id; 403 if the user is not a
     * member of its project.
     */
    @Transactional(readOnly = true)
    fun getForMe(authId: String, moduleId: UUID): CompetencyModuleResponse {
        val module = findModule(moduleId)
        if (module.status != ModuleStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No module found with id: $moduleId")
        }
        if (!userApi.userHasAccessToProject(authId, module.projectId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a member of this module's project")
        }
        return module.toResponseWithJoins()
    }

    @Transactional
    fun update(moduleId: UUID, request: UpdateCompetencyModuleRequest): CompetencyModuleResponse {
        val module = findEditableModule(moduleId)
        request.title?.let { module.title = it }
        request.summary?.let { module.summary = it }
        module.updatedAt = Instant.now()
        return module.toResponseWithJoins()
    }

    /** Offers a draft for review; [approve] is a separate step. */
    @Transactional
    fun propose(moduleId: UUID): CompetencyModuleResponse {
        val module = findModule(moduleId)
        if (module.status != ModuleStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only a DRAFT module can be proposed")
        }
        module.status = ModuleStatus.PROPOSED
        module.updatedAt = Instant.now()
        return module.toResponseWithJoins()
    }

    /**
     * Makes this version the live module for its competency and project, archiving the previous
     * one. This is the only path by which what a hire sees changes.
     *
     * @throws ResponseStatusException 409 if the module is already archived, or if it has no
     * pages — an approved module with nothing in it opens to nothing.
     */
    @Transactional
    fun approve(moduleId: UUID): CompetencyModuleResponse {
        val module = findModule(moduleId)
        if (module.status == ModuleStatus.ARCHIVED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "An archived module cannot be approved")
        }
        if (module.status == ModuleStatus.ACTIVE) {
            return module.toResponseWithJoins()
        }
        if (module.pages.isEmpty()) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A module with no pages cannot be approved")
        }

        competencyModuleRepository
            .findByCompetencyKeyAndProjectIdAndStatus(module.competencyKey, module.projectId, ModuleStatus.ACTIVE)
            ?.let {
                it.status = ModuleStatus.ARCHIVED
                it.updatedAt = Instant.now()
            }

        module.status = ModuleStatus.ACTIVE
        module.updatedAt = Instant.now()
        return module.toResponseWithJoins()
    }

    /** Archives a version without activating it; whatever is live stays live. */
    @Transactional
    fun reject(moduleId: UUID, reason: String? = null): CompetencyModuleResponse {
        val module = findModule(moduleId)
        if (module.status == ModuleStatus.ACTIVE) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The live module cannot be rejected; approve a replacement instead",
            )
        }
        module.status = ModuleStatus.ARCHIVED
        module.updatedAt = Instant.now()
        logger.info(
            "Rejected module {} v{} for {} (reason: {})",
            module.id,
            module.version,
            module.competencyKey,
            reason ?: "none given",
        )
        return module.toResponseWithJoins()
    }

//  ========================== Pages ==========================

    @Transactional
    fun addPage(moduleId: UUID, request: CreateModulePageRequest): ModulePageResponse {
        val module = findEditableModule(moduleId)
        val page = ModulePage(
            module = module,
            kind = request.kind,
            title = request.title,
            body = request.body,
            position = request.position?.coerceIn(0, module.pages.size) ?: module.pages.size,
            provenance = ContentProvenance.PM,
        )
        module.pages.add(page)
        normalizePositions(module, movedTo = page)
        module.updatedAt = Instant.now()
        return page.toResponse()
    }

    @Transactional
    fun updatePage(pageId: UUID, request: UpdateModulePageRequest): ModulePageResponse {
        val page = findPage(pageId)
        requireEditable(page.module)
        request.kind?.let { page.kind = it }
        request.title?.let { page.title = it }
        request.body?.let { page.body = it }
        // A page a human touched is a page re-synthesis must leave alone. Editing is what makes it
        // PM-authored, whoever originally wrote it.
        page.provenance = ContentProvenance.PM
        page.updatedAt = Instant.now()
        page.module.updatedAt = Instant.now()
        return page.toResponse()
    }

    @Transactional
    fun deletePage(pageId: UUID) {
        val page = findPage(pageId)
        val module = page.module
        requireEditable(module)
        module.pages.remove(page)
        normalizePositions(module)
        module.updatedAt = Instant.now()
    }

    /**
     * Applies a complete new page order in one call.
     *
     * @throws ResponseStatusException 400 if [ReorderModulePagesRequest.pageIds] is not exactly the
     * module's current pages. A partial list is ambiguous about where the omitted pages go, and
     * silently appending them is how a reorder quietly loses a page.
     */
    @Transactional
    fun reorderPages(moduleId: UUID, request: ReorderModulePagesRequest): CompetencyModuleResponse {
        val module = findEditableModule(moduleId)
        val current = module.pages.associateBy { it.id }
        if (request.pageIds.toSet() != current.keys || request.pageIds.size != current.size) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "pageIds must list every page of this module exactly once",
            )
        }
        request.pageIds.forEachIndexed { index, pageId ->
            current.getValue(pageId).position = index
        }
        module.pages.sortBy { it.position }
        module.updatedAt = Instant.now()
        return module.toResponseWithJoins()
    }

//  ========================== Helpers ==========================

    /**
     * Copies the live version's pages and check into a new draft.
     *
     * Page provenance is carried over rather than reset: a PM-authored page stays PM-authored
     * across versions, which is what lets re-synthesis keep leaving it alone.
     */
    private fun copyActiveInto(module: CompetencyModule) {
        val active = competencyModuleRepository.findByCompetencyKeyAndProjectIdAndStatus(
            module.competencyKey,
            module.projectId,
            ModuleStatus.ACTIVE,
        ) ?: return

        active.pages.forEach { page ->
            module.pages.add(
                ModulePage(
                    module = module,
                    kind = page.kind,
                    title = page.title,
                    body = page.body,
                    position = page.position,
                    provenance = page.provenance,
                ),
            )
        }
        module.corpusFingerprint = active.corpusFingerprint

        verificationRepository.findByModuleId(active.id)?.let { check ->
            verificationRepository.save(
                Verification(
                    moduleId = module.id,
                    type = check.type,
                    prompt = check.prompt,
                    rubric = check.rubric,
                    canonicalAnswer = check.canonicalAnswer,
                    repositoryConnectionId = check.repositoryConnectionId,
                    competencyKey = check.competencyKey,
                    level = check.level,
                ),
            )
        }
    }

    /**
     * Renumbers positions to a dense 0..n-1 sequence, keeping [movedTo] at the slot it asked for.
     * ⚠️ Positions are an ordering, not identifiers: gaps and ties are corrected on every write.
     */
    private fun normalizePositions(module: CompetencyModule, movedTo: ModulePage? = null) {
        val ordered = module.pages.sortedWith(
            compareBy({ it.position }, { if (it === movedTo) 0 else 1 }),
        )
        ordered.forEachIndexed { index, page -> page.position = index }
        module.pages.sortBy { it.position }
    }

    /**
     * ⚠️ A live or archived version is a record of what hires were taught: it is never edited in
     * place, a new version is created from it instead.
     */
    private fun requireEditable(module: CompetencyModule) {
        if (module.status == ModuleStatus.ACTIVE || module.status == ModuleStatus.ARCHIVED) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Module ${module.id} is ${module.status}; create a new version to change it",
            )
        }
    }

    private fun findEditableModule(moduleId: UUID): CompetencyModule =
        findModule(moduleId).also { requireEditable(it) }

    private fun findModule(moduleId: UUID): CompetencyModule =
        competencyModuleRepository
            .findById(moduleId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No module found with id: $moduleId") }

    private fun findPage(pageId: UUID): ModulePage =
        modulePageRepository
            .findById(pageId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No module page found with id: $pageId") }

    private fun CompetencyModule.toResponseWithJoins(): CompetencyModuleResponse =
        toResponse(
            competencyLabel = competencyRepository.findByKey(competencyKey)?.label,
            verificationType = verificationRepository.findByModuleId(id)?.type,
        )
}

/** Ledger ranks back to the level names the AI service and Verification.level speak. */
private val LEVEL_NAMES = mapOf(1 to "beginner", 2 to "intermediate", 3 to "advanced", 4 to "expert")

/** The `operation` tag on this service's [AiProgressEvent]s. */
private const val MODULE = "module"

/**
 * The check types module generation will persist. ARTIFACT is real proof (a merged PR); ATTEST is
 * a self-confirmation for what cannot be shown in a PR. ⚠️ KNOWLEDGE and EXACT are recall gates and
 * are never auto-created; a module with no check ships without a gate.
 */
private val HONEST_CHECK_TYPES = setOf(VerificationType.ARTIFACT, VerificationType.ATTEST)

/** The page kinds that carry lesson prose, used as grounded evidence when grading a check. */
val LESSON_PAGE_KINDS = setOf(ModulePageKind.CONTEXT, ModulePageKind.LESSON, ModulePageKind.WALKTHROUGH)
