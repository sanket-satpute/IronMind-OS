`DEVELOPMENT_RULES.md`

````md
# IRONMIND DEVELOPMENT RULES

**Document:** `DEVELOPMENT_RULES.md`  
**Status:** AUTHORITATIVE ENGINEERING + AI CODING CONTRACT  
**Parent Documents:**
- `IRONMIND_MASTER_BLUEPRINT.md`
- `PRODUCT_CONSTITUTION.md`
- `SYSTEM_ARCHITECTURE.md`
- `DATA_CONTRACT.md`
- `AI_BEHAVIOR_CONTRACT.md`
- `AUTONOMY_POLICY.md`
- `INTERVENTION_RULES.md`

**Purpose:** Define exactly how IronMind is developed, tested, reviewed, committed, and changed, with special controls for AI coding agents such as Gemini Antigravity.

---

# 1. PURPOSE

IronMind will be developed incrementally.

The primary engineering risk is not only bugs.

A major risk is:

> **Architectural and product drift caused by broad AI-generated changes.**

An AI coding agent can easily:

- modify unrelated files
- "improve" unrelated features
- rewrite architecture
- add unnecessary dependencies
- change product behavior
- change autonomy
- remove controls
- alter state models
- break historical behavior
- introduce hidden coupling
- make large changes that are difficult to review

Therefore this document establishes strict development rules.

---

# 2. DEVELOPMENT NORTH STAR

The goal is:

> **Make small, correct, observable, testable changes while preserving the existing product and architectural contracts.**

---

# 3. CORE DEVELOPMENT LOOP

Every meaningful development task should follow:

```text
READ
 ↓
SCAN
 ↓
UNDERSTAND
 ↓
PLAN
 ↓
IMPLEMENT
 ↓
TEST
 ↓
REVIEW DIFF
 ↓
VERIFY LOGS
 ↓
REPORT
 ↓
COMMIT
````

No step should be casually skipped.

---

# 4. PRODUCT-FIRST DEVELOPMENT

Source code does not define IronMind's product meaning.

The authoritative hierarchy is:

```text
IRONMIND_MASTER_BLUEPRINT.md
        ↓
PRODUCT_CONSTITUTION.md
        ↓
SYSTEM_ARCHITECTURE.md
        ↓
DATA_CONTRACT.md
        ↓
AI_BEHAVIOR_CONTRACT.md
        ↓
AUTONOMY_POLICY.md
        ↓
INTERVENTION_RULES.md
        ↓
DEVELOPMENT_RULES.md
        ↓
