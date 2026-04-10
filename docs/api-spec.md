# LearnMart Offline Training Commerce & Classroom - Internal API Specification

## Overview

LearnMart is a fully offline Android application. There are no REST/HTTP APIs. All operations are mediated through **Use Cases** invoked by ViewModels, which serve as the internal API surface. This document specifies the public interface of each Use Case, its inputs, outputs, validation rules, and transactional guarantees.

All Use Cases are injected via Hilt and follow the pattern:
```kotlin
class SomeUseCase @Inject constructor(
    private val repository: SomeRepository,
    ...
) {
    suspend fun execute(request: Request): AppResult<Response>
}
```

Return type `AppResult<T>` is a sealed class with `Success(data: T)` and `Error(message: String, code: String?)` variants, defined in `util/AppResult.kt`.

---

## 1. Authentication Use Cases

### 1.1 LoginUseCase
**Location**: `domain/usecase/auth/LoginUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `LoginRequest(username: String, credential: String)` |
| Output | `AppResult<LoginResult>` containing `User` + `SessionRecord` |
| Validation | Username non-blank; credential non-blank; account not locked out (5 attempts / 15-min window) |
| Side Effects | Creates `SessionEntity`; terminates any existing session for the user; resets failed attempt counter on success; increments on failure |
| Audit | Login success/failure events logged |

### 1.2 LogoutUseCase
**Location**: `domain/usecase/auth/LogoutUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `sessionId: String` |
| Output | `AppResult<Unit>` |
| Side Effects | Marks session as terminated |

### 1.3 ValidateSessionUseCase
**Location**: `domain/usecase/auth/ValidateSessionUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `sessionId: String` |
| Output | `AppResult<Boolean>` |
| Logic | Returns false if session not found, terminated, or idle > 15 minutes |

### 1.4 CheckPermissionUseCase
**Location**: `domain/usecase/auth/CheckPermissionUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `userId: String, permission: Permission` |
| Output | `AppResult<Boolean>` |
| Logic | Checks user's assigned roles against permission grants from `RoleModels.kt` |

---

## 2. Course Management Use Cases

### 2.1 ManageCourseUseCase
**Location**: `domain/usecase/course/ManageCourseUseCase.kt`

**Operations**:
- `createCourse(title, description, fee, ...)` -> `AppResult<Course>`
- `publishCourse(courseId)` -> `AppResult<Course>` -- requires CATALOG_PUBLISH
- `unpublishCourse(courseId)` -> `AppResult<Course>`
- `createVersion(courseId, changes)` -> `AppResult<CourseVersion>`
- `addMaterial(courseId, material)` -> `AppResult<CourseMaterial>`

### 2.2 ManageClassUseCase
**Location**: `domain/usecase/course/ManageClassUseCase.kt`

**Operations**:
- `openClass(courseId, hardCapacity, ...)` -> `AppResult<ClassOffering>` -- e.g., 25 seats
- `closeClass(classId)` -> `AppResult<ClassOffering>`
- `scheduleSession(classId, timeSlot, ordering)` -> `AppResult<ClassSession>`
- `assignStaff(classId, userId, role)` -> `AppResult<ClassStaffAssignment>` -- requires CLASS_STAFF_ASSIGN

---

## 3. Enrollment Use Cases

