# Test Coverage Audit

## Scope and Method
- Static inspection only (no execution).
- Reviewed: `repo/README.md`, `repo/run_tests.sh`, `docs/api-spec.md`, `repo/app/src/main/**`, `repo/app/src/test/**`, `repo/app/src/androidTest/**`, `repo/tests/unit_tests/**`.

## Project Type Detection
- Declared type: **android** (`repo/README.md:3`).

## Backend Endpoint Inventory
- HTTP endpoints (`METHOD + PATH`): **NONE**.
- Evidence:
  - `docs/api-spec.md:5` states no REST/HTTP APIs.
  - `repo/app/src/main/AndroidManifest.xml:5` indicates offline/no internet permissions.
  - No HTTP route/controller declarations found.

## API Test Mapping Table
| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| _None detected_ | no | non-HTTP only | N/A | `docs/api-spec.md:5` |

## API Test Classification
1. True No-Mock HTTP: **0**
2. HTTP with Mocking: **0**
3. Non-HTTP tests: **present**

## Mock Detection (Strict)
- Mocking/stubbing remains pervasive in unit suites:
  - `repo/app/src/test/java/com/learnmart/app/domain/usecase/operations/BackupRestoreUseCaseTest.kt:30`
  - `repo/app/src/test/java/com/learnmart/app/domain/usecase/assessment/ManageAssessmentUseCaseTest.kt:26`
  - `repo/app/src/test/java/com/learnmart/app/domain/usecase/audit/ViewAuditLogUseCaseTest.kt:21`

## Coverage Summary
- Total endpoints: **0**
- Endpoints with HTTP tests: **0**
- Endpoints with true no-mock HTTP tests: **0**
- HTTP coverage %: **N/A**
- True API coverage %: **N/A**

## Unit Test Summary

### Backend Unit Tests
- `app/src/test`: **42** files
- `tests/unit_tests`: **42** files (mirror)
- `domain/usecase` coverage is broad across assessment, audit, auth, commerce, course, enrollment, operations, policy, and user modules.

### Frontend Unit Tests (STRICT REQUIREMENT)
- Android UI/instrumented tests are now present:
  - `repo/app/src/androidTest/java/com/learnmart/app/ui/screens/login/LoginScreenTest.kt:20`
  - `repo/app/src/androidTest/java/com/learnmart/app/ui/screens/dashboard/DashboardScreenTest.kt:17`
  - `repo/app/src/androidTest/java/com/learnmart/app/ui/navigation/NavigationGuardTest.kt:17`
- Framework/tool evidence:
  - `createAndroidComposeRule<MainActivity>()` usage in all three files.
  - Compose UI assertions/actions (`onNodeWithText`, `assertIsDisplayed`, `performClick`) present.

### Mandatory Verdict
- **Frontend unit tests: PRESENT**

### Cross-Layer Observation
- Coverage is now more balanced: strong domain/use-case tests plus foundational UI instrumented tests.

## API Observability Check
- **Weak / not applicable** for HTTP API (no HTTP endpoints/tests by architecture).

## Test Quality & Sufficiency
- Strengths:
  - Broad domain/use-case test breadth with failure and permission-path assertions.
  - New UI/navigation/login/dashboard instrumented tests add end-user behavior verification.
- Weaknesses:
  - No HTTP/API tests (architectural limitation).
  - Heavy mocking in unit tests limits integration realism.

`run_tests.sh`:
- Docker path present (`repo/run_tests.sh:100`, `repo/run_tests.sh:103`) -> OK.
- Local SDK path present (`repo/run_tests.sh:82`, `repo/run_tests.sh:91`) -> FLAG.
- API tests directory optional/absent (`repo/run_tests.sh:136`, `repo/run_tests.sh:142`) -> FLAG.

## End-to-End Expectations
- Fullstack FE?BE rule not applicable (android project).
- Partial mobile flow automation now exists through Compose instrumented tests.

## Tests Check
- Endpoint inventory/mapping complete for empty HTTP set.
- Mock classification complete.
- Unit + initial instrumented UI coverage is strong.

## Test Coverage Score (0–100)
- **90/100**

## Score Rationale
- Increased due to expanded unit suites and newly added UI instrumented tests.
- Not maxed due to no HTTP layer (N/A), and high mocking reliance.

## Key Gaps
- No HTTP API layer tests (architecture-specific).
- Integration depth still constrained by mock-heavy unit patterns.

## Confidence & Assumptions
- Confidence: **High**.
- Assumptions: `tests/unit_tests` mirrors `app/src/test`; HTTP metrics are N/A because architecture is offline app, not server API.

## Test Coverage Verdict
- **PASS (android-scope)**

---

# README Audit

## README Location
- `repo/README.md` exists.

## High Priority Issues
- None.

## Medium Priority Issues
- Docker test command remains generic (`repo/README.md:66`) and does not specify a dedicated test service.
- Local Gradle alternatives remain (`repo/README.md:69`), reducing strict reproducibility.

## Low Priority Issues
- None significant.

## Hard Gate Failures
- None.

## README Verdict
- **PASS**

## Hard Gate Evidence Snapshot
- Project type declaration: `repo/README.md:3`
- Android launch/access instructions: `repo/README.md:25`, `repo/README.md:29`, `repo/README.md:32`
- Demo credentials with roles: `repo/README.md:37`
- Verification flow: `repo/README.md:48`

## Combined Final Verdicts
- Test Coverage Audit: **PASS (android-scope)**
- README Audit: **PASS**
