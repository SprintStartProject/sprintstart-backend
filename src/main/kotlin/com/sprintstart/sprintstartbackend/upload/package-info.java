/**
 * Upload: files people add, stored and announced for ingestion. An L1 platform module: it depends on
 * user and shared only.
 */
@ApplicationModule(
        allowedDependencies = {"shared :: annotations", "shared :: config", "user :: api"}
)
package com.sprintstart.sprintstartbackend.upload;

import org.springframework.modulith.ApplicationModule;
