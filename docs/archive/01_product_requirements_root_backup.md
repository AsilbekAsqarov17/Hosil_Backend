# Hosil Ecosystem — Product Requirements Document (PRD)

**Document ID:** HOSIL-PRD-001  
**Version:** 0.1  
**Status:** Draft for product review  
**Last updated:** 13 October 2026  
**Initial market:** Uzbekistan  
**Products:** Hosil Bozor, Hosil Farm, Hosil Logistika, shared platform and admin tools

> **Purpose of this document:** define what Hosil must do before implementation begins. It is the product source of truth for scope, user roles, business workflows, and acceptance criteria. Architecture documents explain how we plan to implement the requirements; they must not silently redefine them.

---

## 1. Product vision

Hosil is an agricultural digital platform for Uzbekistan. It connects agricultural commerce, farm management, and produce transportation/financing workflows using one shared identity and data platform.

The ecosystem has three user-facing products:

- **Hosil Bozor:** a marketplace for agricultural supplies and harvests.
- **Hosil Farm:** a practical daily workspace for a farmer's operations and agricultural information.
- **Hosil Logistika:** a service to request and manage produce transportation, with a later partner-led equipment-leasing workflow.

The platform should reduce the friction between growing agricultural products, buying inputs, finding buyers, and moving harvests to their destinations. It should work for a person or business using one product alone as well as someone who uses all three.

### 1.1 Product principles

1. **One identity, separate experiences.** A user has one Hosil account, but each app has its own navigation, tasks, and release priorities.
2. **Farmer-controlled sharing.** Private farm information is not automatically exposed in the marketplace or to other users. Publishing a harvest requires an explicit action.
3. **Trustworthy transactions.** Inventory, orders, payments, cancellations, and shipment status must have explicit states and auditable transitions.
4. **Partner-owned regulated activities.** External payment providers handle payment rails; authorized finance partners make financing decisions. Hosil does not pretend to be a bank.
5. **AI is assistance, not certainty.** Future crop assessments must communicate uncertainty and must not present a model output as a guaranteed diagnosis.
6. **Build a usable MVP first.** Launch core workflows before adding live fleet tracking, automated credit decisions, or advanced personalization.
7. **Local-first product decisions.** Validate local terminology, measurement units, provider availability, user support needs, and regional workflows with real target users before production launch.
8. **AI-generated code is not automatically accepted.** Requirements, tests, review, and runtime verification are mandatory for every implementation task.

---

## 2. Target market, users, and assumptions

### 2.1 Initial market

The first target market is Uzbekistan. Initial product assumptions to validate:

- Uzbek (Latin script) and Russian are launch languages.
- Prices are displayed in Uzbek soʻm (UZS), unless a future approved use case explicitly requires another currency.
- Metric measurements are used by default; supported sale units must be configurable by category and listing.
- Dates and times are displayed in the user's selected locale and the relevant local time zone.
- Mobile-first UX is essential. The first release targets Android; iOS support depends on delivery capacity and the chosen rollout strategy, but the shared Flutter approach should avoid platform-specific business logic.
- Some users may have weak or intermittent connectivity. Forms should avoid losing drafts where reasonably possible, and retrying a request must not create duplicate orders or payments.

These are product assumptions, not confirmed legal or commercial requirements. Validate them with pilot users and integration partners.

### 2.2 Primary personas

**P1 — Farmer / farm manager**
- Manages one or more farms and fields.
- Records crops, agricultural activities, and harvest quantities.
- Buys farm inputs and lists harvests for sale.
- May request transportation to a buyer or market.
- May have limited time, variable connectivity, and a preference for simple, clear language.

**P2 — Agricultural supplier**
- Lists seeds, fertilizer, tools, and equipment.
- Maintains prices, quantities, availability, and listing details.
- Receives and fulfills orders and communicates important order-status changes.

**P3 — Wholesale buyer / produce buyer**
- Searches harvest listings by crop, quantity, quality, region, availability, and price.
- Contacts or orders from producers according to the selected transaction flow.
- May need transport for a purchase.

**P4 — Carrier / transport provider**
- Maintains business/contact details and service area.
- Reviews transport requests and submits quotes.
- Accepts work and updates shipment status.
- Provides evidence of delivery where required.

**P5 — Hosil administrator / support operator**
- Moderates suspicious or incomplete listings and handles account/support issues.
- Reviews audit trails and operational failures.
- Must have narrowly scoped admin permissions and must not have routine access to private credentials or unnecessary personal data.

**P6 — Finance partner operator (future / conditional)**
- Receives leasing applications through an agreed, secure integration or controlled handoff.
- Owns eligibility assessment, credit decision, financing contract, and disbursement.
- Hosil tracks the application workflow but does not decide creditworthiness in the MVP.

**P7 — Farm worker (optional in MVP)**
- Can be invited by a farm owner/manager to perform specific farm tasks.
- Access is limited to farms and actions granted to that worker.
- Team invitations can be postponed if they add too much complexity to the first pilot; farm ownership and access boundaries must still be designed to support them.

### 2.3 Core user and business outcomes

- A farmer can create a useful farm profile and maintain crop/harvest records.
- A supplier can publish an input-product offer and receive a valid order.
- A farmer or producer can publish a harvest listing and receive an order/qualified buyer interest.
- A buyer and seller can understand the current order/payment state and the next action needed.
- A farmer can request transport and select a carrier offer.
- Users can access the same account and relevant permitted data across products.
- Administrators can moderate listings and investigate reported problems.

---

## 3. Product structure and MVP boundary

### 3.1 Products

