/**
 * Logistics Module.
 * Owns shipment requests, carrier quotes, assignments, and delivery proofs.
 */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"identity", "marketplace", "farm", "common::error", "common::security"}
)
package uz.hosil.platform.logistics;
