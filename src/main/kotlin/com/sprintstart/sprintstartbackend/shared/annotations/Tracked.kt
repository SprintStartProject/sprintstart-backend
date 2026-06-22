package com.sprintstart.sprintstartbackend.shared.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Tracked(
    val value: String = "",
)
