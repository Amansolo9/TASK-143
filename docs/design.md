# LearnMart Offline Training Commerce & Classroom - Design Document

## 1. Overview

LearnMart is a fully offline Android application for a US-based professional training provider. It enables course sales, enrollment management, classroom operations, assessments, and payment reconciliation from a single Android device with zero internet dependency. All data is stored on-device in encrypted SQLite databases.

### Target Users
| Role | Responsibilities |
|------|-----------------|
| **Administrator** | Device owner; sets policies, manages backups, user management |
| **Registrar** | Enrollment approvals, seat control, class management |
| **Instructor** | Teaching operations, assessment creation, grading |
| **Teaching Assistant** | Grading support |
| **Learner** | Shopping, enrollment requests, coursework submission |
| **Finance Clerk** | Payment recording, refunds, settlement reconciliation |

---

## 2. Architecture

### 2.1 High-Level Architecture

The application follows **Clean Architecture** with three distinct layers:

```
+---------------------------------------------------------------+
|                   UI Layer (Jetpack Compose)                   |
|  MainActivity + Navigation + 38 Compose Screens + ViewModels  |
|  Route guards: RequirePermission, RequireOperationsAccess     |
+-------------------------------+-------------------------------+
                                |
+-------------------------------v-------------------------------+
|                  Domain Layer (Business Logic)                 |
|  50+ Use Cases | 11 Domain Model files | 9 Repository IFs    |
|  Engines: PricingEngine, AutoGradingEngine, SimilarityEngine  |
+-------------------------------+-------------------------------+
                                |
+-------------------------------v-------------------------------+
|                 Data Layer (Persistence)                       |
|  12 Repository Impls | 12 DAOs | 94 Room Entities            |
|  SQLDelight (type-safe audit queries) | Type Converters       |
+-------------------------------+-------------------------------+
                                |
+-------------------------------v-------------------------------+
|              Encrypted SQLite (On-Device)                      |
|  learnmart_encrypted.db (Room + SQLCipher AES-256)            |
|  learnmart_queries.db (SQLDelight audit read layer)           |
+---------------------------------------------------------------+
```

### 2.2 Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 1.9.22 |
| UI | Jetpack Compose + Material 3 | Compose Compiler 1.5.8 |
| DI | Hilt | 2.50 (KSP) |
| Primary DB | Room | 2.6.1 |
| DB Encryption | SQLCipher | 4.5.4 |
| Type-Safe Queries | SQLDelight | 2.0.1 |
| Async | Kotlin Coroutines | 1.7.3 |
| Background Jobs | WorkManager | 2.9.0 |
| Navigation | Compose Navigation | 2.7.6 |
| Serialization | Kotlinx Serialization | 1.6.2 |
| Testing | JUnit 4 + MockK + Turbine + Truth | Various |
| Min SDK | Android 10 (API 29) | - |
| Target SDK | Android 14 (API 34) | - |

### 2.3 Room + SQLDelight Boundary

- **Room** owns all schema creation, migrations, entity management, and transactional writes across 94 entities and 12 DAOs.
- **SQLDelight** owns a separate `learnmart_queries.db` for type-safe read-only audit queries via `SqlDelightAuditRepository`.
- This avoids dual-driver encryption conflicts while Room remains the authoritative source of truth.

### 2.4 Dependency Injection

Configured in `app/src/main/java/com/learnmart/app/di/`:
- **`DatabaseModule.kt`** -- Room database + SQLCipher + Android Keystore setup
- **`RepositoryModule.kt`** -- Binds 12 repository interfaces to implementations
- **`SqlDelightModule.kt`** -- SQLDelight driver configuration

---

## 3. Database Design

### 3.1 Entity Organization (94 Entities, 5 Phases)

**Phase 1 -- Identity, Policy, Audit (11 entities)**
`UserEntity`, `RoleEntity`, `RolePermissionEntity`, `UserRoleAssignmentEntity`, `SessionEntity`, `AuditEventEntity`, `StateTransitionLogEntity`, `PolicyEntity`, `PolicyHistoryEntity`, `BlacklistFlagEntity`

