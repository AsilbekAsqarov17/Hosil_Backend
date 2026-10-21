/**
 * Marketplace Module.
 * Owns listings, categories, harvest allocation ledger, orders, and payments.
 */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"identity", "farm", "common::error", "common::security"}
)
package uz.hosil.platform.marketplace;
