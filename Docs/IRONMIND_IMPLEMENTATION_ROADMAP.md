# `IRONMIND_IMPLEMENTATION_ROADMAP.md`

````markdown
# IRONMIND IMPLEMENTATION ROADMAP

**Project:** IronMind  
**Document Type:** Authoritative Implementation Roadmap  
**Status:** Active  
**Purpose:** Define the exact implementation sequence for building IronMind from the ground up.

---

# 1. PURPOSE

This document converts the IronMind product, architecture, data, AI, autonomy, intervention, and development contracts into an executable build sequence.

It answers:

- What gets built first?
- What depends on what?
- What must not be built yet?
- What should each AI coding sprint modify?
- What must be tested before moving forward?
- When does IronMind move from a manual system to an intelligent system?
- When does IronMind move from suggestion to autonomy?
- What constitutes completion of each phase?

This roadmap is intentionally sequential.

A later phase must not be implemented merely because its technology is available.

IronMind should become more capable in layers:

```text
FOUNDATION
    ↓
CORE ACTION SYSTEM
    ↓
MEMORY + VOICE + HISTORY
    ↓
INTELLIGENCE
    ↓
AUTONOMY
    ↓
DEEP ADAPTATION
````

The product must remain usable at every completed stage.

---

# 2. AUTHORITY

This document is subordinate to the following permanent contracts:

1. `IRONMIND_MASTER_BLUEPRINT.md`
2. `PRODUCT_CONSTITUTION.md`
3. `SYSTEM_ARCHITECTURE.md`
4. `DATA_CONTRACT.md`
5. `AI_BEHAVIOR_CONTRACT.md`
6. `AUTONOMY_POLICY.md`
7. `INTERVENTION_RULES.md`
8. `DEVELOPMENT_RULES.md`

If this roadmap conflicts with any of those documents, the contracts win.

This roadmap controls implementation order, not product philosophy.

---

# 3. CORE IMPLEMENTATION PRINCIPLE

IronMind must be built from deterministic foundations upward.

The implementation order is:

```text
DATA
→ DOMAIN
→ STATE
→ UI
→ EVENTS
→ OBSERVATION
→ MEMORY
→ INTELLIGENCE
→ DECISION
→ INTERVENTION
→ AUTONOMY
→ ADAPTATION
```

Never reverse this order merely because an AI API is easier to demonstrate.

The system must first know what happened before attempting to explain why it happened.

---

# 4. TARGET DEVELOPMENT MODEL

Every implementation unit is an atomic sprint.

The required cycle is:

```text
READ CONTRACTS
      ↓
SCAN CURRENT REPOSITORY
      ↓
DEFINE SPRINT SCOPE
      ↓
PLAN
      ↓
IMPLEMENT
      ↓
RUN TESTS
      ↓
VERIFY LOGS
      ↓
INSPECT GIT DIFF
      ↓
HUMAN REVIEW
      ↓
COMMIT
      ↓