### 3.1 SubmitEnrollmentUseCase
**Location**: `domain/usecase/enrollment/SubmitEnrollmentUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `learnerId: String, classOfferingId: String, priorityTier: Int?` |
| Output | `AppResult<EnrollmentRequest>` |
| Eligibility | Returns JSON flags: `{"no_blacklist":true,"class_open":true}` |
| Capacity Logic | If at/over `hardCapacity` and waitlist enabled: add to waitlist with position. If waitlist disabled: reject. If under capacity: route to approval flow. |
| Approval Routing | SERIAL: task for first step only. PARALLEL: tasks for all steps. No flow: auto-approve (APPROVED -> ENROLLED). |
| Auto-Approve Side Effects | Creates `EnrollmentRecordEntity` (ACTIVE), increments class enrolled count, creates decision event + state transition log |

### 3.2 ManageEnrollmentUseCase
**Location**: `domain/usecase/enrollment/ManageEnrollmentUseCase.kt`

**Operations**:
- `cancelRequest(requestId)` -> `AppResult<EnrollmentRequest>`
- `withdrawEnrollment(enrollmentId)` -> `AppResult<EnrollmentRecord>`
- `getRequestsByLearner(learnerId)` -> `AppResult<List<EnrollmentRequest>>`
- `getRequestsByClass(classId)` -> `AppResult<List<EnrollmentRequest>>`

### 3.3 ResolveApprovalUseCase
**Location**: `domain/usecase/enrollment/ResolveApprovalUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `taskId: String, decision: ApprovalTaskStatus, reviewerId: String, notes: String?` |
| Output | `AppResult<EnrollmentRequest>` |
| Logic | Records decision event; for SERIAL flows advances to next step or finalizes; for PARALLEL waits for all tasks |
| Over-Capacity Exception | Requires ENROLLMENT_OVERRIDE_CAPACITY permission; creates `EnrollmentExceptionEntity` |

### 3.4 WaitlistPromotionUseCase
**Location**: `domain/usecase/enrollment/WaitlistPromotionUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `classOfferingId: String` |
| Output | `AppResult<List<WaitlistEntry>>` promoted entries |
| Logic | Finds available seats, promotes by position + priority tier, transitions waitlist status to OFFERED, offer expires after 24 hours |

---

## 4. Commerce Use Cases

### 4.1 ManageCartUseCase
**Location**: `domain/usecase/commerce/ManageCartUseCase.kt`

**Operations**:
- `getOrCreateCart(learnerId)` -> `AppResult<Cart>`
- `addItem(cartId, itemType, referenceId, quantity, unitPrice)` -> `AppResult<CartLineItem>`
- `removeItem(cartId, lineItemId)` -> `AppResult<Unit>`
- `updateQuantity(cartId, lineItemId, quantity)` -> `AppResult<CartLineItem>`
- `getCartWithTotals(cartId)` -> `AppResult<Cart>` -- includes real-time pricing via PricingEngine

### 4.2 CheckoutUseCase
**Location**: `domain/usecase/commerce/CheckoutUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `cartId: String, learnerId: String, idempotencyToken: String` |
| Output | `AppResult<Order>` |
| Idempotency | SHA-256 hash of `cartId + sorted(referenceId:quantity:unitPrice)`. Same token within 5 min + same hash = return existing order. Same token + different hash = `IDEMPOTENCY_PAYLOAD_MISMATCH` error. |
| Inventory Lock | Physical items locked for 10 minutes. Reserved stock adjusted atomically. Locks CONSUMED on order creation, RELEASED on failure. |
| Policy Checks | Checkout policy (SAME_CLASS_ONLY / CROSS_CLASS_ALLOWED), blacklist check, minimum $25.00 total |
| Price Validation | All prices recalculated at submit time via `PricingEngine` |
| Transaction | Atomic Room `withTransaction`: order + line items + price components + lock updates + cart status + idempotency token + audit |

### 4.3 PricingEngine
**Location**: `domain/usecase/commerce/PricingEngine.kt`

| Method | Signature |
|--------|-----------|
| `calculatePricing` | `(lineItems: List<CartLineItem>) -> PricingResult` |
| `getMinimumOrderTotal` | `() -> BigDecimal` (default $25.00) |
| `getCheckoutPolicy` | `() -> CheckoutPolicy` |

**PricingResult Components**: SUBTOTAL, DISCOUNT, TAX, SERVICE_FEE, PACKAGING_FEE, GRAND_TOTAL

**Calculation**: Grand Total = (Subtotal - Discount) + Tax + Service Fee + Packaging Fee ($1.50 if physical materials present)

### 4.4 ManageOrderUseCase
**Location**: `domain/usecase/commerce/ManageOrderUseCase.kt`

