# LearnMart Implementation Assumptions

This document records all non-trivial assumptions made during implementation to resolve ambiguities in the PRD.

## Phase 1 — Foundation, Security, and Core Data Layer

### Authentication & Session

1. **Credential storage uses PBKDF2WithHmacSHA256** with 120,000 iterations and 256-bit key length. The PRD specifies PIN/password authentication but does not prescribe the hashing algorithm. PBKDF2 was chosen as it is well-supported on Android without additional dependencies.

2. **Session timeout is measured from last activity**, not from session creation. The 15-minute idle timeout resets on each user interaction that refreshes the session.

3. **Lockout counter resets on successful login**. The PRD specifies 5 failed attempts within 15 minutes triggers a 15-minute lockout. After successful login, the counter resets to zero.

4. **Lockout state persists via user entity status field** (LOCKED with lockedUntil timestamp) rather than a separate lockout table, since the PRD requires lockout to survive app restart.

5. **A user may hold only one active session at a time**. Creating a new session terminates any existing active session for that user. This simplifies single-device operation.

6. **Database encryption key is generated on first launch and stored in SharedPreferences** as a reference. In production, this should use Android Keystore-backed encryption. The current implementation generates a UUID-based key. This is documented as a known area for hardening.

### Roles & Permissions

7. **Roles are pre-seeded system roles and cannot be deleted via the UI**. The 6 roles (Administrator, Registrar, Instructor, Teaching Assistant, Learner, Finance Clerk) are created at first launch with their default permission sets.

8. **Permission checks use capability strings** (e.g., "catalog.manage") matched via the role_permissions join table. This allows future extension without schema changes.

9. **A user may hold multiple roles simultaneously**. Effective permissions are the union of all assigned role permissions.

10. **Registrar gets catalog.manage permission as "Limited"** per the role matrix. This is implemented as full catalog.manage for now; fine-grained field-level restrictions would require additional scope metadata.

### Policies

11. **Policy versioning is implemented via append-only history**. When a policy is updated, the old version is deactivated (is_active=false with effective_until set) and a new version is created. The policy_history table records all changes for audit purposes.

12. **Policy keys are string-typed** and values are stored as strings with type conversion at the repository layer. This allows flexible policy configuration without schema changes per policy type.

13. **Default policy values are defined in PolicyDefaults** and used as fallbacks when no active policy exists for a given type/key combination.

### Audit

14. **Audit events are append-only**. The audit_events table has no UPDATE or DELETE operations exposed. Events are inserted with ABORT conflict strategy to prevent accidental overwrites.

15. **State transition logs are separate from audit events** to allow efficient entity history replay without filtering through all audit events.

### Data Model

16. **All timestamps are stored as epoch milliseconds (Long)** in Room entities and converted to java.time.Instant in domain models. This provides efficient storage and indexing while maintaining type safety in business logic.

17. **Optimistic locking uses a version integer** on mutable entities (User, Policy). Updates that specify a stale version will fail, preventing silent overwrites from concurrent operations.

18. **The seed data password for demo users is "pass1234"** and the admin password is "admin1234". These are documented here for operator reference and should be changed in production deployment.

### Architecture

19. **Room is used as the primary persistence layer** (not SQLDelight for Phase 1). SQLDelight will be integrated for complex queries in later phases where its type-safe SQL advantages are most beneficial. Room provides the transactional guarantees needed for Phase 1.

20. **SQLCipher provides database encryption** via the SupportFactory integration with Room. The entire database file is encrypted at rest.

21. **WorkManager is configured with Hilt** via custom WorkerFactory injection. The default initializer is disabled in the manifest to prevent double initialization.

## General Assumptions (Applicable to All Phases)

22. **Partial payments are allowed by default**, but fulfillment before full payment requires explicit finance exception with audit note.

23. **Waitlist promoted offer expires after 24 hours** (PRD did not specify a promotion acceptance window).

24. **Parallel approvals require unanimous approval by default** (no quorum rules provided).

25. **Refund count limit uses calendar day in device local timezone**.

26. **Default import size cap is 25 MB** for local validation safety.

27. **Signature verification is required whenever signature metadata/policy says so**. Exact signature scheme will be HMAC-SHA256 with a locally configured shared secret.

28. **Transactional source of truth remains the on-device database only**; reports are query-derived and not pre-aggregated.

29. **Archived data remains queryable** unless access policy restricts it; hard deletion is avoided for audited/financial records.

30. **Device timezone is installation-configured operational timezone**, with UTC persisted for timestamp comparisons and local time rendered for display.

## Audit Fix Assumptions (Post-Audit)

31. **Room/SQLDelight boundary**: Room owns all schema creation, migrations, and transactional writes. SQLDelight owns a separate `learnmart_queries.db` for type-safe audit query reads, mirrored from Room's authoritative store. This avoids dual-driver encryption conflicts.

32. **Database encryption key protection**: The DB passphrase is encrypted using Android Keystore (AES/GCM) and the encrypted blob stored in SharedPreferences. On environments without Keystore (Docker/CI), a derived fallback key is used with a logged warning.

33. **Settlement signature verification uses HMAC-SHA256** with a locally configured shared secret stored in policy `settlement_signature_secret`. When `signature_verification_required` policy is true, imports without valid signatures are rejected.

34. **Reconciliation writes are atomic**: The entire reconciliation run (matches, discrepancies, import status update) is wrapped in a Room transaction. Parse/processing failures cause full rollback.