NEXT SPRINT
```

No sprint should become an uncontrolled feature-development session.

---

# 5. SPRINT RULE

Every sprint must explicitly define:

```text
OBJECTIVE
SCOPE
ALLOWED FILES/AREAS
FORBIDDEN AREAS
DATA CHANGES
DOMAIN CHANGES
UI CHANGES
ARCHITECTURE CHANGES
LOGGING REQUIREMENTS
TEST REQUIREMENTS
ACCEPTANCE CRITERIA
```

An AI coding agent must stop when the sprint is complete.

It must not continue implementing “obvious next features.”

---

# 6. VERSION STRATEGY

IronMind is divided into the following major versions.

| Version | Purpose                                   |
| ------- | ----------------------------------------- |
| Phase 0 | Engineering foundation                    |
| V0      | Reliable core action loop                 |
| V1      | Memory, voice, history                    |
| V2      | AI intelligence and pattern understanding |
| V3      | Controlled autonomy and intervention      |
| V4      | Deep adaptation and broader context       |

Each phase has an exit gate.

Do not begin the next major version until the current version passes its exit criteria.

---

# 7. PHASE 0 — ENGINEERING FOUNDATION

## Objective

Create a clean, maintainable Android foundation before implementing meaningful product functionality.

This phase exists to prevent architectural debt.

---

## Sprint 0.1 — Create Fresh Android Project

### Objective

Create the new IronMind Android application from scratch.

### Scope

Establish:

* Kotlin project
* Jetpack Compose
* Android application module
* application namespace/package
* minimum supported Android configuration
* basic project structure
* Git repository
* initial README
* debug build
* release build configuration baseline

### Required principle

The old IronMind repository must not become the architectural starting point.

This is a clean implementation.

### Forbidden

Do not implement:

* goals
* AI
* onboarding logic
* protection
* Firebase
* background workers
* memory
* autonomous actions
* complex navigation
* unnecessary libraries

### Tests

* project builds
* debug APK builds
* existing default tests pass

### Acceptance

A clean application launches successfully on a real Android device.

---

# Sprint 0.2 — Establish Architecture Skeleton

## Objective

Create the architectural boundaries defined in `SYSTEM_ARCHITECTURE.md`.

### Scope

Create clearly separated areas for:

```text
presentation
domain
data
observation
intelligence
decision
intervention
execution
infrastructure
```

The exact package names may vary.

The architectural boundaries may not.

### Required interfaces

Establish appropriate interfaces for:

* repositories
* system adapters
* AI abstraction
* observation sources
* intervention execution
* background execution

### Forbidden

Do not connect these layers with fake feature logic merely to demonstrate architecture.

Do not create giant placeholder classes.

### Tests

* architecture compilation
* basic domain isolation tests where practical

### Acceptance

The project clearly demonstrates where future functionality belongs.

---

# Sprint 0.3 — Lifecycle Logging Infrastructure

## Objective

Implement the standard IronMind lifecycle logging mechanism.

### Standard format

```text
IronMindLifecycle [Component] [EVENT] key=value...
```

Examples:

```text
IronMindLifecycle [App] [STARTED]
IronMindLifecycle [Database] [INITIALIZED]
IronMindLifecycle [Navigation] [DESTINATION_CHANGED] destination=Today
```

### Scope

Create:

* centralized logging abstraction
* standardized event structure
* debug logging implementation
* safe structured values
* sensitive-data filtering

### Tests

Verify:

* logs are emitted
* event names are stable
* sensitive fields are not logged

### Acceptance

Important system state transitions can be reconstructed from logs.

---

# Sprint 0.4 — Core Time / ID / Result Infrastructure

## Objective

Establish common technical primitives.

### Scope

Implement reusable infrastructure for:

* stable IDs
* timestamps
* result/error handling
* clock abstraction
* UUID generation
* deterministic test time

### Principle

Time must be injectable for tests.

Do not scatter calls to uncontrolled system time throughout the domain layer.

### Tests

Test:

* timestamp creation
* deterministic clock
* ID uniqueness
* success/failure results

### Acceptance

The domain layer does not need Android APIs to perform basic logic.

---

# Sprint 0.5 — Room Database Foundation

## Objective

Create the local persistence foundation.

### Initial database scope

Only establish infrastructure necessary for the first V0 entities.

Do not create the complete future database merely because the Data Contract lists more entities.

### Initial direction

Support:

* UserProfile
* Goal
* Plan
* Task
* Commitment
* Outcome
* Reflection

Entities may be introduced incrementally where necessary.

### Scope

Implement:

* Room database
* entities
* DAOs
* migrations
* repositories
* local data source

### Tests

* DAO tests
* repository tests
* migration tests
* persistence/reload tests

### Acceptance

Application data survives process death.

---

# Sprint 0.6 — Testing Infrastructure

## Objective

Make testing a normal development operation before features become large.

### Scope

Establish:

* unit-test structure
* coroutine test support
* fake repositories
* fake clock
* domain test utilities
* Android instrumentation baseline

### Required principle

Tests must not require real external AI or backend services unless the sprint explicitly requires integration testing.

### Acceptance

An AI coding agent can run a deterministic test suite locally.

---

# Sprint 0.7 — Navigation and Application Shell

## Objective

Create the smallest functional application shell.

### Initial destinations

At minimum:

```text
Today
Goals
Settings
```

The exact screen design can remain basic.

### Scope

Implement:

* navigation
* app-level state handling
* basic application shell
* error state
* loading state

### Forbidden

Do not implement a full settings system yet.

Do not implement autonomy controls yet.

### Acceptance

The application has a stable shell through which future functionality can be added.

---

# PHASE 0 EXIT GATE

Phase 0 is complete only when:

* application builds reliably
* application launches on real device
* architecture boundaries exist
* Room works
* migrations work
* tests run
* lifecycle logs work
* Git history is clean
* no major product logic is hidden in UI classes
* no AI dependency exists in core execution

Required checkpoint:

```text
CHECKPOINT: FOUNDATION_COMPLETE
```

Commit before entering V0.

---

# 8. V0 — CORE ACTION LOOP

## Objective

Build the smallest real version of IronMind that can help a user move from intention to action.

V0 must answer:

```text
What matters?
→ What am I doing?
→ What did I commit to?
→ Did I act?
→ What happened?
→ What should happen next?
```

V0 follows:

```text
GOAL
→ PLAN
→ COMMIT
→ PROTECT
→ ACT
→ RESULT
→ REFLECT
```

No advanced AI is required for this loop.

---

# Sprint V0.1 — User Profile and Local Identity

## Objective

Create the local user identity needed by the domain model.

### Scope

Implement:

* UserProfile
* local initialization
* basic profile state
* repository access

### Forbidden

Do not implement:

* account authentication
* cloud sync
* personality inference
* psychological profiling

### Tests

* profile creation
* reload after process death
* repository behavior

### Acceptance

A user can have one stable local IronMind profile.

---

# Sprint V0.2 — Goal Model

## Objective

Allow users to define what matters.

### Scope

Implement:

* Goal entity
* create goal
* edit goal
* archive/end goal
* goal state
* goal persistence
* goal list UI
* goal detail UI

### Important distinction

A Goal is not a Task.

A Goal represents meaningful direction.

### Tests

Test:

* creation
* editing
* ending
* retrieval
* invalid state transitions

### Acceptance

A user can clearly define and manage meaningful goals.

---

# Sprint V0.3 — Plan and Task Model

## Objective

Translate goals into executable work.

### Scope

Implement:

```text
Goal
  ↓
Plan
  ↓
Task
```

Support:

* create plan
* create tasks
* edit tasks
* reorder tasks where necessary
* mark task state
* associate tasks with goals

### Principle

Tasks should represent actionable work.

Avoid turning IronMind into a generic project-management suite.

### Tests

* task persistence
* goal/task relationships
* state changes
* invalid operations

### Acceptance

A meaningful goal can be translated into specific actions.

---

# Sprint V0.4 — Commitment State Machine

## Objective

Implement the canonical commitment lifecycle.

### State machine

```text
PLANNED
   ↓
COMMITTED
   ↓
STARTED
   ↓
COMPLETED
```

Alternative valid transitions:

```text
COMMITTED → POSTPONED
COMMITTED → MISSED

STARTED → POSTPONED
STARTED → MISSED