| Product | Main job | MVP focus |
|---|---|---|
| Hosil Bozor | Buy farm inputs and trade harvests | Catalog, offers/listings, search, seller tools, order lifecycle |
| Hosil Farm | Manage farm information and daily work | Farms, fields, crop cycles, activity logs, harvest records, weather/advisories |
| Hosil Logistika | Arrange movement of crops | Shipment requests, carrier quotes, quote acceptance, status timeline |
| Shared platform | Make all products consistent and secure | Accounts, permissions, API, database, files, notifications, admin and audit logs |
| Farm AI service | Later image-based agricultural assistance | API/worker boundary and requirements defined now; production diagnosis not required for the first MVP |

### 3.2 Priority definitions

- **P0 — Launch blocker:** required for the agreed pilot to work safely end to end.
- **P1 — Important:** improves the pilot materially; may be released in a controlled follow-up if explicitly approved.
- **P2 — Later:** not needed to validate the initial product and should not delay the MVP.

A P0 feature is not done merely because its screen exists. The underlying permissions, persisted data, error handling, and acceptance tests must work.

### 3.3 Initial MVP scope

**P0 — Shared platform**
- Account registration/login using the authentication method selected during Phase 0.
- Shared account/profile across products.
- Role/permission enforcement on the backend.
- PostgreSQL persistence, data constraints, migrations, structured logs, audit events for sensitive actions, and automated tests.
- In-app notification records and an adapter boundary for push/SMS providers.
- Private file storage for listing photos and approved supporting documents.
- Basic admin capabilities to manage categories, review reports, suspend accounts/listings, and inspect relevant operational records.
- API documentation and a repeatable local environment.

**P0 — Hosil Farm**
- Create/edit a farm profile and record a region/location and approximate area.
- Add fields and crop cycles.
- Record dated agricultural activities.
- Record harvest batches.
- View current and upcoming weather information through a selected provider, with clear data timestamps.
- Browse agricultural articles/advisories with a source and publication date.
- Explicitly choose to publish a harvest batch to Hosil Bozor; private farm records remain private by default.

**P0 — Hosil Bozor**
- Browse and search agricultural input catalog items.
- Supplier creates and manages product offers, including price, stock, unit, availability, photos, and delivery/collection terms.
- Producer creates and manages harvest listings, including crop, quantity, unit, region, availability, optional quality/grade, price or quote preference, minimum order, and photos.
- Search/filter listings and view seller/producer information that is intentionally public.
- Buyer places an order or submits a purchase request according to the selected order/payment model.
- Buyer and seller see order details and allowed state changes.
- Order history and basic notifications.
- Payment sandbox integration only after the provider's supported workflow is verified; no live customer money flow until commercial/legal/integration checks are complete.
- Single-seller checkout per order for the MVP. If a buyer wants products from multiple sellers, each seller gets a separate order. Do not implement multi-seller split settlement in the first release.

**P0 — Hosil Logistika**
- Farmer/buyer creates a shipment request, optionally linked to an order or harvest batch.
- Request includes pickup/drop-off details, planned date/time window, cargo description, quantity/unit, vehicle requirements, and contact/handling notes.
- Carrier profile and service area.
- Carrier submits a quote; requester compares available quotes and accepts one.
- Shipment status timeline, basic cancellation behavior, notification on important transitions, and proof-of-delivery attachment.
- Human/manual pilot operations are acceptable for carrier verification and dispute resolution.

**P1 — Shared and product capabilities**
- Push notifications on supported devices.
- Farm-team invitations and granular worker permissions.
- Enhanced seller/buyer messaging or structured inquiry history.
- Better admin reporting and moderated dispute workflows.
- Leasing application collection, only if a finance partner agrees to the journey, data requirements, and secure handoff.
- Basic cached read-only screens for selected Farm information under poor connectivity.

**P2 — Explicitly deferred**
- Production crop-disease AI diagnosis and personalized prescriptions.
- Live GPS tracking and automated fleet dispatch/route optimization.
- Hosil-owned lending, credit scoring, or financing decisions.
- Multi-seller checkout, automated seller split payouts, escrow, or a wallet.
- Loyalty points, complex coupons, influencer/social feeds, and advanced recommendation systems.
- Satellite crop monitoring, yield forecasting, soil analysis, IoT integration, and precision-agriculture analytics.
- Microservices migration before demonstrated scaling or team-boundary needs.

---

## 4. Shared identity, roles, and access requirements

### 4.1 Identity requirements

**ID-001 (P0): One account across products.** A user must not need a separate identity for Bozor, Farm, and Logistika.

**ID-002 (P0): Multiple roles.** A single account may act as farmer, buyer, seller, or carrier where approved. Roles are not mutually exclusive.

**ID-003 (P0): Explicit account state.** Accounts can be active or suspended. Suspended users cannot perform prohibited write actions, and the reason/action is audited.

**ID-004 (P0): Account recovery.** The chosen login mechanism must include a documented recovery flow, abuse/rate limits, and clear user feedback. Do not implement a production OTP flow until a provider and delivery costs have been evaluated.

**ID-005 (P0): Session security.** Tokens or sessions must expire and be revocable. Secrets must not be stored in client code or logs. If refresh tokens are used, their rotation/revocation behavior must be specified and tested.

**ID-006 (P0): Profile control.** Users can view and update allowed profile fields. Changes to sensitive account fields may require re-verification.

### 4.2 Authorization requirements

**ACL-001 (P0): Backend enforcement.** Every protected operation checks the authenticated principal and resource-level permissions in the backend. Hiding a button in Flutter is not authorization.

**ACL-002 (P0): Resource ownership.** A user cannot read or change another user's farm, draft listing, private document, order, shipment, or leasing record simply by changing an ID in a URL.

