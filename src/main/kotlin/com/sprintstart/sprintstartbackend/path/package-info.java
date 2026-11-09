/**
 * Onboarding paths: each hire's phases, steps, tasks, resources, checks and skips, personalized from the
 * project's blueprint. An L2 feature: it depends on blueprint, the platform and shared only.
 */
@ApplicationModule(
        allowedDependencies = {
                "shared :: shared", "shared :: ids", "shared :: ai", "shared :: annotations", "shared :: config",
                "blueprint :: api", "blueprint :: api-enums", "user :: api", "user :: api-events"
        }
)
package com.sprintstart.sprintstartbackend.path;

import org.springframework.modulith.ApplicationModule;
