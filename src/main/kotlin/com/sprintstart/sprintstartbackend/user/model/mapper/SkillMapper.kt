package com.sprintstart.sprintstartbackend.user.model.mapper

import com.sprintstart.sprintstartbackend.user.model.entity.Skill
import com.sprintstart.sprintstartbackend.user.model.response.skill.CreateSkillResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.GetSkillResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.UpdateRoleSkillsResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.UpdateSkillResponse

fun Skill.toGetResponse() = GetSkillResponse(
    id = id,
    name = name,
    roleIds = projectRoles.map { it.id },
    status = status,
)

fun Skill.toCreateResponse() = CreateSkillResponse(
    id = id,
    name = name,
    roleIds = projectRoles.map { it.id },
    status = status,
)

fun Skill.toUpdateResponse() = UpdateSkillResponse(
    id = id,
    name = name,
    roleIds = projectRoles.map { it.id },
    status = status,
)

fun Skill.toUpdateRoleSkillsResponse() = UpdateRoleSkillsResponse(
    id = id,
    name = name,
    roleIds = projectRoles.map { it.id },
    status = status,
)
