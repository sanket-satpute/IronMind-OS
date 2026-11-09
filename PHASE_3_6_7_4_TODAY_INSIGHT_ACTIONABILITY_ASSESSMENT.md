# IronMind-OS — Phase 3.6.7.4 Architecture Assessment
# Today Insight Actionability — Architecture Assessment (READ-ONLY)

**Phase:** Phase 3.6.7.4: Today Insight Actionability Architecture Assessment  
**Repository:** `C:/Users/lenovo/OneDrive/Desktop/IronMind-OS`  
**Android Module:** `IronMind` (`C:/Users/lenovo/OneDrive/Desktop/IronMind-OS/IronMind`)  
**Target Branch:** `main`  
**Current HEAD:** `a73c34597c2eef021b77dfef4ed3bc80f30267ce`  
**Phase Mode:** Assessment-Only (READ-ONLY)

---

## 1. Executive Summary

Phase 3.6.7.4 evaluates the architecture for connecting existing personal intelligence outputs into the **Today** dashboard (`TodayScreen.kt`, `TodayViewModel.kt`) to make insights actionable while strictly preserving user autonomy, epistemic integrity, and crash-safe transactional guarantees.

### Key Assessment Findings:
1. **Foundational Architecture Already Complete**:
   The domain models (`DailyBriefing`, `ProposedAction`), planning engines (`ActionPlanningEngine`, `DailyBriefingEngine`), lifecycle use cases (`AssembleDailyBriefingUseCase`, `ActionLifecycleUseCase`), and persistence (`ActionRepositoryImpl` via Room `memories` table) were designed in Phase 3.5/3.5.1 and are already wired in `AppContainer.kt`.
2. **Today Screen Partial Wiring**:
   `TodayScreen.kt` and `TodayViewModel.kt` already contain rudimentary slots for `DailyBriefingCard`, `ProposedActionCard`, and `BriefingInsightCard`, as well as action review methods (`acceptAction`, `rejectAction`, `dismissAction`, `deferAction`, `executeAction`, `recordActionOutcome`).
3. **Key Identified Implementation Gaps**:
   - **Non-Reactive Action Observation in ViewModel**: `TodayViewModel` currently updates `proposedActions` on-demand via `refreshDailyBriefing()` or local in-memory manipulation; it does not observe `ActionRepository.observeActionsForUser(userId)`.
   - **Heavy Briefing Assembly**: `AssembleDailyBriefingUseCase` executes full `PersonalIntelligenceOrchestrator.orchestrate(...)` synchronously on demand, which can delay briefing availability or stall Today screen rendering if orchestration is slow.
   - **Omitted Intelligence Elements**: Discovered Patterns (`discoveredPatterns`) and Evidence-Backed Barriers (`evidenceBackedBarriers`) are assembled by `DailyBriefingEngine` but have no rendering components in `TodayScreen.kt`.
   - **Read-Only Insights in Today**: `BriefingInsightCard` is purely informational and lacks quick review actions (Useful / Dismiss / Later) that exist in `DigitalBehaviourScreen` and `InsightReviewContent`.
   - **Testing Deficit**: `TodayViewModelTest.kt` has **zero** unit tests for daily briefing refresh, action review, action execution, duplicate prevention, crash recovery, or outcome recording.
4. **Final Verdict**: **READY FOR IMPLEMENTATION**.

---

## 2. Verified Baseline & File Inventory

### 2.1 Git Baseline Status
- **Current Branch:** `main`
- **HEAD Commit SHA:** `a73c34597c2eef021b77dfef4ed3bc80f30267ce`
- **Tracked Modified Files:** 193 files (strictly preserved)
- **Staged Files:** 0 files
- **Untracked Files:** 222 files (strictly preserved)

### 2.2 Inspected File Paths
| Component | Exact File Path |
|---|---|
| Today UI Screen | `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/ui/screens/today/TodayScreen.kt` |
| Today ViewModel | `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/ui/screens/today/TodayViewModel.kt` |
| Today Unit Tests | `IronMind/app/src/test/java/com/sanket_satpute_20/ironmind/ui/screens/today/TodayViewModelTest.kt` |
| Daily Briefing Engine | `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/engine/DailyBriefingEngine.kt` |
| Action Planning Engine | `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/engine/ActionPlanningEngine.kt` |
| Assemble Briefing Use Case | `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/usecase/briefing/AssembleDailyBriefingUseCase.kt` |
| Action Lifecycle Use Case | `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/usecase/action/ActionLifecycleUseCase.kt` |
| Action Repository Interface | `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/repository/ActionRepository.kt` |
| Action Repository Impl | `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/data/repository/ActionRepositoryImpl.kt` |
| Proposed Action Model | `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/model/action/ProposedAction.kt` |
| Daily Briefing Model | `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/domain/model/briefing/DailyBriefing.kt` |
| App Container | `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/di/AppContainer.kt` |
| Action Hardening Tests | `IronMind/app/src/test/java/com/sanket_satpute_20/ironmind/domain/engine/ActionLifecycleReliabilityHardeningTest.kt` |
| Crash-Safety Integration Tests | `IronMind/app/src/test/java/com/sanket_satpute_20/ironmind/integration/ActionExecutionCrashSafetyIntegrationTest.kt` |

