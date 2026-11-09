/**
 * The path's contribution to the hire's Buddy: the read tool, and the actions that let a conversation move
 * the hire along their onboarding path.
 *
 * ### The line these do not cross
 *
 * They write to **the hire's own copy of the path**, never to the blueprint it was copied from. The
 * curriculum is the PM's: a mentor that could edit it is a mentor whose team stops trusting it.
 * Everything here is undoable on the hire's own onboarding page, which is what makes proposing any
 * of it reasonable.
 *
 * ### Why they sit outside the project gate
 *
 * An onboarding path belongs to a *person*. It is generated from one project's blueprint, but the
 * path itself is not project-scoped — so gating these would refuse a hire onboarding on two
 * projects, who still has exactly one path, on the page they are looking at.
 */
package com.sprintstart.sprintstartbackend.path.assistant;