**Phase 2 -- Courses, Classes, Enrollment (17 entities)**
`CourseEntity`, `CourseVersionEntity`, `CourseMaterialEntity`, `PublicationEventEntity`, `ClassOfferingEntity`, `ClassSessionEntity`, `ClassStaffAssignmentEntity`, `CapacityOverrideEntity`, `EnrollmentRequestEntity`, `EnrollmentEligibilitySnapshotEntity`, `ApprovalFlowDefinitionEntity`, `ApprovalStepDefinitionEntity`, `EnrollmentApprovalTaskEntity`, `EnrollmentDecisionEventEntity`, `WaitlistEntryEntity`, `EnrollmentRecordEntity`, `EnrollmentExceptionEntity`

**Phase 3 -- Commerce, Orders, Payments (17 entities)**
`CartEntity`, `CartLineItemEntity`, `QuoteSnapshotEntity`, `OrderEntity`, `OrderLineItemEntity`, `OrderPriceComponentEntity`, `InventoryItemEntity`, `InventoryLockEntity`, `FulfillmentRecordEntity`, `DeliveryConfirmationEntity`, `ReturnExchangeRecordEntity`, `PaymentRecordEntity`, `PaymentAllocationEntity`, `RefundRecordEntity`, `LedgerEntryEntity`, `IdempotencyTokenEntity`

**Phase 4 -- Assessment, Grading, Plagiarism (16 entities)**
`QuestionBankEntity`, `KnowledgeTagEntity`, `QuestionEntity`, `QuestionChoiceEntity`, `AssignmentEntity`, `AssessmentReleaseWindowEntity`, `SubmissionEntity`, `SubmissionAnswerEntity`, `ObjectiveGradeResultEntity`, `SubjectiveGradeQueueItemEntity`, `GradeDecisionEntity`, `WrongAnswerExplanationLinkEntity`, `SimilarityFingerprintEntity`, `SimilarityMatchResultEntity`

**Phase 5 -- Operations (16 entities)**
`ImportJobEntity`, `SettlementImportBatchEntity`, `SettlementImportRowEntity`, `ReconciliationRunEntity`, `ReconciliationMatchEntity`, `DiscrepancyCaseEntity`, `ExportJobEntity`, `BackupArchiveEntity`, `RestoreRunEntity`, `MaintenanceJobRunEntity`, `DataIntegrityIssueEntity`, `SettlementPaymentUpdateEntity`

### 3.2 Encryption at Rest

Configured in `di/DatabaseModule.kt`:
- **SQLCipher AES-256** encrypts the entire Room database file (`learnmart_encrypted.db`)
- Passphrase is a random UUID pair (256-bit entropy), encrypted with **AES-256-GCM** via **Android Keystore** (alias: `learnmart_db_key`)
- Encrypted passphrase + IV stored in SharedPreferences (Base64)
- Release builds fail-closed if Keystore unavailable; debug builds may use SHA-256-derived fallback

### 3.3 Composite Indexes

- `userId + createdAt` on orders for user order history queries
- `courseId + sessionTime` on class sessions for schedule lookups
- Unique username index on users
- Composite indexes on enrollment requests, inventory locks, and audit events

### 3.4 Migration Support

Database version 6 with migration support. Schema exported to `$projectDir/schemas` for verification. No destructive auto-migration; callback logs on downgrade attempts.

---

## 4. Security Design

### 4.1 Credential Management

Implemented in `security/CredentialManager.kt`:
- **Algorithm**: PBKDF2WithHmacSHA256
- **Iterations**: 120,000
- **Key Length**: 256 bits
- **Salt**: 32-byte random (SecureRandom)
- Password validation: minimum 8 characters
- PIN validation: minimum 4 digits, digits-only
- Account lockout: 5 failed attempts triggers 15-minute lockout

### 4.2 Session Management

Implemented in `security/SessionManager.kt`:
- 15-minute idle timeout (reset on activity)
- Single active session per user (new login terminates old session)
- Session validation via `ValidateSessionUseCase`

### 4.3 Role-Based Access Control

Defined in `domain/model/RoleModels.kt` with 6 roles and 24 permissions:

| Role | Key Permissions |
|------|----------------|
| Administrator | All 24 permissions |
| Registrar | CATALOG_MANAGE, CLASS_MANAGE, CLASS_STAFF_ASSIGN, ENROLLMENT_REVIEW, ENROLLMENT_OVERRIDE_CAPACITY, AUDIT_VIEW |
| Instructor | ASSESSMENT_CREATE, ASSESSMENT_GRADE, ASSESSMENT_REOPEN |
| Teaching Assistant | ASSESSMENT_GRADE |
| Learner | ENROLLMENT_REQUEST, ORDER_CREATE |
| Finance Clerk | PAYMENT_RECORD, PAYMENT_RECONCILE, REFUND_ISSUE, IMPORT_MANAGE, EXPORT_MANAGE |