**Operations**:
- `transitionOrder(orderId, newStatus)` -> `AppResult<Order>` -- validates against `OrderStatus.canTransitionTo()`
- `getOrder(orderId)` -> `AppResult<Order>`
- `getOrdersByUser(userId, page, size)` -> `AppResult<List<Order>>`
- `fulfillOrder(orderId)` -> `AppResult<FulfillmentRecord>`
- `confirmDelivery(orderId)` -> `AppResult<DeliveryConfirmation>`
- `requestReturn(orderId, reason)` -> `AppResult<ReturnExchangeRecord>`
- `requestExchange(orderId, reason)` -> `AppResult<ReturnExchangeRecord>`

### 4.5 RecordPaymentUseCase
**Location**: `domain/usecase/commerce/RecordPaymentUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `orderId: String, amount: BigDecimal, tenderType: TenderType, externalReference: String?` |
| Output | `AppResult<PaymentRecord>` |
| Tender Types | CASH, CHECK, EXTERNAL_CARD_TERMINAL_REFERENCE |
| Atomic Operations | Create payment record + payment allocation + ledger entry (PAYMENT_RECEIVED) + update order status (PARTIALLY_PAID or PAID) |

### 4.6 IssueRefundUseCase
**Location**: `domain/usecase/commerce/IssueRefundUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `paymentId: String, amount: BigDecimal, reason: String, overrideNote: String?` |
| Output | `AppResult<RefundRecord>` |
| Minimum | $0.01 |
| Daily Limit | 3 per learner per calendar day (configurable) |
| Override | Requires `REFUND_OVERRIDE_LIMIT` permission + non-blank override note |
| Method | Refund to original tender type |
| Validation | Amount <= (paidAmount - totalExistingRefunds) |
| Atomic Operations | Record refund + ledger entry (negated amount) + update payment status (PARTIALLY_REFUNDED or REFUNDED) + audit |

---

## 5. Assessment Use Cases

### 5.1 ManageAssessmentUseCase
**Location**: `domain/usecase/assessment/ManageAssessmentUseCase.kt`

**Operations**:
- `createAssignment(classId, title, type, releaseWindow, ...)` -> `AppResult<Assignment>`
- `addQuestion(assignmentId, questionBankId, questionType, difficulty, tags)` -> `AppResult<Question>`
- `addChoice(questionId, text, isCorrect)` -> `AppResult<QuestionChoice>`
- `setReleaseWindow(assignmentId, opensAt, closesAt)` -> `AppResult<AssessmentReleaseWindow>`
- `linkExplanation(questionId, wrongAnswerId, explanation)` -> `AppResult<WrongAnswerExplanationLink>`

### 5.2 SubmitAssessmentUseCase
**Location**: `domain/usecase/assessment/SubmitAssessmentUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `submissionId: String, answers: List<SubmissionAnswer>` |
| Output | `AppResult<Submission>` |
| Status Transition | IN_PROGRESS -> SUBMITTED (or LATE_SUBMITTED if past window) |
| Post-Submit | Triggers `AutoGradingEngine` for objective questions, routes subjective to grading queue, triggers `SimilarityEngine` for plagiarism check |

### 5.3 AutoGradingEngine
**Location**: `domain/usecase/assessment/AutoGradingEngine.kt`

| Field | Detail |
|-------|--------|
| Input | `submissionId: String` |
| Output | `AppResult<List<ObjectiveGradeResult>>` |
| Logic | Compares answers to stored correct choices; records per-question results |

### 5.4 SimilarityEngine
**Location**: `domain/usecase/assessment/SimilarityEngine.kt`

| Field | Detail |
|-------|--------|
| Input | `submissionId: String, assessmentId: String` |
| Output | `AppResult<List<SimilarityMatchResult>>` |
| Algorithm | N-gram fingerprinting (n=5), Jaccard similarity comparison |
| Thresholds | >= 0.85 HIGH_SIMILARITY, 0.70-0.84 REVIEW_NEEDED, < 0.70 CLEAR |
| Compaction | Max 100 hashes per fingerprint (evenly sampled if exceeds) |

---

## 6. Operations Use Cases

### 6.1 ImportSettlementUseCase
**Location**: `domain/usecase/operations/ImportSettlementUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `fileUri: Uri, format: String ("csv" or "json"), signatureHex: String?` |
| Output | `AppResult<ImportJob>` |
| Size Limit | 25 MB (configurable) |
| Signature | Optional HMAC-SHA256 verification via `SettlementSignatureVerifier` |
| Row Validation | External ID, amount (BigDecimal), payment reference, tender type, date |
| De-duplication | In-batch + against existing rows by external ID |
| Result Status | VALIDATING -> READY_TO_APPLY (some valid) or REJECTED (all invalid) |

