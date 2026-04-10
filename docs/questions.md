# LearnMart Offline Training Commerce & Classroom - Clarification Questions

## 1. Dual Database Layer: Room vs SQLDelight Boundary

**Question:** The prompt specifies "SQLDelight for type-safe queries and Room for transactional persistence where entity relations are heavy," but running two ORM layers against the same encrypted SQLite file risks driver conflicts and schema ownership ambiguity. How should the boundary be drawn?

**My Understanding:** Room is better suited as the primary schema owner since it handles migrations, entity relations, and transactional writes natively. SQLDelight excels at type-safe read queries where compile-time SQL verification adds safety. Rather than sharing one database file, each should own a separate database to avoid SQLCipher locking conflicts.

**Solution:** The implementation in `di/DatabaseModule.kt` provisions two separate encrypted databases: `learnmart_encrypted.db` (Room primary, 94 entities, 12 DAOs) and `learnmart_queries.db` (SQLDelight read layer). Room owns all schema creation via `LearnMartRoomDatabase.kt` (version 6 with migrations), all write operations, and all transactional guarantees. SQLDelight owns a dedicated `SqlDelightAuditRepository` for optimized, type-safe audit log reads. The `SqlDelightModule.kt` configures a separate SQLDelight driver that does not interfere with Room's connection pool or SQLCipher encryption.

---

## 2. Idempotent Order Submission: Payload Hash vs Token-Only

**Question:** The prompt requires "idempotent order submission (same client-generated id within 5 minutes returns the original order)." Should the idempotency check be token-only (any resubmission with the same token returns the cached result), or should it also verify that the payload matches to detect accidental token reuse?

**My Understanding:** Token-only idempotency risks silently returning a stale order if the client reuses a token with different cart contents. A payload hash adds a safety layer that catches accidental misuse without adding meaningful overhead.

**Solution:** `CheckoutUseCase.kt` implements dual-check idempotency. It computes a SHA-256 payload hash from `cartId + sorted items (referenceId:quantity:unitPrice)`. Within the 5-minute validity window (configurable via `IDEMPOTENCY_WINDOW_MINUTES` policy), if the same token arrives with the same payload hash, the existing order is returned. If the same token arrives with a different payload hash, the use case returns an `IDEMPOTENCY_PAYLOAD_MISMATCH` error. Tokens are persisted to `IdempotencyTokenEntity` and cleaned up by `OrderTimeoutWorker.kt`.

---

## 3. Inventory Lock Expiry: Silent Rollback vs User Notification

**Question:** The prompt requires "a pre-order inventory lock that expires after 10 minutes and rolls back on cancellation." Should expired locks be silently cleaned up in the background, or should the learner be notified that their held items were released?

**My Understanding:** Since the app is offline and single-device, inventory contention is low but not zero (multiple learners may use the device sequentially). Silent cleanup is appropriate for background housekeeping, but the checkout flow should validate lock freshness at submission time to prevent placing an order on released stock.

**Solution:** `OrderTimeoutWorker.kt` runs a periodic cleanup that finds all `InventoryLockEntity` records with status ACTIVE and `expiresAt < now`, releases them, and adjusts reserved stock on the corresponding `InventoryItemEntity`. Each cleanup is audited. Separately, `CheckoutUseCase.kt` acquires fresh locks at checkout time and verifies available stock (total - reserved >= requested quantity). If the lock acquisition fails due to insufficient stock, the checkout returns an error. On checkout failure, a best-effort lock release reverses any partially acquired locks.

---

## 4. Checkout Policy Enforcement: Cart-Level vs Line-Item-Level

**Question:** The prompt mentions configurable checkout policies "same-class only" vs "cross-class allowed." Should this be enforced when items are added to the cart, or only at checkout submission?

**My Understanding:** Enforcing at cart-add time would prevent learners from browsing and adding items freely before deciding. Enforcing at checkout submission gives the learner flexibility while still guaranteeing policy compliance before an order is placed.