UI enforces permissions via `RequirePermission` composable route guards.

### 4.4 Settlement Signature Verification

Implemented in `security/SettlementSignatureVerifier.kt`:
- **Algorithm**: HMAC-SHA256
- Shared secret configured via policy (`settlement_signature_secret`)
- Optional enforcement via `signature_verification_required` policy
- Returns sealed result: Valid, Invalid, Error, or NoSecretConfigured

---

## 5. State Machines

### 5.1 Order Lifecycle (17 States)

Defined in `domain/model/CommerceModels.kt` -- `OrderStatus` enum with `allowedTransitions()`:

```
CART -> QUOTED, PENDING_SUBMISSION
QUOTED -> PENDING_SUBMISSION, CART
PENDING_SUBMISSION -> PLACED_UNPAID
PLACED_UNPAID -> PARTIALLY_PAID, PAID, AUTO_CANCELLED, MANUAL_CANCELLED
PARTIALLY_PAID -> PAID, AUTO_CANCELLED, MANUAL_CANCELLED
PAID -> FULFILLMENT_IN_PROGRESS, REFUND_IN_PROGRESS
FULFILLMENT_IN_PROGRESS -> AWAITING_PICKUP, DELIVERED
AWAITING_PICKUP -> DELIVERED, CLOSED
DELIVERED -> CLOSED, RETURN_REQUESTED, EXCHANGE_IN_PROGRESS
CLOSED -> RETURN_REQUESTED, EXCHANGE_IN_PROGRESS
RETURN_REQUESTED -> RETURNED
EXCHANGE_IN_PROGRESS -> EXCHANGED
Terminal: AUTO_CANCELLED, MANUAL_CANCELLED, RETURNED, EXCHANGED, REFUND_IN_PROGRESS
```

**Timeout Automation** (via `worker/OrderTimeoutWorker.kt`):
- Unpaid orders auto-cancel after 30 minutes (configurable)
- Awaiting-pickup orders auto-close after 7 days (configurable)
- Expired inventory locks cleaned up and stock released

### 5.2 Enrollment Lifecycle (13 States)

Defined in `domain/model/EnrollmentModels.kt` -- `EnrollmentRequestStatus`:

```
DRAFT -> SUBMITTED
SUBMITTED -> PENDING_APPROVAL, REJECTED, WAITLISTED, EXPIRED
PENDING_APPROVAL -> APPROVED, REJECTED, WAITLISTED, EXPIRED
WAITLISTED -> OFFERED, CANCELLED, EXPIRED
OFFERED -> APPROVED, DECLINED, EXPIRED
APPROVED -> ENROLLED, CANCELLED
ENROLLED -> WITHDRAWN, COMPLETED
Terminal: REJECTED, EXPIRED, CANCELLED, DECLINED, WITHDRAWN, COMPLETED
```

**Expiry Automation** (via `worker/EnrollmentExpiryWorker.kt`):
- Pending requests expire after 48 hours
- Waitlist offers expire after 24 hours
- Pending approval tasks auto-expire

### 5.3 Payment Lifecycle (8 States)

Defined in `domain/model/CommerceModels.kt` -- `PaymentStatus`:

```
RECORDED -> ALLOCATED, VOIDED, DISCREPANCY_FLAGGED
ALLOCATED -> CLEARED, PARTIALLY_REFUNDED, REFUNDED, DISCREPANCY_FLAGGED
CLEARED -> PARTIALLY_REFUNDED, REFUNDED, DISCREPANCY_FLAGGED
DISCREPANCY_FLAGGED -> RESOLVED, CLEARED, PARTIALLY_REFUNDED, REFUNDED
RESOLVED -> CLEARED, PARTIALLY_REFUNDED, REFUNDED
Terminal: VOIDED, PARTIALLY_REFUNDED, REFUNDED
```

### 5.4 Submission Lifecycle (13 States)

Defined in `domain/model/AssessmentModels.kt` -- `SubmissionStatus`:

