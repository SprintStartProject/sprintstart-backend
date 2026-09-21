package com.sprintstart.sprintstartbackend.connectors.bitbucket.controller

import com.sprintstart.sprintstartbackend.connectors.bitbucket.service.BitbucketConnectionService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/bitbucket")
internal class BitbucketConnectionController(
    private val service: BitbucketConnectionService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PostMapping
    fun connect(): ResponseEntity<Unit> {
        val response = service.connectRepositoryIfExists()
        return ResponseEntity.ok(response)
    }
}