**Solution:** The `CheckoutUseCase.kt` enforces the policy at submission time. When the policy (read from `PolicyDefaults.CHECKOUT_POLICY`) is `SAME_CLASS_ONLY`, the use case verifies that all cart line items reference the same class offering. If `CROSS_CLASS_ALLOWED`, no such restriction applies. `ManageCartUseCase.kt` allows unrestricted item addition regardless of policy, so learners can assemble their cart freely and receive a clear error at checkout if the policy is violated.

---

## 5. Settlement Reconciliation Atomicity: Per-Row vs Per-Run

**Question:** The prompt requires "reconciliation runs that are atomic with rollback on parse errors." Does "atomic" mean each individual row match is independent (partial success allowed), or the entire reconciliation run must succeed or fail as a unit?

**My Understanding:** Since reconciliation involves matching settlement rows to payment records and flagging discrepancies, a partial run would leave the system in an ambiguous state where some payments are updated and others are not. The entire run should be atomic.

**Solution:** `ReconciliationUseCase.kt` wraps the entire reconciliation run in a single Room `withTransaction` block. This includes: creating the `ReconciliationRunEntity`, processing all settlement row matches, creating `ReconciliationMatchEntity` records, detecting and creating `DiscrepancyCaseEntity` records (NO_REFERENCE, AMOUNT_MISMATCH, UNMATCHED), applying idempotent payment status updates (with SHA-256 dedup key: `externalRowId + "|" + status`), updating the import job status, and writing audit events. If any step throws an exception, the entire transaction rolls back and the run is marked as failed.

---

## 6. Approval Flow: What Happens When No Flow Is Configured?

**Question:** The prompt requires "a multi-level approval flow that supports serial or parallel approvals." But what should happen for classes or courses where the administrator has not configured an approval flow?

**My Understanding:** Requiring explicit flow configuration for every class would create unnecessary overhead for simple scenarios. A sensible default is auto-approval when no flow is defined, which allows the system to work out of the box while still supporting complex flows when configured.

**Solution:** `SubmitEnrollmentUseCase.kt` checks for an `ApprovalFlowDefinitionEntity` associated with the class offering. If no flow is found, or the flow has no `ApprovalStepDefinitionEntity` records, the enrollment is auto-approved: the request transitions SUBMITTED -> APPROVED -> ENROLLED, an `EnrollmentRecordEntity` with ACTIVE status is created, the class enrolled count is incremented, and a `EnrollmentDecisionEventEntity` is recorded (action: "AUTO_APPROVED"). This provides an auditable trail even for auto-approved enrollments.

---

## 7. Refund Limits: Per-Device Day vs Per-Calendar Day

**Question:** The prompt specifies "max 3 refunds per learner per day." Does "day" mean a rolling 24-hour window or a calendar day boundary?

**My Understanding:** A calendar day boundary (midnight-to-midnight local device time) is simpler to reason about, easier to query, and aligns with standard financial reporting periods. Rolling windows add complexity without meaningful fraud prevention benefit in an offline single-device context.

**Solution:** `IssueRefundUseCase.kt` counts existing `RefundRecordEntity` records for the learner where `createdAt` falls within the current calendar day (start of day to current instant). If the count equals or exceeds the configurable limit (default 3, from `MAX_REFUNDS_PER_LEARNER_PER_DAY` policy), the refund is rejected unless the issuer holds the `REFUND_OVERRIDE_LIMIT` permission and provides a non-blank override note. Override refunds are audited with a distinct action type.

---

## 8. Plagiarism Detection: Cross-Assignment vs Same-Assignment Only

**Question:** The prompt requires "basic plagiarism detection using local similarity/fingerprint matching across stored submissions." Should similarity be computed only against other submissions for the same assignment, or across all assignments in the system?

**My Understanding:** Cross-assignment comparison would be computationally expensive on a mobile device and would produce many false positives (e.g., students answering the same question bank). Same-assignment comparison is the standard approach and is sufficient to detect copying between peers.

