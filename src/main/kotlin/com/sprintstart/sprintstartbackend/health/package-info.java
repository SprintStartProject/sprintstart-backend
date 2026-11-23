/** Health: the liveness endpoint. It depends on no other module. */
@ApplicationModule(allowedDependencies = {})
package com.sprintstart.sprintstartbackend.health;

import org.springframework.modulith.ApplicationModule;
