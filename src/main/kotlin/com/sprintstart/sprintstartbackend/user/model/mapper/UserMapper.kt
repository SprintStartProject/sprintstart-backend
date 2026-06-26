package com.sprintstart.sprintstartbackend.user.model.mapper

import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserResponse
import com.sprintstart.sprintstartbackend.user.model.entity.User

fun User.toGetResponse(): GetUserResponse =
    GetUserResponse(
        id = this.id,
        authId = this.authId,
        username = this.username,
        email = this.email,
        firstname = this.firstname,
        lastname = this.lastname,
        workingArea = this.workingArea,
        experience = this.experience,
    )

fun User.toUpdateResponse(): UpdateUserResponse =
    UpdateUserResponse(
        id = this.id,
        authId = this.authId,
        username = this.username,
        email = this.email,
        firstname = this.firstname,
        lastname = this.lastname,
        workingArea = this.workingArea,
        experience = this.experience,
    )

fun User.toPatchResponse(): PatchUserResponse =
    PatchUserResponse(
        id = this.id,
        authId = this.authId,
        username = this.username,
        email = this.email,
        firstname = this.firstname,
        lastname = this.lastname,
        workingArea = this.workingArea,
        experience = this.experience,
    )
