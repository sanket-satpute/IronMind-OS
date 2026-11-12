# Phase 3.6.7.6C: Timestamped App Usage, Switching & Focus Overlap Report

## 1. Preflight HEAD & Worktree Baseline
- **Repository:** `C:/Users/lenovo/OneDrive/Desktop/IronMind-OS` (`IronMind` Android module)
- **Branch:** `main`
- **Baseline HEAD:** `cb58111fdd61e1cac994064123801175318d38ca`
- **Pre-edit worktree baseline:** `397` entries (`185` modified tracked ` M`, `212` untracked `??`, `0` staged).
- **Pre-existing status of Phase 3.6.7.6C target files:**
  - Previously `??` in baseline worktree: `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/model/context/DigitalBehaviourContext.kt` (preserved existing fields verbatim and extended additively with defaulted `usageIntervals` and `usageTemporalSummary`).
  - Clean in `HEAD`: `ContextEngineImpl.kt`, `FakeObservationRepository.kt`.
  - Newly added for Phase 3.6.7.6C: `AppUsageTemporalSummary.kt`, `AppUsageIntervalAnalyzer.kt`, `AppUsageIntervalAnalyzerTest.kt`, `ContextEngineAppUsageOverlapIntegrationRoomTest.kt`, and this report.

## 2. Existing Observation Schema & Timestamp Semantics
- Verified against `ObservationEntity`, `Observation`, `AndroidAppUsageObservationProvider`, and `CollectAppUsageObservationsUseCase`:
  - `APP_USAGE_SESSION` observations persist `userId`, `occurredAt: Long` (epoch ms start of interval/slice), `recordedAt: Long`, `value: String` (package name), `context: String` (`duration_ms=<millis>` in **milliseconds**), and deterministic `id` (`UUID.nameUUIDFromBytes("appusage_${userId}_${packageName}_${startTimeMillis}")` from `CollectAppUsageObservationsUseCase`).
  - Cross-midnight sessions are sliced at midnight by `CollectAppUsageObservationsUseCase`.
  - Interval raw end is derived cleanly as `occurredAt + durationMs` (with `Long.MAX_VALUE` overflow guard).

## 3. Interval Extraction, Clipping & Bounded Retrieval (Workstream A)
- Implemented in `AppUsageIntervalAnalyzer` and `ContextEngineImpl.getUnifiedContext`:
  - **7-day maximum lookback:** Enforced both in repository query (`queryStartMs = (endTime - MAX_LOOKBACK_MS).coerceAtLeast(0L)`) and in `AppUsageIntervalAnalyzer` (`MAX_LOOKBACK_MS = 7L * 24 * 60 * 60 * 1000L`), reporting `isLookbackClamped` and `droppedBeyondLookbackCount` explicitly.
  - **Pre-window overlap & window clipping:** Intervals starting up to 7 days before `endTime` and overlapping `[effectiveStartMs, windowEndMs]` are retrieved, clipped to `[maxOf(rawStart, effectiveStartMs), minOf(rawEnd, windowEndMs)]`, and retain both raw and clipped boundaries plus `isClippedToWindow`.
  - **Validation & deterministic deduplication:** Rejects blank package names, negative `occurredAt`, and missing/zero/negative durations (`droppedInvalidOrNonPositiveCount`). Deduplicates repeated observations deterministically by `observation.id` and semantic key `(packageName, rawStartTimeMs, rawEndTimeMs)` (`deduplicatedCount`).
  - **Bounded cap & ordering:** Queries `ObservationRepository.getObservationsForTimeWindow(..., types = listOf(ObservationType.APP_USAGE_SESSION), limit = MAX_WINDOW_APP_USAGE_OBSERVATIONS + 1, orderAsc = false)` (`MAX_WINDOW_APP_USAGE_OBSERVATIONS = 500`), retaining the most recent 500 observations, reporting `isTruncated` in `provenanceMap["AppUsage"]` and `AppUsageTemporalProvenance`, and sorting preserved intervals chronologically (`startTimeMs ASC, endTimeMs ASC, packageName ASC, observationId ASC`) without merging distinct sessions.

## 4. App Switching & Short-Burst Definitions (Workstream B)
- **App switching (`AppSwitchTransition`, `appSwitchCount`):**
  - Groups chronologically sorted intervals by `startTimeMs`. Transitions from previous package group `P_{k-1}` to `P_k` count an unambiguous chronological switch when `{toPkg}` enters a package not in `P_{k-1}` (or leaves `P_{k-1}` cleanly when disjoint).
  - Simultaneous-start intervals sharing identical `startTimeMs` (`groupPackages.size > 1`) do not fabricate intra-millisecond transitions; ambiguity is recorded in `ambiguousSimultaneousTransitionsSkipped`. Repeated intervals of the same package do not increment switch count.
- **Short usage bursts (`shortBurstThresholdMs`, default `60_000L` ms):**
  - Configurable via `ContextEngineImpl(..., shortBurstThresholdMs)` and `AppUsageIntervalAnalyzer`.
  - Evaluated on `rawDurationMs <= shortBurstThresholdMs` (for valid intervals intersecting `[effectiveStartMs, windowEndMs]`), preventing long sessions trimmed at the window boundary from being misclassified as short bursts and excluding invalid/zero durations. Burst counts remain separate from switch counts.