```
NOT_RELEASED -> AVAILABLE
AVAILABLE -> IN_PROGRESS, MISSED
IN_PROGRESS -> SUBMITTED, ABANDONED, LATE_SUBMITTED
SUBMITTED -> AUTO_GRADED, QUEUED_FOR_MANUAL_REVIEW
LATE_SUBMITTED -> AUTO_GRADED, QUEUED_FOR_MANUAL_REVIEW
AUTO_GRADED -> FINALIZED, QUEUED_FOR_MANUAL_REVIEW
QUEUED_FOR_MANUAL_REVIEW -> GRADED
GRADED -> FINALIZED
FINALIZED -> REOPENED_BY_INSTRUCTOR
REOPENED_BY_INSTRUCTOR -> IN_PROGRESS
Terminal: MISSED, ABANDONED
```

---

## 6. Commerce Engine

### 6.1 Pricing

Implemented in `domain/usecase/commerce/PricingEngine.kt`:

```
Subtotal = SUM(line item totals)
Discount = 0 (placeholder for future discount engine)
Tax = (Subtotal - Discount) * tax_rate
Service Fee = (Subtotal - Discount) * service_fee_rate
Packaging Fee = $1.50 if any PHYSICAL_MATERIAL items, else $0.00
Grand Total = (Subtotal - Discount) + Tax + Service Fee + Packaging Fee
```

Constants from `CommerceModels.kt`:
- `MIN_ORDER_TOTAL = $25.00`
- `DEFAULT_PACKAGING_FEE = $1.50`
- `MIN_REFUND = $0.01`
- Scale: 2 decimal places, HALF_UP rounding

### 6.2 Idempotent Order Submission

Implemented in `domain/usecase/commerce/CheckoutUseCase.kt`:
1. Client provides `idempotencyToken` with each order submission
2. Payload hash: SHA-256 of `cartId + sorted items (referenceId:quantity:unitPrice)`
3. Within 5-minute window: same token + same hash returns existing order
4. Same token + different hash returns `IDEMPOTENCY_PAYLOAD_MISMATCH` error
5. Token persisted to `IdempotencyTokenEntity` and cleaned up by `OrderTimeoutWorker`

### 6.3 Inventory Lock Mechanism

1. Physical material items acquire locks at checkout time
2. Lock expiry: 10 minutes (configurable via `INVENTORY_LOCK_EXPIRY_MINUTES`)
3. Reserved stock adjusted atomically with lock acquisition
4. On order creation: locks transition to CONSUMED
5. On failure/timeout: locks RELEASED, reserved stock reversed
6. Background cleanup by `OrderTimeoutWorker`

### 6.4 Checkout Flow

1. Idempotency check (return existing if duplicate)
2. Load and validate cart (ACTIVE status, owned by requesting user)
3. Checkout policy validation (SAME_CLASS_ONLY vs CROSS_CLASS_ALLOWED)
4. Blacklist check (reject if flagged)
5. Reprice at submit time (fresh tax, service fee, packaging calculation)
6. Minimum order total validation ($25.00)
7. Inventory lock acquisition for physical items
8. Atomic transaction: create order + line items + price components + update locks + mark cart checked out + save idempotency token + audit event

### 6.5 Refund Processing

Implemented in `domain/usecase/commerce/IssueRefundUseCase.kt`:
- Minimum refund: $0.01
- Daily limit: 3 refunds per learner per calendar day (configurable)
- Override requires `REFUND_OVERRIDE_LIMIT` permission + non-blank override note
- Refund method matches original tender type (CASH, CHECK, EXTERNAL_CARD_TERMINAL_REFERENCE)
- Atomic: record refund + create ledger entry (negated amount) + update payment status + audit trail

### 6.6 Tender Types

Defined in `CommerceModels.kt`: CASH, CHECK, EXTERNAL_CARD_TERMINAL_REFERENCE

---

## 7. Enrollment System

### 7.1 Enrollment Request Flow

Implemented in `domain/usecase/enrollment/SubmitEnrollmentUseCase.kt`:

1. **Eligibility evaluation** -- returns JSON flags: `{"no_blacklist":true,"class_open":true}`
2. **Capacity check** -- count active enrollments against `hardCapacity`
   - At/over capacity with waitlist enabled: add to waitlist with position and priority tier
   - At/over capacity with waitlist disabled: reject
   - Under capacity: route to approval flow
3. **Approval flow routing**:
   - SERIAL: create task for first step only
   - PARALLEL: create tasks for all steps concurrently
   - No flow configured: auto-approve immediately
4. **Auto-approval**: status -> APPROVED -> ENROLLED, create enrollment record, increment enrolled count

### 7.2 Multi-Level Approval