**ACL-003 (P0): Farm access.** Farm data is visible only to the farm owner and explicitly authorized members, subject to their granted role.

**ACL-004 (P0): Seller actions.** A seller can edit only their own offers/listings and manage only orders assigned to them.

**ACL-005 (P0): Buyer actions.** A buyer can view their own orders and perform only buyer-permitted transitions.

**ACL-006 (P0): Carrier actions.** A carrier can see requests made available to it and update only shipments it has been assigned/accepted to handle.

**ACL-007 (P0): Admin least privilege.** Admin actions use separate permissions and are audited. Support operators should not automatically get access to private farm records or uploaded documents unrelated to a case.

**ACL-008 (P0): Public/private boundary.** Only explicitly published marketplace fields are public. Farm area, activity history, precise farm coordinates, private notes and identity documents are private unless the user authorizes a specific sharing purpose.

### 4.3 Initial role/action matrix

| Action | Farmer/owner | Seller | Buyer | Carrier | Admin |
|---|---:|---:|---:|---:|---:|
| Manage own farm and fields | Yes | If authorized | If authorized | No by default | Limited support access |
| Publish own harvest listing | Yes | If authorized | No by default | No | Moderate, not impersonate |
| Manage own product offers | If enabled as seller | Yes | No by default | No | Moderate |
| Place/order and view own order | Yes | If buying | Yes | No by default | Support metadata only unless authorized |
| Submit carrier quote | No | No | No | Yes | Moderate carrier access |
| Update accepted shipment | Requester sees it | No by default | Requester sees it | Assigned carrier | Support override with audit |
| Suspend user/listing | No | No | No | No | Authorized admin only |

Role checks must be combined with ownership checks. A role alone must not grant access to all records in that domain.

---

## 5. Hosil Farm requirements

### 5.1 Farm profiles and fields

**FARM-001 (P0): Create farm.** An authorized user can create a farm with a name, region/district or location description, approximate area and area unit. Precise coordinates are optional unless required by a selected feature.

**FARM-002 (P0): Manage farm profile.** An owner/authorized manager can update permitted profile fields and archive a farm without deleting linked historical records.

**FARM-003 (P0): Multiple farms.** One user may manage multiple farms. Every farm-scoped record identifies the farm it belongs to.

**FARM-004 (P0): Manage fields.** A farm member with the correct permission can create and edit fields with a name/label, area, unit, optional location and notes.

**FARM-005 (P0): Crop cycles.** Users can record crop type, field, planting date or planned date, expected season dates when known, and status. A crop cycle cannot reference a field belonging to another farm.

**FARM-006 (P0): Activity logs.** Users can record activity type (e.g., planting, irrigation, fertilizing, treatment, inspection, harvest), date/time, optional quantity/unit, notes and related crop cycle. Editing/deleting history must follow a documented policy; material edits should be auditable.

**FARM-007 (P0): Harvest batch.** Users can record crop, quantity/unit, harvest date, optional quality/grade, storage/availability notes, and farm/field association. A harvest batch has a clear available quantity and status.

**FARM-008 (P0): Share with Bozor explicitly.** A user can start a publishing flow from a harvest batch. The UI must show which details will be public and ask for explicit confirmation before publishing.

**FARM-009 (P0): Avoid inconsistent quantities.** If part of a harvest batch is reserved/sold through marketplace activity, the platform must not allow the same quantity to be sold twice. The product/domain design must specify whether the source of available quantity is the batch, listing, or allocation ledger before implementation.

### 5.2 Weather and advisories

**FARM-010 (P0): Weather view.** Show forecast/current conditions supported by the chosen provider and the selected farm/location. Always display the last-updated time and an understandable unavailable/stale state.

**FARM-011 (P0): Weather source.** Show the provider/source and avoid presenting a failed or stale fetch as current data.

**FARM-012 (P1): Weather alerts.** Users opt in to relevant alerts. Alert rules, location precision, schedule, supported conditions and delivery provider must be configurable and tested.

**FARM-013 (P0): Agricultural articles/advisories.** Each item includes title, body/summary, source, publication/update date, language and category. Do not publish unverified generated claims as authoritative agronomic instructions.

**FARM-014 (P1): Bookmark or save.** Users can save useful advisory content for later access.

### 5.3 Farm AI (defined now, production feature later)

**AI-001 (P1 design / P2 public release): Diagnosis request.** A future client can submit a crop/leaf image and contextual details such as crop type, region, observed symptoms and growth stage when known.

**AI-002 (P2): Asynchronous processing.** Image analysis uses a job/status flow. The user can leave the screen and return to a processing/completed/failed result.

**AI-003 (P2): Result structure.** A result should include predicted condition(s), confidence or calibrated uncertainty, model/version identifier, generated time, limitations and carefully scoped next-step guidance.

**AI-004 (P2): Uncertainty and safety.** Low-confidence/unsupported images must result in an “unable to assess reliably” outcome, not an invented diagnosis. Advice must not imply guaranteed crop recovery or yield.

**AI-005 (P2): Data consent.** State how images may be stored and whether they may be used for model improvement. Model-training use requires a clearly disclosed and legally reviewed basis; it must not be inferred from uploading a photo alone.

**AI-006 (P2): Service separation.** Python/FastAPI performs AI inference and preprocessing. Java remains the source of truth for user identity, access control and diagnosis records. The Python service does not directly write to the primary business database.

**AI-007 (P2): Evaluation gate.** No public diagnosis launch before evaluation on appropriate, representative, labeled data across target crops, diseases, phone cameras and field conditions. Product claims must match measured performance.

---

## 6. Hosil Bozor requirements