---

## 3. Existing Intelligence-to-Today Data Flow

### 3.1 Architectural Flow Diagram
```
Room SQLite (`patterns`, `memories`, `barrier_hypotheses`, `tasks`, `commitments`)
                              │
                              ▼
        PersonalIntelligenceOrchestrator.orchestrate()
                              │
                              ▼
       ActionPlanningEngine.planActions() + DailyBriefingEngine
                              │
                              ▼
        AssembleDailyBriefingUseCase.invoke(userId)
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
       Persist to ActionRepository   Return DailyBriefing
                    │                   │
                    ▼                   ▼
           ActionRepositoryImpl     TodayViewModel.refreshDailyBriefing()
          (Room memories table)         │
                    │                   ▼
                    │            TodayUiState.Success
                    │          (dailyBriefing, proposedActions)
                    ▼                   │
         [Reactive Gap:                 ▼
         not observed by Today]   TodayScreen.kt (Compose UI)
```

### 3.2 Intelligence Availability
1. **Available Outputs**:
   - `DailyBriefing.attentionSummary`: Plain-language focus sentence computed deterministically from overdue tasks, due today tasks, active commitments, and stalled plans.
   - `DailyBriefing.goalsAndPlansNeedingAttention`: Active plans with overdue tasks or stalled plans (>7 days inactive).
   - `DailyBriefing.discoveredPatterns`: Behavioural patterns with occurrences, confidence, and epistemic authority.
   - `DailyBriefing.evidenceBackedBarriers`: Obstacles with `evidenceCount >= 2` or `isUserConfirmed == true`.
   - `DailyBriefing.relevantInsights`: Insights sorted by epistemic authority and confidence, excluding dismissed insights.
   - `DailyBriefing.proposedNextActions`: Concrete actionable recommendations.
   - `DailyBriefing.dataAvailabilityNote`: Warning message when usage permissions are absent or telemetry is missing.
2. **Current Rendering in Today**:
   - `DailyBriefingCard` renders `attentionSummary`, `dataAvailabilityNote`, and `goalsAndPlansNeedingAttention`.
   - `ProposedActionCard` renders active `proposedActions`.
   - `BriefingInsightCard` renders top 3 `relevantInsights` in read-only form.
   - `discoveredPatterns` and `evidenceBackedBarriers` are completely unrendered in `TodayScreen`.

---

## 4. Action Lifecycle and Persistence Flow

### 4.1 Supported ProposedAction Categories
`ProposedAction` categorizes actions into 6 distinct types:
1. `TASK_RESCHEDULE`: Updates an overdue or competing task's `dueAt`.
2. `TASK_DECOMPOSITION`: Creates smaller, manageable sub-tasks from a large task (>=90 min).
3. `FOCUS_PROTECTION`: Creates a protected focus block commitment.
4. `COMMITMENT_REVIEW`: Reschedules or modifies an existing commitment.
5. `GOAL_PLAN_RECONSIDERATION`: Pauses, reviews, or adjusts stalled plans.
6. `BEHAVIOUR_PATTERN_REVIEW`: Prompts user reflection on an observed behavioural pattern.

### 4.2 Lifecycle State Machine
```
[Generated] ──► PROPOSED
                   │
         ┌─────────┼─────────┬─────────┐
         ▼         ▼         ▼         ▼
     ACCEPTED   REJECTED  DISMISSED  DEFERRED
         │
         ▼ (User clicks "Execute Action")
     EXECUTING (Durable Room state marker)
         │
   ┌─────┴─────┐
   ▼           ▼
EXECUTED   EXECUTION_FAILED
   │           │
   ▼           ▼
Outcome     User Reset
Recorded
```