- `ApprovalFlowDefinitionEntity` + `ApprovalStepDefinitionEntity` define the flow
- `EnrollmentApprovalTaskEntity` tracks individual approval tasks
- `EnrollmentDecisionEventEntity` records each decision for auditable replay
- Task expiry matches request expiry (48 hours default)
- `ResolveApprovalUseCase` handles approval/rejection at each step

### 7.3 Waitlist Management

- `WaitlistEntryEntity` with position, priority tier, status
- Statuses: ACTIVE, OFFERED, ACCEPTED, EXPIRED, CANCELLED
- `WaitlistPromotionUseCase` handles promotion when seats become available
- Offers expire after 24 hours

---

## 8. Assessment Engine

### 8.1 Question Banks

- Questions tagged with `DifficultyLevel` (EASY, MEDIUM, HARD) and `KnowledgeTagEntity`
- `QuestionType`: OBJECTIVE (auto-gradable) and SUBJECTIVE (manual grading queue)
- `AssessmentType`: ASSIGNMENT, QUIZ
- Timed release windows via `AssessmentReleaseWindowEntity`

### 8.2 Auto-Grading

Implemented in `domain/usecase/assessment/AutoGradingEngine.kt`:
- Objective questions auto-graded against stored correct answers
- Results stored in `ObjectiveGradeResultEntity`
- Subjective questions routed to `SubjectiveGradeQueueItemEntity` for manual review
- Queue statuses: PENDING, IN_REVIEW, GRADED, SKIPPED

### 8.3 Plagiarism Detection

Implemented in `domain/usecase/assessment/SimilarityEngine.kt`:
- **Algorithm**: N-gram fingerprinting (n=5 word sequences, shingle size 3)
- **Normalization**: lowercase, remove non-alphanumeric except spaces
- **Fingerprint compaction**: if >100 hashes, sample evenly spaced (keep max 100)
- **Comparison**: Jaccard similarity (intersection/union of hash sets)
- **Thresholds** (configurable via policy):
  - >= 0.85: `HIGH_SIMILARITY`
  - 0.70--0.84: `REVIEW_NEEDED`
  - < 0.70: `CLEAR`
- Fingerprints and match results persisted for audit

### 8.4 Wrong-Answer Tracking

- `WrongAnswerExplanationLinkEntity` links incorrect answers to pedagogical explanations
- `GradeDecisionEntity` records grading decisions with feedback

---

## 9. Operations

### 9.1 Settlement Import

Implemented in `domain/usecase/operations/ImportSettlementUseCase.kt`:
- Supported formats: CSV (RFC 4180 compliant) and JSON
- File size limit: 25 MB (configurable)
- Signature verification: optional HMAC-SHA256 via `SettlementSignatureVerifier`
- Row validation: external ID, amount (valid BigDecimal), payment reference, tender type, date
- Duplicate detection: in-batch and against existing rows
- Job statuses: VALIDATING -> READY_TO_APPLY or REJECTED

### 9.2 Reconciliation

Implemented in `domain/usecase/operations/ReconciliationUseCase.kt`:
- **Matching**: settlement rows matched to payments by payment reference
- **Status mapping**: CLEARED/SETTLED/CONFIRMED -> CLEARED, VOIDED/VOID/CANCELLED -> VOIDED, DISCREPANCY/FLAGGED/DISPUTED -> DISCREPANCY_FLAGGED
- **Idempotent payment updates**: SHA-256 key of `externalRowId + "|" + status` prevents duplicate application
- **Discrepancy types**: NO_REFERENCE, AMOUNT_MISMATCH, UNMATCHED
- **Atomicity**: entire run wrapped in Room `withTransaction` -- full rollback on any error

### 9.3 Backup and Restore

Implemented in `domain/usecase/operations/BackupRestoreUseCase.kt`:

**Encryption**:
- Algorithm: AES-256-GCM (authenticated encryption)
- Key derivation: PBKDF2WithHmacSHA256, 120,000 iterations, 256-bit key
- Salt: 32 bytes (random), IV: 12 bytes (random)
- GCM tag: 128 bits

**Archive format**: `[salt_len:1][salt:32][iv_len:1][iv:12][encrypted_payload]`

**Backup process**: permission check -> load passphrase from policy -> generate salt/IV -> encrypt DB -> compute SHA-256 checksum -> persist metadata

**Restore process**: permission check -> verify archive status -> verify checksum -> read salt/IV from header -> derive key -> decrypt -> verify non-empty -> replace DB file

**Fail-closed**: backup/restore fails if passphrase not configured in policy

### 9.4 File I/O and Scoped Storage

