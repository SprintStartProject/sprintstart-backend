package com.sprintstart.sprintstartbackend.user.model.mapper

import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserResponse
import com.sprintstart.sprintstartbackend.user.model.entity.User

fun User.toCreateResponse(): CreateUserResponse {
    return CreateUserResponse(
        id = this.id,
        username = this.username,
        firstname = this.firstname,
        lastname = this.lastname,
        primaryRole = this.primaryRole,
        secondaryRole = this.secondaryRole,
        workingArea = this.workingArea,
    )
}

fun User.toGetResponse(): GetUserResponse {
    return GetUserResponse(
        id = this.id,
        username = this.username,
        firstname = this.firstname,
        lastname = this.lastname,
        primaryRole = this.primaryRole,
        secondaryRole = this.secondaryRole,
        workingArea = this.workingArea,
    )
}

fun User.toUpdateResponse(): UpdateUserResponse {
    return UpdateUserResponse(
        id = this.id,
        username = this.username,
        firstname = this.firstname,
        lastname = this.lastname,
        primaryRole = this.primaryRole,
        secondaryRole = this.secondaryRole,
        workingArea = this.workingArea,
    )
}

fun User.toPatchResponse(): PatchUserResponse {
    return PatchUserResponse(
        id = this.id,
        username = this.username,
        firstname = this.firstname,
        lastname = this.lastname,
        primaryRole = this.primaryRole,
        secondaryRole = this.secondaryRole,
        workingArea = this.workingArea,
    )
}