### 4.3 Action Lifecycle Invariants
1. **Acceptance != Execution**: Accepting an action updates its review status to `ACCEPTED` and syncs with any source `InterventionRecommendation`, but performs **no domain mutations**.
2. **Execution != Success**: Executing an action applies domain mutations (e.g. `editTaskUseCase`, `createTaskUseCase`); success requires an explicit user-confirmed `Outcome`.
3. **Rejection != Refutation**: Rejecting or dismissing an action never refutes underlying insights or confirms/refutes barrier hypotheses.
4. **Concurrency Guard**: `ActionLifecycleUseCase` enforces JVM `Mutex` locking per `action.id` (`executionLocks`), preventing double-tap or race conditions.
5. **Deterministic Idempotency**: Sub-tasks (`${action.id}_sub_$index`) and focus commitments (`${action.id}_focus_comm`) use deterministic IDs, preventing duplication on crash recovery.
6. **Recovery Protocol**: `reconcileInterruptedActions` checks domain entities to verify if a crash interrupted execution before or after commit. If committed, marks `EXECUTED`; if uncommitted, safely reverts to `ACCEPTED`.

---

## 5. Current Implementation Gaps

| Area | Current Behavior | Target Behavior | Severity |
|---|---|---|---|
| **Action Observation** | `TodayViewModel` loads actions on-demand via `refreshDailyBriefing()` and mutates local state in memory. | `TodayViewModel` reactively observes `ActionRepository.observeActionsForUser(userId)` so Room is the single source of truth. | High |
| **Briefing Orchestration** | `AssembleDailyBriefingUseCase` invokes full `PersonalIntelligenceOrchestrator.orchestrate()` synchronously, which can be slow. | Briefing assembly should consume already-persisted intelligence and Room repositories directly; orchestration should remain asynchronous in background. | Medium |
| **Patterns & Barriers Display** | `discoveredPatterns` and `evidenceBackedBarriers` exist in `DailyBriefing` but are not displayed in `TodayScreen`. | Add a clean, expandable section in Today for Briefing Highlights (patterns/barriers with epistemic badges). | Medium |
| **Insight Interactivity** | `BriefingInsightCard` is purely read-only; user cannot mark Useful, Dismiss, or Review Later. | Provide standard review actions on `BriefingInsightCard` matching `DigitalBehaviourScreen`. | Medium |
| **Today Test Coverage** | `TodayViewModelTest` contains 0 tests for daily briefing or proposed action review/execution. | Implement complete suite of unit tests covering briefing, review actions, execution, recovery, and outcome recording. | High |

---

## 6. Recommended Minimal Implementation Design

### 6.1 Reactive Action Observation in `TodayViewModel`
1. Inject `ActionRepository` into `TodayViewModel` (already available in `AppContainer`).
2. Add `observeActions(userId)` job inside `observeSession()`:
   ```kotlin
   actionObserveJob = viewModelScope.launch {
       actionRepository.observeActionsForUser(userId).collect { actions ->
           if (isCurrentSession(userId)) {
               val active = actions.filter {
                   it.reviewStatus == ActionReviewStatus.PROPOSED ||
                   it.reviewStatus == ActionReviewStatus.ACCEPTED ||
                   (it.reviewStatus == ActionReviewStatus.EXECUTED && it.outcomeId == null)
               }
               updateSuccessState { copy(proposedActions = active) }
           }
       }
   }
   ```
3. When actions are accepted, rejected, dismissed, deferred, executed, or have outcomes recorded, `ActionLifecycleUseCase` updates Room, which automatically emits into the UI without manual local list manipulation.

### 6.2 Today Screen UI Hierarchy
Maintain the clean, user-centric hierarchy:
1. **Daily Briefing Card** (Top): Attention summary + data availability note + quick refresh.
2. **Recommended Next Actions** (Priority Section): Top 1-3 prioritized actions with clear Accept, Defer, Dismiss, and Execute controls.
3. **Commitments (Next Action)**: Immediate active commitment.
4. **Tasks (Overdue & Due Today)**: Direct task management.
5. **Later Today Commitments**: Remaining scheduled commitments.
6. **Briefing Highlights & Insights** (Bottom): 2-3 relevant insights with Useful/Dismiss/Later controls and a summary pill for discovered patterns.

---

## 7. Exact Files Expected to Change in Phase 3.6.7.4 Implementation

1. `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/ui/screens/today/TodayViewModel.kt`:
   - Inject `actionRepository: ActionRepository? = null`.
   - Add reactive observation of `actionRepository.observeActionsForUser(userId)`.
   - Add `markInsightUseful`, `dismissInsight`, `reviewInsightLater` delegating to existing `ReviewPersonalInsightUseCase`.
   - Update `Companion.Factory` to wire `container.actionRepository` and `container.reviewPersonalInsightUseCase`.
