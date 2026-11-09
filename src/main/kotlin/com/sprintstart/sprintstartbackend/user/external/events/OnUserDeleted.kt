package com.sprintstart.sprintstartbackend.user.external.events

import org.springframework.context.event.EventListener
import org.springframework.transaction.annotation.Transactional

/**
 * Marks a method that erases a module's data about a user in reaction to [UserDeletedEvent].
 *
 * The method runs synchronously in the deleting transaction, so a failed deletion takes the erasure
 * with it and the two cannot end up disagreeing.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@EventListener
@Transactional
annotation class OnUserDeleted