MISSED → RECOVERED
MISSED → ABANDONED
POSTPONED → COMMITTED
```

### Scope

Implement:

* Commitment entity
* transition validation
* timestamps
* transition history/events
* repository
* UI controls

### Tests

Every valid transition must be tested.

Every invalid transition must be rejected.

### Acceptance

Commitment state is deterministic and cannot be silently corrupted.

---

# Sprint V0.5 — Today / Action Screen

## Objective

Create the primary execution surface.

### Purpose

The user should be able to understand:

```text
What matters today?
What did I commit to?
What is the next action?
```

### Scope

Implement:

* Today screen
* current commitments
* action details
* start action
* complete action
* postpone action
* missed handling
* next action selection

### Principle

The Today screen should not become a dashboard full of metrics.

Action clarity is more important than visual complexity.

### Acceptance

A user can open IronMind and immediately understand what action matters.

---

# Sprint V0.6 — Outcome Tracking

## Objective

Separate commitment status from real-world outcome.

### Scope

Implement:

* Outcome entity
* completion result
* meaningful result recording
* failure/result distinction
* outcome display

### Example

A commitment can be completed while producing an unexpected outcome.

The system must not collapse these into one field.

### Tests

Verify:

* completed commitment + positive outcome
* completed commitment + negative outcome
* completed commitment + neutral outcome
* incomplete commitment + partial outcome

### Acceptance

IronMind records what the user committed to and what actually happened as distinct information.

---

# Sprint V0.7 — Basic Protection Engine

## Objective

Introduce the first version of IronMind's Shield role.

### Scope

Implement protection as a bounded domain concept:

```text
ProtectionRule
ProtectionSession
```

Support manually initiated protection.

### Important

V0 protection should first establish domain behavior before attempting complex Android system integration.

### Scope may include

* start protection session
* stop protection session
* time-bound protection
* target defined by user
* cancellation
* logging

### Forbidden

Do not implement broad autonomous device control.

Do not block arbitrary apps without explicit user configuration.

### Acceptance

Protection is:

* explicit
* bounded
* reversible
* observable

---

# Sprint V0.8 — Android Protection Adapter

## Objective

Connect the protection domain to supported Android capabilities.

### Scope

Implement the first concrete Android adapter required by the approved protection behavior.

Possible system integrations must remain behind interfaces.

### Required behavior

The domain does not directly depend on Android APIs.

### Tests

* adapter unit tests where possible
* instrumentation tests
* permission-denied behavior
* unavailable-capability behavior
* session termination

### Real-device test

Test on physical Android hardware.

### Acceptance

Protection works when permission exists and fails safely when permission does not exist.

---

# Sprint V0.9 — Basic Scheduling and Reminder Mechanism

## Objective

Allow commitments to have planned timing.

### Scope

Implement only deterministic user-configured scheduling.

Use the appropriate Android scheduling mechanism.

### Required principles

* no spam
* timezone awareness
* cancellation
* rescheduling
* idempotency

### Forbidden

Do not yet introduce AI-generated schedules.

### Acceptance

A scheduled commitment produces the expected reminder once and can be changed or cancelled.

---

# Sprint V0.10 — Minimal Night Reflection

## Objective

Close the V0 daily loop.

### Flow

```text
DAY
 ↓
PLANNED
 ↓
ACTED / POSTPONED / MISSED
 ↓
RESULT
 ↓
REFLECTION
```

### Scope

Implement a simple end-of-day reflection.

Support:

* today's summary
* planned/completed/postponed/missed information
* user text reflection
* save reflection

### Principle

Reflection is synchronization with reality, not a journaling requirement.

### Acceptance

The day can be closed and reflected on without AI.

---

# V0 EXIT GATE

V0 is complete only when a real user can:

```text
define a goal
→ create a plan
→ create a task
→ make a commitment
→ protect the action
→ act
→ complete/postpone/miss
→ record outcome
→ reflect
```

And when all of the above:

* persist locally
* survive app restart
* produce meaningful lifecycle logs
* have automated tests
* work without AI
* work without cloud connectivity

Required checkpoint:

```text
CHECKPOINT: V0_CORE_LOOP_COMPLETE
```

Commit before V1.

---

# 9. V1 — MEMORY, VOICE, AND HISTORY

## Objective

Turn IronMind from an action tracker into a system that can remember the user's lived history.

The emphasis is not yet autonomous intelligence.

The emphasis is:

```text
RECORD
→ REMEMBER
→ RETRIEVE
→ REFLECT
```

---

# Sprint V1.1 — Event Store

## Objective

Create the event-first history required by the Data Contract.

### Scope

Implement event recording for meaningful domain actions.

Examples:

```text
GOAL_CREATED
TASK_CREATED
COMMITMENT_COMMITTED
COMMITMENT_STARTED
COMMITMENT_COMPLETED
COMMITMENT_POSTPONED
COMMITMENT_MISSED
OUTCOME_RECORDED
REFLECTION_CREATED
PROTECTION_STARTED
PROTECTION_ENDED
```

### Acceptance

Historical events can be used to reconstruct important state transitions.

---

# Sprint V1.2 — History and Timeline

## Objective

Expose useful historical information.

### Scope

Implement:

* commitment history
* outcomes
* reflections
* major events
* goal history

### Principle

History must answer meaningful questions.

Avoid building a meaningless event log UI for ordinary users.

### Acceptance

The user can understand what they actually did over time.

---

# Sprint V1.3 — Reflection Improvements

## Objective

Make reflection a natural daily synchronization mechanism.

### Scope

Improve:

* daily summary
* missed/postponed review
* result review
* user-entered reflection
* reflection retrieval

### Acceptance

Reflection captures useful information without forcing structured journaling.

---

# Sprint V1.4 — Speech-to-Text Abstraction

## Objective

Allow users to speak naturally.

### Architecture

Create:

```text
SpeechInput
    ↓
SpeechToText interface
    ↓
Android/provider implementation
```

### Principle

Speech technology must remain replaceable.

### Tests

Use fake STT implementations in domain tests.

### Acceptance

Voice can be introduced without coupling the domain to one provider.

---

# Sprint V1.5 — Voice Reflection

## Objective

Allow natural voice reflection.

### Flow

```text
USER SPEAKS
→ STT
→ ORIGINAL TRANSCRIPT STORED
→ REFLECTION STORED
```

AI extraction is not yet required.

### Critical rule

Never replace the original transcript with AI interpretation.

### Acceptance

The original spoken content remains recoverable.

---

# Sprint V1.6 — Memory Model

## Objective

Implement the distinction between raw history and learned memory.

### Scope

Implement:

* Memory entity
* provenance
* evidence
* confidence
* confirmation state
* recency
* expiration/decay metadata

### Important

Memory is not automatically truth.

### Acceptance

A memory can be:

* proposed
* confirmed
* corrected
* weakened
* expired

---

# Sprint V1.7 — User Corrections

## Objective

Allow the user to correct IronMind's understanding.

### Scope

Support corrections to:

* goals
* commitments
* memories
* inferred information where applicable

### Principle

A user correction is high-value evidence.

### Acceptance

Corrected information takes precedence over stale assumptions where the contracts require it.

---

# Sprint V1.8 — Local Search / Retrieval

## Objective

Provide deterministic retrieval of historical information.

### Scope

Support retrieval by:

* goal
* date
* commitment
* reflection
* memory
* event

### Forbidden

Do not build semantic AI retrieval yet unless explicitly approved as part of a separate sprint.

### Acceptance

The system can reliably retrieve relevant known information.

---

# Sprint V1.9 — Firebase Authentication Foundation

## Objective

Introduce optional account identity for future sync.

### Scope

Implement the minimum authentication architecture required for remote persistence.

### Principle

Authentication must not become a requirement for basic local use unless product requirements explicitly change.

### Acceptance

Local core functionality remains usable independently.

---

# Sprint V1.10 — Firestore Sync Foundation

## Objective

Introduce controlled remote synchronization.

### Scope

Implement:

* remote repository interfaces
* sync state
* outbox
* idempotency
* basic conflict strategy
* controlled uploads/downloads

### Required

Offline-first behavior remains intact.

### Tests

Test:

* offline creation
* reconnect
* upload
* duplicate retry
* conflict
* partial failure

### Acceptance

Network loss does not destroy core functionality.

---

# V1 EXIT GATE

V1 is complete when:

* history exists
* events exist
* reflections persist
* voice input works through an abstraction
* user corrections work
* memory can be represented responsibly
* local use remains offline-capable
* basic cloud sync is reliable where enabled

Required checkpoint:

```text
CHECKPOINT: V1_MEMORY_HISTORY_COMPLETE
```

Commit before V2.

---

# 10. V2 — INTELLIGENCE

## Objective

Introduce AI and pattern understanding without granting AI execution authority.

The critical architecture becomes:

```text
OBSERVATIONS
      ↓