### 6.1 Catalog, offers, and harvest listings

**MKT-001 (P0): Browse catalog.** A buyer can browse active agricultural input categories and products without seeing drafts, suspended listings or unavailable offers as purchasable.

**MKT-002 (P0): Search/filter.** Search and filters should support the fields needed for the selected listing type, including keyword, category/crop, region, price range, available quantity, unit and availability. Exact supported filters may vary by category.

**MKT-003 (P0): Product offer.** An authorized seller can create an offer linked to a catalog product with price, currency, sale unit, quantity in stock, availability, photos, minimum order if applicable, and fulfillment terms.

**MKT-004 (P0): Harvest listing.** An authorized producer can publish a crop/harvest listing with crop, quantity/unit, region, available date/window, price or request-for-quote preference, minimum order, optional quality/grade, photos and pickup/delivery expectations.

**MKT-005 (P0): Listing lifecycle.** A listing can be draft, published, paused, sold out/fully allocated, or archived. Only eligible active listings appear in public search.

**MKT-006 (P0): Seller ownership.** Only the listing owner or a specifically authorized farm/business member can modify a listing.

**MKT-007 (P0): Moderation.** Users can report a listing; authorized admins can hide/suspend it with a reason and audit event. The owner receives an appropriate status explanation.

**MKT-008 (P0): Public seller information.** Listings show only approved public profile/business fields. Private phone numbers or location details must not be exposed by default without the agreed contact and privacy policy.

**MKT-009 (P1): Inquiry flow.** If direct purchase is not appropriate for a wholesale listing, a buyer can submit a structured inquiry/quote request with quantity and message. The seller can respond in a defined, auditable workflow.

### 6.2 Orders and inventory

**MKT-010 (P0): Single-seller order.** Each MVP order belongs to one seller. Multi-seller shopping is split into separate orders or deferred; a single order must not contain unrelated sellers unless the business and settlement model is redesigned.

**MKT-011 (P0): Server-side pricing.** The backend calculates totals from current stored prices, quantities, fees and delivery selections. It never trusts a total submitted by a mobile client.

**MKT-012 (P0): Quantity validation.** Order quantities must be positive, use a compatible unit, satisfy minimum order requirements and not exceed available quantity.

**MKT-013 (P0): Inventory reservation.** The order workflow must reserve or otherwise safely allocate stock/harvest quantity to prevent overselling under concurrent requests. Reservation expiry and release rules must be documented and tested.

**MKT-014 (P0): Retry safety.** Retried checkout or payment requests must not create duplicate orders, inventory decrements or charges. Use idempotency/unique operation identifiers where appropriate.

**MKT-015 (P0): Order history.** Buyer and seller can see their respective orders and permitted status/details. They cannot enumerate another user's orders.

**MKT-016 (P0): Cancellation.** Who can cancel and at what stage must be explicit. Cancellation must update inventory/payment workflow consistently and leave an audit trail.

**MKT-017 (P0): Fulfillment.** Seller can make permitted fulfillment updates. Buyer sees status and any action needed. The seller cannot mark an order paid based only on a client-provided claim.

**MKT-018 (P1): Reviews.** Reviews are deferred until the platform can establish that the reviewer participated in the relevant transaction and moderate abuse.

### 6.3 Payment requirements

**PAY-001 (P0 before real-money launch): Provider verification.** Select a provider only after confirming legal/commercial onboarding, the supported checkout flow, merchant-of-record arrangement, refund support, callback verification, and whether the required marketplace settlement model is supported.

**PAY-002 (P0): Separate states.** Payment status and order status are separate state machines. A paid order is not inferred solely from a client screen or redirect.

**PAY-003 (P0): Verified outcome.** The backend validates payment notifications/callbacks using the provider's approved verification mechanism and, where required, queries the provider server-to-server.

**PAY-004 (P0): Duplicate callback safety.** Repeated provider callbacks must not create duplicate state changes, refunds, or notifications.

**PAY-005 (P0): Reconciliation.** Pending/ambiguous payments need a way to reconcile with the provider and an operator-visible status. Timeouts must not silently mark a payment successful or failed without a defined verification rule.

**PAY-006 (P0 before real-money launch): Refund/cancellation policy.** Supported refund/cancellation scenarios, responsibility, timing, fees and customer communications must be approved before live transactions.

**PAY-007 (P0): No stored card data.** Hosil must not store payment-card credentials; use the selected provider's approved payment interface/tokenization flow.

**PAY-008 (P0): Auditability.** Record provider reference IDs, relevant state transitions and timestamps without storing secret credentials or unnecessary payment-sensitive data.

**PAY-009 (P0): Sandbox first.** Payment integration is tested in sandbox before live launch. If live provider approval is unavailable, the pilot must be clearly constrained to a non-live payment or manually approved order workflow.

---

## 7. Hosil Logistika requirements

### 7.1 Shipment requests and carrier offers

**LOG-001 (P0): Create shipment request.** An authorized requester can create a request from an order/harvest batch or enter cargo information manually. The request contains pickup/drop-off details, desired time window, cargo type, quantity/unit, required vehicle capabilities, contact instructions and notes.

**LOG-002 (P0): Location privacy.** Exact pickup and drop-off information is visible only to parties who need it for the request/accepted shipment, plus appropriately authorized support roles.

**LOG-003 (P0): Carrier profile.** A carrier can maintain business/contact information, service area, vehicle/transport capabilities and active status. Verification status is visible where relevant.

**LOG-004 (P0): Quote submission.** An eligible carrier can submit a quote with price/currency, validity time, proposed pickup time, vehicle/capacity details and conditions.

