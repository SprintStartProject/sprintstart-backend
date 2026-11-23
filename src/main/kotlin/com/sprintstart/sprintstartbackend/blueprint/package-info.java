/**
 * Blueprints: the template a project's onboarding paths are built from, edited by its PMs and read by the
 * path through [com.sprintstart.sprintstartbackend.blueprint.external.BlueprintApi]. An L2 feature: it depends on
 * the platform and shared only.
 */
@ApplicationModule(
        allowedDependencies = {"shared :: ids", "user :: api", "user :: api-events"}
)
package com.sprintstart.sprintstartbackend.blueprint;

import org.springframework.modulith.ApplicationModule;
