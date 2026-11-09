/**
 * Shared: the kernel every module may use, as named interfaces per concern (ids, model, ai, annotations,
 * config, crypto, failure, git, scheduler and web). L0: it depends on no other module.
 */
@ApplicationModule(allowedDependencies = {})
package com.sprintstart.sprintstartbackend.shared;

import org.springframework.modulith.ApplicationModule;
