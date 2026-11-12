# Phase 3.6.7.8B.1: App Usage Interval-Based Pattern Intelligence Report

## 1. Preflight Branch, HEAD, and Worktree Baseline
- **Repository:** `C:/Users/lenovo/OneDrive/Desktop/IronMind-OS` (`IronMind` Android module)
- **Branch:** `main`
- **Baseline HEAD:** `aac8021cc08237b62bd6c9c675c773e32ee7e60a`
- **Pre-edit worktree baseline:** `393` entries (`184` modified tracked ` M`, `209` untracked `??`, `0` staged).
- **Pre-existing status of target files:**
  - Clean in `HEAD`: `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/engine/AppUsageIntervalAnalyzer.kt`
  - Clean in `HEAD`: `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/engine/BehaviourPatternDiscoveryEngine.kt`
  - Pre-existing untracked `??`: `IronMind/app/src/test/java/com/sanket_satpute_20/ironmind/domain/engine/BehaviourPatternDiscoveryEngineTest.kt` (all 13 existing tests preserved verbatim; 11 regression tests appended cleanly)

## 2. Existing Data Contracts Verified Before Editing
- **`AppUsageInterval`** (`AppUsageTemporalSummary.kt`): Carries `observationId: String` (authoritative `Observation.id` from `ObservationType.APP_USAGE_SESSION`), `userId: String`, `packageName: String`, raw bounds (`rawStartTimeMs`, `rawEndTimeMs`, `rawDurationMs`), clipped bounds (`startTimeMs`, `endTimeMs`, `durationMs`), `isClippedToWindow: Boolean`, and `EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION`.
- **`AppUsageIntervalAnalyzer`** (`AppUsageIntervalAnalyzer.kt`): Enforces bounded 7-day lookback (`MAX_LOOKBACK_MS`), deduplicates by `obs.id` and `(packageName, rawStartTimeMs, rawEndTimeMs)`, clips usage and trackable `ProtectionSession` ranges to `[effectiveStartMs, windowEndMs]`, unions overlapping usage ranges (`mergeRanges`), and computes `FocusProtectionOverlapSummary` (`AVAILABLE`, `EMPTY_NO_PROTECTION_SESSIONS`, `UNAVAILABLE` with nullable `overlappingUsageDurationMs`) using `effectiveProtectionEndMs(session, nowMs)`.
- **`EvidenceReference` & `EvidenceSufficiencyEvaluator`** (`EvidenceReference.kt`, `EvidenceSufficiencyEvaluator.kt`): `EvidenceReference(sourceId, sourceType)` deduplicates evidence by identity; sufficiency requires `MINIMUM_UNIQUE_EVIDENCE_COUNT = 3` unique references across `MINIMUM_DISTINCT_CALENDAR_DAYS = 2` in the supplied `ZoneId`.
- **Availability States** (`SourceFreshness.kt`, `ContextEngineImpl.kt`): Separates `AVAILABLE`, `PARTIAL_HISTORY`, `EMPTY_NO_DATA`, `STALE_DATA`, `PERMISSION_DENIED`, `PERMISSION_NOT_REQUESTED`, and `UNAVAILABLE_HARDWARE`.

## 3. Production Changes (2 files)
- [`AppUsageIntervalAnalyzer.kt`](IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/engine/AppUsageIntervalAnalyzer.kt):
  - Added `internal fun unionDurationMs(ranges: List<Pair<Long, Long>>): Long` delegating to `mergeRanges`.
  - Added `internal data class ProtectionOverlapMatch` and `internal fun findProtectionOverlappingIntervals(...)` reusing `extractClippedProtectionRanges`, `effectiveProtectionEndMs`, `mergeRanges`, and `intersectTotalDuration` so no protection-interval math is duplicated downstream.
- [`BehaviourPatternDiscoveryEngine.kt`](IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/engine/BehaviourPatternDiscoveryEngine.kt):
  - Added defensive `normalizeUsageIntervals(...)` enforcing `userId` isolation, valid duration/bounds, deterministic sorting, deduplication by `observationId` and `(packageName, rawStartTimeMs, rawEndTimeMs)`, and clipping to `[window.startTimeMs, window.endTimeMs]`.
  - Switched digital behaviour pattern evaluation (`evaluateDigitalBehaviourPatterns`) and cross-domain digital paths (`D.1`, `D.2`, `D.4`) to consume authoritative `usageIntervals` whenever supplied (never leaking back into `appUsages` when all provided intervals are out-of-window or invalid), while keeping legacy `appUsages` fallback solely when `usageIntervals` is empty and summary data exists.
  - Added readability and provenance helpers (`isDigitalUsageReadable`, `resolveEffectiveUsageAvailability`, `isFocusOverlapUnavailable`, `isFocusProtectionUnavailable`, `buildUsageProvenanceMetadata`).

