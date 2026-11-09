/**
 * What the project knows beyond its documents: the questions hires asked, the gaps they escalated, and the
 * answers PMs gave and kept. An L2 feature: it depends on path, the platform and shared only.
 */
@ApplicationModule(
        allowedDependencies = {"shared :: ids", "shared :: annotations", "path :: api", "path :: api-model", "user :: api", "user :: api-events", "assistant :: api"}
)
package com.sprintstart.sprintstartbackend.knowledge;

import org.springframework.modulith.ApplicationModule;
