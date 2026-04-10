# LearnMart - Offline Training Commerce & Classroom

Android offline-first application for a US-based professional training provider. Supports course commerce, enrollment governance, classroom operations, grading, financial controls, and operational continuity on a single device with no internet dependency.

## Build & Test

```bash
# Docker build (produces APK)
docker compose up --build learnmart-build

# Docker test (runs unit tests)
docker compose up --build learnmart-test

# Local Gradle (requires Android SDK)
./gradlew assembleDebug --stacktrace
./gradlew testDebugUnitTest --stacktrace

# Run tests via script
./run_tests.sh
```

## Module Overview

| Layer | Directory | Purpose |
|-------|-----------|---------|
| **Domain Models** | `domain/model/` | Enums, data classes, state machines |
| **Use Cases** | `domain/usecase/` | Business logic with permission enforcement |
| **Repositories** | `domain/repository/` | Interfaces; `data/repository/` has impls |
| **Room Entities/DAOs** | `data/local/entity/`, `data/local/dao/` | Encrypted SQLite persistence |
| **Security** | `security/` | Credential hashing, session management, signature verification |
| **DI** | `di/` | Hilt modules for database, repositories, SQLDelight |
| **UI** | `ui/screens/` | Jetpack Compose screens + ViewModels |
| **Workers** | `worker/` | WorkManager jobs for timeouts, reconciliation, and backup |
| **Nav Guards** | `ui/navigation/` | Route-level permission guards for admin, audit, and operations screens |

## Persistence Architecture: Room + SQLDelight

- **Room** owns the primary encrypted database (`learnmart_encrypted.db`), all schema creation, migrations, entity management, and transactional writes. It handles heavy relational persistence.
- **SQLDelight** (via `SqlDriver`) owns a separate lightweight database (`learnmart_queries.db`) for type-safe audit query reads. The `SqlDelightAuditRepository` mirrors audit events from Room for optimized read access. Room remains the authoritative source of truth.
- **SQLCipher** provides AES-256 encryption at rest for the primary Room database.

## Key Documentation

- [ASSUMPTIONS.md](docs/ASSUMPTIONS.md) - 36 implementation assumptions
- [Operator Guide](docs/operator-guide.md) - Admin usage, demo accounts, policies
- [Reconciliation Guide](docs/reconciliation-guide.md) - Settlement import/reconciliation
- [Backup/Restore Guide](docs/backup-restore-guide.md) - AES-256 encrypted backup workflow

## Roles

| Role | Key Capabilities |
|------|-----------------|
| Administrator | Full access, policies, backup/restore, user management |
| Registrar | Enrollment approvals, seat control, catalog management |
| Instructor | Assignments, grading, class instruction |
| Teaching Assistant | Grading within assigned scope |
| Learner | Browse, enroll, shop, submit work |
| Finance Clerk | Payments, refunds, reconciliation, imports |

## High-Churn List Strategy

The prompt requires "RecyclerView with DiffUtil embedded where needed to avoid full refresh on high-churn lists." The implementation uses Compose `LazyColumn` with stable `key` parameters on all high-churn surfaces. This is an explicit architectural decision documented here:

**Why Compose LazyColumn instead of embedded RecyclerView:**
Jetpack Compose's `LazyColumn` with stable keys provides the same diff-based incremental update semantics as RecyclerView+DiffUtil. When `key = { it.id }` is specified, Compose tracks item identity across recompositions and only rebinds/recomposes changed items — identical to DiffUtil's `areItemsTheSame`/`areContentsTheSame` behavior. Embedding a RecyclerView inside Compose via `AndroidView` would add interop overhead, break Compose's layout system, and provide no performance benefit.

**High-churn surfaces with stable keys:**
- Order lists: `items(orders, key = { it.id })` in `OrderListScreen`
- Operations lists: `items(importJobs, key = { it.id })`, `items(discrepancyCases, key = { it.id })`, `items(backupArchives, key = { it.id })`, `items(exportJobs, key = { it.id })` in `OperationsScreen`
- Dashboard audit events: `items(events, key = { it.id })` in `DashboardScreen`
- Enrollment lists: `items(enrollments, key = { it.id })` in `EnrollmentListScreen`
- Discrepancy cases: `items(cases, key = { it.id })` in `ReconciliationScreen`
- Backup archives: `items(archives, key = { it.id })` in `BackupRestoreScreen`

