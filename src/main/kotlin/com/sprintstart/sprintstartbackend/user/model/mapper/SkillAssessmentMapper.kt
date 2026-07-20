package com.sprintstart.sprintstartbackend.user.model.mapper

import com.sprintstart.sprintstartbackend.user.model.entity.UserSkillAssessment
import com.sprintstart.sprintstartbackend.user.model.response.skill.CreateSkillAssessmentResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.GetSkillAssessmentResponse

fun UserSkillAssessment.toCreateResponse() = CreateSkillAssessmentResponse(
    userId = user.id,
    skillId = skill.id,
    level = level,
)

fun UserSkillAssessment.toGetResponse() = GetSkillAssessmentResponse(
    id = id,
    userId = user.id,
    skillId = skill.id,
    level = level,
)