**Solution:** `SimilarityEngine.kt` compares each finalized submission against all other submissions for the same assessment only. After a submission's text answers are concatenated (sorted by questionId) and normalized (lowercase, non-alphanumeric removed), n-gram fingerprints (n=5) are generated and compacted to a maximum of 100 hashes. Pairwise Jaccard similarity (intersection/union of hash sets) is computed against every other submission's fingerprint for the same assignment. Results are persisted as `SimilarityMatchResultEntity` records with flags: HIGH_SIMILARITY (>= 0.85), REVIEW_NEEDED (0.70-0.84), or CLEAR (< 0.70). Thresholds are configurable via `plagiarism_high_threshold` and `plagiarism_review_threshold` policies.

---

## 9. Backup Encryption Key Management: Keystore vs Passphrase

**Question:** The prompt requires "AES-256 encrypted backups restorable without network access." The Android Keystore secures the database passphrase, but Keystore keys are device-bound and non-exportable. How should backup encryption keys be managed to allow restoration on a different device?

**My Understanding:** Backups must be portable across devices (e.g., device replacement), so the encryption key cannot be tied to the Android Keystore. A user-provided passphrase with strong key derivation is the standard approach for portable encrypted archives.

**Solution:** `BackupRestoreUseCase.kt` uses a separate encryption scheme for backups, independent of the Keystore-backed database encryption. The backup passphrase is configured by the administrator via the `backup_passphrase` policy. Key derivation uses PBKDF2WithHmacSHA256 with 120,000 iterations and a random 32-byte salt. The archive format stores `[salt_len:1][salt:32][iv_len:1][iv:12][encrypted_payload]` with no raw key material. AES-256-GCM provides authenticated encryption (128-bit tag). A SHA-256 checksum of the entire archive is stored in the `BackupArchiveEntity` for integrity verification before restore. The system is fail-closed: backup creation fails if no passphrase is configured.

---

## 10. Settlement Signature Verification: Mandatory vs Optional

**Question:** The prompt requires "signature-verified reconciliation" for settlement files. Should signature verification be mandatory for all imports, or configurable per deployment?

**My Understanding:** In an offline environment, the settlement file source varies (USB transfer, local generation, etc.). Mandatory verification would block imports when no signing infrastructure exists. Making it configurable allows strict deployments to enforce signatures while simpler setups can import unsigned files.

**Solution:** `ImportSettlementUseCase.kt` checks the `signature_verification_required` policy (default: `false`). When enabled, the imported file's content is verified against the provided HMAC-SHA256 signature using `SettlementSignatureVerifier.kt` and the shared secret from the `settlement_signature_secret` policy. If verification fails, the import is rejected. If the policy is disabled, the signature parameter is ignored and the import proceeds. The `SettlementSignatureVerifier` returns a sealed result type (`Valid`, `Invalid(expected, actual)`, `Error(message)`, `NoSecretConfigured`) for precise error reporting.

---

## 11. High-Churn List Performance: RecyclerView vs LazyColumn

**Question:** The prompt specifies "RecyclerView with DiffUtil (embedded where needed) to avoid full refresh on high-churn lists." However, the UI is built with Jetpack Compose. Should we embed RecyclerView via AndroidView interop, or use Compose's native LazyColumn which provides equivalent incremental rendering?

**My Understanding:** Jetpack Compose's `LazyColumn` with stable keys achieves the same incremental-diff behavior as RecyclerView + DiffUtil. Embedding RecyclerView via `AndroidView` interop adds complexity, breaks Compose's declarative model, and does not provide meaningful performance benefits. The prompt's intent (60fps scrolling without full refresh) is satisfied by LazyColumn.

**Solution:** All high-churn list surfaces (order lists, operations dashboards, audit events, enrollment queues, discrepancy lists, backup archives) use `LazyColumn` with stable `key = { it.id }` for incremental diff-based rendering. ViewModels expose paginated `StateFlow` lists, and DAOs provide offset-based queries for surfaces exceeding 1,000 items. The `RecyclerView` library (1.3.2) is included as a dependency for potential future use in edge cases, but current screens use Compose-native lists exclusively. This achieves 60fps scrolling while maintaining a fully declarative Compose UI.

---

## 12. Enrollment Expiry: Hard Delete vs Soft Expire with Audit Trail

**Question:** The prompt requires "auto-expiration of pending requests after 48 hours with an auditable replay of each decision." Should expired requests be deleted or marked with a terminal status?