MEMORY / PATTERNS
      ↓
AI REASONING
      ↓
RECOMMENDATION
      ↓
DECISION ENGINE
```

During V2, autonomous execution is still limited.

---

# Sprint V2.1 — Observation Framework

## Objective

Create the standard observation abstraction.

### Scope

Implement:

```text
Observation
ObservationSource
ObservationAdapter
ObservationRepository
```

### Principle

An observation describes what happened.

It does not explain why.

### Acceptance

New observation sources can be added without rewriting the domain.

---

# Sprint V2.2 — Context Engine Foundation

## Objective

Combine known information into current context.

Potential context dimensions:

* current time
* day
* active commitment
* recent action
* recent reflection
* protection session
* goal relevance
* current application state

### Acceptance

The system can generate a deterministic current context snapshot.

---

# Sprint V2.3 — Pattern Engine

## Objective

Detect repeated behavioral patterns without identity labeling.

### Examples

```text
Repeated postponement
Repeated late starts
Certain tasks repeatedly abandoned
Certain conditions associated with better execution
Certain interventions repeatedly ignored
```

### Pattern requirements

Each pattern must include:

* evidence
* confidence
* recency
* decay/expiration
* provenance

### Forbidden

Do not create identity labels such as:

```text
lazy
undisciplined
anxious person
procrastinator
```

### Acceptance

Patterns describe behavior, not identity.

---

# Sprint V2.4 — AI Abstraction

## Objective

Create the provider-independent AI reasoning interface.

### Required conceptual interface

```text
IronMindAI
```

The exact Kotlin API may vary.

### Responsibilities

AI may:

* summarize
* classify
* extract
* plan
* recommend
* reason
* propose memory candidates
* propose patterns

AI may not:

* directly execute Android actions
* directly modify protected domain state
* change autonomy
* silently overwrite memory truth

---

# Sprint V2.5 — AI Provider Adapter

## Objective

Connect the first approved AI provider.

### Scope

Implement:

```text
IronMindAI
    ↓
Provider Adapter
    ↓
Model
```

### Required

Keep provider-specific logic isolated.

### Required controls

* model/version tracking
* prompt/version tracking
* structured output validation
* failure handling
* timeout
* retry policy
* offline fallback

### Acceptance

Provider replacement would not require rewriting product logic.

---

# Sprint V2.6 — Natural Language Intent Extraction

## Objective

Allow users to express intentions naturally.

Examples:

> “I want to finish the business proposal this week.”

> “Tomorrow I need to prepare the presentation.”

> “I kept postponing this because I didn't know where to start.”

AI should produce structured candidates.

### Required flow

```text
USER INPUT
→ AI EXTRACTION
→ VALIDATION
→ USER/DOMAIN CONFIRMATION WHERE REQUIRED
→ DOMAIN STATE
```

### Critical rule

AI output does not automatically become domain truth.

---

# Sprint V2.7 — Reflection Understanding

## Objective

Use AI to extract useful information from text/voice reflection.

### Possible outputs

* events
* barriers
* outcomes
* reasons supplied by the user
* candidate memories
* candidate patterns
* next-action suggestions

### Principle

Preserve:

```text
original reflection
+
AI interpretation
```

separately.

### Acceptance

AI can enrich reflection without destroying the source.

---

# Sprint V2.8 — AI Planning

## Objective

Allow AI to recommend practical breakdowns.

### Scope

AI can propose:

```text
Goal
→ smaller plan
→ tasks
→ next action
```

### Requirements

Recommendations must consider:

* current commitments
* time constraints
* user-confirmed goals
* known context
* uncertainty
* realistic workload

### Forbidden

AI cannot silently commit the user to high-impact actions.

---

# Sprint V2.9 — Barrier Understanding

## Objective

Model barriers as contextual possibilities.

Potential barrier categories:

* uncertainty
* distraction
* fear
* boredom
* lack of clarity
* environmental friction
* low energy
* excessive task size
* competing priorities
* scheduling mismatch

### Critical rule

A barrier is a hypothesis unless explicitly confirmed.

### Acceptance

IronMind can say, conceptually:

```text
“I've noticed X happens often before Y.
Could that be part of what is getting in the way?”
```

rather than:

```text
“You procrastinate because you are afraid.”
```

---

# Sprint V2.10 — Intervention Recommendation Engine

## Objective

Generate possible interventions without yet granting broad autonomy.

### Candidate types

```text
REMIND
REDIRECT
PROTECT
BREAK_DOWN
REASSURE
CHALLENGE
ASK
RECOVER
RESCHEDULE
REFLECT
CELEBRATE
STAY_SILENT
```

### Output

The engine recommends:

```text
candidate intervention
+
reason
+
confidence
+
supporting context
```

It does not directly execute.

---

# Sprint V2.11 — Suggestion-Only Intelligence UI

## Objective

Make V2 intelligence visible while preserving user control.

### Example conceptual experience

```text
IronMind noticed:
You have postponed this commitment three times.

Possible next step:
Break it into a 15-minute action.

