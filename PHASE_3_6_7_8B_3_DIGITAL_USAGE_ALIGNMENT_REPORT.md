# IronMind-OS — Phase 3.6.7.8B.3 Implementation Report
## Digital Behaviour UI & App Usage Calculation Alignment

### Date: 2026-11-13
### Branch: `main`
### Baseline Commit: `2a9cc367663a2d7be7a13eb01f0b7c98922db225`
### Target Module: `IronMind/app`

---

## 1. Executive Summary

Phase 3.6.7.8B.3 resolves the algorithmic divergence between:
1. `GetDailyAppUsageIntelligenceUseCase`, which serves the Digital Behaviour UI (`DigitalBehaviourViewModel`) and previously summed raw clamped UsageStats intervals without deduplication or union merging (risking double-counting overlapping sessions).
2. `AppUsageIntervalAnalyzer`, which computes authoritative interval metrics for `ContextEngineImpl.getUnifiedContext` using normalized union interval math.

To eliminate double-counting while preserving each path's distinct provenance, availability, and coverage window:
- Extracted a pure mathematical utility: `IntervalMath` (`com.sanket_satpute_20.ironmind.domain.common.IntervalMath`).
- Adapted `AppUsageIntervalAnalyzer` to delegate its range merging, union duration, and boundary clipping to `IntervalMath`.
- Aligned `GetDailyAppUsageIntelligenceUseCase` to use `IntervalMath` for semantic deduplication per package, window boundary clipping, and package-level interval union merging.
- Ensured total day usage equals the sum of correctly unioned package durations where package intervals are independent.
- Retained strict source availability semantics: missing Usage Access permission remains `null` duration and `PERMISSION_DENIED` (never `0ms`), while confirmed zero usage remains `0ms` and `EMPTY_NO_DATA`.
- Handled DST and calendar day boundaries cleanly using `ZoneId` without assuming all days are 24 hours.
- Added comprehensive unit tests in `IntervalMathTest.kt` and `GetDailyAppUsageIntelligenceUseCaseTest.kt`.

---

## 2. Git Preflight & Baseline Verification

- **Repository**: `C:/Users/lenovo/OneDrive/Desktop/IronMind-OS`
- **Android Module**: `IronMind`
- **Active Branch**: `main`
- **Baseline HEAD**: `2a9cc367663a2d7be7a13eb01f0b7c98922db225`
- **Preflight Worktree Baseline**: 394 entries captured in `preflight_3_6_7_8b3_status.txt`.
- **Target Files Modified / Added**:
  - `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/common/IntervalMath.kt` (new pure utility)
  - `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/engine/AppUsageIntervalAnalyzer.kt` (adapted to delegate to `IntervalMath`)
  - `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/usecase/digital/GetDailyAppUsageIntelligenceUseCase.kt` (aligned union calculation)
  - `IronMind/app/src/test/java/com/sanket_satpute_20/ironmind/domain/common/IntervalMathTest.kt` (new unit tests)
  - `IronMind/app/src/test/java/com/sanket_satpute_20/ironmind/domain/usecase/digital/GetDailyAppUsageIntelligenceUseCaseTest.kt` (regression tests)
  - `PHASE_3_6_7_8B_3_DIGITAL_USAGE_ALIGNMENT_REPORT.md` (this report)

---

## 3. Data Contracts & Architecture Alignment

### A. Live UsageStats Path (`GetDailyAppUsageIntelligenceUseCase`)
- **Source**: `AppUsageObservationProvider.getAppUsageSince(...)` querying platform `UsageStatsManager.queryEvents(...)`.
- **Fallback**: Historical fallback to Room `ObservationRepository` for `APP_USAGE_SESSION` when on-demand queries yield no events (e.g., past days).
- **Semantics**:
  - Target day window: `[startOfDay, endOfDay)` defined by `localDate.atStartOfDay(zoneId)` and `localDate.plusDays(1).atStartOfDay(zoneId)`, or explicit custom window.
  - Granularity: Grouped per package. Overlapping and adjacent intervals for the same package are unioned so duplicate/overlapping events never inflate duration.
  - Session Count: Represents the count of distinct, valid, window-scoped usage intervals for the package.
  - Independent Packages: Overlapping intervals from different packages are maintained separately per package (never merged across packages).
  - Day Total: `totalDayDurationMs = appItems.sumOf { it.totalDurationMs }`. Where package intervals are independent, this equals the union across all packages.
  - Previous Day Comparison: Computed using the exact same package-level union mathematics. If no valid baseline data exists, comparison is strictly `null`.

### B. Persisted Room Intelligence Path (`AppUsageIntervalAnalyzer`)
- **Source**: Persisted `ObservationType.APP_USAGE_SESSION` in Room.
- **Lookback Ceiling**: Clamped to 7 days maximum lookback (`MAX_LOOKBACK_MS = 7L * 24L * 60L * 60L * 1000L`).
- **Focus Overlap**: Intersects merged app usage intervals with active/completed/cancelled/overridden `ProtectionSession` intervals.
- **Provenance**: Preserves `retainedRawObservationCount`, `validIntervalCount`, `droppedInvalidOrNonPositiveCount`, `droppedBeyondLookbackCount`, and `deduplicatedCount`.