SOURCE CODE
```

---

# 5. DOCUMENT HIERARCHY

If implementation disagrees with an authoritative contract:

> **Do not silently modify the contract or implementation.**

First determine whether:

1. the implementation is wrong, or
2. the requirement has intentionally changed.

---

# 6. AI CODING AGENT ROLE

An AI coding agent is an implementation tool.

It is NOT:

* product owner
* architecture owner
* autonomous feature designer
* policy authority

The coding agent must implement the requested scope.

---

# 7. AI CODING AGENT MUST READ FIRST

Before making meaningful changes, the agent must read:

```text
IRONMIND_MASTER_BLUEPRINT.md
PRODUCT_CONSTITUTION.md
SYSTEM_ARCHITECTURE.md
DATA_CONTRACT.md
AI_BEHAVIOR_CONTRACT.md
AUTONOMY_POLICY.md
INTERVENTION_RULES.md
DEVELOPMENT_RULES.md
```

For a narrow task, the agent may read only the relevant sections after the initial project setup, but it must understand the applicable contracts.

---

# 8. AI AGENT MUST SCAN THE CODEBASE

Before modifying architecture or non-trivial behavior, inspect:

* current package structure
* relevant source files
* existing tests
* Gradle configuration
* manifest if relevant
* current dependencies
* current implementation
* current logs
* current behavior

Do not start coding from assumptions.

---

# 9. NEVER BUILD FROM MEMORY

The agent must not assume:

> "I know how this project is structured."

Always inspect the actual repository.

---

# 10. SPRINT-FIRST DEVELOPMENT

All meaningful implementation must happen through a defined sprint or task.

A sprint must identify:

```text
OBJECTIVE
IN SCOPE
OUT OF SCOPE
ALLOWED AREAS
FORBIDDEN AREAS
EXPECTED BEHAVIOR
DATA IMPACT
ARCHITECTURE IMPACT
TEST REQUIREMENTS
LOGGING REQUIREMENTS
ACCEPTANCE CRITERIA
```

---

# 11. NO "BUILD EVERYTHING"

Never give an AI coding agent a vague instruction such as:

```text
Build IronMind.
```

or:

```text
Finish the whole app.
```

Prefer:

```text
Implement Goal creation and local persistence.
Do not modify Commitments, AI, Backend, Protection, or Navigation outside the required path.
```

---

# 12. SPRINT SIZE

Prefer small, independently verifiable changes.

Good:

```text
Implement Goal entity + repository + create flow.
```

Bad:

```text
Build Goals + Commitments + AI + Backend + Notifications + Protection.
```

---

# 13. ONE PRIMARY OBJECTIVE

Each sprint should have one primary objective.

Secondary work should be explicitly listed.

---

# 14. OUT OF SCOPE IS ENFORCEABLE

"Out of scope" means:

> Do not modify it unless technically necessary and explicitly reported.

---

# 15. ALLOWED FILES

Where practical, sprint instructions should identify allowed directories/files.

Example:

```text
Allowed:
domain/goal/**
data/goal/**
feature/goals/**

Protected:
domain/commitment/**
intelligence/**
ai/**
backend/**
```

---

# 16. PROTECTED AREAS

A sprint may explicitly mark areas that must not change.

Examples:

```text
Autonomy
AI
Intervention
Backend
Database migrations
Manifest
Gradle
```

---

# 17. TECHNICAL NECESSITY EXCEPTION

Sometimes an out-of-scope file must change to complete the requested work.

If so, the agent must:

```text
1. Identify the file.
2. Explain why it must change.
3. Make the smallest required change.
4. Report it.
```

It must not silently expand scope.

---

# 18. NO UNRELATED REFACTORING

If the agent notices ugly code outside the sprint:

> Do not automatically refactor it.

Report it as a follow-up.

---

# 19. NO "WHILE I'M HERE" CHANGES

Forbidden pattern:

```text
Implement feature A
+
rewrite unrelated feature B
+
rename feature C
+
modernize Gradle
+
change UI theme
```

without scope.

---

# 20. NO ARCHITECTURE DRIFT

The agent must not casually replace:

* repository architecture
* state management
* database architecture
* event model
* AI boundary
* intervention engine
* autonomy model
* package architecture

because a different pattern seems cleaner.

---

# 21. ARCHITECTURAL CHANGE REQUIREMENT

Any meaningful architecture change must explicitly state:

```text
WHY
WHAT CHANGES
WHY CURRENT DESIGN IS INSUFFICIENT
RISKS
MIGRATION IMPACT
TEST PLAN
```

---

# 22. ARCHITECTURAL CHANGE APPROVAL

If the architecture change affects a major boundary, do not combine it with an unrelated feature implementation unless necessary.

Prefer a dedicated architecture sprint.

---

# 23. EXAMPLES OF MAJOR ARCHITECTURAL CHANGES

These require explicit review:

```text
Changing Room architecture
Changing backend provider
Changing authentication
Changing event architecture
Changing AI abstraction
Changing autonomy model
Changing intervention architecture
Changing commitment state machine
Changing synchronization strategy
Changing permission model
```

---

# 24. NO CASUAL DEPENDENCIES

Do not add a library simply because:

> "It makes the code easier."

---

# 25. DEPENDENCY ADDITION CHECKLIST

Before adding a dependency, answer:

```text
Why is it needed?
Why can't existing APIs solve this?
What maintenance burden does it create?
Does it increase app size?
Does it introduce security risk?
Does it introduce licensing concerns?
```

---

# 26. DEPENDENCY SCOPE

A dependency should have the smallest reasonable scope.

Do not expose third-party APIs throughout the entire architecture when an adapter can isolate them.

---

# 27. NO GLOBAL BUILD CHANGES WITHOUT REASON

Do not casually modify:

* Gradle configuration
* compiler options
* Kotlin settings
* lint configuration
* warning suppression
* dependency resolution
* build plugins

for unrelated reasons.

---

# 28. NO GLOBAL SUPPRESSIONS

Do not hide problems using broad:

```text
@Suppress(...)
```

or equivalent global suppression unless explicitly justified.

---

# 29. NO FAKE TEST FIXES

Do not make tests pass by weakening or deleting the test.

---

# 30. TESTS ARE PART OF THE FEATURE

A feature is not complete merely because:

```text
it compiles
```

or:

```text
the screen appears
```

---

# 31. REQUIRED VALIDATION

After meaningful implementation, run appropriate:

```text
build
unit tests
integration tests
UI tests where relevant
lint/static analysis where configured
```

---

# 32. TEST EVIDENCE

The agent must report:

```text
Tests run:
...

Passed:
...

Failed:
...

Skipped:
...
```

---

# 33. NO CLAIM WITHOUT VERIFICATION

Do not say:

> "Everything works."

unless the relevant behavior was actually tested.

---

# 34. COMPILE SUCCESS IS NOT FEATURE SUCCESS

A successful build proves compilation.

It does not prove:

* correct state transitions
* correct UI behavior
* correct autonomy
* correct background execution
* correct intervention behavior
* correct persistence

---

# 35. UNIT TEST PRINCIPLE

Business rules should be testable independently of Android UI whenever practical.

---

# 36. DOMAIN TESTS

Important domain behavior must have tests.

Examples:

```text
CreateGoal
CompleteCommitment
PostponeCommitment
RecoverCommitment
UpdatePattern
EvaluateProtection
```

---

# 37. STATE MACHINE TESTS

For each state machine:

Test:

```text
valid transitions
invalid transitions
terminal states
recovery paths
repeated actions
```

---

# 38. DATA TESTS

Test:

```text
persistence
mapping
serialization
migration
user scoping
duplicate handling
```

---

# 39. AI TESTS

AI features should have tests using mocks/stubs/fixed responses where possible.

Do not rely exclusively on live AI calls.

---

# 40. AI OUTPUT TESTS

Test:

```text
valid response
malformed response
missing field
invalid enum
invalid confidence
unknown entity
unexpected output
```

---

# 41. AUTONOMY TESTS

Every autonomous capability must test:

```text
OFF
SUGGEST_ONLY
ASK_BEFORE_ACTION
FULL_AUTO
```

where applicable.

---

# 42. INTERVENTION TESTS

Test:

```text
trigger
cooldown
deduplication
suppression
override
stale state
successful delivery
technical failure
behavioral outcome
```

---

# 43. BACKGROUND TESTS

Test:

```text
job execution
retry
cancellation
failure
idempotency
process restart where relevant
```

---

# 44. LOGGING TESTS

Important lifecycle logging must be verified where it is part of the subsystem contract.

---

# 45. LIFECYCLE LOGGING STANDARD

Use:

```text
IronMindLifecycle [Component] [EVENT] key=value ...
```

Examples:

```text
IronMindLifecycle Goal CREATED goalId=123
```

```text
IronMindLifecycle Commitment COMPLETED commitmentId=456
```

```text
IronMindLifecycle Protection ENABLED sessionId=789
```

---

# 46. LOGGING IS NOT OPTIONAL OBSERVABILITY

When a sprint explicitly requires lifecycle logging, omitting it means the sprint is incomplete.

---

# 47. SENSITIVE DATA LOGGING RULE

Do NOT log:

```text
passwords
API keys
tokens
private credentials
raw voice recordings
raw sensitive reflections
unnecessary location coordinates
unnecessary private calendar information
```

---

# 48. LOG IDENTIFIERS, NOT PERSONAL CONTENT

Prefer:

```text
IronMindLifecycle Reflection COMPLETED reflectionId=123
```

instead of:

```text
IronMindLifecycle Reflection COMPLETED text="..."
```

---

# 49. DIFF REVIEW

After implementation, inspect:

```text
git status
git diff
```

before considering the sprint complete.

---

# 50. DIFF REVIEW PURPOSE

Look specifically for:

```text
unrelated file changes
deleted functionality
dependency changes
Gradle changes
manifest changes
permission changes
architecture changes
removed tests
modified contracts
autonomy changes
logging removal
```

---

# 51. LARGE DIFF WARNING

If a supposedly small feature produces a huge diff:

> Stop and investigate.

Possible causes:

* formatting entire files
* generated changes
* accidental refactor
* architecture expansion
* wrong scope

---

# 52. FORMAT-ONLY DIFFS

Do not reformat unrelated files as part of a feature sprint.

---

# 53. FILE CREATION RULE

New files should have an identifiable purpose.

Do not create:

```text
Helper1
Manager2
UtilsNew
TempService
ExperimentalManager
```

without architectural justification.

---

# 54. DELETE RULE

Do not delete existing functionality because:

> "It isn't currently used."

Unless deletion is explicitly in scope.

---

# 55. RENAME RULE

Large-scale renaming should not be bundled into unrelated feature work.

---

# 56. MOVE RULE

Moving packages/classes is an architectural change if it alters dependency boundaries.

Report it accordingly.

---

# 57. NO HIDDEN BEHAVIOR

A feature must not introduce hidden behavior that is absent from the relevant contract.

---

# 58. NO MAGIC AUTONOMY

Do not introduce:

```text
if (...) automaticallyBlock()
```

outside the defined autonomy/decision architecture.

---

# 59. NO MAGIC NOTIFICATIONS

Do not send notifications directly from arbitrary classes.

Follow the notification/intervention architecture.

---

# 60. NO DIRECT AI FROM UI

Avoid:

```text
Composable
↓
Gemini API
```

Use the defined AI abstraction.

---

# 61. NO DIRECT DATABASE FROM UI

Avoid:

```text
Composable
↓
Room DAO
```

Use the application/domain architecture.

---

# 62. NO DIRECT FIREBASE FROM DOMAIN

Domain logic must not depend directly on Firestore/Firebase implementation details.

---

# 63. NO DIRECT ANDROID APIs FROM DOMAIN

Domain code should not directly depend on:

* Activity
* Context
* Compose UI
* Android managers

unless explicitly justified by architecture.

---

# 64. ADAPTER RULE

External systems should enter through explicit adapters/interfaces.

Examples:

```text
UsageStatsProvider
SpeechToTextProvider
CalendarProvider
LocationProvider
AppProtectionProvider
```

---

# 65. DOMAIN MODEL PURITY

Domain objects should represent IronMind concepts, not external SDK objects.

---

# 66. DATA MODEL PURITY

Database entities should represent persistence requirements while preserving the canonical semantic model.

---

# 67. AI PROVIDER ISOLATION

Do not spread Gemini/other provider SDK types throughout the codebase.

---

# 68. BACKEND PROVIDER ISOLATION

Do not spread Firebase types throughout the domain.

---

# 69. TEST DOUBLES

Important external dependencies should be mockable/fakeable.

Examples:

```text
AI
Clock
Repositories
Notification delivery
Protection
Speech-to-text
Network
```

where appropriate.

---

# 70. TIME TESTABILITY

Logic involving:

* scheduling
* cooldown
* expiration
* nightly review
* pattern decay

must be testable without depending entirely on the real system clock.

---

# 71. CLOCK ABSTRACTION

Where necessary use a testable time source.

Conceptually:

```text
Clock.now()
```

rather than scattered direct system-time access.

---

# 72. RANDOMNESS

If randomness is required, isolate it so tests can control it.

---

# 73. THREADING RULE

Long-running work must not block the main/UI thread.

---

# 74. COROUTINE RULE

Use structured coroutine/lifecycle patterns.

Avoid uncontrolled background coroutines that outlive their owner.

---

# 75. ASYNC ERROR HANDLING

Every asynchronous operation should have defined:

```text
success
failure
cancellation
retry
```

behavior as appropriate.

---

# 76. BACKGROUND WORK

Use the appropriate Android-supported mechanism for the actual requirement.

Do not create a permanent service merely because background execution is inconvenient.

---

# 77. WORKMANAGER RULE

Use WorkManager for appropriate deferrable/reliable background work unless there is a specific reason another mechanism is required.

---

# 78. FOREGROUND SERVICE RULE

A foreground service should only exist where a genuinely ongoing user-visible operation requires it.

---

# 79. BACKGROUND BATTERY RULE

Do not poll aggressively.

Prefer:

```text
scheduled
event-driven
batched
context-triggered
```

processing.

---

# 80. NETWORK RULE

Do not make local critical functionality depend unnecessarily on network availability.

---

# 81. OFFLINE RULE

Where appropriate:

```text
local state
↓
local event
↓
pending sync
```

rather than:

```text
wait for backend
```

before acknowledging user action.

---

# 82. RETRY RULE

Retries must be bounded.

Do not retry indefinitely.

---

# 83. IDEMPOTENCY RULE

Any operation that can be retried must consider duplicate processing.

---

# 84. EVENT IDEMPOTENCY

Events should have stable identifiers that allow duplicate detection.

---

# 85. OPERATION IDEMPOTENCY

Background/sync operations should use operation identifiers where necessary.

---

# 86. DATABASE TRANSACTIONS

Where a business operation changes multiple related records, use an appropriate transaction boundary.

---

# 87. STATE + EVENT ATOMICITY

If a state change requires a corresponding event, the implementation should avoid partial persistence.

Example:

```text
Commitment completed
+
COMMITMENT_COMPLETED event
```

should be treated as one logical operation where the persistence system supports it.

---

# 88. DATA MIGRATIONS

Any persisted schema change must be handled deliberately.

---

# 89. MIGRATION RULE

Document:

```text
old schema
new schema
migration path
test cases
```

before shipping.

---

# 90. NO UNSAFE DATABASE RESET

Do not solve a schema problem by casually deleting the local database.

---

# 91. MIGRATION DATA PRESERVATION

Existing user data should be preserved unless deletion is explicitly intended.

---

# 92. BACKEND SCHEMA CHANGES

Backend schema changes require the same level of care as local database changes.

---

# 93. CONTRACT SYNCHRONIZATION

When Android and backend share a schema:

> Update both consistently.

Do not let one side silently diverge.

---

# 94. API CONTRACT CHANGES

API changes must consider:

```textexisting clients
new clients
migration
backward compatibility
```

---

# 95. EVENT CONTRACT CHANGES

Do not change the semantic meaning of an existing event name without explicit migration/versioning.

---

# 96. STATE ENUM CHANGES

Do not casually rename state values stored in persistence.

---

# 97. AI PROMPT CHANGES

Prompt changes can alter application behavior.

Important prompt changes should be reviewed and tested.

---

# 98. AI MODEL CHANGES

Model/provider changes may alter:

* output
* latency
* reasoning
* recommendations

Run regression tests.

---

# 99. AI SCHEMA CHANGES

Changing AI output schema requires:

```text
provider implementation
validation
tests
downstream consumers
```

review.

---

# 100. AUTONOMY CHANGES

Changing:

```text
OFF
SUGGEST_ONLY
ASK_BEFORE_ACTION
FULL_AUTO
```

or their semantics is a product/policy change.

It is not a simple code refactor.

---

# 101. INTERVENTION CHANGES

Adding or changing:

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

requires reviewing intervention policy.

---

# 102. NEW INTERVENTION TYPE

A new intervention type must define:

```text
purpose
trigger
target
invasiveness
autonomy
delivery
cooldown
suppression
outcome
logging
tests
```

---

# 103. NEW AUTONOMOUS ACTION

A new autonomous action must define:

```text
capability
scope
minimum autonomy level
permissions
validation
reversibility
logging
failure behavior
tests
```

---

# 104. NO AUTOMATION BY ACCIDENT

Adding a feature must not accidentally make it autonomous.

---

# 105. DEFAULT SAFETY RULE

If a new action has no defined autonomy policy:

```text
Do not automatically execute it.
```

Prefer:

```text
suggest
or
ask
```

according to context.

---

# 106. USER CONTROL REQUIREMENT

Any new automation must define how the user can:

```text
disable
override
pause
```

where appropriate.

---

# 107. NO LOCK-IN AUTOMATION

The system must not create automation that cannot reasonably be stopped by the user.

---

# 108. GLOBAL PAUSE

If the product provides a global automation pause:

All applicable autonomous actions must respect it.

---

# 109. BACKGROUND WORKER AUTHORITY

A worker does not receive additional authority simply because it runs in the background.

---

# 110. AI AGENT MUST NOT CHANGE AUTONOMY

Coding agents must not:

```text
enable FULL_AUTO
remove confirmation
remove override
```

unless explicitly scoped.

---

# 111. AI AGENT MUST NOT MODIFY PRODUCT RULES

Coding agents must not casually rewrite:

* Product Constitution
* Master Blueprint
* AI Behavior Contract
* Autonomy Policy
* Intervention Rules

during normal coding.

---

# 112. CONTRACT CHANGE RULE

If implementation reveals that a contract is insufficient:

Do not silently work around it.

Report:

```text
Contract limitation
Proposed change
Why required
Affected systems
```

Then update the contract through an explicit product/architecture decision.

---

# 113. SOURCE CODE COMMENT RULE

Comments should explain:

```text
why
constraint
trade-off
non-obvious behavior
```

not repeat obvious syntax.

---

# 114. NO COMMENT-DRIVEN ARCHITECTURE

A comment such as:

```text
// Do not change this
```

is not sufficient protection.

The actual architecture must enforce the intended boundary.

---

# 115. NAMING RULE

Names should communicate domain meaning.

Good:

```text
CommitmentRepository
InterventionEngine
PatternAnalyzer
ProtectionSession
```

Bad:

```text
Manager
Helper
Thing
Util2
EngineFinal
```

without clear purpose.

---

# 116. DOMAIN TERMINOLOGY

Use terms established in:

`DATA_CONTRACT.md`

Do not create synonyms casually.

---

# 117. FEATURE NAMING

Feature names should communicate user/product behavior.

---

# 118. CLASS RESPONSIBILITY

Each class should have a clear responsibility.

---

# 119. METHOD RESPONSIBILITY

Methods should perform coherent operations.

Avoid giant methods that:

```text
load data
call AI
change state
send notification
update UI
```

all at once.

---

# 120. GOD CLASS PROHIBITION

Avoid giant:

```text
MainViewModel
IronMindManager
AIManager
AppManager
EverythingService
```

classes.

---

# 121. GOD FILE PROHIBITION

Avoid giant files containing unrelated behavior.

---

# 122. NAVIGATION RULE

Navigation should not contain product logic.

---

# 123. VIEWMODEL RULE

ViewModels coordinate UI.

They should not become:

```text
database
AI
intervention
background
Android system
```

all at once.

---

# 124. REPOSITORY RULE

Repositories manage data access.

They should not become product decision engines.

---

# 125. WORKER RULE

Workers orchestrate background work.

They should not become an alternate hidden domain layer.

---

# 126. AI SERVICE RULE

AI services perform AI interactions.

They should not directly change UI or execute arbitrary device actions.

---

# 127. INTERVENTION ENGINE RULE

Intervention Engine creates/executes approved interventions according to policy.

It should not secretly modify autonomy.

---

# 128. POLICY ENGINE RULE

Policy decides what is permitted.

It should not rely solely on natural-language AI output.

---

# 129. EXECUTION RULE

Execution adapters perform approved actions.

They do not decide product policy.

---

# 130. EVENT PROCESSOR RULE

Event processors should handle defined responsibilities.

Do not allow arbitrary event handlers to modify unrelated domains.

---

# 131. TEST ISOLATION RULE

A unit test should not require:

```text
network
real AI
real Firebase
real device service
```

unless it is explicitly an integration/system test.

---

# 132. MOCK RULE

Use deterministic mocks/fakes for:

```text
AI
network
clock
Android system integration
backend
```

when appropriate.

---

# 133. LIVE API TEST RULE

Do not make normal unit tests depend on live AI or backend services.

---

# 134. TEST DATA

Test data should be:

* deterministic
* minimal
* representative
* safe

---

# 135. NO PRODUCTION PERSONAL DATA IN TESTS

Do not hard-code real personal data into test fixtures.

---

# 136. UI TEST DATA

Use synthetic users/goals/commitments.

---

# 137. LOGGING TEST DATA

Verify event names and identifiers, not sensitive raw content.

---

# 138. ERROR TESTING

Every meaningful subsystem should have at least one failure-path test.

---

# 139. HAPPY PATH IS NOT ENOUGH

Test:

```text
success
failure
empty
null/missing
duplicate
retry
stale
permission denied
network unavailable
```

where relevant.

---

# 140. EDGE CASES

Consider:

```text
zero tasks
duplicate tap
very long title
past date
overlapping commitments
completed commitment reopened
app restart
network interruption
AI malformed response
```

where applicable.

---

# 141. DOUBLE ACTION PROTECTION

User actions such as complete/postpone/start should be safe against accidental repeated taps.

---

# 142. RACE CONDITIONS

Concurrent UI/background operations must be considered.

---

# 143. CURRENT STATE REVALIDATION

Before high-impact operations:

```text
re-read or validate current state
```

rather than relying on stale UI state.

---

# 144. STALE UI STATE

The UI may be out of date.

Domain actions should validate current authoritative state.

---

# 145. STALE BACKGROUND JOB

A background job must revalidate relevant state before important action.

---

# 146. STALE AI RECOMMENDATION

An old AI recommendation may become invalid.

Revalidate before execution.

---

# 147. NO BLIND RETRIES

Retry logic must re-check whether retry is still meaningful.

---

# 148. CANCELLATION

Long-running tasks should support cancellation where appropriate.

---

# 149. USER EXPERIENCE DURING ASYNC OPERATIONS

The UI should clearly represent:

```text
loading
success
failure
retry
```

without leaving the user in an ambiguous state.

---

# 150. NO SILENT FAILURE

Do not swallow important exceptions.

---

# 151. ERROR LOGGING

Important failures should emit useful lifecycle/error information without leaking sensitive content.

---

# 152. ERROR MESSAGES

User-facing errors should be:

* understandable
* actionable
* proportionate

---

# 153. DEBUG VS USER ERROR

Internal technical details should not necessarily be shown directly to normal users.

---

# 154. DEVELOPMENT DEBUGGING

Development builds may expose more technical information.

---

# 155. DEBUG MODE

A development-only debug/control system may expose:

```text
current state
events
AI calls
interventions
workers
sync
permissions
autonomy
```

---

# 156. DEBUG MODE MUST NOT BECOME PRODUCTION AUTHORITY

Debug controls should never accidentally become unrestricted production actions.

---

# 157. DEBUG ACTION LOGGING

Development actions that alter important state should still be logged.

---

# 158. DEBUG DATA PRIVACY

Do not create debug panels that dump all private user data unnecessarily.

---

# 159. GIT RULES

Git is part of the engineering safety system.

---

# 160. WORKING TREE BEFORE SPRINT

Before starting a sprint:

```text
git status
```

Understand whether there are existing uncommitted changes.

---

# 161. DO NOT DESTROY USER CHANGES

If unrelated uncommitted changes exist:

> Do not overwrite, reset, or discard them.

---

# 162. BASELINE CHECKPOINT

Where appropriate, create or identify a clean checkpoint before major changes.

---

# 163. FOCUSED COMMITS

Prefer one focused commit per sprint.

---

# 164. COMMIT MESSAGE

Use clear messages such as:

```text
feat(goal): add goal creation flow
feat(commitment): implement completion state transition
test(intervention): add cooldown coverage
fix(sync): prevent duplicate event upload
```

---

# 165. NO GIANT MIXED COMMITS

Avoid:

```text
feat: everything
```

containing many unrelated changes.

---

# 166. COMMIT AFTER VALIDATION

Commit after:

```text
tests
build
diff review
```

---

# 167. DO NOT COMMIT BROKEN CODE

Unless explicitly creating a checkpoint for an experimental branch and clearly labeling it, production development commits should represent verified states.

---

# 168. COMMIT HISTORY AS ARCHITECTURAL RECORD

Commit history should make it understandable how IronMind evolved.

---

# 169. REVERTABILITY

Prefer focused commits so bad changes can be reverted safely.

---

# 170. BRANCHING

Feature branches may be used when appropriate.

---

# 171. MERGE RULE

Before merge:

```text
tests
diff
scope
contract compliance
```

must be reviewed.

---

# 172. TAGGING

Stable milestones may be tagged.

Examples:

```text
v0-foundation
v0-core-loop
v1-reflection
```

Exact naming is optional.

---

# 173. RELEASE CHECKPOINT

Before a meaningful release:

```text
clean build
tests
manual validation
permissions
background behavior
critical flows
```

---

# 174. MANUAL VALIDATION

Automated tests do not replace manual testing of:

* notifications
* Android permissions
* app protection
* background execution
* voice
* real device lifecycle
* UI interaction

where required.

---

# 175. TEST DEVICE PRINCIPLE

Early development may use a small number of controlled devices.

But behavior that depends on Android system integration should eventually be tested on actual supported devices.

---

# 176. ANDROID-SPECIFIC VALIDATION

Real-device testing may be required for:

```text
Accessibility
Usage data
Notifications
Background work
Foreground services
Voice
App protection
Permission flows
```

---

# 177. PERMISSION TESTING

Test:

```text
granted
denied
revoked
never requested
```

where relevant.

---

# 178. APP RESTART TEST

Important persistent state should survive app restart.

---

# 179. PROCESS-DEATH TEST

Important background/persistence flows should be tested against process death where relevant.

---

# 180. DEVICE-RESTART TEST

Important scheduled/background functionality should be validated after device restart where applicable.

---

# 181. NETWORK TESTING

Test:

```text
online
offline
intermittent
reconnect
```

where synchronization exists.

---

# 182. AI AVAILABILITY TESTING

Test:

```text
AI available
AI timeout
AI malformed output
AI unavailable
```

---

# 183. BACKEND AVAILABILITY TESTING

Test:

```text
backend available
backend unavailable
partial failure
retry
duplicate
```

---

# 184. OBSERVABILITY CHECK

After implementing a subsystem:

Ask:

```text
Can I tell what happened?
Can I tell where it failed?
Can I reproduce it?
```

If not, improve observability.

---

# 185. LIFECYCLE EVENTS

Meaningful state transitions should emit lifecycle logs.

---

# 186. STRUCTURED LOG FORMAT

Required format:

```text
IronMindLifecycle [Component] [EVENT] key=value ...
```

Example:

```text
IronMindLifecycle Goal CREATED goalId=abc
```

---

# 187. LOG EVENT NAMING

Event names should be:

* uppercase
* semantic
* stable
* consistent

---

# 188. LOG COMPONENT NAMING

Component names should correspond to actual subsystem responsibility.

Examples:

```text
Goal
Commitment
Protection
Intervention
Reflection
Memory
Pattern
Sync
BackgroundWorker
AI
Autonomy
```

---

# 189. NO RANDOM LOG STRINGS

Avoid inconsistent messages such as:

```text
goal created!!!
goal-created
GoalThing done
Created goal successfully
```

when the lifecycle contract expects structured events.

---

# 190. LOGGING FAILURE STATES

Important failure transitions should be observable.

---

# 191. LOGGING PERFORMANCE

Do not emit extremely high-frequency logs that create noise or battery/performance problems.

---

# 192. OBSERVABILITY VS PRIVACY

More logs do not automatically mean better debugging.

Use meaningful metadata.

---

# 193. AI CODING AGENT REPORT

Every sprint implementation must produce a structured report.

---

# 194. REQUIRED REPORT FORMAT

```text
SPRINT:
<name>

OBJECTIVE:
<primary objective>

IN SCOPE:
<summary>

OUT OF SCOPE:
<summary>

FILES CHANGED:
<list>

FILES ADDED:
<list>

FILES DELETED:
<list>

ARCHITECTURAL CHANGES:
<none or explanation>

DATA CHANGES:
<none or explanation>

AI CHANGES:
<none or explanation>

AUTONOMY CHANGES:
<none or explanation>

INTERVENTION CHANGES:
<none or explanation>

BEHAVIOR IMPLEMENTED:
<summary>

TESTS RUN:
<list/results>

BUILD:
<result>

LOGGING VERIFIED:
<yes/no + details>

DIFF REVIEW:
<summary>

KNOWN ISSUES:
<list>

FOLLOW-UP:
<optional>

COMMIT:
<hash/message>
```

---

# 195. REPORT TRUTHFULNESS

The report must state what was actually verified.

Do not report:

```text
"works perfectly"
```

unless evidence supports it.

---

# 196. KNOWN ISSUES

Known issues must be listed explicitly.

Do not hide incomplete behavior.

---

# 197. FOLLOW-UP ISSUES

Out-of-scope discoveries should be documented as follow-up work.

---

# 198. AI CODING AGENT STOP CONDITION

When:

```text
acceptance criteria
+
tests
+
verification
```

are satisfied:

> Stop.

---

# 199. NO AUTONOMOUS SCOPE EXPANSION

The agent must not continue working because:

> "I saw some other things that could be improved."

---

# 200. DISCOVERY REPORTING

If the agent finds a problem outside scope:

```text
FOUND:
...

NOT FIXED:
Outside current sprint.

RECOMMENDATION:
Future sprint.
```

---

# 201. HUMAN APPROVAL GATE

For meaningful product or architectural changes:

```text
AI implementation
↓
tests
↓
diff review
↓
human approval
↓
commit
```

---

# 202. PRODUCT DECISION VS ENGINEERING DECISION

Engineering can determine:

```text
how
```

Product determines:

```text
what
```

Architecture determines:

```text
where
```

Policy determines:

```text
what is allowed
```

---

# 203. CODING AGENT MUST NOT MAKE PRODUCT DECISIONS

Examples:

```text
Should IronMind become gamified?
Should notifications be more aggressive?
Should automation be always on?
Should users have social features?
```

These are not coding decisions.

---

# 204. CODING AGENT MAY FLAG DESIGN PROBLEMS

It may say:

> "This design will make synchronization difficult."

That is useful.

But it should not silently replace the architecture.

---

# 205. ARCHITECTURE PROPOSAL FORMAT

When recommending an architecture change:

```text
Problem:
...

Current behavior:
...

Why insufficient:
...

Proposed architecture:
...

Benefits:
...

Risks:
...

Migration:
...

Testing:
...
```

---

# 206. NO SILENT SECURITY CHANGES

Security changes must be explicit.

---

# 207. NO SILENT PERMISSION CHANGES

Do not add permissions to the manifest simply because a feature might use them.

---

# 208. PERMISSION CHANGE REQUIREMENT

Any new Android permission should state:

```text
permission
feature requiring it
why necessary
user-visible purpose
failure behavior
```

---

# 209. MANIFEST REVIEW

Manifest changes should be explicitly reviewed because they may change device behavior and privacy boundaries.

---

# 210. ACCESSIBILITY SERVICE CHANGES

Changes to Accessibility-based functionality require heightened review because they affect device-level behavior.

---

# 211. BACKGROUND SERVICE CHANGES

Changes involving services/workers require explicit scope and lifecycle validation.

---

# 212. NOTIFICATION PERMISSION CHANGES

Notification behavior changes must be tested on actual device behavior where relevant.

---

# 213. MICROPHONE PERMISSION

Voice features must explicitly justify microphone access.

---

# 214. LOCATION PERMISSION

Location-based functionality must explicitly justify location access.

---

# 215. CALENDAR ACCESS

Calendar integration must explicitly justify calendar access.

---

# 216. HEALTH / ACTIVITY DATA

Future health/activity integrations require explicit product and data-contract review.

---

# 217. DATA COLLECTION CHANGES

Adding a new observation source requires:

```text
purpose
data type
collection frequency
retention
privacy impact
user control
AI usage
test plan
```

---

# 218. NO DATA COLLECTION "JUST IN CASE"

Do not collect information merely because it might become useful later.

---

# 219. DATA CONTRACT FIRST

New persistent data should be defined in the data contract before implementation.

---

# 220. NEW ENTITY PROCESS

Before adding a canonical entity:

```text
Why?
Why existing entities are insufficient?
Who owns it?
Who writes it?
Who reads it?
Lifecycle?
Persistence?
Synchronization?
```

---

# 221. NEW FIELD PROCESS

Before adding important fields:

```text
Meaning
Type
Required/optional
Default
Source
Retention
```

---

# 222. DATABASE CHANGE REVIEW

New entities/fields may affect:

```text
Android
backend
AI
sync
tests
analytics
migrations
```

Review all affected boundaries.

---

# 223. AI MEMORY CHANGES

Any change that lets AI persist more memory requires review of:

```text
authority
confidence
provenance
retention
user correction
```

---

# 224. PATTERN CHANGES

Any change to pattern detection should consider:

```text
evidence
confidence
recency
decay
false positives
false negatives
```

---

# 225. AUTONOMY CHANGES

Any change to automatic execution should consider:

```text
authority
invasiveness
reversibility
override
logging
policy
```

---

# 226. INTERVENTION CHANGES

Any intervention change should consider:

```text
timing
frequency
cooldown
deduplication
user burden
outcome
learning
```

---

# 227. PRODUCT EXPERIMENTS

Experiments must remain isolated from stable production behavior.

---

# 228. EXPERIMENT FLAGS

Feature flags may be used for controlled rollout.

---

# 229. EXPERIMENT DATA

Experiments should collect only the data required to evaluate them.

---

# 230. EXPERIMENT EXIT CONDITION

Every experiment should have:

```text
hypothesis
success criteria
failure criteria
review point
```

---

# 231. NO PERMANENT EXPERIMENTAL CODE

Do not allow temporary experiments to become permanent architecture without review.

---

# 232. CODE OWNERSHIP

Every important subsystem should have identifiable ownership at the architectural level.

---

# 233. DOMAIN OWNERSHIP

Examples:

```text
Goals
→ Goal domain

Commitments
→ Commitment domain

Patterns
→ Intelligence/Pattern system

Interventions
→ Intervention system

Autonomy
→ Autonomy/Policy system
```

---

# 234. CROSS-DOMAIN ACCESS

Cross-domain behavior should happen through explicit contracts/use cases.

---

# 235. NO DIRECT INTERNAL MUTATION

One domain should not reach into another domain's private persistence structures.

---

# 236. EVENT-BASED CROSS-DOMAIN COMMUNICATION

Where appropriate, use events to communicate meaningful state changes.

---

# 237. EVENT BUS CONTROL

Do not create a completely unrestricted global event bus.

Event consumers must be intentional.

---

# 238. DOMAIN EVENTS VS ANALYTICS EVENTS

Do not confuse business events with analytics telemetry.

---

# 239. ANALYTICS IS SECONDARY

Product correctness must not depend on analytics.

---

# 240. FEATURE COMPLETION DEFINITION

A feature is complete only when:

```text
behavior implemented
+
correct domain state
+
data persistence
+
tests
+
logging
+
error handling
+
scope review
```

where applicable.

---

# 241. FEATURE PARTIAL COMPLETION

If a feature is only partially complete:

State clearly:

```text
Implemented:
...

Not implemented:
...
```

---

# 242. NO FALSE COMPLETION

Do not mark a sprint complete because:

```text
UI exists
```

if:

```text
backend
state
tests
```

are still missing from the defined scope.

---

# 243. UI PLACEHOLDERS

A placeholder UI may be acceptable in an explicitly defined foundation sprint.

It must be clearly identified as placeholder.

---

# 244. MOCK DATA

Mock data may be used temporarily.

It must not be mistaken for real persistence.

---

# 245. MOCK DATA LABELING

Development builds should make it clear when data is mocked.

---

# 246. NO MOCKS IN PRODUCTION BY ACCIDENT

Before release:

```text
remove/disable mock pathways
```

where applicable.

---

# 247. ENVIRONMENT CONFIGURATION

Development/test/prod configurations must remain clearly separated.

---

# 248. DEBUG FLAGS

Debug flags must not accidentally enable high-authority behavior in production.

---

# 249. PRODUCTION SAFETY CHECK

Before release:

```text
debug autonomy
debug control center
mock data
test endpoints
development credentials
```

must be reviewed.

---

# 250. SECRETS

Never commit:

```text
API keys
passwords
private keys
service account secrets
authentication tokens
```

---

# 251. SECRET SCANNING

Where available, use secret scanning before important commits/releases.

---

# 252. CREDENTIAL ROTATION

If a secret is accidentally exposed:

Treat it as compromised and rotate it according to the relevant provider process.

---

# 253. USER DATA IN DEVELOPMENT

Use synthetic development data wherever practical.

---

# 254. USER DATA IN LOGS

Do not copy production personal content into logs for debugging unless explicitly justified and protected.

---

# 255. DEVELOPMENT DATABASE

Development should preferably use isolated test/dev data.

---

# 256. TEST ENVIRONMENT

Automated tests should not mutate production resources.

---

# 257. BACKEND ENVIRONMENT

Development backend and production backend must be distinguishable.

---

# 258. FIREBASE PROJECTS

If Firebase is used, separate development/testing and production environments where practical.

---

# 259. RELEASE CONFIGURATION

Production builds must use explicit production configuration.

---

# 260. BUILD REPRODUCIBILITY

Build configuration should be deterministic and documented.

---

# 261. DEPENDENCY LOCKING

Where appropriate, dependency versions should be controlled.

---

# 262. BUILD FAILURE RULE

Do not hide a build failure by weakening compiler/lint configuration.

---

# 263. TEST FAILURE RULE

Do not delete a failing test merely because implementation is inconvenient.

---

# 264. LINT FAILURE RULE

Fix meaningful issues or explicitly document why suppression is justified.

---

# 265. WARNING POLICY

Do not globally suppress warnings simply to create a clean build.

---

# 266. TECHNICAL DEBT

Technical debt should be tracked rather than hidden.

---

# 267. DEBT REPORTING

When knowingly accepting a shortcut:

```text
Shortcut:
...

Reason:
...

Risk:
...

Follow-up:
...
```

---

# 268. NO ACCIDENTAL DEBT

Do not leave temporary hacks without documentation.

---

# 269. TEMPORARY CODE

Temporary code should have:

```text
clear marker
reason
removal condition
```

where practical.

---

# 270. TODO POLICY

TODOs should describe real follow-up work, not vague statements.

Good:

```text
TODO: Replace temporary local scheduler with production policy-aware scheduler.
```

Bad:

```text
TODO: improve this
```

---

# 271. DOCUMENTATION RULE

Important architectural behavior should be reflected in the appropriate contract/document.

---

# 272. CODE IS NOT THE ONLY DOCUMENTATION

Contracts exist to explain:

```text
why
```

while code explains:

```text
how
```

---

# 273. TESTS ARE EXAMPLES OF EXPECTED BEHAVIOR

Tests should make important behavior executable and verifiable.

---

# 274. REGRESSION TESTS

When fixing a meaningful bug:

> Add a regression test where practical.

---

# 275. BUG FIX PROCESS

```text
REPRODUCE
↓
UNDERSTAND
↓
FIX ROOT CAUSE
↓
ADD REGRESSION TEST
↓
VERIFY
↓
REVIEW DIFF
```

---

# 276. DO NOT PATCH SYMPTOMS blindly

If a bug originates in domain/state logic, do not merely alter UI presentation.

---

# 277. ROOT CAUSE PRINCIPLE

Fix the problem at the correct architectural layer.

---

# 278. PERFORMANCE DEBUGGING

Measure before optimizing where practical.

---

# 279. NO PREMATURE OPTIMIZATION

Do not introduce complex caching/concurrency merely for theoretical future scale.

---

# 280. PERFORMANCE REGRESSIONS

Important performance changes should be measured where possible.

---

# 281. BATTERY REGRESSIONS

Background changes must consider battery impact.

---

# 282. MEMORY REGRESSIONS

Avoid retaining large user/AI payloads indefinitely.

---

# 283. NETWORK REGRESSIONS

Avoid unnecessary network calls.

---

# 284. AI COST REGRESSIONS

Avoid unnecessary AI calls.

---

# 285. AI CALL REVIEW

Before adding an AI call:

```text
Can deterministic logic solve this?
Can cached/structured data solve this?
Does reasoning materially improve the result?
```

---

# 286. BACKGROUND AI REVIEW

Background AI should be batched where practical.

---

# 287. AI CONTEXT REVIEW

Do not send unrelated user history.

---

# 288. DATA RETENTION REVIEW

Any new persistent data source must consider retention.

---

# 289. PRIVACY REVIEW

Any new sensitive observation should consider:

```text
what
why
where
how long
who accesses it
```

---

# 290. USER CONTROL REVIEW

Any new autonomous/sensitive feature should define:

```text
enable
disable
override
```

where appropriate.

---

# 291. ARCHITECTURE REVIEW BEFORE SCALE

Before adding advanced intelligence:

Ensure:

```text
events reliable
state reliable
data reliable
logging reliable
tests reliable
```

---

# 292. BUILD FOUNDATION BEFORE INTELLIGENCE

Do not prematurely implement:

```text
deep behavioral prediction
full autonomy
complex AI agents
```

before reliable event/state foundations exist.

---

# 293. CORE LOOP FIRST

Prioritize:

```text
Goal
↓
Plan
↓
Commitment
↓
Action
↓
Outcome
```

before sophisticated learning.

---

# 294. EVENT FOUNDATION BEFORE PATTERN LEARNING

Pattern quality depends on event quality.

---

# 295. REFLECTION BEFORE DEEP PERSONAL MODEL

The system should collect meaningful reflection and outcome data before making strong long-term assumptions.

---

# 296. PATTERN LEARNING BEFORE ADVANCED AUTONOMY

Autonomous decisions should become more sophisticated only as reliable evidence exists.

---

# 297. AUTONOMY AFTER CONTROL

Do not build powerful automation before:

```text
settings
override
policy
logging
```

exist.

---

# 298. INTERVENTION AFTER OBSERVABILITY

Do not build aggressive intervention behavior without the ability to understand:

```text
why it triggered
what happened
```

---

# 299. BACKGROUND AFTER LIFECYCLE SAFETY

Background behavior should be added only after:

```text
persistence
state recovery
idempotency
logging
```

are understood.

---

# 300. AI AFTER DATA CONTRACT

AI schemas should derive from the domain/data contract.

---

# 301. NO "AI FIRST"

The product should not become an AI wrapper around poorly structured data.

---

# 302. ENGINEERING PRIORITY

When choosing between:

```text
new intelligence
```

and:

```text
better reliability
```

prefer reliability if the reliability gap affects core product behavior.

---

# 303. ENGINEERING PRIORITY ORDER

Generally prefer:

```text
Correctness
↓
Reliability
↓
Data integrity
↓
Observability
↓
Security
↓
Maintainability
↓
Performance
↓
Intelligence
↓
Convenience
↓
Cosmetic polish
```

This is a guiding priority, not an absolute rule for every task.

---

# 304. PRODUCT VS TECHNICAL QUALITY

A technically elegant feature that does not help the user is not automatically valuable.

---

# 305. USER OUTCOME

Whenever practical, connect implementation to:

```text
What user behavior should improve?
```

---

# 306. REAL-LIFE OUTCOME TEST

Ask:

> "What changes in the user's actual life because this feature exists?"

---

# 307. NO ENGAGEMENT OPTIMIZATION

Do not optimize code or product behavior for:

```text
more opens
more notifications
more AI conversations
```

unless explicitly justified by user value.

---

# 308. APP SHOULD DISAPPEAR

A successful feature may cause the user to spend less time inside IronMind because it helped them act.

That is acceptable and often desirable.

---

# 309. RELEASE CRITERIA

A release should have:

```text
known scope
validated critical flows
known limitations
build success
test results
manual validation
no unresolved critical regressions
```

---

# 310. RELEASE NOTES

Meaningful releases should document:

```text
what changed
what is new
what was fixed
known limitations
```

---

# 311. CRITICAL REGRESSION

A regression affecting:

* core commitments
* data integrity
* user control
* autonomy
* security
* major device behavior

must block release until understood/resolved or explicitly accepted.

---

# 312. DATA LOSS REGRESSION

Data loss is a high-severity defect.

---

# 313. AUTONOMY REGRESSION

Unexpected automatic behavior is a high-severity defect.

Example:

```text
Protection OFF
→ application blocked
```

is unacceptable.

---

# 314. USER CONTROL REGRESSION

If a user can no longer:

```text
override
disable
change settings
```

as previously defined, this is a significant regression.

---

# 315. FALSE STATE REGRESSION

If UI says:

```text
completed
```

while domain state says:

```text
postponed
```

this is a serious consistency bug.

---

# 316. FALSE EXECUTION REGRESSION

If IronMind claims:

```text
protection active
```

while execution failed:

> This is a critical trust bug.

---

# 317. BACKGROUND REGRESSION

A background worker that causes repeated:

```text
notification spam
duplicate actions
battery drain
```

must be treated as a significant defect.

---

# 318. AI HALLUCINATION REGRESSION

If AI begins repeatedly:

```text
inventing user facts
```

or:

```text
claiming false actions
```

that is a product integrity defect.

---

# 319. DATA CORRUPTION REGRESSION

Any bug that damages:

```text
goals
commitments
events
memory
patterns
```

requires immediate review.

---

# 320. SECURITY REGRESSION

Any bug that exposes:

```text
user data
credentials
tokens
other users' data
```

is high severity.

---

# 321. DEVELOPMENT GOLDEN RULES

The following rules are permanent:

```text
1. Read before modifying.

2. Scope before coding.

3. Scan before assuming.

4. Implement only what is requested.

5. Do not silently expand scope.

6. Do not casually change architecture.

7. Do not change product meaning.

8. Do not change autonomy without explicit scope.

9. Do not bypass the Decision Engine.

10. Do not give AI direct device authority.

11. Do not add unnecessary dependencies.

12. Do not weaken tests to make builds pass.

13. Test every meaningful behavior.

14. Verify lifecycle logging.

15. Review git diff.

16. Preserve unrelated work.

17. Keep commits focused.

18. Report known issues.

19. Stop when the sprint is complete.

20. Prefer reliable small changes over impressive large changes.
```

---

# 322. AI CODING AGENT GOLDEN RULES

An AI coding agent must remember:

```text
1. You implement; you do not redefine.

2. You inspect; you do not assume.

3. You follow scope.

4. You protect existing behavior.

5. You preserve contracts.

6. You test your changes.

7. You review your own diff.

8. You verify logs.

9. You report exactly what changed.

10. You stop when done.
```

---

# 323. SPRINT ACCEPTANCE TEMPLATE

Every sprint should eventually satisfy:

```text
OBJECTIVE
[ ] Implemented

SCOPE
[ ] Only intended scope changed

DOMAIN
[ ] Domain behavior correct

DATA
[ ] Persistence correct

ARCHITECTURE
[ ] Boundaries preserved

AI
[ ] No unintended AI behavior

AUTONOMY
[ ] No unintended authority changes

INTERVENTION
[ ] No unintended intervention changes

LOGGING
[ ] Lifecycle logs verified

TESTS
[ ] Relevant tests pass

BUILD
[ ] Build passes

DIFF
[ ] Diff reviewed

KNOWN ISSUES
[ ] Documented

COMMIT
[ ] Focused commit created
```

---

# 324. FULL DEVELOPMENT WORKFLOW

The complete preferred workflow is:

```text
                 PRODUCT CONTRACTS
                        ↓
                  SPRINT DEFINITION
                        ↓
                  CODEBASE SCAN
                        ↓
                  IMPLEMENTATION PLAN
                        ↓
                    CODING
                        ↓
                 UNIT / DOMAIN TESTS
                        ↓
                 INTEGRATION TESTS
                        ↓
                  BUILD / LINT
                        ↓
               LIFECYCLE LOG REVIEW
                        ↓
                    GIT DIFF
                        ↓
                 SCOPE REVIEW
                        ↓
               HUMAN REVIEW / APPROVAL
                        ↓
                     COMMIT
                        ↓
                  NEXT SPRINT
```

---

# 325. WHAT THE AI AGENT MUST NEVER DO

The following are explicitly prohibited without dedicated scope:

```text
Rewrite the entire application
Replace the architecture
Rebuild navigation globally
Change product philosophy
Introduce social features
Introduce gamification
Increase autonomy
Remove user overrides
Add broad permissions
Add unrestricted background services
Replace AI provider globally
Replace backend globally
Rewrite database architecture
Delete unrelated code
Remove tests
Disable logging
Change global build configuration
```

---

# 326. WHAT THE AI AGENT SHOULD DO

```text
Read
Scan
Understand
Plan
Implement
Test
Verify
Diff
Report
Stop
```

---

# 327. ENGINEERING CULTURE

IronMind development should favor:

```text
clarity
discipline
small iterations
evidence
reversibility
observability
testability
user control
```

over:

```text
speed at any cost
code volume
feature count
architectural novelty
```

---

# 328. ARCHITECTURE IS A PRODUCT ASSET

Do not treat architecture as disposable code.

The architecture determines whether IronMind can safely evolve into the autonomous system defined by the Master Blueprint.

---

# 329. DATA IS A PRODUCT ASSET

Reliable event history and personal memory are part of the product's intelligence foundation.

Protect their integrity.

---

# 330. LOGGING IS A PRODUCT INFRASTRUCTURE ASSET

Observability is required for:

```text
debugging
trust
autonomy
learning
```

---

# 331. TESTS ARE A PRODUCT ASSET

Tests protect the behavior that future AI agents might otherwise accidentally change.

---

# 332. GIT HISTORY IS A SAFETY ASSET

Focused commits provide recovery points when AI-generated changes go wrong.

---

# 333. DOCUMENTATION IS A SAFETY ASSET

The contracts reduce reliance on tribal knowledge and AI assumptions.

---

# 334. AI CODING AGENTS REQUIRE GUARDRAILS

The better the coding agent becomes, the more important explicit boundaries remain.

---

# 335. DO NOT REWARD LARGE DIFFS

A large diff is not proof of productivity.

---

# 336. DO NOT REWARD MORE CODE

More code is not automatically better.

---

# 337. REWARD CORRECT BEHAVIOR

The real engineering objective is:

```text
Correct product behavior
+
Maintainable architecture
+
Reliable data
+
Strong tests
+
Controlled automation
```

---

# 338. FINAL DEVELOPMENT PRINCIPLE

> **Every change should make IronMind more capable without making IronMind less understandable, less controllable, or less reliable.**

---

# 339. FINAL AI CODING PRINCIPLE

> **An AI coding agent should leave the codebase in a state that a human can understand, test, review, and safely continue from.**

---

# 340. FINAL SCOPE PRINCIPLE

> **A sprint is successful when its intended problem is solved — not when the agent has changed as much code as possible.**

---

# 341. FINAL PRODUCT PROTECTION PRINCIPLE

> **Never let implementation convenience silently change what IronMind means.**

---

# 342. FINAL ARCHITECTURAL PROTECTION PRINCIPLE

> **Keep observation, state, reasoning, policy, intervention, and execution separated.**

---

# 343. FINAL AUTONOMY PROTECTION PRINCIPLE

> **Never increase IronMind's authority as a side effect of implementing another feature.**

---

# 344. FINAL DATA PROTECTION PRINCIPLE

> **Never trade data integrity for development speed.**

---

# 345. FINAL TESTING PRINCIPLE

> **If important behavior is not tested, it is not yet trusted.**

---

# 346. FINAL OBSERVABILITY PRINCIPLE

> **If we cannot tell what happened, why it happened, and where it failed, the system is not sufficiently observable.**

---

# 347. FINAL GIT PRINCIPLE

> **Every meaningful change should be easy to inspect and, when necessary, easy to revert.**

---

# 348. FINAL DEVELOPMENT NORTH STAR

```text
SMALL CHANGE
    ↓
CLEAR INTENT
    ↓
CORRECT IMPLEMENTATION
    ↓
TESTED BEHAVIOR
    ↓
OBSERVABLE SYSTEM
    ↓
REVIEWED DIFF
    ↓
FOCUSED COMMIT
    ↓
SAFE PROGRESS
```

---

# 349. FINAL ENGINEERING RULE

> **Build IronMind deliberately. Never allow AI-generated code to become the accidental architect of the product.**

---

# END OF DEVELOPMENT RULES

````