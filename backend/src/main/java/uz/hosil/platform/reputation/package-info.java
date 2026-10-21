/**
 * Reputation & Reviews Module.
 * Owns two-sided reviews, double-blind publications, and reputation scores.
 */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"identity", "marketplace", "logistics", "common::error"}
)
package uz.hosil.platform.reputation;