2. `IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/ui/screens/today/TodayScreen.kt`:
   - Add action buttons (Useful, Dismiss, Later) to `BriefingInsightCard`.
   - Pass insight review callbacks from `TodayViewModel` to `BriefingInsightCard`.
   - Add concise rendering for `discoveredPatterns` and `evidenceBackedBarriers` within `DailyBriefingCard`.
3. `IronMind/app/src/test/java/com/sanket_satpute_20/ironmind/ui/screens/today/TodayViewModelTest.kt`:
   - Add unit tests covering briefing loading, reactive action updates, accept/reject/dismiss/defer, execution, duplicate prevention, crash recovery, and outcome recording.

---

## 8. Required Dependency Injection Changes

In `AppContainer.kt`:
- No new dependencies need to be created.
- `actionRepository`, `actionLifecycleUseCase`, `assembleDailyBriefingUseCase`, and `reviewPersonalInsightUseCase` are **already declared and instantiated** in `AppContainer.kt`.
- In `TodayViewModel.Companion.Factory`:
  - Pass `actionRepository = container.actionRepository`
  - Pass `reviewPersonalInsightUseCase = container.reviewPersonalInsightUseCase`

---

## 9. Comprehensive Test Plan

### Test Scenarios to Implement in `TodayViewModelTest`:
1. **Daily Briefing Loading**: Verify `refreshDailyBriefing()` populates `dailyBriefing` and handles failure gracefully without crashing.
2. **Reactive Action Observation**: Emitting actions from `actionRepository` updates `uiState.proposedActions`.
3. **Empty States**: Empty briefing and empty actions render cleanly with default placeholder states.
4. **Stale/Missing Intelligence**: Verify `dataAvailabilityNote` and priority downgrades for stale data.
5. **Session Isolation**: Switching session to `Initializing` clears briefing and actions; new user sees only their own actions.
6. **Cross-Session Overwrite Prevention**: Late emissions from previous user cannot overwrite active user's state.
7. **Action Acceptance**: Calling `acceptAction` transitions status to `ACCEPTED` and syncs with recommendation.
8. **Action Rejection & Dismissal**: Calling `rejectAction` / `dismissAction` transitions status without refuting insights or barriers.
9. **Action Deferral**: Calling `deferAction` sets `deferredUntilMs`.
10. **Action Execution**: Calling `executeAction` on an `ACCEPTED` action performs domain mutation and transitions to `EXECUTED`.
11. **Concurrency Protection**: Simultaneous `executeAction` calls on the same action are rejected.
12. **Interrupted Execution Recovery**: Actions left in `EXECUTING` state are reconciled on startup/briefing assembly.
13. **Outcome Recording**: Calling `recordActionOutcome` records `Outcome` and updates `action.outcomeId` without re-executing.
14. **Insight Review from Today**: Calling `markInsightUseful`, `dismissInsight`, `reviewInsightLater` from Today updates insight repository.
15. **Today Regression Suite**: All existing commitment and task tests continue to pass.

---

## 10. Risks and Safeguards

| Risk | Mitigation |
|---|---|
| **Accidental Autonomous Execution** | Invariant enforced: Actions can ONLY execute on explicit user UI click. No background workers or schedulers execute actions. |
| **Epistemic Conflation** | Invariant enforced: AI hypotheses carry `AI_HYPOTHESIS` badge; accepting an action NEVER confirms a barrier or proves an AI hypothesis. |
| **Performance Degradation** | Invariant enforced: Today screen observes Room reactive flows for actions. Intelligence orchestration remains asynchronous. |
| **Session Bleed** | Invariant enforced: All operations use `runWithSession`, jobs cancelled on session reset, and user IDs verified on every emission. |
| **Database Migration Hazards** | Invariant enforced: Zero Room migrations. Actions persist in existing `MemoryEntity` table using `ActionJsonCodec`. |

---

## 11. Git Baseline & Verification Statement

- **Branch:** `main`
- **HEAD Commit:** `a73c34597c2eef021b77dfef4ed3bc80f30267ce`
- **Worktree Changes:** Exactly preserved (193 tracked modified files, 222 untracked files).
- **Source Code Alteration Statement:**  
  *No source code, test code, configuration, or database files were modified, created, deleted, staged, or committed during this assessment. Only this assessment document was authored.*

---

## 12. Final Verdict

### **READY FOR IMPLEMENTATION**

All necessary domain models, planning engines, lifecycle use cases, Room repositories, and DI components are already built, tested, and available in `AppContainer`. Implementing Phase 3.6.7.4 requires only wiring reactive action observation into `TodayViewModel`, connecting review controls in `TodayScreen`, and adding targeted unit tests.

