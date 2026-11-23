/**
 * Config: the composition root (security, CORS, OpenAPI, coroutine scope). It may use any module's API and
 * nothing depends on it; today it uses user's security API and role enum only.
 */
@ApplicationModule(allowedDependencies = {"user :: api", "user :: api-enum"})
package com.sprintstart.sprintstartbackend.config;

import org.springframework.modulith.ApplicationModule;