**LOG-005 (P0): Quote acceptance.** The requester can compare quotes and accept one. Acceptance must prevent multiple carriers from simultaneously becoming the assigned carrier for the same request unless a multi-carrier load is explicitly designed.

**LOG-006 (P0): Shipment status.** Authorized parties can view a timestamped status timeline.

**LOG-007 (P0): Status permissions.** Only the responsible requester/carrier/admin can perform each status transition. A carrier cannot update a shipment belonging to another carrier.

**LOG-008 (P0): Proof of delivery.** The responsible carrier can attach approved proof of delivery, such as a photo or recipient confirmation, with access limited to relevant parties. File type/size limits and retention must be defined.

**LOG-009 (P0): Cancellation.** Cancellation rules depend on current status and must explain whether a quote/assignment has already been accepted. All cancellations record actor, time and reason where applicable.

**LOG-010 (P1): Delivery location map.** Map/geocoding can be added after a reliable provider and local coverage are confirmed. The first version can use address/region fields if needed.

**LOG-011 (P2): Live tracking.** Background GPS collection, route optimization and automated dispatch are out of the MVP.

### 7.2 Leasing applications

**LEASE-001 (P1, partner-dependent): Application draft and submission.** A user can begin and submit an application for a listed leasing opportunity only when an authorized finance partner and an approved application process exist.

**LEASE-002 (P1, partner-dependent): Consent and documents.** Explain required data and sharing destination before collecting documents or forwarding data to a partner. Store documents privately and record consent/version/time.

**LEASE-003 (P1, partner-dependent): Status tracking.** Users see statuses such as submitted, under review, information requested, referred to partner, approved, rejected, withdrawn, or completed, as agreed with the partner.

**LEASE-004 (P0 design boundary): Partner decision ownership.** Hosil does not calculate or promise approval, set unapproved credit terms, or claim to provide financing itself. The partner owns eligibility, credit decisions, contracts and disbursement.

**LEASE-005 (P1): Access control.** Only the applicant and authorized partner/operations roles can view the application and documents. Administrative access is logged.

---

## 8. Shared platform and administrator requirements

### 8.1 Notifications

**PLAT-001 (P0): In-app events.** Record important user-visible events such as new order, order status update, accepted carrier quote, shipment status update, listing moderation and relevant weather alert.

**PLAT-002 (P0): Delivery is not the source of truth.** A failed push/SMS must not undo a successful business transaction. Notification attempts can be retried without duplicating the underlying transaction.

**PLAT-003 (P1): Preferences.** Users can control non-essential notification categories and delivery channels where supported. Mandatory transaction/security notices must be distinguished from promotional communications.

**PLAT-004 (P1): Localization.** Notification templates support the user's selected language and avoid leaking private order/location details to lock-screen previews unless settings and product policy allow it.

### 8.2 Files and media

**MEDIA-001 (P0): Private-by-default storage.** Uploaded files are private unless explicitly intended to be public listing media.

**MEDIA-002 (P0): Access-controlled retrieval.** Download/upload access is authorized by the backend; private files must not be exposed through guessable permanent public URLs.

**MEDIA-003 (P0): Validation.** Enforce allowed file types and size, verify content where feasible, use generated storage keys, and reject files that violate policy.

**MEDIA-004 (P0): Metadata.** Store owner, purpose, creation time, content type, size, and relevant business association. Do not rely on the original filename for authorization.

**MEDIA-005 (P0): Deletion and retention.** Define what happens to files when a listing/user is deleted or suspended and how long transaction/finance evidence must be retained.

### 8.3 Administration and support

**ADM-001 (P0): Category management.** Authorized administrators can create, edit, order, enable/disable marketplace categories.

**ADM-002 (P0): Listing moderation.** Admins can review reported listings, hide or restore listings, and record a reason.

**ADM-003 (P0): Account status.** Admins can suspend/reactivate accounts with reason and audit entry. High-risk actions may require confirmation or a second-person review later.

**ADM-004 (P0): Operational search.** Authorized staff can find an order, payment reference, shipment, or report by supported identifiers without being granted unrestricted access to unrelated user data.

**ADM-005 (P0): Audit log.** Record who performed sensitive admin and financial state changes, what object was affected, action/time, and a safe summary. Do not log passwords, access tokens or private credentials.

**ADM-006 (P1): Dispute workflow.** A support case can relate to an order/shipment, record messages/evidence and track open/investigating/resolved states. Full dispute automation is not required for the first pilot if a documented manual process exists.

---

## 9. Core workflow and state requirements

The exact implementation and state names should be finalized in the domain model/API contract, but the system must preserve the distinctions below.

### 9.1 Listing lifecycle

Suggested states:

`DRAFT → PUBLISHED ↔ PAUSED → SOLD_OUT/ALLOCATED → ARCHIVED`

An administrator may apply a separate moderation status such as `UNDER_REVIEW` or `SUSPENDED`. Moderation status should not overwrite the seller's intended publication state. Only authorized owners/admins can transition the listing; every state change is validated.

### 9.2 Order lifecycle

Candidate states:

`CREATED → PENDING_PAYMENT or AWAITING_SELLER_CONFIRMATION → CONFIRMED → PROCESSING → READY_FOR_DISPATCH → IN_TRANSIT → COMPLETED`

Possible alternate outcomes: `CANCELLED`, `PAYMENT_FAILED`, `REFUND_PENDING`, `REFUNDED`, `DISPUTED`.

The final state machine depends on whether the approved MVP flow uses immediate payment, seller acceptance first, or a request-for-quote workflow. These states must not be implemented as arbitrary strings with no transition rules. A transition table must specify allowed actor, preconditions, side effects, and inventory/payment behavior.

