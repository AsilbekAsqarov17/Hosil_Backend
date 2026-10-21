# Archive Notice — Historical Root Files

**Archived Date:** 14 October 2026  
**Location:** `docs/archive/`

---

## Purpose

During Phase 0 documentation audit, two files previously located at the workspace root (`c:\Users\Oliya\Desktop\Hosil\`) were verified and archived here to ensure zero loss of historical records while establishing unambiguous canonical locations under `docs/`:

1. **`01_product_requirements_root_backup.md`**  
   - **Original filename:** `01_product_requirements (1).md` (at root)  
   - **Verification:** SHA-256 hash verified as 100% byte-for-byte identical (`A88978E45F735E404EB45550F4C37FE537DC34FB27FF40161A52ECFC4F1E203B`) to the canonical active document [`docs/01_prd.md`](../01_prd.md).  
   - **Canonical active document:** [`docs/01_prd.md`](../01_prd.md).

2. **`hosil_java_architecture_blueprint_v0.3_original.md`**  
   - **Original filename:** `hosil_java_architecture_blueprint (1).md` (at root)  
   - **Verification:** Represents the original v0.3 architecture blueprint (766 lines) prior to the v0.4 additions (which incorporated prioritized deliverables, offline strategy, observability, notifications, admin panel, API versioning, and hosting companion cross-references).  
   - **Canonical active document:** [`docs/02_architecture.md`](../02_architecture.md) (Architecture Blueprint v0.4+).

---

## Governance Rule

Do not edit files in this `docs/archive/` directory. All future architectural updates, product requirements changes, and RFCs must be made directly against active documents in `docs/` and recorded in `docs/00_documentation_index.md`.
