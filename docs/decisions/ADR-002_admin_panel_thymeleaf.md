# ADR-002: Server-Rendered Admin Panel (Spring Boot + Thymeleaf)

**Status:** Proposed  
**Date:** 13 October 2026  
**Deciders:** Project owner  
**Context document:** [10_admin_and_api_versioning.md](../10_admin_and_api_versioning.md) §1

---

## Context

The Hosil platform requires an admin panel for user management, listing moderation, order oversight, content management, and audit log viewing. The team is small (< 10 people initially), and the admin panel's MVP requirements are predominantly CRUD operations.

Two approaches were evaluated:

| Criterion | Spring Boot + Thymeleaf | Separate React/Next.js Dashboard |
|---|---|---|
| Build pipeline | Same as backend (Maven) | Separate Node.js build + deployment |
| Auth integration | Spring Security session-based (trivial) | Requires CORS, JWT handling, separate auth flow |
| Interactivity | Server-rendered; can add HTMX for dynamic elements | Full SPA, rich component ecosystem |
| Maintenance burden | One codebase, one deployment | Two codebases, two deployments, two tech stacks |
| Best suited for | CRUD screens, moderation queues, content editing | Complex dashboards, real-time analytics, data visualization |
| Developer requirement | Java/Spring knowledge (already required) | Additional frontend developer with React experience |

## Decision

Use **Spring Boot + Thymeleaf** for the MVP admin panel.

The admin panel's requirements — listing moderation, user management, order viewing, content editing, audit logs — are CRUD-heavy and do not require SPA-level interactivity. Thymeleaf with HTMX can add targeted dynamic behavior (inline search, live table updates) without a separate frontend build.

## Consequences

**Positive:**
- No additional build pipeline, deployment target, or frontend framework to maintain.
- Spring Security integration is built-in (session-based auth, CSRF protection).
- Faster to ship initial admin capabilities.
- The same developer who writes backend code can build admin screens.

**Negative:**
- Limited interactivity compared to a React SPA.
- If admin needs grow to include complex dashboards or real-time monitoring, Thymeleaf may become constraining.

**Migration path:** If admin requirements outgrow Thymeleaf, the admin panel can be rebuilt as a React SPA consuming the same `/api/v1/` REST API that the mobile apps use. The backend API does not change.

## Reassessment Triggers

- Admin panel requires complex data visualization or real-time updates.
- A dedicated frontend developer joins the team.
- The mobile API is mature enough that building a React admin on top of it is straightforward.

---

## References

- [10_admin_and_api_versioning.md](../10_admin_and_api_versioning.md)
- [02_architecture.md §9 — Flutter applications and shared design system](../02_architecture.md)
