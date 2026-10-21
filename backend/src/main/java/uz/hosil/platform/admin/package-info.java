/**
 * Admin Back-Office Operations Module (ADR-002).
 * Server-rendered operational UI via Thymeleaf and HTMX.
 */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"identity", "farm", "marketplace", "logistics", "reputation", "common::error"}
)
package uz.hosil.platform.admin;
