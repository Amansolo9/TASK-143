# LearnMart - Offline Training Commerce & Classroom

**Project type: android**

Android offline-first application for a US-based professional training provider. Supports course commerce, enrollment governance, classroom operations, grading, financial controls, and operational continuity on a single device with no internet dependency.

## Prerequisites

- Docker Desktop (recommended) OR Android SDK with JDK 17
- Android device or emulator running Android 10+ (API 29+)
- ADB installed and device connected (`adb devices` shows your device)

## Build

```bash
# Docker build (recommended — no local SDK required)
docker compose up --build
# APK output: repo/output/learnmart-debug.apk

# OR local Gradle build (requires Android SDK)
./gradlew assembleDebug --stacktrace
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## Install & Launch

```bash
# Install the APK on connected device/emulator
adb install -r output/learnmart-debug.apk

# Launch the app
adb shell am start -n com.learnmart.app/.ui.MainActivity
```

The app opens to the Login screen. No internet connection is required.

## Demo Credentials

| Username | Password | Role | Key Capabilities |
|----------|----------|------|-----------------|
| `admin` | `admin1234` | Administrator | Full access, policies, backup/restore, user management |
| `registrar` | `pass1234` | Registrar | Enrollment approvals, seat control, catalog management |
| `instructor` | `pass1234` | Instructor | Assignments, grading, class instruction |
| `ta` | `pass1234` | Teaching Assistant | Grading within assigned scope |
| `learner` | `pass1234` | Learner | Browse, enroll, shop, submit work |
| `finance` | `pass1234` | Finance Clerk | Payments, refunds, reconciliation, imports |

## Verification Steps

After installing and launching the app:

1. **Login**: Enter `admin` / `admin1234` and tap Login. You should see the Dashboard with navigation cards for all features.
2. **User Management**: Tap "User Management" to view all demo accounts. Tap a user to see details and role assignments.
3. **Policies**: Tap "Policies" to view and edit system configuration policies.
4. **Catalog**: Tap "Course Catalog" to browse courses. Use the + button to create a new course.
5. **Enrollments**: Log out, log in as `learner` / `pass1234`. Tap "Enrollments" to view enrollment options.
6. **Orders/Cart**: As learner, tap "Shopping Cart" or "Orders" to view commerce flows.
7. **Assessments**: Tap "Assessments" to view available assignments.
8. **Operations**: Log out, log in as `finance` / `pass1234`. Tap "Operations" for imports, reconciliation, and exports.
9. **Audit Log**: Log in as `admin`. Tap "Audit Log" to view the complete system audit trail.

## Run Tests

```bash
# Docker test run
docker compose up --build

# Local test run
./gradlew testDebugUnitTest --stacktrace

# Run specific test suite
./gradlew testDebugUnitTest --tests "com.learnmart.app.domain.usecase.operations.SettlementPaymentIdempotencyTest"

# Run via script
./run_tests.sh
```

## Module Overview

| Layer | Directory | Purpose |
|-------|-----------|---------|
| **Domain Models** | `domain/model/` | Enums, data classes, state machines |
| **Use Cases** | `domain/usecase/` | Business logic with permission enforcement |
| **Repositories** | `domain/repository/` | Interfaces; `data/repository/` has impls |
| **Room Entities/DAOs** | `data/local/entity/`, `data/local/dao/` | Encrypted SQLite persistence |
| **Security** | `security/` | Credential hashing, session management, signature verification, device fingerprinting |
| **DI** | `di/` | Hilt modules for database, repositories, SQLDelight |
| **UI** | `ui/screens/` | Jetpack Compose screens + ViewModels |
| **Workers** | `worker/` | WorkManager jobs for timeouts, reconciliation, and backup |
| **Nav Guards** | `ui/navigation/` | Route-level permission guards for admin, audit, and operations screens |

## Persistence Architecture: Room + SQLDelight

- **Room** owns the primary encrypted database (`learnmart_encrypted.db`), all schema creation, migrations, entity management, and transactional writes.
- **SQLDelight** (via `SqlDriver`) owns a separate lightweight database (`learnmart_queries.db`) for type-safe audit query reads.
- **SQLCipher** provides AES-256 encryption at rest for the primary Room database.

## Key Documentation

- [ASSUMPTIONS.md](guides/ASSUMPTIONS.md) - Implementation assumptions
- [Operator Guide](guides/operator-guide.md) - Admin usage, demo accounts, policies
- [Reconciliation Guide](guides/reconciliation-guide.md) - Settlement import/reconciliation
- [Backup/Restore Guide](guides/backup-restore-guide.md) - AES-256 encrypted backup workflow

## Route-Level Permission Guards

All sensitive routes are wrapped with composable permission guards that check authorization via `CheckPermissionUseCase` before rendering content. Unauthorized users see an access-denied message and a back button.

**Guarded route categories:**
- **Admin routes** (user management, user detail, create user): `RequirePermission(USER_MANAGE)`
- **Policy routes** (policy list, policy edit): `RequirePermission(POLICY_MANAGE)`
- **Audit route**: `RequirePermission(AUDIT_VIEW)`
- **Enrollment approval**: `RequirePermission(ENROLLMENT_REVIEW)`
- **Grading**: `RequirePermission(ASSESSMENT_GRADE)`
- **Payment recording**: `RequirePermission(PAYMENT_RECORD)`
- **Refund issuance**: `RequirePermission(REFUND_ISSUE)`
- **Operations routes** (overview, import, reconciliation, backup/restore): `RequireOperationsAccess` with role-appropriate permissions

## High-Churn List Strategy

Compose `LazyColumn` with stable `key` parameters provides diff-based incremental updates equivalent to RecyclerView+DiffUtil. All high-churn surfaces use `items(list, key = { it.id })`.

## Backup Encryption Model

Backups use **AES-256-GCM** with keys derived via **PBKDF2WithHmacSHA256** (120,000 iterations). The archive header contains only the random salt and IV. Export/import uses Android SAF for scoped-storage compliance on Android 10+.

## WorkManager Execution Model

Reconciliation and backup are routed through real WorkManager jobs with idle+charging constraints, not inline use-case calls.

## Image Memory Controls

The current UI uses only vector Material Icons (no bitmap/raster images). An `ImageMemoryManager` utility with BitmapFactory downsampling and a bounded LruCache (20MB budget) is provided for future catalog image support.

## Instrumented/Manual Verification Notes

These features require a real Android device or emulator and cannot be fully validated via unit tests:
- SAF file picker for settlement imports, backup export, and restore
- Android Keystore-backed database encryption key management
- WorkManager job scheduling with idle/charging constraints
- SQLCipher database encryption at rest
- Compose route guard rendering
