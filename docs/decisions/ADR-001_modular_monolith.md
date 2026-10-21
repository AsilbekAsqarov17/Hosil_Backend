# ADR-001: Modular Monolith as Starting Architecture

**Status:** Accepted  
**Date:** 13 October 2026  
**Deciders:** Project owner  
**Context document:** [02_architecture.md](../02_architecture.md) §1, §3

---

## Context

The Hosil Ecosystem comprises three mobile applications (Hosil Bozor, Hosil Farm, Hosil Logistika), an admin panel, and a shared backend. The platform targets farmers in Uzbekistan and is built by a small team.

A common assumption is that three separate apps require three separate backend services (microservices). However, the platform has zero users at launch, the team is small, and the operational overhead of distributed systems (service discovery, distributed transactions, inter-service auth, independent deployment pipelines, network failure handling) would consume development time without delivering user value.

## Decision

Start with a **modular monolith**: one Spring Boot deployment with well-separated internal modules (`identity`, `farms`, `marketplace`, `payments`, `logistics`, `advisory`, `media`, `notifications`, `admin`).

### Rules

1. Each module owns its business rules and persistence entities.
2. Modules interact through public service interfaces or application events, **never** by directly accessing another module's database tables or JPA repositories.
3. Controllers handle HTTP; application services enforce use cases; repositories handle persistence; DTOs define the API contract.
4. JPA entities are never exposed directly as API responses.
5. External integrations sit behind adapter interfaces.

### Exception

The **Python + FastAPI AI inference service** is a genuinely separate runtime because:
- It uses a fundamentally different technology stack (Python, PyTorch/ONNX).
- It performs computationally expensive work (image preprocessing, model inference) that benefits from independent scaling and isolation.
- It communicates with the Java backend via a versioned internal API or message queue, not by accessing the platform database.

This service is introduced in Phase 8, not at project start.

## Consequences

**Positive:**
- Single deployment simplifies DevOps, monitoring, and debugging.
- Shared database with foreign keys and transactions ensures data integrity.
- Module boundaries can be verified with Spring Modulith.
- Faster development: no network calls between modules, no distributed transaction coordination.
- Clear migration path: any module can be extracted into a separate service later if scaling, ownership, or deployment needs justify it.

**Negative:**
- All modules share one JVM process; a memory leak or crash in one module affects all.
- Scaling is vertical (bigger machine) until a module is extracted.
- Developers must maintain discipline around module boundaries without the "hard wall" of network separation.

**Mitigations:**
- Enforce module boundary rules via code review and optionally Spring Modulith's architecture tests.
- Monitor JVM health (Actuator, Micrometer) to catch resource issues early.
- Design modules so extraction is straightforward: service interfaces, no cross-module table joins, event-driven communication where appropriate.

## Reassessment Triggers

Reconsider extracting a module when:
- A specific module's traffic or resource needs justify independent scaling.
- Different modules need different deployment cadences (e.g., marketplace needs zero-downtime deploys while advisory can tolerate brief restarts).
- Team grows and module ownership boundaries need enforcement.
- A measured performance bottleneck cannot be resolved within the monolith.

Do **not** split preemptively based on anticipated future load. Split based on evidence.

---

## References

- [Spring Modulith](https://spring.io/projects/spring-modulith)
- [02_architecture.md §3 — Platform architecture](../02_architecture.md)
- [02_architecture.md §16 — What not to build at the beginning](../02_architecture.md)