## 4. Evidence Identity and Deduplication Approach
- Every digital interval evidence item maps to `EvidenceReference(interval.observationId, EvidenceSourceType.OBSERVATION)` paired with `interval.startTimeMs` inside the clipped analysis window, preserving traceable source observation IDs without fabricating synthetic identifiers.
- `normalizeUsageIntervals` removes duplicate `observationId`s and semantic duplicates sharing `(packageName, rawStartTimeMs, rawEndTimeMs)` before any pattern calculation, preventing duplicate interval representations from inflating counts or unioned durations.
- Within time-of-day bucket analysis (`B.4`), slices from the same `observationId` (such as a midnight-crossing session inside `Night`) are deduplicated by `EvidenceReference` identity and retain their earliest in-bucket timestamp so one observation cannot count as two evidence items or two days.

## 5. Pattern-Specific Behavior Changes
- **B.1 (`FREQUENTLY_USED_APPLICATIONS`), B.2 (`HIGH_USAGE_PERIODS`), B.3 (`REPEATED_APP_USAGE_SESSIONS`):** Multiple genuine distinct intervals from one package contribute distinct `EvidenceReference(observationId, OBSERVATION)` items and unioned duration totals, allowing multi-day recurring usage (`N >= 3` across `>= 2` days) to reach `ESTABLISHED` while same-day repeated sessions remain strictly `EMERGING` and single intervals remain `CANDIDATE`.
- **B.4 (`USAGE_DISTRIBUTION_ACROSS_TIME_WINDOWS`):** Sliced clipped intervals along `zoneId` bucket boundaries (`Morning` `06..11`, `Afternoon` `12..16`, `Evening` `17..21`, `Night` otherwise) using `ZonedDateTime`, unioned overlapping slices per bucket via `AppUsageIntervalAnalyzer.unionDurationMs`, ranked buckets by unioned duration (tie-broken by distinct evidence count and bucket order), and recorded per-bucket durations and evidence counts in `metadata` so single-package morning and night usage attributes accurately without collapsing to `lastObservedAt`.
- **B.5 (`USAGE_DURING_SCHEDULED_FOCUS`):** Respects unavailable overlap states (`UNAVAILABLE_HARDWARE`, `!isProtectionHistoryAvailable`, or `FocusOverlapAvailability.UNAVAILABLE`), distinguishes `EMPTY_NO_PROTECTION_SESSIONS` / `0L` overlap from unavailable telemetry, delegates session intersection to `AppUsageIntervalAnalyzer.findProtectionOverlappingIntervals` (preserving Phase 3.6.7.8A `effectiveProtectionEndMs` bounds on expired `ACTIVE` sessions), and preserves strictly non-causal phrasing.
- **Provenance Integrity:** Preserves `PARTIAL_HISTORY` in `metadata["usageAvailability"]` and `metadata["isPartialHistory"] = "true"`, blocks `PERMISSION_DENIED`, `PERMISSION_NOT_REQUESTED`, `UNAVAILABLE_HARDWARE`, and `EMPTY_NO_DATA`, and caps stale sources at `STALE_CONFIDENCE_CAP`.

## 6. Verification Results (Run After Final Edit)
- `./gradlew testDebugUnitTest` across **13 suites**: **133 tests run, 0 failures, 0 errors, 0 skipped**:
  - `AppUsageIntervalAnalyzerTest`: `5/5`
  - `BehaviourPatternDiscoveryEngineTest`: `24/24` (`13` pre-existing + `11` new Phase 3.6.7.8B.1 regression tests)
  - `ContextEngineAppUsageOverlapIntegrationRoomTest`: `2/2`
  - `ContextEngineCorrectnessRemediationTest`: `4/4`
  - `ContextEngineImplTest`: `14/14`
  - `ContextEngineTemporalIntegrationRoomTest`: `3/3`
  - `DigitalPipelineCrossLayerConsistencyTest`: `6/6`
  - `EvidenceSufficiencyEvaluatorTest`: `13/13`
  - `PatternEngineImplTest`: `6/6`
  - `PersonalIntelligenceOrchestrationTest`: `16/16`
  - `UnifiedContextEngineTest`: `11/11`
  - `EvaluatePatternCandidateUseCaseTest`: `19/19`
  - `EvaluatePatternCandidatesUseCaseTest`: `10/10`
- `./gradlew :app:assembleDebug`: **PASSED (`BUILD SUCCESSFUL`)**.
- **Unverified / out of scope:** Full unfiltered repository unit test run beyond the 13 targeted suites, `androidTest` instrumentation suites, and physical-device verification were not executed.

## 7. Known Limitations and Deferred Scope
- `GetDailyAppUsageIntelligenceUseCase`, Digital Behaviour UI/ViewModels, Notification/Calendar integration, Room migrations, Workers/orchestration scheduling, and AI prompts were intentionally left untouched.
- Downstream `PersonalInsightDiscoveryEngine` task-execution timing and interval consumption remain deferred to subsequent phases (`3.6.7.8B.2` / `3.6.7.8B.3`).