- Android Storage Access Framework (SAF) for all file operations
- `CreateDocument` for backup export, `OpenDocument` for file import
- Format validation on import (CSV structure, JSON tokenization)
- Data cleansing: trim whitespace, de-duplicate by primary keys, reject invalid dates

---

## 10. Background Processing

### 10.1 WorkManager Integration

Configured in `LearnMartApplication.kt` with custom `HiltWorkerFactory`. Default `WorkManagerInitializer` disabled in manifest to prevent double initialization.

### 10.2 Workers

| Worker | Trigger | Constraints |
|--------|---------|-------------|
| `OrderTimeoutWorker` | Periodic | Unpaid cancel (30 min), pickup close (7 days), lock cleanup, token cleanup |
| `EnrollmentExpiryWorker` | Periodic | Request expiry (48h), offer expiry (24h), task expiry |
| `ReconciliationWorker` | On-demand (UI) | Idle + charging + battery-not-low |
| `BackupWorker` | On-demand (UI) | Idle + charging + battery-not-low |

All workers are idempotent with up to 4 retry attempts.

---

## 11. UI and Performance

### 11.1 Compose Architecture

- `MainActivity.kt` hosts the Compose navigation graph
- 38+ screen composables organized by feature domain
- ViewModels expose `StateFlow` consumed via `collectAsState()`
- Route-level permission guards: `RequirePermission` and `RequireOperationsAccess` composable wrappers

### 11.2 High-Churn List Performance

- `LazyColumn` with stable `key = { it.id }` for incremental diff-based rendering
- Offset-based DAO pagination for surfaces with 1000+ items
- Applied to: order lists, operations dashboards, audit events, enrollment queues, discrepancy lists, backup archives

### 11.3 Image Memory Management

Implemented in `util/ImageMemoryManager.kt`:
- LRU cache with 20 MB budget
- Two-pass bitmap decoding with `inSampleSize` downsampling
- Default max dimensions: 1024x1024
- Cache size tracked by `bitmap.byteCount`
- All decode operations on `Dispatchers.IO`

---

## 12. Configurable Policies

Defined in `domain/model/PolicyModels.kt` -- `PolicyDefaults`:

| Policy | Default | Type |
|--------|---------|------|
| Session timeout | 15 minutes | SYSTEM |
| Lockout attempts | 5 | SYSTEM |
| Lockout duration | 15 minutes | SYSTEM |
| Password min length | 8 | SYSTEM |
| Enrollment request expiry | 48 hours | ENROLLMENT |
| Waitlist offer expiry | 24 hours | ENROLLMENT |
| Minimum order total | $25.00 | COMMERCE |
| Packaging fee | $1.50 | FEE |
| Checkout policy | SAME_CLASS_ONLY | COMMERCE |
| Unpaid order cancel | 30 minutes | COMMERCE |
| Awaiting pickup close | 7 days | COMMERCE |
| Inventory lock expiry | 10 minutes | COMMERCE |
| Idempotency window | 5 minutes | COMMERCE |
| Max refunds/learner/day | 3 | RISK |
| Backup encryption required | true | BACKUP |
| Max import size | 25 MB | IMPORT_MAPPING |
| Signature verification required | false | IMPORT_MAPPING |
| Plagiarism high threshold | 0.85 | SYSTEM |
| Plagiarism review threshold | 0.70 | SYSTEM |

---

## 13. Testing Strategy

### 13.1 Unit Tests (41 files)

Located in `app/src/test/java/com/learnmart/app/`:
- Repository layer tests (`data/repository/`)
- Use case tests mirroring production structure (`domain/usecase/`)
- Security tests (`security/`)
- ViewModel and navigation tests (`ui/`)
- Utility tests (`util/`)

**Libraries**: JUnit 4.13.2, MockK 1.13.9, Turbine 1.0.0, Truth 1.2.0, Coroutines Test 1.7.3

### 13.2 Instrumented Tests

Located in `app/src/androidTest/java/com/learnmart/app/`:
- Room DAO integration tests (`data/local/`)
- Compose UI tests
- Custom `HiltTestRunner`

---

## 14. Audit Trail

- `AuditEventEntity` provides immutable append-only audit log
- `StateTransitionLogEntity` records all state machine transitions
- `EnrollmentDecisionEventEntity` enables auditable replay of approval decisions
- Audit categories and actions defined in `domain/model/AuditModels.kt`
- Read queries optimized via SQLDelight (`SqlDelightAuditRepository`)