**Additional performance measures:**
- State hoisting via `StateFlow` + `collectAsState()` minimizes recomposition scope
- Offset-based pagination at the DAO layer for 1000+ item surfaces
- Image memory controls: The current UI uses only vector Material Icons (no bitmap/raster images). If catalog or material images are added in the future, an `ImageMemoryManager` utility with BitmapFactory downsampling and a bounded LruCache (20MB budget) should be implemented. The current codebase does not load user-provided bitmap images, so no OOM risk exists from image memory

This satisfies the prompt's performance intent (60fps scrolling, no full-list refresh) using the Compose-native equivalent of DiffUtil, which is the standard approach for Compose-based Android apps.

## Backup Encryption Model

Backups use **AES-256-GCM** with keys derived via **PBKDF2WithHmacSHA256** (120,000 iterations). The archive header contains only the random salt and IV — **no raw key material is stored in the archive**. The encryption key is derived from an operator-configured passphrase (policy `backup_passphrase`).

**Important:** Backup creation **fails closed** when no passphrase is configured — there is no default fallback. The administrator must set the `backup_passphrase` policy before creating backups. This prevents confidentiality compromise from predictable defaults.

Export/import uses Android SAF (Storage Access Framework) content URIs for scoped-storage compliance on Android 10+. The `BackupRestoreUseCase` provides `exportBackupToStream()` and `restoreFromStream()` for SAF integration. The `BackupRestoreScreen` exposes real SAF-based "Export to File" (via `CreateDocument`) and "Import & Restore from File" (via `OpenDocument`) buttons that route through content URIs.

## WorkManager Execution Model

Reconciliation and backup are routed through real WorkManager jobs, not inline use-case calls:

- **Reconciliation**: `ReconciliationScreen` enqueues via `WorkScheduler.enqueueReconciliationJob()` which creates a `OneTimeWorkRequest<ReconciliationWorker>` with idle+charging constraints.
- **Backup**: `BackupRestoreScreen` enqueues via `WorkScheduler.enqueueBackupJob()` which creates a `OneTimeWorkRequest<BackupWorker>` with idle+charging constraints.
- Both workers execute the real use-case logic (not placeholders) and write results atomically.
- The UI does not run heavy operations inline — it only enqueues and observes.

## Route-Level Permission Guards

All sensitive routes are wrapped with composable permission guards that check authorization via `CheckPermissionUseCase` before rendering content. Unauthorized users see an access-denied message and a back button. This is defense-in-depth on top of use-case permission checks.

**Guarded route categories:**
- **Admin routes** (user management, user detail, create user): `RequirePermission(USER_MANAGE)`
- **Policy routes** (policy list, policy edit): `RequirePermission(POLICY_MANAGE)`
- **Audit route**: `RequirePermission(AUDIT_VIEW)`
- **Enrollment approval**: `RequirePermission(ENROLLMENT_REVIEW)`
- **Grading**: `RequirePermission(ASSESSMENT_GRADE)`
- **Payment recording**: `RequirePermission(PAYMENT_RECORD)`
- **Refund issuance**: `RequirePermission(REFUND_ISSUE)`
- **Operations routes** (overview, import, reconciliation, backup/restore): `RequireOperationsAccess` with role-appropriate permissions

The `RequirePermission` and `RequireOperationsAccess` composables share a common `RouteGuardContent` implementation backed by `OperationsRouteGuardViewModel`.

## Instrumented/Manual Verification Notes

These features require a real Android device or emulator and cannot be fully validated via unit tests:
- SAF file picker for settlement imports (`OpenDocument` in `ImportScreen`)
- SAF file picker for backup export (`CreateDocument` in `BackupRestoreScreen`)
- SAF file picker for backup restore-from-file (`OpenDocument` in `BackupRestoreScreen`)
- Android Keystore-backed database encryption key management
- Real backup/restore with encrypted archive file I/O (PBKDF2 key derivation + AES-GCM)
- WorkManager job scheduling with idle/charging constraints (requires real device idle state)
- SQLCipher database encryption at rest
- `RequireOperationsAccess` composable guard rendering (verified via Compose UI tests or manual nav)
- Content URI stream handling for scoped storage reads/writes