Why:
The previous larger commitment was repeatedly postponed.
```

### Acceptance

The user can:

* accept
* reject
* correct
* ignore

and the result is recorded.

---

# V2 EXIT GATE

V2 is complete when IronMind can:

```text
observe
→ understand history
→ identify patterns
→ reason with AI
→ propose actions
→ explain uncertainty
→ learn from correction
```

But:

```text
AI ≠ executor
AI ≠ autonomy authority
```

Required checkpoint:

```text
CHECKPOINT: V2_INTELLIGENCE_COMPLETE
```

Commit before V3.

---

# 11. V3 — CONTROLLED AUTONOMY

## Objective

Move from:

```text
AI recommends
```

to:

```text
Policy decides
→ approved autonomy executes
```

This is the point where `AUTONOMY_POLICY.md` becomes operational.

---

# Sprint V3.1 — Autonomy Settings

## Objective

Implement capability-specific autonomy controls.

### Levels

```text
OFF
SUGGEST_ONLY
ASK_BEFORE_ACTION
FULL_AUTO
```

### Capabilities

At minimum:

* planning
* scheduling
* protection
* proactive notifications
* background learning
* goal resurfacing
* reflection processing
* memory/pattern processing

### Acceptance

Changing one capability's autonomy does not unintentionally change another.

---

# Sprint V3.2 — Decision Engine

## Objective

Introduce the authoritative decision layer.

### Decision flow

```text
Candidate
→ autonomy enabled?
→ capability level?
→ current context?
→ permission?
→ user override?
→ cooldown?
→ duplicate?
→ safe?
→ reversible?
→ execute or STAY_SILENT
```

### Principle

This is the authority boundary.

AI recommendation must pass through policy.

---

# Sprint V3.3 — Decision Record

## Objective

Make autonomous decisions inspectable.

### Record

Where appropriate:

* decision ID
* timestamp
* action
* trigger
* source
* context
* policy
* autonomy level
* reasoning summary
* confidence
* result
* failure
* user response

### Acceptance

A significant autonomous action can be explained after the fact.

---

# Sprint V3.4 — Proactive Notification Engine

## Objective

Allow IronMind to initiate interventions under policy control.

### Scope

Implement:

* notification candidate creation
* priority
* channel selection
* quiet periods
* cooldown
* deduplication
* suppression
* cancellation
* delivery tracking

### Critical principle

Notification generation does not guarantee notification delivery.

The system must be capable of choosing:

```text
STAY_SILENT
```

---

# Sprint V3.5 — Auto Scheduling

## Objective

Allow approved autonomous scheduling.

### Requirements

Scheduling must respect:

* existing commitments
* user constraints
* available time
* workload
* current intent
* autonomy level

### Required

Before execution, revalidate current state.

### Acceptance

Stale AI recommendations cannot blindly create obsolete schedules.

---

# Sprint V3.6 — Auto Protection

## Objective

Allow bounded automatic protection.

### Requirements

Protection must be:

* contextual
* time-bound
* user-authorized
* reversible
* permission-aware
* observable

### Forbidden

No unlimited device control.

No silent expansion of protected targets.

---

# Sprint V3.7 — Intervention Execution Pipeline

## Objective

Implement the full intervention lifecycle.

```text
CANDIDATE
→ PROPOSED
→ APPROVED
→ TRIGGERED
→ DELIVERED
→ RESPONSE
→ OUTCOME
```

Additional states:

```text
SUPPRESSED
CANCELLED
EXPIRED
FAILED
```

### Acceptance

Ignored, dismissed, overridden, technical failure, and behavioral failure remain distinguishable.

---

# Sprint V3.8 — Intervention Cooldowns and Escalation

## Objective

Prevent notification and intervention abuse.

### Scope

Implement:

* cooldowns
* dedupe
* interruption budget
* bounded escalation
* suppression
* one-primary-intervention rule

### Critical principle

Escalation must never become punishment.

---

# Sprint V3.9 — Goal Resurfacing

## Objective

Allow IronMind to detect neglected goals.

### Concept

Example:

```text
“You mentioned this goal 37 days ago.
You haven't worked on it recently.
Is it still important?”
```

### Requirements

The system must consider:

* goal age
* recent activity
* explicit user changes
* evidence
* current priorities
* recency
* confidence

### Critical rule

A neglected goal is not necessarily a failed goal.

---

# Sprint V3.10 — Autonomous Reflection Processing

## Objective

Allow approved autonomy around reflection synchronization.

### Example flow

```text
Day closes
→ summary generated
→ user speaks
→ transcript stored
→ AI processes
→ candidates generated
→ policy/confirmation applied
→ memory/pattern updated
```

### Acceptance

Original user input remains preserved.

---

# Sprint V3.11 — Background Processing

## Objective

Introduce appropriate background work.

### Scope

Use appropriate Android background mechanisms for:

* event processing
* reflection processing
* sync
* pattern updates
* scheduled intervention evaluation

### Requirements

Jobs must be:

* idempotent
* cancellable
* retry-safe
* observable
* permission-aware

### Acceptance

Background execution failure does not corrupt domain state.

---

# Sprint V3.12 — Global Pause / Emergency Control

## Objective

Provide an obvious mechanism to stop autonomous behavior.

### Scope

Global autonomy pause concept.

### Requirements

The control must be easy to locate and reliable.

### Acceptance

Active autonomous behavior can be stopped without uninstalling IronMind.

---

# V3 EXIT GATE

V3 is complete when IronMind can:

```text
observe
→ reason
→ recommend
→ apply policy
→ execute approved autonomous action
→ explain it
→ observe outcome
→ learn from outcome
```

And when:

* autonomy is capability-specific
* user control exists
* actions are bounded
* actions are reversible where applicable
* significant actions are explainable
* failures are safe
* silence is possible
* background work is reliable

Required checkpoint:

```text
CHECKPOINT: V3_CONTROLLED_AUTONOMY_COMPLETE
```

Commit before V4.

---

# 12. V4 — DEEP ADAPTATION

## Objective

Move from a system that understands IronMind history to a system that adapts continuously to the user's changing real-world context.

V4 is the beginning of the deeper long-term vision.

This phase must be implemented carefully because data volume, privacy impact, false inference risk, and autonomy complexity increase significantly.

---

# Sprint V4.1 — App Usage Observation

## Objective

Add broader behavioral context from approved Android capabilities.

Potential information:

* application usage
* focus/distraction transitions
* usage timing
* repeated distraction patterns

### Principle

Observe behavior, not identity.

### Required

User controls must define whether this observation is enabled.

---

# Sprint V4.2 — Notification Context

## Objective

Use approved notification-related context where technically and permission-wise appropriate.

### Purpose

Understand interruption conditions.

### Forbidden

Do not treat every notification as a meaningful behavioral signal.

### Acceptance

Notification observations are filtered and purpose-driven.

---

# Sprint V4.3 — Calendar Context

## Objective

Use calendar constraints when enabled.

Potential context:

* upcoming event
* free time
* time conflicts
* schedule pressure

### Acceptance

IronMind can reason about time reality rather than scheduling into fictional availability.

---

# Sprint V4.4 — Location / Environment Context

## Objective

Introduce contextual environment information only where useful and properly authorized.

Potential use:

```text
home
work
travel
usual location
specific context
```

### Critical principle

Context must not become surveillance.

Collect only what provides meaningful product value.

---

# Sprint V4.5 — Activity / Energy Context

## Objective

Allow appropriate activity or energy-related context where technically feasible and explicitly enabled.

### Principle

No health claims.

No diagnosis.

No unsupported psychological conclusions.

### Acceptance

Context may inform decisions but must not be represented as medical truth.

---

# Sprint V4.6 — Personal Context Engine

## Objective

Combine multiple observations into a coherent current context.

Potential dimensions:

```text
TIME
LOCATION
CALENDAR
CURRENT COMMITMENT
RECENT BEHAVIOR
RECENT REFLECTION
APP CONTEXT
HISTORY
PATTERNS
USER INTENT
```

### Principle

Current explicit intent has higher authority than older inferred patterns.

---

# Sprint V4.7 — Intervention Learning

## Objective

Learn which interventions tend to help under which conditions.

### Examples

```text
Reminder successful
Breakdown successful
Protection ignored
Challenge rejected
Reschedule repeatedly accepted
Silence better than interruption
```

### Critical distinction

Do not learn from technical failures as if they were behavioral failures.

---

# Sprint V4.8 — Adaptive Timing

## Objective

Learn better intervention timing.

The system may learn:

* useful time windows
* bad interruption periods
* response patterns
* context-dependent timing

### Constraint

Learning timing must not increase interruption frequency without policy authorization.

---

# Sprint V4.9 — Adaptive Channel Selection

## Objective

Choose the least intrusive effective intervention channel.

Potential channels include:

* in-app
* notification
* protection
* scheduling suggestion
* other approved execution channels

### Principle

Channel selection optimizes usefulness, not app engagement.

---

# Sprint V4.10 — Experimental Learning

## Objective

Allow controlled experimentation where useful.

Example:

```text
Try shorter commitment
vs.
Try larger planned block
```

Compare real outcomes.

### Requirements

Experiments must be:

* bounded
* explainable
* reversible
* user-respectful
* non-manipulative

IronMind must not covertly experiment on users.

---

# Sprint V4.11 — Personal Model Evolution

## Objective

Allow the personal model to evolve continuously.

The model should incorporate:

```text
current user intent
+
confirmed information
+
recent behavior
+
strong evidence
+
stable patterns
+
intervention outcomes
```

Older weak assumptions must decay.

### Acceptance

The personal model can change when reality changes.

---

# Sprint V4.12 — Reliability and Resilience Hardening

## Objective

Prepare the architecture for sustained autonomous operation.

### Scope

Harden:

* offline mode
* sync
* background jobs
* retries
* persistence
* migrations
* permission loss
* AI outages
* provider outages
* battery constraints
* duplicate events
* duplicate interventions
* stale decisions
* process death
* clock changes

### Acceptance

IronMind fails safely rather than silently corrupting state.

---

# Sprint V4.13 — Data Export / Deletion Foundation

## Objective

Implement responsible long-term data management.

### Scope

Support appropriate:

* export
* deletion
* data lifecycle
* memory cleanup
* learned-pattern cleanup

### Principle

User data must remain manageable.

---

# Sprint V4.14 — Development Control Center

## Objective

Complete the development-only observability/control surface.

### It may expose

```text
CURRENT STATE
EVENTS
OBSERVATIONS
MEMORIES
PATTERNS
AI REQUESTS
AI RESPONSES
DECISIONS
INTERVENTIONS
BACKGROUND JOBS
SYNC
PERMISSIONS
AUTONOMY SETTINGS
PROTECTION
ERRORS
```

### Critical rule

The Control Center is a development/debugging facility.

It must not become the normal user experience.

---

# V4 EXIT GATE

V4 is complete only when the system can adapt using broader context while retaining:

* user agency
* bounded autonomy
* evidence-based learning
* explainability
* reversibility
* privacy controls
* reliable background behavior
* offline resilience
* data lifecycle controls

Required checkpoint:

```text
CHECKPOINT: V4_DEEP_ADAPTATION_COMPLETE
```

---

# 13. IMPLEMENTATION DEPENDENCY GRAPH

The major dependency order is:

```text
Fresh Project
    ↓
