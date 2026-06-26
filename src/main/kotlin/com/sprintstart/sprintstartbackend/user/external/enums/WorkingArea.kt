package com.sprintstart.sprintstartbackend.user.external.enums

enum class WorkingArea {
    NO_WORKING_AREA,
    FRONTEND_DEV,
    BACKEND_DEV,
    DEV_OPS,
    QA,
    HR,
    ;

    fun toAiScope(): String = when (this) {
        BACKEND_DEV -> "backend"
        FRONTEND_DEV -> "frontend"
        DEV_OPS -> "devops"
        QA -> "qa"
        HR -> "hr"
        NO_WORKING_AREA -> "unknown"
    }
}