## 5. True Focus Protection Overlap & Context Integration (Workstreams C & D)
- **Focus overlap (`FocusProtectionOverlapSummary`):**
  - Uses trackable `ProtectionSession` lifecycle statuses (`ACTIVE` bounded by `minOf(nowMs, scheduledEndAt ?: nowMs)`; `COMPLETED`, `CANCELLED`, `OVERRIDDEN` bounded by `endedAt`; `EXPIRED` bounded by `endedAt ?: minOf(nowMs, scheduledEndAt)`), clipped to `[effectiveStartMs, windowEndMs]`. Never substitutes scheduled duration for ended sessions.
  - Unions overlapping protection sessions and overlapping usage intervals before computing intersection (`overlappingUsageDurationMs`) and remaining usage (`outsideProtectionUsageDurationMs`), guaranteeing no millisecond is double-counted.
  - Distinguishes `FocusOverlapAvailability.AVAILABLE`, `EMPTY_NO_PROTECTION_SESSIONS` (`0L` overlap), and `UNAVAILABLE` (`null` overlap/counts when `ProtectionRepository` fails).
- **Single authoritative path & missing-data boundaries:**
  - `ContextEngineImpl.getUnifiedContext` derives both `appUsages` (`List<AppUsageSummary>`) and `usageIntervals` / `usageTemporalSummary` from the same `AppUsageIntervalAnalyzer.analyze` result.
  - When Usage Access is denied, disabled, or `ObservationRepository` fails/is absent, `usageIntervals` is empty and usage durations/counts remain `null` (never `0`). Existing Phase 3.6.7.6B `executionTiming` remains intact and unaffected.

## 6. Files Changed
### Production (4 files)
- `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/model/context/AppUsageTemporalSummary.kt`
- `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/model/context/DigitalBehaviourContext.kt`
- `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/engine/AppUsageIntervalAnalyzer.kt`
- `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/engine/ContextEngineImpl.kt`

### Tests (3 files)
- `IronMind/app/src/test/java/com/sanket_satpute_20/ironmind/domain/engine/AppUsageIntervalAnalyzerTest.kt`
- `IronMind/app/src/test/java/com/sanket_satpute_20/ironmind/domain/engine/ContextEngineAppUsageOverlapIntegrationRoomTest.kt`
- `IronMind/app/src/test/java/com/sanket_satpute_20/ironmind/testutil/fake/FakeObservationRepository.kt`

### Report (1 file)
- `PHASE_3_6_7_6C_APP_USAGE_INTERVALS_AND_FOCUS_OVERLAP_REPORT.md`

## 7. Verification Results (Run After Final Code Edit)
- Unified `./gradlew testDebugUnitTest` run across **17 test suites**: **160 tests run, 0 failures, 0 errors, 0 skipped**:
  - `AppUsageIntervalAnalyzerTest`: `5/5`
  - `ContextEngineAppUsageOverlapIntegrationRoomTest`: `2/2`
  - `ContextEngineImplTest`: `14/14`
  - `UnifiedContextEngineTest`: `11/11`
  - `ContextEngineMemoryIntegrationTest`: `6/6`
  - `ExecutionTimingAnalyzerTest`: `6/6`
  - `ContextEngineTemporalIntegrationRoomTest`: `3/3`
  - `BehaviourPatternDiscoveryEngineTest`: `13/13`
  - `DailyBriefingEngineTest`: `13/13`
  - `AssembleDailyBriefingUseCaseTest`: `12/12`
  - `DigitalPipelineCrossLayerConsistencyTest`: `6/6`
  - `UsageEventsRetentionAndCoverageTest`: `5/5`
  - `PermissionRevocationAndRestorationTest`: `1/1`
  - `BuildFactualContextSnapshotUseCaseTest`: `10/10`
  - `ObservationRepositoryImplTest`: `10/10`
  - `DigitalBehaviourViewModelTest`: `33/33`
  - `GetDailyAppUsageIntelligenceUseCaseTest`: `10/10`
- `./gradlew :app:assembleDebug`: **PASSED (`BUILD SUCCESSFUL`)**.
- Physical-device validation was **not** performed in this environment (JVM + Robolectric/Room verification only).

## 8. Constraints, Known Limitations & Git Integrity
- Zero Room schema/migration changes (`IronMindDatabase` remains `version = 30`), zero new permissions, zero `UsageStats` collector algorithm rewrites, zero UI/ViewModel changes, zero background workers, and zero autonomous actions.
- **Known limitation / deferred work:** If a historical session was sliced across multiple days before the 7-day lookback cutoff, intervals older than 7 days before `windowEndMs` are intentionally excluded by the 7-day lookback ceiling. Pattern discovery consumption of switching/overlap signals is deferred cleanly without modifying existing pattern rules.
- Staged only the 8 Phase 3.6.7.6C files, verified `git diff --cached --check` and `git diff --check`, and committed locally on `main` without pushing.
