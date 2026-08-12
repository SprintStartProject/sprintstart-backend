package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContentProvenance
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyTombstone
import com.sprintstart.sprintstartbackend.onboarding.model.request.competency.CreateCompetencyRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.competency.UpdateCompetencyRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.CompetencyGraphResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.CompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.DeleteCompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyTombstoneRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/**
 * Authoring the competency vocabulary: reading it, adding a competency, editing one, removing one.
 *
 * ⚠️ **A list, not a graph. Nothing orders it** — there are no edges, and a structure claiming one
 * thing "usually comes after" another is a claim nothing here can support.
 *
 * ⚠️ **Removal is a real delete**, and two things survive it because both are keyed by the
 * competency *key* rather than by a foreign key: the **ledger** (nobody un-earns a competency
 * because somebody tidied the vocabulary) and its **modules** (which stop appearing until a
 * competency with that key exists again, and re-adding it restores them).
 *
 * ⚠️ **Deletion is sticky.** Removing a competency writes a [CompetencyTombstone] and the generator
 * is given those as exclusions, or a removed competency returns on the next crawl under a
 * rephrasing. A person re-adding the same key clears it — it binds the generator, not somebody who
 * changed their mind.
 *
 * ⚠️ Every write here marks the row `PM`, and regeneration must leave those alone.
 */
@Service
class CompetencyGraphAuthoringService(
    private val competencyRepository: CompetencyRepository,
    private val tombstoneRepository: CompetencyTombstoneRepository,
    private val areaNormalizer: CompetencyAreaNormalizer,
) {
    /**
     * Reads one competency, so an editor can show what it currently says.
     *
     * @throws ResponseStatusException 404 if no competency has [key].
     */
    @Transactional(readOnly = true)
    fun getCompetency(key: String): CompetencyResponse = findCompetency(key).toAuthoringResponse()

    /** The whole vocabulary — what a PM authors against. */
    @Transactional(readOnly = true)
    fun getGraph(): CompetencyGraphResponse =
        CompetencyGraphResponse(
            competencies = competencyRepository.findAll().map { it.toAuthoringResponse() },
        )

    /**
     * Creates a hand-authored competency, with no AI proposal in the loop.
     *
     * This is what makes the AI genuinely optional rather than merely reviewable: a PM who wants a
     * competency the generator never suggested can add one.
     *
     * The key is slugified (see [CreateCompetencyRequest]) so it matches the house style and is
     * URL-safe.
     *
     * @throws ResponseStatusException 400 if the key or label is blank or `targetLevel` is outside
     * 1..4; 409 if a competency already has this key.
     */
    @Transactional
    fun createCompetency(request: CreateCompetencyRequest): CompetencyResponse {
        val key = slugifyKey(request.key)
        if (key.isBlank()) reject(HttpStatus.BAD_REQUEST, "key must not be blank")
        if (request.label.isBlank()) reject(HttpStatus.BAD_REQUEST, "label must not be blank")
        val targetLevel = request.targetLevel ?: Competency.DEFAULT_TARGET_LEVEL
        requireValidTargetLevel(targetLevel)
        if (competencyRepository.findByKey(key) != null) {
            reject(HttpStatus.CONFLICT, "A competency with key $key already exists")
        }

        // A person re-adding a key overrides their own deletion. The tombstone binds the
        // generator, not the PM who changed their mind.
        tombstoneRepository.deleteByKey(key)

        val competency = Competency(
            key = key,
            label = request.label.trim(),
            description = request.description?.trim()?.takeIf(String::isNotBlank),
            kind = request.kind,
            area = areaNormalizer.normalize(request.area),
            targetLevel = targetLevel,
            provenance = ContentProvenance.PM,
        )
        competencyRepository.save(competency)
        return competency.toAuthoringResponse()
    }

    /**
     * Applies an edit to one competency.
     *
     * Omitted fields are left alone. [key] is not editable — see [UpdateCompetencyRequest]: the
     * ledger points at it, so renaming a key would orphan everybody's progress.
     *
     * @throws ResponseStatusException 404 if no competency has [key]; 400 if `targetLevel` is
     * outside 1..4 or `label` is blank.
     */
    @Transactional
    fun updateCompetency(key: String, request: UpdateCompetencyRequest): CompetencyResponse {
        val competency = findCompetency(key)

        request.targetLevel?.let { level ->
            requireValidTargetLevel(level)
            competency.targetLevel = level
        }
        request.label?.let { label ->
            if (label.isBlank()) reject(HttpStatus.BAD_REQUEST, "label must not be blank")
            competency.label = label.trim()
        }
        // A blank description is how a PM clears one, so it maps to null rather than being rejected.
        request.description?.let { competency.description = it.trim().takeIf(String::isNotBlank) }
        request.kind?.let { competency.kind = it }
        // Blank clears the grouping, matching how a blank description clears one.
        request.area?.let { competency.area = areaNormalizer.normalize(it) }
        // Any edit makes this a human's row, so regeneration must leave it alone from here on --
        // the same rule a module page follows. Unconditional rather than per-field: a PM who
        // re-typed a value to the same string still reviewed it and decided it was right.
        competency.provenance = ContentProvenance.PM

        competencyRepository.save(competency)
        return competency.toAuthoringResponse()
    }

    /**
     * Removes a competency from the vocabulary.
     *
     * The ledger and any authored modules survive — see the class KDoc for why that is safe and what
     * it means for a module whose competency is gone.
     *
     * @throws ResponseStatusException 404 if no competency has [key].
     */
    @Transactional
    fun deleteCompetency(key: String): DeleteCompetencyResponse {
        val competency = findCompetency(key)
        competencyRepository.delete(competency)

        // Remembered so the generator cannot bring it back under a rephrasing next crawl. Upsert
        // rather than insert: re-adding and re-deleting a key must not fail on the unique index.
        val tombstone = tombstoneRepository.findByKey(key)
        if (tombstone == null) {
            tombstoneRepository.save(CompetencyTombstone(key = key, label = competency.label))
        } else {
            tombstone.label = competency.label
            tombstone.deletedAt = Instant.now()
        }

        return DeleteCompetencyResponse(key = key)
    }

    private fun findCompetency(key: String): Competency =
        competencyRepository.findByKey(key)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No competency found with key: $key")

    private fun requireValidTargetLevel(level: Int) {
        if (level !in MIN_TARGET_LEVEL..MAX_TARGET_LEVEL) {
            reject(
                HttpStatus.BAD_REQUEST,
                "targetLevel must be between $MIN_TARGET_LEVEL and $MAX_TARGET_LEVEL, got $level",
            )
        }
    }

    private fun reject(status: HttpStatus, message: String): Nothing = throw ResponseStatusException(status, message)

    private fun Competency.toAuthoringResponse() =
        CompetencyResponse(
            key = key,
            label = label,
            description = description,
            kind = kind,
            area = area,
            targetLevel = targetLevel,
        )

    private companion object {
        const val MIN_TARGET_LEVEL = 1
        const val MAX_TARGET_LEVEL = 4

        /** Kebab-cases a proposed key so hand-authored keys match the generator's house style. */
        fun slugifyKey(raw: String): String =
            raw
                .trim()
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
    }
}