Architecture
    ↓
Logging
    ↓
Infrastructure
    ↓
Database
    ↓
Domain Models
    ↓
Commitment State
    ↓
Core UI
    ↓
Outcome
    ↓
Protection
    ↓
Scheduling
    ↓
Reflection
    ↓
Events
    ↓
History
    ↓
Voice
    ↓
Memory
    ↓
Observation
    ↓
Context
    ↓
Patterns
    ↓
AI Abstraction
    ↓
AI Provider
    ↓
AI Extraction
    ↓
AI Planning
    ↓
Intervention Recommendation
    ↓
Autonomy Policy
    ↓
Decision Engine
    ↓
Intervention Execution
    ↓
Automatic Protection / Scheduling
    ↓
Goal Resurfacing
    ↓
Broader Context
    ↓
Adaptive Learning
```

This ordering should normally not be bypassed.

---

# 14. WHAT MUST NOT BE BUILT EARLY

The following should not be introduced in the foundation or early V0 simply because they sound impressive:

```text
Advanced AI
Autonomous planning
Autonomous notifications
Automatic app blocking
Complex behavioral scoring
Psychological profiling
Gamification
Leaderboards
Social features
Large analytics dashboards
Complex achievements
AI chat as the primary interface
Unbounded background services
Large third-party dependency stacks
Cloud-first architecture
Opaque recommendation systems
```

IronMind must earn complexity through demonstrated product need.

---

# 15. FEATURE INTRODUCTION RULE

Every new feature must answer:

### 1. Which real problem does it solve?

### 2. Which IronMind role does it strengthen?

```text
Mirror
Assistant
Shield
Learner
```

### 3. What real-life behavior should change?

### 4. What data does it require?

### 5. Is the data necessary?

### 6. Is the behavior deterministic, learned, or AI-reasoned?

### 7. What autonomy level applies?

### 8. What happens when the system is wrong?

### 9. Can the user override it?

### 10. Can it fail safely?

If these questions are unanswered, the feature is not ready for implementation.

---

# 16. AI CODING AGENT OPERATING RULE

When using an AI coding agent such as Gemini Antigravity, every sprint should be issued as a narrowly scoped implementation request.

The agent must be told:

```text
READ THE CONTRACTS FIRST.