### 9.3 Payment lifecycle

Candidate states:

`CREATED → PENDING → SUCCEEDED`

Alternate/terminal outcomes may include `FAILED`, `CANCELLED`, `REFUND_PENDING`, `PARTIALLY_REFUNDED`, `REFUNDED`, or `UNKNOWN_REQUIRES_RECONCILIATION` if the provider workflow requires them.

Payment state is controlled by verified backend/provider events, not by the mobile app. Provider callbacks can arrive more than once or out of order; processing must be idempotent and validate current state.

### 9.4 Shipment lifecycle

Candidate states:

`REQUESTED → QUOTED → ACCEPTED → PICKUP_ARRANGED → PICKED_UP → IN_TRANSIT → DELIVERED`

Alternate states: `CANCELLED`, `DISPUTED`, `FAILED_DELIVERY` where operationally applicable.

A request can receive multiple quotes, but only the accepted quote assigns the carrier. A delivered shipment must have the required delivery evidence or an authorized, audited exception.

### 9.5 Harvest and inventory allocation

A harvest batch's recorded quantity, available quantity, listed quantity, reserved quantity, and sold quantity must be distinguishable where necessary. Publishing a listing must not duplicate the underlying harvest quantity. Order cancellation or payment expiry must release the correct reservation exactly once. The ERD and transaction design must explicitly resolve this before implementing checkout.

---

## 10. API and client behavior requirements

**API-001 (P0): Versioned API.** Public app APIs use a versioned prefix such as `/api/v1/`.

**API-002 (P0): Documented schemas.** Request and response DTOs, enums, validation rules and error formats are documented and tested. Database entities must not be serialized as uncontrolled API responses.

**API-003 (P0): Consistent errors.** Errors return stable machine-readable codes and a localized or safely displayable user message. Do not return stack traces or internal secrets to clients.

**API-004 (P0): Pagination.** Collection endpoints that may grow must support bounded pagination, stable ordering and documented filtering/sorting.

**API-005 (P0): Validation.** Validate data server-side, including quantity, unit, price, state transition, ownership and cross-field dependencies.

**API-006 (P0): Idempotency.** Checkout and other non-repeatable operations must safely handle client retries. Payment provider events must be idempotent.

**API-007 (P0): Time and money.** API contracts use explicit currency codes, decimal-safe monetary representations, timestamps with clear timezone semantics, and documented date-only fields where relevant.

**API-008 (P0): No secret exposure.** Responses must not reveal credentials, tokens, internal stack traces, private document URLs or fields the current user is not entitled to see.

**API-009 (P1): Generated client.** OpenAPI may be used to generate or validate a Flutter API client after the contract stabilizes. Generated code must be reproducible, not hand-edited in an undocumented way.

### Client UX requirements

- The user must see loading, empty, success, offline/error and retry states for primary workflows.
- Destructive or financially significant actions require clear confirmation and understandable consequences.
- Form errors should identify the field and how to fix the problem.
- Drafts should be preserved where practical, especially for farm logs, harvest forms and shipment requests.
- The three apps share typography, color tokens, buttons, spacing rules, form components and accessibility behavior, but each has product-appropriate navigation.
- No client-side state may be treated as authoritative for roles, inventory, order total, payment success, or financing approval.

---

## 11. Non-functional requirements

These are proposed MVP targets and must be tested against expected pilot usage. They are not guarantees until capacity and hosting are selected.

### 11.1 Security and privacy

- HTTPS for all non-local environments.
- Secrets supplied through environment/secret management, never committed to Git or embedded in mobile builds.
- Strong password hashing if passwords are used; abuse controls for OTP/login and recovery flows.
- Least-privilege access, ownership checks and negative authorization tests for every protected domain.
- Sensitive actions and state changes are audited without logging secrets or excessive personal data.
- Rate limiting for authentication, public search where needed, uploads, and other abuse-sensitive endpoints.
- Validate uploads; private files are stored privately and served through authorized or short-lived access.
- Define user-data retention, deletion/account closure, consent and data-export needs before launch, subject to applicable requirements.
- Review common web/API risks including broken object-level authorization, injection, excessive data exposure and insecure integration callbacks.

### 11.2 Reliability and data integrity

- Automated migrations run predictably in local/test/staging environments.
- Database constraints protect critical invariants even when application code has a bug.
- Order, inventory, payment and shipment state changes use well-defined transactions and state-transition validation.
- External-provider failure must not corrupt core business records.
- Background jobs are retryable where appropriate and safe from duplicate side effects.
- Backups and a tested restore procedure are required before production customer data is collected.

### 11.3 Performance and scale

Initial validation targets:

- Common read endpoints: target p95 under 500 ms under the defined pilot load, excluding third-party/provider latency.
- Common writes: target p95 under 800 ms under the defined pilot load, excluding external provider processing.
- Search results should be paginated and bounded.
- Image uploads and AI processing must not block ordinary API worker capacity.
- Slow integrations should have timeouts, safe failure behavior, retries where appropriate, and observability.

These figures are starting targets. After pilot traffic and hosting are known, run load tests and adjust them using measured data.

### 11.4 Localization, accessibility, and usability

- Uzbek and Russian strings must be externalized rather than hardcoded throughout the UI.
- Currency, number, date and measurement formatting must respect locale while stored values remain normalized.
- Text should remain readable at larger font sizes and critical interactions should not rely only on color.
- Support screen readers and sensible focus order for important controls where the framework allows it.
- Critical error messages should explain next steps instead of displaying raw technical output.

### 11.5 Observability and operations

