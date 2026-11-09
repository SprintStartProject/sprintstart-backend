/**
 * User: people, projects, memberships, roles and project sources, and the events their deletion raises.
 * An L1 platform module: it depends on shared only.
 */
@ApplicationModule(
        allowedDependencies = {"shared :: shared", "shared :: annotations", "shared :: config"}
)
package com.sprintstart.sprintstartbackend.user;

import org.springframework.modulith.ApplicationModule;
