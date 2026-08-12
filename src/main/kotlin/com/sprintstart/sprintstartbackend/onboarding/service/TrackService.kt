package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTrack
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingTrackRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Which track a hire is onboarding on, and what that implies.
 *
 * Resolution is deliberately total: every lookup returns a track, never null and never an error.
 * A role with no track, a track key pointing at a row that no longer exists, a project nobody has
 * configured — all of them land on [OnboardingTrack.DEFAULT_KEY], because the alternative is that
 * adding this feature can break onboarding for somebody who never asked for it.
 */
@Service
class TrackService(
    private val onboardingTrackRepository: OnboardingTrackRepository,
    private val projectMembershipApi: ProjectMembershipApi,
    private val userApi: UserApi,
) {
    /**
     * The track this member onboards on for this project.
     *
     * @param member The hire, already resolved against the project.
     * @return Their track, or the default when their role declares none.
     */
    @Transactional(readOnly = true)
    fun forMember(member: ProjectMember): OnboardingTrack {
        val key = member.onboardingTrackKey ?: return default()
        return onboardingTrackRepository.findByKey(key) ?: default()
    }

    /**
     * Whether any project this hire belongs to could produce [kind] evidence for them.
     *
     * Answers a *cross-project* question the per-project [forMember] cannot: the buddy is not
     * scoped to one project, so deciding whether to offer it a pull-request tool means asking
     * whether pull requests are relevant anywhere this person works. Erring toward offering keeps
     * a developer who also runs the retro from losing their pull-request tool.
     *
     * @param userId The hire.
     * @param kind The evidence kind in question.
     * @return True when at least one of their projects admits it; false when none do.
     */
    @Transactional(readOnly = true)
    fun admitsAnywhere(userId: UUID, kind: ContributionEvidenceKind): Boolean {
        val projects = userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
        if (projects.isEmpty()) {
            // Nobody has placed them yet, so nothing is ruled out. The default track's answer is
            // the honest one until somebody says otherwise.
            return default().admits(kind)
        }
        return projects.any { project ->
            val member = projectMembershipApi
                .getProjectMembers(project.projectId)
                .firstOrNull { it.userId == userId }
            member != null && forMember(member).admits(kind)
        }
    }

    /**
     * The one track whose words describe this hire, across every project they are on.
     *
     * The buddy is not scoped to a project, so its persona needs a single vocabulary while
     * [forMember] needs a per-project one. Where [admitsAnywhere] is deliberately **permissive** —
     * a capability is offered if any project could use it — this is deliberately **strict**: a
     * sentence can only be written in one set of words, so two projects putting the hire on
     * different tracks resolves to the default rather than picking a winner. Same rule the user
     * module applies when two roles disagree, for the same reason: the wrong vocabulary in front of
     * somebody is worse than the neutral one.
     *
     * @param userId The hire.
     * @return Their track when every project agrees, otherwise the default.
     */
    @Transactional(readOnly = true)
    fun forUser(userId: UUID): OnboardingTrack {
        val projects = userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
        val memberships = projects.mapNotNull { project ->
            projectMembershipApi
                .getProjectMembers(project.projectId)
                .firstOrNull { it.userId == userId }
        }
        val tracks = memberships.map { forMember(it) }.distinctBy { it.key }
        return tracks.singleOrNull() ?: default()
    }

    /**
     * Every track a PM may point a role at, stable order.
     *
     * Falls back to the single in-memory default when nothing is seeded, so the chooser is never
     * an empty list -- an empty list would read as "tracks are broken" when it means "nobody has
     * added any beyond the default yet".
     */
    @Transactional(readOnly = true)
    fun listTracks(): List<OnboardingTrack> {
        val tracks = onboardingTrackRepository.findAll().sortedBy { it.label }
        return tracks.ifEmpty { listOf(default()) }
    }

    /**
     * The tracks some project role actually points at.
     *
     * Distinct from [listTracks], which is every track a PM *could* choose. Readiness checks and
     * warnings use this one: telling a PM that a track nobody is on has no starter work is noise
     * they cannot act on, and noise on a readiness ladder is how the ladder stops being read.
     *
     * The default track is always in use — every role that declares nothing resolves to it.
     */
    @Transactional(readOnly = true)
    fun tracksInUse(): List<OnboardingTrack> {
        val declared = projectMembershipApi.onboardingTrackKeysInUse()
        return listTracks().filter { it.key in declared || it.key == OnboardingTrack.DEFAULT_KEY }
    }

    /**
     * The fallback track.
     *
     * Falls back again to an in-memory engineering track when the seeded row is absent. That is not
     * defensive padding: schema-from-entities contexts (the test suite) never run the migration
     * that seeds it, and onboarding must not depend on a row's existence to describe itself.
     */
    @Transactional(readOnly = true)
    fun default(): OnboardingTrack =
        onboardingTrackRepository.findByKey(OnboardingTrack.DEFAULT_KEY) ?: ENGINEERING

    private companion object {
        /** Mirrors the row `V43` seeds; see [default] for why an in-memory twin has to exist. */
        val ENGINEERING = OnboardingTrack(
            key = OnboardingTrack.DEFAULT_KEY,
            label = "Engineering",
            contributionNoun = "change",
            contributionNounPlural = "changes",
            contributionVerbPast = "merged",
            evidenceKinds = mutableSetOf(ContributionEvidenceKind.PULL_REQUEST),
        )
    }
}