- Structured application logs with request/correlation IDs.
- Health/readiness checks and monitoring for the Java API, database, background workers and Python AI service when introduced.
- Metrics for API errors/latency, job failure/retry, payment-state mismatches, and notification delivery outcomes.
- Alerts for failed backups, growing job queues, repeated provider failures and unresolved payment reconciliation cases.

---

## 12. Success metrics

The pilot should be evaluated using a small set of measurable outcomes. Establish baselines and target numbers with pilot partners before treating these as commitments.

### Acquisition and activation
- Number of invited users who complete registration.
- Percentage who complete the primary setup for their persona (farm profile, seller profile, or carrier profile).
- Time from registration to first meaningful action.

### Hosil Farm
- Weekly active farmers.
- Percentage of active farmers who record a field/crop cycle or farm activity.
- Harvest batches recorded and percentage explicitly published to Bozor.
- Weather/advisory views and opt-in alert usage.

### Hosil Bozor
- Active sellers and published offers/listings.
- Search-to-listing-view and listing-view-to-order/request conversion.
- Orders created, confirmed, completed, cancelled and disputed.
- Inventory mismatch/oversell incidents; target is zero known oversells in validated concurrency tests.
- Payment success/failure/reconciliation rates when provider integration is live.

### Hosil Logistika
- Requests receiving at least one quote.
- Time from request to first quote and from request to accepted quote.
- Accepted shipments completed and cancellations by stage.
- Missing or disputed proof of delivery.

### Reliability and trust
- Crash-free sessions and API error rates.
- Support issues per completed transaction.
- Unauthorized access test failures; target is zero known access-control failures before pilot.
- User-reported listing fraud and time to moderate.

Do not optimize metrics by encouraging unnecessary notifications, collecting excessive personal data, or treating AI confidence as proof of correctness.

---

## 13. Analytics and privacy requirements

**DATA-001 (P0): Minimum necessary data.** Collect only data required to operate the selected feature or fulfill a clearly disclosed purpose.

**DATA-002 (P0): Separate private and public fields.** A database entity may contain both; API DTOs and file access must expose only approved fields.

**DATA-003 (P0): Purpose and consent.** Before collecting precise location, sensitive documents, or AI images for additional uses, explain the purpose and obtain the required permission/consent after legal review.

**DATA-004 (P0): Audit sensitive access.** Access to finance documents, private delivery information, account suspension and financial state changes must be auditable.

**DATA-005 (P1): Product analytics.** Record privacy-respecting product events with user identifiers minimized/pseudonymized where practical. Do not include passwords, tokens, document contents or raw AI images in analytics events.

**DATA-006 (P0): Account lifecycle.** Define account closure, deactivation, retention of transaction records, deletion/anonymization rules and backups before launch. Do not promise immediate erasure of records that must lawfully be retained; obtain qualified local review for applicable requirements.

---

## 14. Integrations and external dependencies

The following providers are not yet selected. Each integration needs a short technical/commercial feasibility record before it becomes a production dependency.

| Integration | Purpose | Required validation before commitment |
|---|---|---|
| SMS/OTP provider | Login verification/recovery if phone OTP selected | Delivery to target regions, cost, rate limits, anti-abuse controls, uptime and sender approval |
| Payment provider (e.g. Payme/Click or another approved provider) | Customer payment | Merchant onboarding, checkout model, signed/verified callbacks, refunds, reconciliation, commissions and settlement model |
| Weather provider | Current conditions/forecast/alerts | Uzbekistan coverage, forecast resolution, usage rights, quotas, freshness, language/units and cost |
| Push notifications | Mobile notifications | Platform setup, user opt-in, delivery behavior and localization |
| Maps/geocoding | Pickup/drop-off and optional farm location | Local address quality, coverage, price, terms and user privacy |
| Object storage | Photos and private documents | Private access, lifecycle policies, upload/download limits, backup and region/hosting decision |
| Finance partner | Leasing referral/application | Partner agreement, accepted data, explicit consent, secure handoff and status feedback |
| AI model/data source | Crop assessment | Dataset rights, label quality, representative coverage, measured accuracy, operating cost and limitations |

### Integration rule

Keep provider-specific APIs behind interfaces/adapters where the ongoing product benefit justifies it. Do not build a general-purpose abstraction framework for hypothetical providers; implement the actual selected provider cleanly and isolate the vendor-specific contract.

---

## 15. Launch blockers and business decisions

The following decisions must be made or explicitly scoped out before the related feature goes live. They are not details the coding model should invent.

### Required before first pilot

1. **Pilot region and recruitment:** Which province/district and what farmer, supplier, buyer and carrier cohort will test the product?
2. **Language confirmation:** Are Uzbek Latin and Russian both required on day one? Is Uzbek Cyrillic required later?
3. **Authentication:** Phone OTP, email/password, or another mechanism? Which provider, delivery limits and recovery process?
4. **Marketplace transaction model:** Is an MVP order binding, or does it begin as a purchase request requiring seller confirmation?
5. **Seller eligibility and moderation:** Can any registered user sell, or must sellers/businesses be verified first?
6. **Sale units and categories:** What units are allowed per category/crop, and how will unit conversion/rounding work?
7. **Harvest quantity allocation:** Can one harvest batch be published to several listings? How do reservations and partial fulfillment work?
8. **Delivery responsibility:** Does the seller, buyer, carrier, or platform arrange delivery for a given order type?
9. **Support/disputes:** Who handles complaints during the pilot and what outcomes can they authorize?
10. **Data policy:** Who owns operational data, how user consent is recorded, and what retention/deletion rules apply?

### Required before real-money transactions

