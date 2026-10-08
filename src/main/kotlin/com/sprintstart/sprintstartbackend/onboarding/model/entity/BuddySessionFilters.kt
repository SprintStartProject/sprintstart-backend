package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.fasterxml.jackson.annotation.JsonProperty
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The retrieval filters a hire sets on a buddy message: which sources to search and in which time range.
 *
 * The class is read in two directions, and each uses its own serializer. Spring reads the request
 * body with Jackson, which ignores [SerialName]; without [JsonProperty] the snake_case fields the
 * frontend sends would be dropped silently and the filters would never arrive. kotlinx writes the
 * same fields to the AI service, which is where [SerialName] applies. Both annotations carry the
 * same names so the wire format is identical on both sides.
 */
@Serializable
class BuddySessionFilters(
    @SerialName("source_systems")
    @JsonProperty("source_systems")
    var sourceSystems: List<SourceSystem>?,
    @SerialName("time_from")
    @JsonProperty("time_from")
    var from: String?,
    @SerialName("time_to")
    @JsonProperty("time_to")
    var to: String?,
)
