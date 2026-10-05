package com.sprintstart.sprintstartbackend.ingestion.model.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.util.UUID

/**
 * Persisted as a bare JSON object with no type tag (see [ArtifactMetadataJsonMapper]), so the
 * concrete subtype has to be recovered on read from the fields that are present. DEDUCTION does
 * exactly that — it writes no discriminator (existing rows and new writes stay identical) and
 * infers the subtype from its distinct field set. The subtypes are disjoint
 * (`repositoryId`/`repositoryFullName` vs `workspace`/`slug` vs `storagePath`/`actorId` vs
 * `issueKey`/`statusCategory`), so deduction is unambiguous.
 *
 * Registering a further subtype here is not free: the nested Jira shapes (`JiraIssueType`,
 * `JiraAuthor`, ...) also implement this interface, and DEDUCTION accepts any candidate whose
 * field set is a superset of the payload. [GithubOrgMetadataArtifactMetadata] is deliberately
 * *not* registered — its `name`/`description` fields subset-match a `JiraIssueType` payload, so
 * registering it leaves every nested issue-type read with two matching candidates and Jackson
 * throws instead of guessing.
 *
 * Without this, `objectMapper.readValue(json, ArtifactMetadata::class.java)` cannot construct the
 * abstract interface and throws — which is what stalled the buddy's `get_suggested_tasks` tool.
 *
 * Wrappers ingestion writes that carry a `name` field are the exception: they must *not* be
 * registered here but read back through `ArtifactMetadataJsonMapper`'s explicit methods, which
 * dispatch on the artifact's coordinates instead of its field set.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes(
    JsonSubTypes.Type(GithubArtifactMetadata::class),
    JsonSubTypes.Type(BitbucketArtifactMetadata::class),
    JsonSubTypes.Type(UploadArtifactMetadata::class),
    JsonSubTypes.Type(JiraArtifactMetadataWrapper::class),
)
sealed interface ArtifactMetadata

data class GithubArtifactMetadata(
    val repositoryId: UUID,
    val repositoryFullName: String,
) : ArtifactMetadata

/**
 * Org-level identity carried by a GitHub `ORG_METADATA` artifact's metadata.
 *
 * Deliberately *not* registered for DEDUCTION (see [ArtifactMetadata]): its `name`/`description`
 * fields subset-match a nested `JiraIssueType` payload. It is read back through
 * `ArtifactMetadataJsonMapper`'s explicit methods instead, and the `NONE` marker below opts out
 * of the inherited deduction so that concrete-class read stays a plain bean read.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
data class GithubOrgMetadataArtifactMetadata(
    val login: String,
    val name: String,
    val description: String?,
    val company: String?,
    val blog: String?,
    val location: String?,
    val email: String?,
    val publicRepos: Int?,
    val privateRepos: Int?,
    val teams: List<GithubOrgMetadataTeam>?,
    val members: List<GithubOrgMetadataMember>,
) : ArtifactMetadata

data class GithubOrgMetadataTeam(
    val name: String,
    val slug: String?,
    val orgLogin: String,
    val orgName: String?,
    val members: List<GithubOrgMetadataTeamMember>,
)

data class GithubOrgMetadataTeamMember(
    val login: String,
    val name: String?,
)

data class GithubOrgMetadataMember(
    val login: String,
    val url: String,
)

/**
 * `actorId` is operation-neutral: it is the uploader for stored artifact metadata and the remover
 * for failed deletion metadata.
 */
data class UploadArtifactMetadata(
    var storagePath: String? = null,
    var actorId: UUID,
) : ArtifactMetadata
