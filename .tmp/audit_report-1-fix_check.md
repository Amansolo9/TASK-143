# Follow-up Status Check (Against `audit_regenerated_2026-04-10_v4.md`)

## Static Boundary
- This is a static-only verification.
- No project run, tests, Docker, or runtime validation was performed.

## Summary
- Issues reviewed from latest audit: **5**
- Fixed: **5**
- Not Fixed: **0**

## Issue-by-Issue Status

| # | Issue From Latest Audit | Current Status | Evidence | Notes |
|---|---|---|---|---|
| 1 | Reconciliation/backup execution still primarily direct UI use-case calls, not constrained WorkManager jobs | **Fixed** | `app/src/main/java/com/learnmart/app/ui/screens/operations/ReconciliationScreen.kt:101-103`, `app/src/main/java/com/learnmart/app/ui/screens/operations/ReconciliationScreen.kt:267-269`, `app/src/main/java/com/learnmart/app/ui/screens/operations/BackupRestoreScreen.kt:124-126`, `app/src/main/java/com/learnmart/app/ui/screens/operations/BackupRestoreScreen.kt:360-362`, `app/src/main/java/com/learnmart/app/worker/WorkScheduler.kt:85-104` | Reconciliation and backup actions are now enqueued via `WorkScheduler` from UI flows. |
| 2 | Scoped-storage backup import/export not wired into composable UX flow | **Fixed** | `app/src/main/java/com/learnmart/app/ui/screens/operations/BackupRestoreScreen.kt:138-178`, `app/src/main/java/com/learnmart/app/ui/screens/operations/BackupRestoreScreen.kt:305-307`, `app/src/main/java/com/learnmart/app/ui/screens/operations/BackupRestoreScreen.kt:313-317`, `app/src/main/java/com/learnmart/app/ui/screens/operations/BackupRestoreScreen.kt:380-385`, `app/src/main/java/com/learnmart/app/ui/screens/operations/BackupRestoreScreen.kt:476` | SAF launchers (`OpenDocument`/`CreateDocument`) are implemented and wired to ViewModel stream handlers. |
| 3 | ReconciliationWorker lacks transactional wrapper despite atomicity requirement | **Fixed** | `app/src/main/java/com/learnmart/app/worker/ReconciliationWorker.kt:5`, `app/src/main/java/com/learnmart/app/worker/ReconciliationWorker.kt:107-110` | Worker now imports and uses `database.withTransaction { ... }` around multi-write reconciliation persistence. |
| 4 | Route-level authorization for operations remains implicit | **Fixed** | `app/src/main/java/com/learnmart/app/ui/navigation/LearnMartNavGraph.kt:299-306`, `app/src/main/java/com/learnmart/app/ui/navigation/LearnMartNavGraph.kt:318-320`, `app/src/main/java/com/learnmart/app/ui/navigation/LearnMartNavGraph.kt:333-335`, `app/src/main/java/com/learnmart/app/ui/navigation/LearnMartNavGraph.kt:343-345`, `app/src/main/java/com/learnmart/app/ui/navigation/OperationsRouteGuard.kt:86-90`, `app/src/main/java/com/learnmart/app/ui/navigation/OperationsRouteGuard.kt:108-119` | Operations routes are wrapped with `RequireOperationsAccess`; unauthorized users are blocked before screen content renders. |
| 5 | Backup guide says Keystore-backed key model, mismatching implemented PBKDF2 policy-passphrase model | **Fixed** | `docs/backup-restore-guide.md:12`, `docs/backup-restore-guide.md:26`, `docs/backup-restore-guide.md:40`, `docs/backup-restore-guide.md:55-56`, `README.md:84-88` | Documentation now consistently states PBKDF2 + passphrase backup model and SAF-based import/export flow. |

## Conclusion
All issues listed in the latest audit are fixed by current static evidence.

## Manual Verification Required
- WorkManager constraints (idle/charging) enforcement at runtime.
- SAF interaction behavior on target Android 10+ devices.
- End-to-end reconciliation/backup execution outcomes in device conditions.
