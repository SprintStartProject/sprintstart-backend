package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "buddy_citations")
class BuddyCitation(
    @Id
    var id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "message_id")
    var message: BuddyMessage,
    @Column(name = "artifact_id", nullable = false)
    var artifactId: UUID,
    @Column(nullable = false)
    var filename: String,
    @Column(name = "source_url")
    var sourceUrl: String? = null,
    @Column(name = "start_line")
    var startLine: Int? = null,
    @Column(name = "start_page")
    var startPage: Int? = null,
)