**My Understanding:** Deleting expired requests would destroy the audit trail. The auditable replay requirement demands that every state transition, including expiry, is preserved as a decision event.

**Solution:** `EnrollmentExpiryWorker.kt` transitions expired requests to the `EXPIRED` terminal status (never deletes). For each expired request, it: (1) updates `EnrollmentRequestEntity.status` to EXPIRED, (2) cancels any remaining PENDING `EnrollmentApprovalTaskEntity` records, (3) cancels related `WaitlistEntryEntity` if applicable, and (4) creates an `EnrollmentDecisionEventEntity` recording the auto-expiry as a system decision. The `StateTransitionLogEntity` captures the before/after status. This preserves a complete, replayable history of every enrollment request from submission through final disposition.

---

## 13. Payment Status Updates from Settlement Import: Conflict Resolution

**Question:** The prompt requires "idempotent payment status updates from file imports." What should happen when a settlement file attempts to update a payment to a status that conflicts with the payment's current state machine position?

**My Understanding:** The payment status state machine (`PaymentStatus.canTransitionTo()`) must be respected even during bulk file imports. Attempting an invalid transition should be flagged as a discrepancy rather than silently ignored or forced.

**Solution:** `ReconciliationUseCase.kt` maps settlement statuses to `PaymentStatus` values (e.g., "CLEARED"/"SETTLED"/"CONFIRMED" -> `CLEARED`, "VOIDED"/"VOID"/"CANCELLED" -> `VOIDED`). Before applying an update, it checks `currentStatus.canTransitionTo(newStatus)`. If the transition is valid and the idempotency key (SHA-256 of `externalRowId + "|" + status`) has not been applied, the update proceeds and a `SettlementPaymentUpdateEntity` dedup record is persisted. If the transition is invalid, the row is recorded as a discrepancy with type information for manual review. If the idempotency key already exists, the update is silently skipped (already applied).

---

## 14. Image Memory Budget: Static Cap vs Dynamic Allocation

**Question:** The prompt requires "image downsampling plus an LRU cache to keep peak image memory under 20MB to prevent OOM." Should the 20MB budget be a hard static cap, or should it adapt based on available device memory?

**My Understanding:** A static 20MB cap provides predictable behavior across all devices and is conservative enough to avoid OOM even on low-memory devices. Dynamic allocation adds complexity and may still exceed safe limits on constrained hardware.

**Solution:** `ImageMemoryManager.kt` implements a hard 20MB LRU cache using Android's `LruCache<String, Bitmap>` with `bitmap.byteCount` as the size metric. Bitmap loading uses two-pass decoding: a bounds-only pass to calculate `inSampleSize`, then a downsampled decode pass constrained to 1024x1024 max dimensions. All decode operations run on `Dispatchers.IO` to keep the main thread clear. The cache provides `currentCacheSizeBytes()` and `maxCacheSizeBytes()` for monitoring. Currently the UI uses only vector Material Icons (no bitmap loading), so the cache is provisioned for future image-heavy features without risking OOM.

---

## 15. Scoped Storage: File Validation and Data Cleansing Rules

**Question:** The prompt requires "format validation, field mapping, and data cleansing rules (trim, de-dup by primary keys, reject invalid dates)" for file imports. How strict should validation be -- reject the entire file on any error, or process valid rows and report invalid ones?

**My Understanding:** Rejecting an entire large settlement file due to a single malformed row would be impractical. Row-level validation with per-row error tracking allows operators to review and correct specific issues while processing the valid portion.

**Solution:** `ImportSettlementUseCase.kt` implements row-level validation. Each row in the settlement file (CSV via RFC 4180-compliant parser, or JSON) is individually validated for: external ID presence, amount (valid BigDecimal), payment reference, tender type, and transaction date format. Invalid rows are tracked with specific error messages in `SettlementImportRowEntity`. Duplicate detection runs both in-batch (within the current file) and against existing rows (by external ID). After validation, the job transitions to `READY_TO_APPLY` if at least one valid row exists, or `REJECTED` if all rows are invalid. Whitespace trimming is applied to all string fields. The file size is checked against the `max_import_size_bytes` policy (default 25 MB) before parsing begins.
