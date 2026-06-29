package com.sprintstart.sprintstartbackend.shared.annotations

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.time.TimeSource

@Aspect
@Component
class TrackedAspect {
    @Around("@annotation(tracked)")
    fun logExecutionTime(joinPoint: ProceedingJoinPoint, tracked: Tracked): Any? {
        val targetClass = joinPoint.target.javaClass
        val dynamicLogger = LoggerFactory.getLogger(targetClass)

        val label = tracked.value.ifBlank { joinPoint.signature.name }
        val startMark = TimeSource.Monotonic.markNow()

        return try {
            val result = joinPoint.proceed()
            dynamicLogger.info("$label: ${startMark.elapsedNow()} (success)")
            result
        } catch (ex: Throwable) {
            dynamicLogger.error("$label: ${startMark.elapsedNow()} (failed) (reason: ${ex.message})")
            throw ex
        }
    }
}