---

## 4. Shared Interval Utility: `IntervalMath`

Located at `com.sanket_satpute_20.ironmind.domain.common.IntervalMath`:
- `data class Interval(val startMs: Long, val endMs: Long)`
  - `durationMs: Long`: `(endMs - startMs).coerceAtLeast(0L)`
  - `isValid: Boolean`: `startMs >= 0L && endMs > startMs` (rejects zero-length and negative intervals)
- `safeRawEndMs(startMs: Long, durationMs: Long): Long?`: Guards against Long addition overflow cleanly (`Long.MAX_VALUE`), returns null if start < 0 or duration <= 0.
- `clipInterval(startMs, endMs, windowStartMs, windowEndMs): Interval?`: Clamps to `[windowStartMs, windowEndMs)`. Returns null if clipped duration <= 0.
- `mergeIntervals(intervals: List<Interval>): List<Interval>`:
  - Discards invalid intervals.
  - Sorts deterministically by `startMs` ascending, then `endMs` ascending.
  - Merges overlapping (`next.startMs < currentEnd`) AND adjacent (`next.startMs == currentEnd`) intervals into canonical disjoint intervals.
- `unionDurationMs(intervals: List<Interval>): Long`: Sum of durations of merged disjoint intervals.
- `unionDurationFromPairs(ranges: List<Pair<Long, Long>>): Long`: Convenience helper for pairs.
- `intersectDurationMs(firstMerged, secondMerged): Long`: Two-pointer O(N + M) sweep computing exact temporal overlap between two merged interval lists.

---

## 5. Verification & Test Suite Results

### Targeted Test Suites Run
1. `IntervalMathTest`: 12 tests, 0 failures, 0 errors, 0 skipped (**PASSED**)
2. `GetDailyAppUsageIntelligenceUseCaseTest`: 18 tests, 0 failures, 0 errors, 0 skipped (**PASSED**)
3. `DigitalBehaviourViewModelTest`: 33 tests, 0 failures, 0 errors, 0 skipped (**PASSED**)
4. `AppUsageIntervalAnalyzerTest`: 5 tests, 0 failures, 0 errors, 0 skipped (**PASSED**)
5. `ContextEngineTemporalIntegrationRoomTest`: 3 tests, 0 failures, 0 errors, 0 skipped (**PASSED**)
6. `DigitalPipelineCrossLayerConsistencyTest`: 6 tests, 0 failures, 0 errors, 0 skipped (**PASSED**)
7. `ContextEngineCorrectnessRemediationTest`: 4 tests, 0 failures, 0 errors, 0 skipped (**PASSED**)

**Total Test Count**: 81 tests across 7 suites, 0 failures, 0 errors, 0 skipped.

### Build Verification
- `:app:assembleDebug` completed successfully (`BUILD SUCCESSFUL in 1m 7s`).

---

## 6. Scope Boundaries & Invariants

1. **`PersonalInsightDiscoveryEngine` & `BehaviourPatternDiscoveryEngine`**: Unmodified; 8B.1 and 8B.2 implementations remain intact.
2. **`ExecutionTimingAnalyzer`**: Unmodified.
3. **Room Schemas / Migrations**: Zero schema changes; zero new entities.
4. **Permissions / Manifest**: No new permissions or manifest edits.
5. **Background Workers & Autonomous Interventions**: Untouched.
6. **UI / ViewModel Contracts**: `DigitalBehaviourViewModel` and UI state models remain 100% backward compatible.
7. **Pre-existing Worktree Integrity**: All pre-existing user-owned worktree entries preserved untouched.

---

## 7. Verified vs. Unverified Behavior

### Verified Behavior:
- Overlapping intervals from the same package are unioned without double counting.
- Duplicate intervals do not inflate package duration or session count.
- Overlapping intervals from different packages maintain distinct package-level totals.
- Adjacent intervals merge into a continuous interval following the documented contract.
- Intervals spanning midnight or window boundaries are clamped accurately.
- Invalid, negative, zero-length, and blank-package intervals are safely dropped.
- Missing usage permission returns `null` duration and `PERMISSION_DENIED` (never `0ms`).
- Successfully queried empty days return `0ms` and `EMPTY_NO_DATA`.
- Daylight-saving transition days (e.g. 23-hour days) use exact timezone start-of-day offsets without assuming 24 hours.
- APK compilation (`:app:assembleDebug`) succeeds without errors.

### Unverified Behavior:
- Multi-window split-screen usage where two active foreground apps report concurrent foreground events simultaneously in hardware Android 14+ devices (tested via unit test doubles, not physical dual-screen emulator in this turn).
