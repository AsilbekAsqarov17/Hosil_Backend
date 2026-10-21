# Hosil Farm AI Inference Service (Future Boundary)

**Status:** Documented Boundary (Implementation Deferred to Milestone 8)  
**Architecture Reference:** [`docs/02_architecture.md`](../../docs/02_architecture.md) §2.4, §8.4

---

## Service Overview

The `services/farm_ai/` service boundary is reserved for the Python 3.11+ / FastAPI crop disease computer vision inference service.

### Architectural Invariants:
1. **Isolated Inference Service:** The Python service performs stateless computer vision inference on leaf images uploaded via Hosil Farm.
2. **No Direct Database Access:** The Python service **never** connects directly to the platform's PostgreSQL database.
3. **Java Platform Ownership:** The Java/Spring Boot modular monolith owns all user accounts, farm memberships, access permissions, image storage orchestration, and persistent AI diagnosis audit records (`ai_diagnosis_records`).
4. **Communication Protocol:** The Java platform communicates with the Farm AI inference service over an internal HTTP/REST interface using structured JSON request/response payloads (`/api/v1/internal/inference/diagnose`).

---

## Directory Reservation

When implemented in Milestone 8, this directory will contain:
- `main.py` (FastAPI application entrypoint)
- `model/` (Pre-trained ONNX or PyTorch weights for localized Central Asian crop diseases)
- `schemas.py` (Pydantic inference request and structured response schemas)
- `Dockerfile` & `requirements.txt`
