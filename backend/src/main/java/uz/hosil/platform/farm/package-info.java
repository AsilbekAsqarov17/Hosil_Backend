/**
 * Farm Management Module.
 * Owns farms, fields, crop cycles, daily activities, and harvest batches.
 */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"identity", "common::error", "common::security"}
)
package uz.hosil.platform.farm;
