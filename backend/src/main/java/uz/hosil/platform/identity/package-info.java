/**
 * Identity & Access Management Module.
 * Owns users, profiles, roles, and authentication challenges.
 */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"common::error", "common::security"}
)
package uz.hosil.platform.identity;