### 6.2 ReconciliationUseCase
**Location**: `domain/usecase/operations/ReconciliationUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `importJobId: String` |
| Output | `AppResult<ReconciliationRun>` |
| Matching | Settlement rows matched to payments by payment reference |
| Status Mapping | CLEARED/SETTLED/CONFIRMED -> CLEARED; VOIDED/VOID/CANCELLED -> VOIDED; DISCREPANCY/FLAGGED/DISPUTED -> DISCREPANCY_FLAGGED; RESOLVED -> RESOLVED |
| Idempotent Updates | SHA-256 key: `externalRowId + "\|" + status`. Checks `SettlementPaymentUpdateEntity` before applying. |
| Discrepancy Types | NO_REFERENCE, AMOUNT_MISMATCH, UNMATCHED |
| Atomicity | Entire run in Room `withTransaction` -- full rollback on any error |

### 6.3 BackupRestoreUseCase
**Location**: `domain/usecase/operations/BackupRestoreUseCase.kt`

**Backup**:

| Field | Detail |
|-------|--------|
| Input | `outputUri: Uri` |
| Output | `AppResult<BackupArchive>` |
| Permission | BACKUP_RUN required |
| Encryption | AES-256-GCM, PBKDF2 key (120k iterations), 32-byte salt, 12-byte IV |
| Archive | `[salt_len][salt][iv_len][iv][encrypted_payload]` |
| Integrity | SHA-256 checksum of entire encrypted archive |
| Fail-Closed | Fails if backup passphrase not configured in policy |

**Restore**:

| Field | Detail |
|-------|--------|
| Input | `archiveId: String, inputUri: Uri` |
| Output | `AppResult<RestoreRun>` |
| Permission | RESTORE_RUN required |
| Validation | Checksum verification, schema version check, non-empty decrypted payload |

### 6.4 ExportUseCase
**Location**: `domain/usecase/operations/ExportUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `exportType: String, outputUri: Uri, filters: Map<String, String>?` |
| Output | `AppResult<ExportJob>` |
| Permission | EXPORT_MANAGE required |

### 6.5 ManageOperationsUseCase
**Location**: `domain/usecase/operations/ManageOperationsUseCase.kt`

**Operations**:
- `getImportJobs()` -> `AppResult<List<ImportJob>>`
- `getReconciliationRuns()` -> `AppResult<List<ReconciliationRun>>`
- `getDiscrepancies(runId)` -> `AppResult<List<DiscrepancyCase>>`
- `resolveDiscrepancy(caseId, resolution, notes)` -> `AppResult<DiscrepancyCase>` -- notes required
- `getBackupArchives()` -> `AppResult<List<BackupArchive>>`

---

## 7. Policy Use Cases

### 7.1 ManagePolicyUseCase
**Location**: `domain/usecase/policy/ManagePolicyUseCase.kt`

**Operations**:
- `getPolicy(key: String)` -> `AppResult<Policy>`
- `updatePolicy(key, value, updatedBy)` -> `AppResult<Policy>` -- requires POLICY_MANAGE; creates `PolicyHistoryEntity` for versioning
- `getPoliciesByType(type: PolicyType)` -> `AppResult<List<Policy>>`
- `resetToDefault(key)` -> `AppResult<Policy>`

---

## 8. User Management Use Cases