11. Provider/merchant approval and the exact Payme/Click/other payment flow.
12. Which legal entity receives funds and whether the selected model supports marketplace transactions and seller settlements.
13. Cancellation, refunds, partial refunds, fees, charge/complaint handling, invoice/receipt and reconciliation process.
14. Seller identity/business verification, tax/accounting responsibilities and customer terms reviewed by qualified local advisers.

### Required before leasing launch

15. Named authorized finance partner and confirmed application handoff/status flow.
16. Approved data/document list, applicant consent, privacy and retention procedure.
17. Exact boundary between platform referral/status tracking and the partner's financing decision/contract.

### Required before production AI diagnosis

18. Initial crop types and disease categories supported.
19. Data source, image label standard, dataset rights and independent evaluation set.
20. Acceptance thresholds by supported class and conditions for abstaining/returning “unable to assess.”
21. Human/agronomist escalation language and how model updates will be evaluated/versioned.

---

## 16. Acceptance criteria for the first pilot

The pilot is ready only when the criteria relevant to its agreed scope are demonstrated—not merely generated as code.

### Shared platform
- A single user identity works across all three apps/environments according to the chosen login model.
- Role and ownership tests prove users cannot access other users' private records through direct API calls.
- Migrations can build the schema from an empty database and upgrade an existing test database reproducibly.
- API error formats, validation, pagination, logs and documented health checks work.
- Private uploaded files cannot be retrieved by an unauthorized user.
- Backup and restore have been tested in the selected hosting environment before production data is used.

### Farm
- A farmer can create a farm, field, crop cycle, activity log and harvest batch.
- Farm-scoped records enforce the correct ownership relationships.
- Weather data displays a source/update time and a meaningful failure state.
- Publishing a harvest requires explicit user action and exposes only approved public fields.
- Availability/reservation logic cannot sell more harvest than is available in concurrent tests.

### Bozor
- A seller can publish, edit, pause and archive an offer/listing.
- A buyer can search, view and create a valid order/request.
- Invalid quantity, incompatible units, insufficient stock and stale pricing are rejected on the server.
- Duplicate retries do not create duplicate orders or charges.
- Order and payment states remain separate and are visible to authorized parties.
- Real payments are disabled until provider and business-model blockers are cleared.

### Logistika
- A requester can create a shipment request and eligible carriers can submit quotes.
- The requester can accept one quote and the system assigns only the selected carrier.
- Unauthorized parties cannot view private pickup/drop-off data or update the shipment.
- Status transitions are validated and important changes are recorded.
- Delivery evidence is stored and accessible only to authorized parties.
- Leasing stays disabled or clearly informational until a partner has approved the workflow.

### Operations and release
- Automated unit/integration tests and mobile critical-flow tests pass in CI.
- Dependency/security checks have no unresolved critical findings for the release.
- API, job failures and operational exceptions are observable.
- Admin moderation/support instructions and user-facing terms/privacy notices exist for the pilot.
- A pilot feedback process and a method for triaging issues are ready.

---

## 17. Implementation handoff rules for AI coding models

This PRD is not a prompt to generate the entire system in one attempt. Convert each requirement or small group of related requirements into an implementation ticket.

For each ticket, provide:
1. The exact requirement IDs being implemented.
2. Relevant architecture/domain/API documents.
3. Allowed files/modules to change and interfaces that must remain stable.
4. Acceptance criteria and required tests.
5. Explicit exclusions and assumptions that must not be invented.

The model must:
- inspect the existing repository before editing;
- propose a short implementation plan when a task crosses multiple modules;
- make focused changes rather than replacing unrelated code;
- add/update tests for normal, invalid, unauthorized, duplicate/retry and failure paths as applicable;
- run the actual build and test commands, report their real output, and never claim tests passed unless they ran;
- call out unresolved product decisions instead of silently guessing;
- avoid committing secrets, real customer data or generated files that should not be tracked;
- stop and request a requirement/architecture decision when a change would alter a business invariant or public API contract.

A separate review pass should check correctness, authorization, transaction boundaries, migration safety, privacy, test adequacy, and whether the code fits the chosen module boundaries.

### Definition of done for every feature

- Requirement(s) and acceptance criteria are met.
- API, domain and database rules are aligned.
- Relevant tests pass in the actual environment.
- Invalid input, unauthorized access, retries and important external failures are covered.
- Logs/errors expose enough diagnostic detail without leaking secrets or personal data.
- Documentation and API schemas are updated.
- Diff is reviewed and the repository remains buildable.

---

## 18. Recommended next artifacts and order

1. **PRD review:** confirm the product scope, role assumptions, P0/P1/P2 boundary, and launch blockers in this document.
2. **Role/permission matrix:** convert the rules in Section 4 into a complete operation-by-operation matrix.
3. **User journeys and state diagrams:** detail onboarding, harvest publication, order/payment, shipment and leasing workflows.
4. **Domain model and ERD:** resolve ownership, harvest quantity allocation, inventory reservation, order/payment separation, and cross-module relationships.
5. **API contract:** define DTOs, endpoints, status codes, idempotency, pagination and errors.
6. **Wireframes and design system:** map critical journeys to mobile screens and shared components.
7. **Integration feasibility checklist:** verify OTP, payment, weather, push, mapping/storage and finance partners.
8. **Repository scaffold:** initialize Java/Spring Boot, PostgreSQL, Flyway, test tooling and Flutter packages only after the relevant decisions are recorded.

**Recommended immediate next task:** review the launch-blocker decisions in Section 15 and resolve the ones that affect the MVP domain model—especially order/payment responsibility, wholesale purchase flow, harvest allocation, carrier workflow, and authentication. Then proceed to the domain model/ERD.