SCAN THE CURRENT REPOSITORY FIRST.

DO NOT MODIFY UNRELATED AREAS.

DO NOT INVENT FEATURES.

DO NOT REFACTOR OUTSIDE SCOPE.

DO NOT CHANGE PRODUCT BEHAVIOR WITHOUT AUTHORIZATION.

DO NOT CHANGE AUTONOMY LEVELS.

DO NOT ADD DEPENDENCIES WITHOUT JUSTIFICATION.

IMPLEMENT ONLY THIS SPRINT.

WRITE TESTS.

RUN TESTS.

VERIFY LOGGING.

REVIEW GIT DIFF.

REPORT EXACTLY WHAT CHANGED.

STOP WHEN THE SPRINT IS COMPLETE.
```

---

# 17. STANDARD ANTIGRAVITY SPRINT TEMPLATE

Use this format for each implementation sprint.

```text
You are implementing one atomic IronMind sprint.

AUTHORITATIVE DOCUMENTS:
- IRONMIND_MASTER_BLUEPRINT.md
- PRODUCT_CONSTITUTION.md
- SYSTEM_ARCHITECTURE.md
- DATA_CONTRACT.md
- AI_BEHAVIOR_CONTRACT.md
- AUTONOMY_POLICY.md
- INTERVENTION_RULES.md
- DEVELOPMENT_RULES.md
- IRONMIND_IMPLEMENTATION_ROADMAP.md

SPRINT:
[SPRINT ID]

OBJECTIVE:
[ONE CLEAR OBJECTIVE]

SCOPE:
[EXACT FUNCTIONALITY TO IMPLEMENT]

ALLOWED AREAS:
[FILES / PACKAGES / MODULES]

FORBIDDEN AREAS:
[FILES / PACKAGES / FEATURES THAT MUST NOT BE MODIFIED]

DATA CHANGES:
[ENTITIES / FIELDS / MIGRATIONS]

DOMAIN CHANGES:
[STATE / RULES / USE CASES]

UI CHANGES:
[SCREENS / COMPONENTS]

ARCHITECTURE CHANGES:
[ONLY IF REQUIRED]

LOGGING:
Use:
IronMindLifecycle [Component] [EVENT] key=value...

TESTS REQUIRED:
[EXACT TESTS]

ACCEPTANCE CRITERIA:
[EXACT CONDITIONS]

FAILURE HANDLING:
[EXPECTED FAILURE BEHAVIOR]

Before editing:
1. Read the authoritative documents.
2. Inspect git status.
3. Scan the existing implementation.
4. Explain the intended implementation plan.

During implementation:
- Modify only the approved scope.
- Do not invent additional features.
- Do not perform unrelated refactors.
- Preserve user changes.
- Keep architecture boundaries intact.

After implementation:
1. Run relevant tests.
2. Run build/compile checks.
3. Verify lifecycle logs.
4. Inspect git diff.
5. Report every changed file.
6. Report tests executed and results.
7. Report any unresolved issue.
8. STOP.

Do not commit unless explicitly instructed.
```

---

# 18. GIT CHECKPOINT STRATEGY

Git is part of the development safety system.

Recommended progression:

```text
main
 |
 +-- foundation checkpoint
 |
 +-- V0 checkpoints
 |
 +-- V1 checkpoints
 |
 +-- V2 checkpoints
 |
 +-- V3 checkpoints
 |
 +-- V4 checkpoints