35. **Destructive migration on downgrade is removed**. If a schema downgrade is detected, the app logs a warning and the operator should restore from backup. Data is never silently destroyed.

36. **WorkManager constraints for heavy jobs**: Reconciliation and backup workers require `idle + charging + battery not low` per prompt. Lightweight timeout workers require only `battery not low`.

37. **UI triggers WorkManager, not inline execution**: The ReconciliationScreen and BackupRestoreScreen enqueue jobs via `WorkScheduler` rather than calling use cases directly. This ensures heavy operations only run under constrained conditions. The UI shows "enqueued" state and allows loading results after worker completion.

38. **Scoped storage backup UX**: The BackupRestoreScreen uses real SAF launchers (`CreateDocument` for export, `OpenDocument` for restore-from-file). Content URIs from SAF are passed to `BackupRestoreUseCase.exportBackupToStream()` and `restoreFromStream()`. Internal-only file paths are not exposed to the user.

39. **Operations route-level guards**: All operations routes are wrapped with `RequireOperationsAccess`, a composable guard that checks permissions before rendering. This is defense-in-depth on top of use-case permission checks, preventing access via crafted navigation state.

40. **ReconciliationWorker atomicity**: The worker wraps all writes (reconciliation run, matches, discrepancies, import job status update, audit event) in a single `database.withTransaction {}` block. Parse or write failures cause full rollback. This matches the same transactional path used by `ReconciliationUseCase`.

41. **High-churn list strategy**: Compose `LazyColumn` with stable `key = { it.id }` is used instead of embedded RecyclerView+DiffUtil. This provides equivalent diff-based incremental updates natively in Compose without interop overhead. See README for full justification.

42. **Backup encryption model**: The backup passphrase is operator-configured (policy `backup_passphrase`), NOT stored in Android Keystore. Keystore is used only for the primary database encryption key. PBKDF2WithHmacSHA256 derives the AES-256 key from passphrase + random salt. The archive header contains only salt and IV — no raw key material.

## Post-Audit Fix Assumptions (Audit Round 2)

43. **Sensitive read authorization**: All read methods on ManageUserUseCase, ManagePolicyUseCase, ManageEnrollmentUseCase (global pending feeds), and ViewAuditLogUseCase (getRecentEvents, countAll) now enforce permission checks. ViewModels access data exclusively through permission-checked use case paths.

44. **Route-level guards for admin routes**: Admin (USER_MANAGE), policy (POLICY_MANAGE), audit (AUDIT_VIEW), enrollment approval (ENROLLMENT_REVIEW), grading (ASSESSMENT_GRADE), payment recording (PAYMENT_RECORD), and refund (REFUND_ISSUE) routes are all wrapped with `RequirePermission` composable guards. This is defense-in-depth on top of use-case permission checks.

45. **Financial transaction atomicity**: CheckoutUseCase, RecordPaymentUseCase, and IssueRefundUseCase wrap all multi-write operations (order creation, line items, price components, payment records, allocations, ledger entries, status updates, audit events) in `database.withTransaction {}` blocks. Failures cause full rollback with no partial state.

46. **Settlement import idempotent payment-status updates**: When reconciliation matches a settlement row to a payment, it maps the settlement status (CLEARED, VOIDED, etc.) to a PaymentStatus and applies it. Idempotency is enforced via a SHA-256 hash of (externalRowId + status) stored in the `settlement_payment_updates` table. Re-imports skip already-applied updates.

47. **Assessment finalization correctness**: The `checkAndFinalizeSubmission` method now queries `getQueueItemsForSubmission(submissionId)` instead of `getPendingQueueByClassOffering("")`. When all subjective items are graded, the submission transitions directly to FINALIZED (not stuck at GRADED).

48. **Import parsing robustness**: CSV parsing uses RFC 4180-compliant field parsing with proper handling of quoted commas, escaped quotes, and whitespace trimming. JSON parsing uses a tokenized approach supporting escaped strings and numeric/boolean values. Both formats apply de-duplication by external_id and reject rows with invalid dates.

49. **Image memory controls**: The current UI uses only vector Material Icons. An `ImageMemoryManager` utility is provided with BitmapFactory downsampling and a bounded LRU cache (20MB budget) for when bitmap images are added. Docs have been corrected to reflect this.

50. **Dashboard navigation**: The dashboard now exposes Catalog, Enrollments, Cart, Orders, Assessments, User Management, Policies, Audit Log, and Operations cards with role-based visibility. All entries route through guarded destinations.

51. **Keystore fallback fail-closed in production**: `DatabaseModule.getOrCreateDatabaseKey` now checks `isTestOrDebugEnvironment()` before allowing the insecure fallback path. Release builds (non-debuggable, no test runner on classpath) throw `IllegalStateException` instead of silently downgrading to SharedPreferences-only key storage. The fallback key derivation has been upgraded from plain UUID to SHA-256(package:salt:uuid) and uses a new pref key (`fallback_db_key_v2`) to avoid reusing old insecure keys.

52. **PolicyEditViewModel authorization**: `PolicyEditViewModel` no longer injects `PolicyRepository` directly. It loads policy data exclusively through `ManagePolicyUseCase.getPolicyById()` which enforces `POLICY_MANAGE` permission. The `ManagePolicyUseCase.getPolicyValue` method is also now permission-gated.
