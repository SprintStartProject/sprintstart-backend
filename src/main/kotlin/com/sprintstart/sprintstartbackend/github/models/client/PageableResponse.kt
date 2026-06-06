package com.sprintstart.sprintstartbackend.github.models.client

interface PageableResponse <S> {
    val hasNextPage: Boolean
    val endCursor: String?
    val results: List<S>
}