### 8.1 CreateUserUseCase
**Location**: `domain/usecase/user/CreateUserUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `username, displayName, credential, credentialType (PASSWORD/PIN)` |
| Output | `AppResult<User>` |
| Credential Processing | Salt generated (32 bytes), hashed via PBKDF2 (120k iterations), stored as hex |
| Validation | Username uniqueness, password strength (min 8 chars), PIN strength (min 4 digits) |

### 8.2 ManageUserUseCase
**Location**: `domain/usecase/user/ManageUserUseCase.kt`

**Operations**:
- `assignRole(userId, roleType)` -> `AppResult<UserRoleAssignment>` -- requires USER_MANAGE
- `removeRole(userId, roleType)` -> `AppResult<Unit>`
- `setBlacklistFlag(userId, reason)` -> `AppResult<BlacklistFlag>` -- requires RISK_MANAGE
- `removeBlacklistFlag(userId)` -> `AppResult<Unit>`
- `getUsers(page, size)` -> `AppResult<List<User>>`

---

## 9. Audit Use Cases

### 9.1 ViewAuditLogUseCase
**Location**: `domain/usecase/audit/ViewAuditLogUseCase.kt`

| Field | Detail |
|-------|--------|
| Input | `filters: AuditFilters (category, action, userId, dateRange, page, size)` |
| Output | `AppResult<List<AuditEvent>>` |
| Permission | AUDIT_VIEW required |
| Read Layer | Optimized via `SqlDelightAuditRepository` for type-safe queries |

---

## 10. Background Workers (Scheduled Operations)

### 10.1 OrderTimeoutWorker
**Location**: `worker/OrderTimeoutWorker.kt`

| Task | Logic |
|------|-------|
| Unpaid cancel | Orders in PLACED_UNPAID/PARTIALLY_PAID older than 30 min -> AUTO_CANCELLED + release locks |
| Pickup close | Orders in AWAITING_PICKUP older than 7 days -> CLOSED |
| Lock cleanup | ACTIVE locks past expiresAt -> RELEASED + adjust stock |
| Token cleanup | Expired idempotency tokens purged |

### 10.2 EnrollmentExpiryWorker
**Location**: `worker/EnrollmentExpiryWorker.kt`

| Task | Logic |
|------|-------|
| Request expiry | SUBMITTED/PENDING_APPROVAL/WAITLISTED past 48h -> EXPIRED + cancel tasks/waitlist |
| Offer expiry | OFFERED waitlist entries past 24h -> EXPIRED |
| Task expiry | PENDING approval tasks past deadline -> EXPIRED + decision event |

### 10.3 ReconciliationWorker / BackupWorker
**Location**: `worker/ReconciliationWorker.kt`, `worker/BackupWorker.kt`

Constraints: idle + charging + battery-not-low. Enqueued on-demand from UI.

---

## 11. Data Access Objects (DAO Summary)

All DAOs are Room `@Dao` interfaces in `data/local/dao/`:

| DAO | Key Queries |
|-----|-------------|
| `UserDao` | findByUsername, findById, insertUser, updateLockoutStatus |
| `RoleDao` | getRolesForUser, getPermissionsForRole, assignRole, removeRole |
| `SessionDao` | getActiveSession, createSession, terminateSession |
| `AuditDao` | insertEvent, getEvents (filtered, paginated) |
| `PolicyDao` | getByKey, upsert, getByType, insertHistory |
| `BlacklistDao` | getByUserId, insert, delete |
| `CourseDao` | CRUD for courses, versions, materials, classes, sessions, staff |
| `EnrollmentDao` | Requests, approval tasks, decision events, waitlist entries, enrollment records |
| `CommerceDao` | Carts, line items, orders, inventory items, locks, fulfillment, delivery |
| `PaymentDao` | Payments, allocations, refunds, ledger entries, idempotency tokens |
| `AssessmentDao` | Question banks, questions, assignments, submissions, grading queue, similarity |
| `OperationsDao` | Import jobs, settlement rows, reconciliation runs, matches, discrepancies, backups |