```

Every completed sprint should produce a reviewable diff.

Preferred pattern:

```text
one sprint
→ one focused commit
```

Large combined commits should be avoided.

---

# 19. REQUIRED PRE-SPRINT CHECK

Before every sprint:

```text
git status
git branch
git log --oneline -n 10
```

The agent must understand the current repository state before changing it.

It must never assume that the working tree is clean.

---

# 20. REQUIRED POST-SPRINT CHECK

After every sprint:

```text
git diff
git status
tests
build
```

The agent must verify:

* only intended files changed
* no debug code was accidentally left behind
* no accidental dependency changes
* no manifest changes outside scope
* no permission changes outside scope
* no autonomy changes outside scope

---

# 21. LOGGING CHECKPOINT

Every sprint involving state changes must answer:

```text
What happened?
When did it happen?
Which component caused it?
What state changed?
Was it successful?
Did it fail?
Why did it fail?
```

Important events should use:

```text
IronMindLifecycle [Component] [EVENT] key=value...
```

Examples:

```text
IronMindLifecycle [Commitment] [CREATED] commitmentId=...
IronMindLifecycle [Commitment] [STATE_CHANGED] from=COMMITTED to=STARTED
IronMindLifecycle [Protection] [STARTED] sessionId=...
IronMindLifecycle [AI] [REQUEST_FAILED] reason=timeout
IronMindLifecycle [Decision] [SUPPRESSED] reason=cooldown
IronMindLifecycle [Intervention] [DELIVERED] type=REMIND
```

Never log sensitive raw personal content unnecessarily.

---

# 22. TESTING STRATEGY BY PHASE

## Phase 0

Focus on:

* architecture
* infrastructure
* database
* migrations
* logging

## V0

Focus on:

* domain state
* persistence
* UI state
* commitment transitions
* protection
* scheduling
* real-device behavior

## V1

Focus on:

* events
* history
* voice
* memory
* sync
* conflict handling

## V2

Focus on:

* structured AI output
* uncertainty
* pattern detection
* retrieval
* AI failures
* AI provider abstraction

## V3

Focus on:

* autonomy policy
* decision correctness
* intervention suppression
* permission behavior
* background execution
* stale-state protection
* reversibility

## V4

Focus on:

* privacy
* reliability
* adaptation
* observation correctness
* learning correctness
* long-running behavior
* resource usage

---

# 23. REAL-DEVICE VALIDATION RULE

Android system integration cannot be considered complete from compilation alone.

The following require physical-device validation:

```text
Notifications
Background execution
Scheduling
Protection
Permissions
App usage observation
Notification observation
Location
Speech input
Battery/background constraints
System interruptions
```

An emulator test may supplement but does not replace real-device validation where hardware/system behavior matters.

---

# 24. OFFLINE-FIRST REQUIREMENT

The following must remain functional without full network/AI availability:

```text
Goals
Plans
Tasks
Commitments
Completion
Postponement
Missed state
Outcomes
Basic reflection
Core local history
Core protection where technically possible
```

When AI is unavailable:

```text
Do not invent AI results.
Do not block core product behavior.
Fall back to deterministic behavior where possible.
```

---

# 25. FAILURE MODE PRIORITY

IronMind must prioritize avoiding the following failures:

## Severity 1 — Critical

```text
Incorrect user data
Unauthorized autonomous action
Silent data loss
Privacy breach
Broken user override
Corrupted commitment state
```

## Severity 2 — High

```text
Wrong intervention
Repeated unwanted notifications
Incorrect learned pattern treated as fact
Broken protection
Background process causing incorrect state
AI hallucination becoming domain truth
```

## Severity 3 — Medium

```text
Poor recommendation
Incorrect timing
Sync delay
UI inconsistency
```

## Severity 4 — Low

```text
Visual polish
Minor convenience
Non-critical performance optimization
```

The roadmap prioritizes Severity 1 and Severity 2 failures over visual sophistication.

---

# 26. DEFINITION OF DONE

A sprint is not complete merely because the application compiles.

A sprint is complete only when:

```text
IMPLEMENTED
+
TESTED
+
LOGGED
+
REVIEWED
+
ACCEPTANCE CRITERIA PASSED
```

For system integrations:

```text
+
REAL DEVICE VERIFIED
```

For autonomy features:

```text
+
POLICY VERIFIED
+
OVERRIDE VERIFIED
+
FAIL-SAFE VERIFIED
```

For AI features:

```text
+
STRUCTURED OUTPUT VALIDATED
+
UNCERTAINTY HANDLED
+
FAILURE HANDLED
+
NO DIRECT EXECUTION PATH
```

---

# 27. HUMAN APPROVAL GATES

The following require explicit human review before moving forward:

### Architecture

Any change affecting:

* layer boundaries
* data ownership
* AI abstraction
* repository strategy
* execution architecture

### Data

Any:

* new sensitive field
* major entity
* retention change
* observation source

### Autonomy

Any:

* new automatic action
* increase in authority
* new system permission
* new autonomous intervention

### Privacy

Any:

* new personal data source
* broader observation
* location
* notification access
* health/activity information

### AI

Any change that makes AI:

* more authoritative
* more autonomous
* more persistent
* able to influence higher-impact actions

---

# 28. ROADMAP STOP CONDITIONS

Implementation must pause when:

```text
architecture becomes unclear
data ownership becomes ambiguous
autonomy authority is undefined
AI output cannot be validated
user control is unclear
tests cannot verify the feature
background behavior is nondeterministic
permissions are insufficient
privacy implications are unresolved
```

The correct response is to resolve the contract or architecture.

The correct response is not to “just implement something.”

---

# 29. WHAT THE ROADMAP IS OPTIMIZING FOR

The roadmap deliberately does not optimize for:

```text
number of screens
number of AI features
number of notifications
number of integrations
app engagement
feature count
visual complexity
```

It optimizes for:

```text
real-world action
reliable execution
correct understanding
useful intervention
user control
explainability
adaptation
recovery
trust
```

---

# 30. FINAL IMPLEMENTATION SEQUENCE

The complete intended build sequence is:

```text
PHASE 0
Fresh Android Project
→ Architecture
→ Logging
→ Infrastructure
→ Room
→ Testing
→ Application Shell

V0
User Profile
→ Goals
→ Plans
→ Tasks
→ Commitments
→ Today
→ Outcomes
→ Protection
→ Scheduling
→ Reflection

V1
Events
→ History
→ Reflection Improvements
→ Speech Abstraction
→ Voice
→ Memory
→ Corrections
→ Retrieval
→ Authentication
→ Sync

V2
Observations
→ Context
→ Patterns
→ AI Abstraction
→ AI Provider
→ Intent Extraction
→ Reflection Understanding
→ AI Planning
→ Barrier Understanding
→ Intervention Recommendations
→ Suggestion-Only Intelligence

V3
Autonomy Settings
→ Decision Engine
→ Decision Records
→ Proactive Notifications
→ Auto Scheduling
→ Auto Protection
→ Intervention Pipeline
→ Cooldowns
→ Goal Resurfacing
→ Autonomous Reflection Processing
→ Background Processing
→ Global Pause

V4
App Usage Context
→ Notification Context
→ Calendar Context
→ Environment Context
→ Activity/Energy Context
→ Personal Context
→ Intervention Learning
→ Adaptive Timing
→ Adaptive Channels
→ Controlled Experiments
→ Personal Model Evolution
→ Resilience
→ Data Lifecycle
→ Development Control Center
```

---

# 31. FINAL PRODUCT MATURITY MODEL

IronMind should evolve through the following maturity levels:

```text
LEVEL 0
IronMind records what the user wants.

LEVEL 1
IronMind helps the user turn intent into commitments.

LEVEL 2
IronMind remembers what actually happened.

LEVEL 3
IronMind recognizes recurring behavioral patterns.

LEVEL 4
IronMind reasons about what may help next.

LEVEL 5
IronMind can recommend interventions.

LEVEL 6
IronMind can execute bounded interventions under policy.

LEVEL 7
IronMind learns which interventions work under which conditions.

LEVEL 8
IronMind continuously adapts while respecting user control.
```

The system must earn each level.

---

# 32. FINAL ROADMAP RULE

Never skip the boring layer because the intelligent layer looks more exciting.

The sequence is intentional:

```text
TRUSTWORTHY DATA
→ RELIABLE STATE
→ USEFUL ACTION
→ REAL HISTORY
→ EVIDENCE
→ INTELLIGENCE
→ POLICY
→ AUTONOMY
→ ADAPTATION
```

IronMind should not begin by pretending to understand the user.

It should first become capable of accurately recording what the user intended, what they committed to, what they actually did, what happened afterward, and what they themselves said about it.

Only then should the system begin learning.

Only after learning becomes reliable should the system begin acting autonomously.

And even then:

```text
AI recommends.
Policy decides.
The system executes.
Reality provides the feedback.
```

That is the implementation path for IronMind.

```

This should be the **authoritative build-order document** for the project. The key point is that Antigravity should not receive the entire roadmap as one coding task; it should receive **one sprint from this roadmap at a time**, with the contracts included as its permanent constraints.
```
