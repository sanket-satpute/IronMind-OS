`SYSTEM_ARCHITECTURE.md`

````md
# IRONMIND SYSTEM ARCHITECTURE

**Document:** `SYSTEM_ARCHITECTURE.md`  
**Status:** AUTHORITATIVE SYSTEM ARCHITECTURE CONTRACT  
**Parent Documents:**
- `IRONMIND_MASTER_BLUEPRINT.md`
- `PRODUCT_CONSTITUTION.md`

**Purpose:** Define how IronMind is technically structured, how its components communicate, where responsibilities live, how data flows, and how future development must preserve architectural boundaries.

---

# 1. DOCUMENT PURPOSE

This document defines the technical architecture of IronMind.

The Master Blueprint defines the overall product vision.

The Product Constitution defines what IronMind must and must not become.

This document defines:

- system boundaries
- application layers
- module responsibilities
- data flow
- event flow
- state management
- local persistence
- backend responsibilities
- AI boundaries
- decision engine
- intervention engine
- background processing
- Android system integrations
- synchronization
- observability
- error handling
- security boundaries
- testing architecture
- development architecture
- scalability principles

This document exists primarily to prevent architectural drift.

An AI coding agent must not restructure the application simply because another architecture appears more convenient.

---

# 2. ARCHITECTURAL NORTH STAR

The architecture must support this product loop:

```text
UNDERSTAND
    ↓
PLAN
    ↓
COMMIT
    ↓
PROTECT
    ↓
ACT
    ↓
RESULT
    ↓
REFLECT
    ↓
LEARN
    ↓
ADAPT
    ↓
REPEAT
````

The technical architecture must make this loop possible without coupling every subsystem together.

---

# 3. CORE ARCHITECTURAL PRINCIPLE

The system follows this high-level model:

```text
                    USER
                     │
                     ▼
               ANDROID CLIENT
                     │
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
       UI        SYSTEM INPUT   USER INPUT
        │            │            │
        └────────────┼────────────┘
                     ▼
               DOMAIN LAYER
                     │
          ┌──────────┼──────────┐
          │          │          │
          ▼          ▼          ▼
       Actions     Events     State
          │          │          │
          └─────┬────┴─────┬────┘
                │          │
                ▼          ▼
          LOCAL DATA     BACKEND
                │          │
                └────┬─────┘
                     ▼
              PERSONAL MEMORY
                     │
                     ▼
              CONTEXT ENGINE
                     │
                     ▼
               AI REASONING
                     │
                     ▼
              DECISION ENGINE
                     │
                     ▼
           INTERVENTION ENGINE
                     │
                     ▼
              ACTION EXECUTION
                     │
                     ▼
                  DEVICE
```

---

# 4. ARCHITECTURAL RULE

No subsystem should have unrestricted access to every other subsystem.

Dependencies must follow explicit boundaries.

Prefer:

```text
UI
 ↓
Domain
 ↓
Repositories / Interfaces
 ↓
Data / System Implementations
```

and:

```text
Observation
 ↓
Event
 ↓
State / Memory
 ↓
Reasoning
 ↓
Decision
 ↓
Execution
```

Avoid:

```text
UI
 ↘
  Database
 ↘
  AI
 ↘
  Android APIs
 ↘
  Backend
 ↘
  Notifications
```

all directly from one feature.

---

# 5. PRIMARY ARCHITECTURAL LAYERS

IronMind is conceptually divided into the following layers:

```text
1. EXPERIENCE
2. DOMAIN
3. OBSERVATION
4. DATA
5. INTELLIGENCE
6. DECISION
7. INTERVENTION
8. EXECUTION
9. INFRASTRUCTURE
```

These layers may map to packages/modules differently during implementation, but the responsibility boundaries must remain.

---

# 6. EXPERIENCE LAYER

The Experience layer contains everything directly responsible for presenting information to the user.

Responsibilities:

* screens
* Compose UI
* navigation
* UI state
* user interaction
* accessibility of UI
* user-facing error states
* user-facing settings
* user-facing reflection
* user-facing intervention actions

The Experience layer should not contain complex business rules.

---

# 7. EXPERIENCE LAYER MUST NOT OWN DOMAIN LOGIC

Bad:

```text
Compose screen decides:
"If current time > 8 PM and task not complete, mark task missed."
```

Good:

```text
UI
 ↓
Domain use case
 ↓
Commitment state transition
```

The UI displays state.

The domain owns behavior.

---

# 8. PRESENTATION ARCHITECTURE

Android UI should use a presentation pattern such as:

```text
Composable
   ↓
ViewModel / Presentation Model
   ↓
Use Case
   ↓
Repository / Domain
```

ViewModels coordinate presentation state.

They should not become enormous service classes containing the entire application.

---

# 9. UI STATE

UI state should represent what the user interface currently needs.

Example:

```text
TodayUiState
├── currentCommitment
├── upcomingActions
├── completedCount
├── postponedCount
├── activeProtection
└── loading/error state
```

UI state should not become the canonical database representation.

---

# 10. NAVIGATION

Navigation should remain independent from business logic.

Navigation should define:

* destinations
* navigation arguments
* navigation flow

It should not contain:

* AI logic
* database writes
* intervention policy
* background processing

---

# 11. DOMAIN LAYER

The Domain layer contains IronMind's core business meaning.

Responsibilities include:

* Goals
* Ambitions
* Commitments
* Tasks
* Plans
* Outcomes
* Reflections
* Patterns
* Memories
* Interventions
* Autonomy
* Protection

The Domain layer should remain as platform-independent as reasonably practical.

---

# 12. DOMAIN RULE

If a rule describes:

> "What IronMind means"

it belongs in the domain or a domain-facing policy layer.

If a rule describes:

> "How Android performs it"

it belongs in an infrastructure/system layer.

---

# 13. USE CASES

Complex domain actions should be represented using explicit use cases.

Examples:

```text
CreateGoal
CreateCommitment
StartCommitment
CompleteCommitment
PostponeCommitment
RecoverCommitment
CreateTask
CreatePlan
SubmitReflection
UpdatePattern
TriggerIntervention
EvaluateProtection
```

Exact use-case naming may evolve.

The principle is:

> Important business actions should be explicit and testable.

---

# 14. DOMAIN STATE TRANSITIONS

State transitions must be explicit.

Example:

```text
PLANNED
   ↓
COMMITTED
   ↓
STARTED
   ↓
COMPLETED
```

Alternative:

```text
COMMITTED
   ↓
POSTPONED
   ↓
RESCHEDULED
   ↓
STARTED
```

Failure:

```text
COMMITTED
   ↓
MISSED
   ↓
RECOVERED
```

Abandonment:

```text
COMMITTED
   ↓
ABANDONED
```

The exact state vocabulary will be finalized in `DATA_CONTRACT.md`.

---

# 15. STATE MACHINE RULE

Do not modify important state by assigning arbitrary values from unrelated parts of the application.

Bad:

```kotlin
commitment.status = "completed"
```

from arbitrary UI code.

Prefer an explicit domain operation such as:

```text
CompleteCommitment
```

which:

1. validates the current state
2. changes state
3. records the event
4. produces the expected outcome
5. logs the transition

---

# 16. OBSERVATION LAYER

The Observation layer receives information about what actually happened.

Potential observations:

* app usage
* app launches
* notification interactions
* user input
* task actions
* commitment transitions
* context changes
* location/context
* calendar information
* voice reflection
* intervention responses

Observations are raw or normalized evidence.

---

# 17. OBSERVATION DOES NOT EQUAL INTERPRETATION

Observation:

```text
Instagram opened 8 times between 8 PM and 10 PM.
```

Inference:

```text
Evening Instagram usage may correlate with study interruption.
```

These must not be stored as equivalent facts.

---

# 18. OBSERVATION INGESTION PIPELINE

The general flow should be:

```text
SOURCE
 ↓
OBSERVATION ADAPTER
 ↓
NORMALIZATION
 ↓
EVENT
 ↓
EVENT STORE
 ↓
PROCESSORS
```

Examples of sources:

```text
Android system
User input
Voice
Calendar
External integration
Internal domain action
```

---

# 19. EVENT SYSTEM

Events are the historical record of meaningful things that happened.

Examples:

```text
GOAL_CREATED
COMMITMENT_CREATED
COMMITMENT_STARTED
COMMITMENT_COMPLETED
COMMITMENT_POSTPONED
COMMITMENT_MISSED
COMMITMENT_RECOVERED

APP_OPENED
APP_CLOSED

PROTECTION_ENABLED
PROTECTION_DISABLED

INTERVENTION_TRIGGERED
INTERVENTION_ACCEPTED
INTERVENTION_IGNORED

REFLECTION_STARTED
REFLECTION_COMPLETED

VOICE_CAPTURED
VOICE_TRANSCRIBED

MEMORY_CREATED
MEMORY_UPDATED

PATTERN_CREATED
PATTERN_UPDATED
PATTERN_DECAYED
```

The final event schema will be defined in `DATA_CONTRACT.md`.

---

# 20. EVENT-FIRST PRINCIPLE

Meaningful state transitions should generate domain events or equivalent persistent history.

For example:

```text
Commitment completed
```

should produce:

```text
Current State:
COMPLETED

Historical Event:
COMMITMENT_COMPLETED
```

---

# 21. EVENTS MUST BE TRACEABLE

Important events should have enough metadata to answer:

```text
What happened?
When?
To what entity?
Who/what caused it?
What context was relevant?
What was the previous state?
What is the resulting state?
```

---

# 22. EVENT IDS

Each persisted event should have a unique identifier.

This enables:

* deduplication
* synchronization
* tracing
* debugging
* idempotency

---

# 23. EVENT TIMESTAMPING

Important events should contain appropriate timestamps.

Where relevant, distinguish:

```text
OccurredAt
RecordedAt
ProcessedAt
```

This matters because an event may happen offline and synchronize later.

---

# 24. EVENT IMMUTABILITY

Historical events should generally be treated as append-oriented records.

Do not casually rewrite history.

If a previous event was wrong, prefer recording corrective information instead of silently mutating historical truth unless the architecture explicitly defines another approach.

---

# 25. CURRENT STATE VS HISTORY

Current state answers:

> What is true now?

Events answer:

> What happened?

Both may be required.

Example:

```text
Commitment status:
COMPLETED

Event history:
CREATED
STARTED
COMPLETED
```

---

# 26. DATA LAYER

The Data layer handles persistence and retrieval.

It includes:

* local database
* remote database
* repositories
* DTOs
* entity mapping
* serialization
* synchronization
* caching

The Data layer should not redefine domain semantics.

---

# 27. REPOSITORY PATTERN

Domain-facing repositories should abstract persistence.

Conceptually:

```text
GoalRepository
CommitmentRepository
TaskRepository
EventRepository
ReflectionRepository
PatternRepository
MemoryRepository
InterventionRepository
```

Exact interfaces are defined later.

---

# 28. LOCAL PERSISTENCE

The initial Android local database should use Room unless a future architecture decision replaces it.

Local storage may contain:

* active commitments
* goals
* tasks
* local events
* intervention state
* protection state
* autonomy settings
* pending synchronization
* relevant personal model data

---

# 29. LOCAL STORAGE PRINCIPLE

Local storage exists for:

* fast access
* offline operation
* immediate state changes
* device-local behavior
* resilience

It should not become an unstructured dump of every object used by the application.

---

# 30. REMOTE STORAGE

The backend should provide persistent long-term storage for appropriate user data.

Potential backend responsibilities:

* user account
* goals
* ambitions
* commitments
* event history
* reflections
* patterns
* memory
* intervention history
* synchronization state

---

# 31. BACKEND IS NOT THE REAL-TIME EXECUTION LAYER

Do not rely on backend round trips for time-sensitive local device actions where local execution is practical.

For example:

```text
Active focus session
+
protected application
```

should be enforceable locally.

---

# 32. RECOMMENDED BACKEND DIRECTION

Initial backend direction:

```text
Firebase Authentication
Firestore
Cloud Functions
Scheduled backend processing where appropriate
```

This is a pragmatic starting point.

The system must preserve abstractions so the backend can be replaced later if necessary.

---

# 33. BACKEND ABSTRACTION

Android code should not depend on Firebase APIs throughout the domain layer.

Prefer:

```text
Domain
 ↓
Repository Interface
 ↓
Firebase Repository Implementation
```

This reduces vendor lock-in.

---

# 34. SYNCHRONIZATION

Synchronization may eventually follow:

```text
LOCAL CHANGE
 ↓
LOCAL PERSISTENCE
 ↓
OUTBOX / PENDING SYNC
 ↓
REMOTE WRITE
 ↓
SYNC CONFIRMATION
 ↓
SYNC STATE UPDATE
```

Exact synchronization strategy will be defined during implementation.

---

# 35. OFFLINE-FIRST PRINCIPLE

Important functionality should continue where reasonably possible without network access.

Examples:

* viewing local commitments
* starting a local session
* completing a local commitment
* protection
* basic notifications
* event recording

AI and remote persistence may be temporarily unavailable.

The system should degrade gracefully.

---

# 36. SYNC RETRIES

Network failures should not automatically discard user actions.

Where appropriate:

```text
FAILED
 ↓
RETRY
 ↓
SUCCESS
```

The retry mechanism must avoid duplicate side effects.

---

# 37. IDEMPOTENCY

Operations that may be retried must be safe against duplicate processing.

Example:

A completion event may be delivered twice.

The system must not:

* complete twice
* trigger duplicate interventions
* send duplicate notifications

without an explicit reason.

---

# 38. INTELLIGENCE LAYER

The Intelligence layer converts accumulated information into understanding.

It includes:

* aggregation
* pattern analysis
* memory formation
* context construction
* AI reasoning
* recommendation generation
* model adaptation

---

# 39. PERSONAL MODEL

The Personal Model represents the system's structured understanding of the user.

Conceptually:

```text
PERSONAL MODEL
├── Goals
├── Ambitions
├── Commitments
├── Preferences
├── Patterns
├── Barrier hypotheses
├── Strength patterns
├── Context relationships
├── Reflection history
├── Intervention history
└── Relevant outcomes
```

---

# 40. PERSONAL MODEL IS NOT ONE GIANT AI PROMPT

The personal model should be structured data.

It should not exist only as text passed into a language model.

Structured data enables:

* querying
* updates
* confidence
* expiration
* testing
* auditing
* efficient context generation

---

# 41. MEMORY LAYER

Memory stores durable information that may matter in future reasoning.

Examples:

```text
User goal
User preference
Confirmed barrier
Confirmed success condition
Long-term ambition
Relevant personal context
```

Not every event becomes memory.

---

# 42. MEMORY PROMOTION

A useful conceptual pipeline:

```text
EVENT
 ↓
REPEATED / IMPORTANT INFORMATION
 ↓
CANDIDATE MEMORY
 ↓
VALIDATION
 ↓
MEMORY
```

AI should not automatically convert every statement into permanent memory.

---

# 43. MEMORY PROVENANCE

Every important memory should conceptually preserve:

```text
Source
Evidence
CreatedAt
LastObserved
Confidence
ConfirmationState
Status
```

This allows the system to know how trustworthy the memory is.

---

# 44. PATTERN ENGINE

The Pattern Engine looks for repeated relationships.

Examples:

```text
Context
→ Behavior

Intervention
→ Outcome

Task Size
→ Completion Probability

Time
→ Postponement Probability

Social Context
→ Success Probability
```

Patterns are hypotheses supported by evidence.

---

# 45. PATTERN CONFIDENCE

A pattern should support:

```text
confidence
evidenceCount
firstObservedAt
lastObservedAt
status
```

Potential status:

```text
ACTIVE
DECAYING
INACTIVE
```

---

# 46. PATTERN DECAY

Patterns should lose influence when no longer supported.

Conceptually:

```text
Repeated evidence
 ↓
High confidence

No recent evidence
 ↓
Confidence decay

Long-term unsupported
 ↓
Inactive
```

Exact mathematical behavior belongs in the data/intelligence implementation contracts.

---

# 47. CONTEXT ENGINE

The Context Engine determines what information is relevant to the current situation.

Potential inputs:

```text
current time
day
active commitment
current task
recent application activity
calendar
recent interventions
recent outcomes
user preferences
location/context where permitted
recent reflection
relevant personal patterns
```

---

# 48. CONTEXT MUST BE SELECTIVE

Do not send all available personal information to every decision.

Context should be:

* relevant
* minimal
* recent where important
* privacy-conscious
* explainable

---

# 49. AI LAYER

The AI layer provides reasoning capabilities.

Potential responsibilities:

* natural-language understanding
* reflection interpretation
* goal decomposition
* plan generation
* pattern hypothesis
* barrier hypothesis
* personalized recommendations
* summarization
* intervention recommendation

---

# 50. AI ABSTRACTION

The product should expose an internal abstraction such as:

```text
IronMindAI
```

Implementations may include:

```text
GeminiIronMindAI
```

or another future provider.

The rest of the application must not depend directly on a specific AI provider whenever practical.

---

# 51. AI PROVIDER INDEPENDENCE

A future migration:

```text
Gemini
→
Provider B
```

should primarily require changing AI infrastructure rather than rewriting the entire application.

---

# 52. AI REQUEST PIPELINE

Conceptually:

```text
DOMAIN / EVENT
 ↓
RELEVANT CONTEXT
 ↓
AI REQUEST BUILDER
 ↓
IRONMIND AI
 ↓
STRUCTURED OUTPUT
 ↓
VALIDATION
 ↓
DOMAIN / DECISION SYSTEM
```

---

# 53. AI OUTPUTS MUST BE STRUCTURED

Avoid using unconstrained natural language as the only control mechanism.

For example:

```json
{
  "recommendation": "BREAK_DOWN",
  "confidence": 0.78,
  "reason": "The task appears to be too large to start easily",
  "suggestedAction": "Define the first 10-minute step"
}
```

The exact schemas will be defined later.

---

# 54. AI OUTPUT VALIDATION

AI-generated results must be validated before being accepted.

Validate:

* schema
* required fields
* allowed enum values
* confidence range
* referenced entities
* policy compatibility
* safety constraints

Invalid AI output should not directly execute.

---

# 55. AI MUST NOT DIRECTLY EXECUTE DEVICE ACTIONS

The following architecture is forbidden:

```text
AI
 ↓
arbitrary Android command
```

Preferred:

```text
AI
 ↓
structured recommendation
 ↓
Decision Engine
 ↓
approved intervention
 ↓
Execution layer
```

---

# 56. DECISION ENGINE

The Decision Engine determines what IronMind should actually do.

It evaluates:

* current state
* user autonomy
* context
* AI recommendation
* rules
* cooldowns
* permissions
* safety constraints
* recent interventions

---

# 57. DECISION ENGINE PRINCIPLE

The Decision Engine is the policy boundary between:

```text
"What could be useful?"
```

and:

```text
"What IronMind is actually allowed to do."
```

---

# 58. DECISION FLOW

Conceptually:

```text
TRIGGER
 ↓
BUILD CONTEXT
 ↓
OPTIONALLY ASK AI
 ↓
GENERATE RECOMMENDATION
 ↓
CHECK USER AUTONOMY
 ↓
CHECK POLICY
 ↓
CHECK CONTEXT
 ↓
CHECK COOLDOWN
 ↓
CHECK PERMISSIONS
 ↓
DECIDE
 ↓
EXECUTE OR STAY SILENT
```

---

# 59. STAY SILENT

A valid decision is:

```text
STAY_SILENT
```

The Decision Engine must support no-action outcomes.

---

# 60. AUTONOMY ENGINE

The Autonomy layer determines whether the system may:

* suggest
* ask
* act automatically

Conceptually:

```text
OFF
SUGGEST_ONLY
ASK_BEFORE_ACTION
FULL_AUTO
```

The exact policy is defined in:

`AUTONOMY_POLICY.md`

---

# 61. AUTONOMY MUST BE EXPLICIT

The system must never infer:

> "The user probably wants this automated."

Autonomy comes from explicit policy.

---

# 62. INTERVENTION ENGINE

The Intervention Engine converts an approved decision into an actual intervention.

Potential intervention types:

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

---

# 63. INTERVENTION FLOW

```text
DECISION
 ↓
INTERVENTION TYPE
 ↓
MESSAGE / ACTION BUILDER
 ↓
COOLDOWN CHECK
 ↓
DELIVERY
 ↓
USER RESPONSE
 ↓
OUTCOME
 ↓
EVENT
 ↓
LEARNING
```

---

# 64. INTERVENTION COOLDOWNS

Interventions should track enough information to prevent spam.

Potential fields:

```text
interventionType
target
trigger
createdAt
deliveredAt
cooldownUntil
status
outcome
```

---

# 65. INTERVENTION DEDUPLICATION

Before delivering a new intervention, determine whether a semantically equivalent intervention has already occurred recently.

Do not simply compare exact strings.

The product may eventually use:

```text
intervention purpose
target
context
time window
```

to determine duplication.

---

# 66. INTERVENTION OUTCOMES

Possible outcomes:

```text
ACCEPTED
IGNORED
DISMISSED
OVERRIDDEN
COMPLETED
FAILED
EXPIRED
```

The exact vocabulary belongs in the data contract.

---

# 67. INTERVENTION LEARNING

The system should eventually learn:

```text
Intervention
+
Context
+
Outcome
```

Example:

```text
BREAK_DOWN
+
Large ambiguous task
+
Completed
```

This can become evidence that the intervention may be useful in similar conditions.

---

# 68. PROTECTION ENGINE

The Protection Engine handles distraction protection.

Potential responsibilities:

* active protection session
* selected packages
* protection rules
* temporary restrictions
* overrides
* protection state
* protection lifecycle

---

# 69. PROTECTION BOUNDARY

Protection logic should not be buried inside UI code.

The flow should be:

```text
Commitment / Decision
 ↓
Protection Policy
 ↓
Protection State
 ↓
Android Execution
```

---

# 70. PROTECTION MUST BE CONTEXTUAL

Protection may depend on:

* active commitment
* current schedule
* user settings
* selected applications
* current context
* learned distraction risk

Protection should not globally declare an application "bad."

---

# 71. PROTECTION OVERRIDE

The user should have an appropriate mechanism for overriding protection according to their configuration.

Overrides should be recorded where useful.

Example:

```text
PROTECTION_OVERRIDE
```

This can become behavioral evidence.

---

# 72. ANDROID SYSTEM INTEGRATION LAYER

This layer interacts with Android APIs.

Potential components:

```text
Usage Observation
Notification Observation
Accessibility
Foreground Services
WorkManager
Notifications
Audio / Microphone
Location
Calendar
App Restrictions / Protection
```

Only capabilities actually required by the implementation should be included.

---

# 73. ANDROID INTEGRATION RULE

Android-specific APIs should not leak throughout the domain layer.

Prefer adapters such as:

```text
UsageStatsProvider
NotificationProvider
VoiceCaptureProvider
LocationProvider
CalendarProvider
AppProtectionProvider
```

The actual Android implementation can sit behind these interfaces.

---

# 74. PERMISSION BOUNDARY

Permission checks belong near system integration boundaries.

A subsystem should know how to respond if a permission is:

```text
GRANTED
DENIED
REVOKED
UNAVAILABLE
```

The rest of the domain should not depend on raw permission API details.

---

# 75. BACKGROUND PROCESSING

Background work should be divided by purpose.

Possible categories:

```text
Observation Processing
Event Processing
Synchronization
Pattern Analysis
Memory Processing
Notification Scheduling
Nightly Review
Goal Resurfacing
Cleanup
```

---

# 76. BACKGROUND EXECUTION PRINCIPLE

Use Android-supported mechanisms appropriate to the workload.

Do not assume unlimited background execution.

Do not create a permanent background process when a scheduled or event-driven mechanism is sufficient.

---

# 77. WORKMANAGER

WorkManager is the preferred mechanism for appropriate deferrable background jobs unless a specific platform requirement justifies another mechanism.

Examples:

* synchronization
* periodic analysis
* cleanup
* non-immediate processing

---

# 78. FOREGROUND EXECUTION

Foreground execution should be used only where the product genuinely requires an ongoing user-visible operation.

Examples may include an active user-started session where continuous operation is necessary.

Do not use foreground execution as a generic workaround for background restrictions.

---

# 79. BACKGROUND JOB DESIGN

Each important background job should be:

* bounded
* observable
* retryable where appropriate
* cancellable
* idempotent where possible
* battery-conscious
* network-aware

---

# 80. BACKGROUND JOB LOGGING

Example:

```text
IronMindLifecycle BackgroundWorker STARTED job=PatternAnalysis
```

and:

```text
IronMindLifecycle BackgroundWorker COMPLETED job=PatternAnalysis durationMs=4210
```

Failures:

```text
IronMindLifecycle BackgroundWorker FAILED job=PatternAnalysis error=...
```

---

# 81. NIGHTLY REVIEW ARCHITECTURE

Nightly review should eventually use:

```text
Daily Events
+
Commitment Outcomes
+
Relevant Observations
+
User Reflection
```

Pipeline:

```text
DAY DATA
 ↓
DAILY SUMMARY
 ↓
NOTIFICATION
 ↓
USER TEXT / VOICE
 ↓
TRANSCRIPTION
 ↓
UNDERSTANDING
 ↓
STRUCTURED EXTRACTION
 ↓
MEMORY UPDATE
 ↓
PATTERN UPDATE
 ↓
NEXT-DAY CONTEXT
```

---

# 82. VOICE ARCHITECTURE

Voice should be treated as an input pipeline.

```text
USER SPEAKS
 ↓
AUDIO CAPTURE
 ↓
SPEECH-TO-TEXT
 ↓
TRANSCRIPT
 ↓
LANGUAGE UNDERSTANDING
 ↓
STRUCTURED DATA
 ↓
DOMAIN UPDATE
```

---

# 83. SPEECH-TO-TEXT ABSTRACTION

The app should not hard-code business logic to a single transcription provider where practical.

Conceptually:

```text
SpeechToTextProvider
```

allows future provider changes.

---

# 84. VOICE FAILURE

If transcription fails:

```text
VOICE_CAPTURED
 ↓
TRANSCRIPTION_FAILED
 ↓
USER INFORMED
 ↓
RETRY / TEXT FALLBACK
```

Do not silently discard user input.

---

# 85. PERSONAL MODEL UPDATE PIPELINE

The personal model should be updated using:

```text
Events
+
Outcomes
+
Reflections
+
User corrections
```

Conceptually:

```text
NEW EVIDENCE
 ↓
RELEVANT EXISTING MEMORY
 ↓
COMPARE
 ↓
UPDATE CONFIDENCE
 ↓
CREATE / UPDATE / RETIRE
```

---

# 86. USER CORRECTIONS

Explicit user corrections should be treated as high-value evidence.

Example:

```text
Pattern:
Solo gym sessions are often postponed.

User:
"That's because my schedule changed, not because I dislike going alone."
```

The model should update accordingly.

---

# 87. MODEL UPDATE MUST BE AUDITABLE

Important memory or pattern changes should create events.

Examples:

```text
MEMORY_CREATED
MEMORY_UPDATED
PATTERN_CREATED
PATTERN_UPDATED
PATTERN_DECAYED
```

---

# 88. CONTEXT-AWARE DECISION FLOW

A future intelligent intervention may follow:

```text
CURRENT SITUATION
 ↓
CURRENT COMMITMENT
 ↓
RECENT BEHAVIOR
 ↓
RELEVANT PATTERNS
 ↓
USER PREFERENCES
 ↓
RECENT INTERVENTIONS
 ↓
AI RECOMMENDATION
 ↓
POLICY CHECK
 ↓
ACTION
```

---

# 89. DATA FLOW: GOAL → ACTION

```text
USER INPUT
 ↓
CREATE GOAL
 ↓
GOAL PERSISTED
 ↓
GOAL_CREATED EVENT
 ↓
PLAN / TASK CREATION
 ↓
COMMITMENT
 ↓
COMMITMENT_CREATED EVENT
 ↓
SCHEDULE
 ↓
ACTION
 ↓
RESULT
 ↓
OUTCOME EVENT
```

---

# 90. DATA FLOW: DISTRACTION

Example:

```text
ACTIVE STUDY COMMITMENT
 ↓
USER OPENS DISTRACTING APPLICATION
 ↓
OBSERVATION
 ↓
EVENT
 ↓
CONTEXT EVALUATION
 ↓
DISTRACTION RISK
 ↓
DECISION ENGINE
 ↓
PROTECTION / REDIRECT / SILENCE
 ↓
INTERVENTION OUTCOME
 ↓
LEARNING
```

---

# 91. DATA FLOW: NIGHTLY REFLECTION

```text
DAY ENDS
 ↓
DAILY SUMMARY
 ↓
USER NOTIFIED
 ↓
VOICE / TEXT
 ↓
TRANSCRIPT
 ↓
AI EXTRACTION
 ↓
OBSERVATIONS / BARRIERS / SUCCESS CONDITIONS
 ↓
MEMORY / PATTERN UPDATE
 ↓
TOMORROW CONTEXT
```

---

# 92. DATA FLOW: GOAL RESURFACING

```text
LONG-TERM GOAL
 ↓
NO RECENT PROGRESS
 ↓
NEGLECT DETECTION
 ↓
CHECK IMPORTANCE
 ↓
DECISION
 ↓
RESURFACE
 ↓
USER RESPONSE
 ↓
UPDATE GOAL STATE
```

---

# 93. SYSTEM OF RECORD

Each important state must have a canonical source.

For every domain entity, implementation must determine:

```text
Where is the source of truth?
Who owns mutations?
How is state synchronized?
How is history recorded?
```

No duplicated competing sources of truth should be introduced casually.

---

# 94. CANONICAL STATE

Examples of likely canonical ownership:

```text
Commitment current state
→ Commitment domain/store

Protection current state
→ Protection subsystem

Autonomy settings
→ Autonomy configuration store

Historical events
→ Event store

Long-term patterns
→ Pattern / personal model store
```

Exact details belong in `DATA_CONTRACT.md`.

---

# 95. CACHE POLICY

Caches are not sources of truth.

A cache may accelerate reads.

It must be possible to invalidate or rebuild it from authoritative data where practical.

---

# 96. CONFIGURATION

Configuration should be separated from user-generated domain data.

Examples:

```text
App configuration
Feature flags
AI provider configuration
Environment configuration
Debug configuration
```

must not be mixed with:

```text
User goals
User commitments
Personal memory
```

---

# 97. ENVIRONMENT SEPARATION

The application should support clear separation between:

```text
Development
Testing
Production
```

Credentials and endpoints must not be mixed.

---

# 98. SECRETS

Never hard-code:

* API keys
* service credentials
* tokens
* passwords
* private signing values

in source code.

---

# 99. OBSERVABILITY ARCHITECTURE

Every important subsystem should expose enough observability to understand:

```text
What happened?
Why?
What state changed?
What failed?
```

Observability consists of:

* lifecycle logs
* structured events
* error reporting
* state inspection
* test evidence

---

# 100. LIFECYCLE LOGGING STANDARD

All important state transitions should follow:

```text
IronMindLifecycle [Component] [EVENT] key=value ...
```

Examples:

```text
IronMindLifecycle Mission CREATED missionId=123
```

```text
IronMindLifecycle Protection ENABLED package=com.example.app reason=active_commitment
```

```text
IronMindLifecycle Intervention TRIGGERED type=REDIRECT source=pattern
```

```text
IronMindLifecycle Reflection COMPLETED reflectionId=42
```

---

# 101. LOG CONTENT RULE

Logs should provide operational value without becoming a dump of personal content.

Avoid unnecessarily logging:

* raw voice transcripts
* raw private reflections
* sensitive personal content
* tokens
* passwords
* authentication credentials

---

# 102. CORRELATION IDS

Important asynchronous operations should support correlation or operation identifiers where useful.

This allows tracing:

```text
Trigger
 ↓
Decision
 ↓
Intervention
 ↓
Outcome
```

through logs.

---

# 103. ERROR HANDLING ARCHITECTURE

Errors should be classified.

Conceptually:

```text
USER_ERROR
DOMAIN_ERROR
VALIDATION_ERROR
PERMISSION_ERROR
NETWORK_ERROR
AI_ERROR
STORAGE_ERROR
BACKGROUND_ERROR
SYSTEM_ERROR
```

The exact hierarchy is implementation-specific.

---

# 104. ERROR HANDLING PRINCIPLE

Errors should not silently disappear.

Each important error should result in an appropriate combination of:

* logs
* state
* retry
* user feedback
* fallback
* telemetry

---

# 105. AI FAILURE MODE

When AI is unavailable:

```text
AI unavailable
 ↓
Use deterministic fallback where possible
 ↓
Continue core operation
```

IronMind must not become completely unusable because the AI provider is temporarily unavailable.

---

# 106. NETWORK FAILURE MODE

When network is unavailable:

```text
Continue locally where possible
 ↓
Persist pending changes
 ↓
Retry synchronization
```

---

# 107. PERMISSION FAILURE MODE

If a feature requires permission:

```text
Permission unavailable
 ↓
Feature reports unavailable
 ↓
Core app continues
```

Do not crash the application because optional capability access is unavailable.

---

# 108. DATABASE FAILURE MODE

Database failures should:

* log clearly
* avoid corruption
* surface appropriate errors
* preserve user data where possible

The architecture must treat persistence as critical infrastructure.

---

# 109. TESTING ARCHITECTURE

Testing should exist at multiple levels.

```text
UNIT
 ↓
DOMAIN
 ↓
DATA
 ↓
INTEGRATION
 ↓
SYSTEM
 ↓
UI
```

Not every feature requires every layer, but important behavior must be tested at the correct boundary.

---

# 110. UNIT TESTS

Use unit tests for:

* domain rules
* state transitions
* utility logic
* pattern calculations
* autonomy policy
* intervention eligibility
* cooldown logic

---

# 111. DOMAIN TESTS

The domain layer must be highly testable without requiring the Android UI.

Examples:

```text
CompleteCommitment
PostponeCommitment
RecoverCommitment
EvaluateProtection
EvaluateIntervention
UpdatePattern
```

---

# 112. DATA TESTS

Test:

* persistence
* serialization
* mapping
* migration
* synchronization logic
* duplicate handling

---

# 113. AI TESTS

AI-related tests should not depend solely on live model calls.

Use:

* mocked providers
* fixed AI responses
* schema tests
* invalid-output tests
* edge cases

Example:

```text
Valid AI response
Invalid enum
Missing confidence
Malformed JSON
Out-of-range confidence
Unsupported recommendation
```

---

# 114. DECISION ENGINE TESTS

The Decision Engine should have deterministic tests.

Examples:

```text
Autonomy OFF
→ no automatic action

Suggest Only
→ suggestion produced

Ask Before Action
→ confirmation required

Full Auto
→ permitted action may execute
```

---

# 115. INTERVENTION TESTS

Test:

* trigger conditions
* cooldowns
* deduplication
* priority
* suppression
* user overrides
* outcome recording

---

# 116. BACKGROUND TESTS

Test:

* job scheduling
* retry
* cancellation
* idempotency
* failure recovery

---

# 117. LOG TESTS

Where lifecycle logs are part of the contract, tests should verify important lifecycle events exist.

Example:

```text
Commitment created
→ CREATED lifecycle log

Commitment completed
→ COMPLETED lifecycle log
```

---

# 118. UI TESTS

UI tests should focus on important user journeys.

Examples:

```text
Create Goal
Create Commitment
Start Commitment
Complete Commitment
Postpone Commitment
Start Protection
Override Protection
Complete Reflection
```

---

# 119. END-TO-END TESTS

Important complete flows should eventually be validated:

```text
Goal
→ Commitment
→ Action
→ Outcome
→ Reflection
→ Model update
```

and:

```text
Active commitment
→ Distraction
→ Decision
→ Protection
→ User response
```

---

# 120. ARCHITECTURAL TESTABILITY

Every subsystem should have a testable boundary.

If a feature cannot be tested without launching the entire application, consider whether its responsibilities are too tightly coupled.

---

# 121. SECURITY ARCHITECTURE

Security boundaries must exist at:

```text
Android
Backend
Authentication
Database
AI
Logs
Secrets
```

---

# 122. AUTHENTICATION

Authentication belongs to infrastructure/data concerns, not domain logic.

The domain should not depend directly on Firebase authentication APIs.

---

# 123. BACKEND SECURITY

Backend access must use appropriate authorization rules.

A user's personal data must not become readable or writable by arbitrary users.

Exact Firebase/backend security rules must be defined during backend implementation.

---

# 124. AI DATA BOUNDARY

Only the information required for a given AI operation should be included in AI context.

Avoid unnecessary transmission of unrelated personal information.

---

# 125. AI REQUEST AUDIT

Where practical, store metadata about AI operations such as:

```text
request type
model/provider
timestamp
success/failure
latency
```

Do not automatically store raw sensitive prompts/responses forever.

Retention must be deliberate.

---

# 126. SYSTEM INTEGRATION SECURITY

Sensitive Android integrations must be isolated.

For example:

```text
Usage data
Accessibility
Microphone
Location
Calendar
```

should be accessed through explicit adapters rather than from arbitrary parts of the codebase.

---

# 127. DATA MINIMIZATION

Only collect data needed for a meaningful capability.

The architecture should permit individual observation sources to be enabled/disabled.

---

# 128. FEATURE FLAGS

Feature flags may be used for controlled rollout.

However, feature flags must not become a hidden replacement for the architecture or product contract.

Important behavioral rules should live in explicit policy/configuration.

---

# 129. DEVELOPMENT CONTROL CENTER

A development-only control center may eventually expose:

```text
Current Goal
Current Commitment
Current State
Protection State
Recent Events
Patterns
Memory
AI Decisions
Interventions
Background Jobs
Sync State
Permissions
Autonomy
```

This is a diagnostic capability.

---

# 130. DEVELOPMENT CONTROL CENTER RULE

It must not be treated as the production user experience.

It should be:

* hidden
* restricted
* removable
* development-only

where appropriate.

---

# 131. MODULE / PACKAGE ORGANIZATION

The exact package structure may evolve, but a conceptual starting point is:

```text
com.ironmind
│
├── core
│   ├── common
│   ├── logging
│   ├── errors
│   ├── time
│   └── result
│
├── domain
│   ├── goal
│   ├── ambition
│   ├── commitment
│   ├── task
│   ├── plan
│   ├── outcome
│   ├── reflection
│   ├── memory
│   ├── pattern
│   ├── intervention
│   ├── autonomy
│   └── protection
│
├── data
│   ├── local
│   ├── remote
│   ├── repository
│   └── sync
│
├── observation
│   ├── usage
│   ├── notifications
│   ├── context
│   └── events
│
├── intelligence
│   ├── context
│   ├── memory
│   ├── patterns
│   └── ai
│
├── decision
│
├── intervention
│
├── system
│   ├── usage
│   ├── accessibility
│   ├── notification
│   ├── voice
│   ├── location
│   └── calendar
│
├── background
│
└── feature
    ├── today
    ├── goals
    ├── commitments
    ├── reflection
    ├── settings
    └── debug
```

This is a conceptual starting structure, not permission for agents to create all modules immediately.

---

# 132. PACKAGE OWNERSHIP RULE

Each package/module must answer:

> What responsibility does this own?

If a package contains unrelated behavior, reconsider the boundary.

---

# 133. AVOID GOD CLASSES

Do not create giant classes such as:

```text
IronMindManager
IronMindEngine
AppManager
MainViewModel
AIManager
```

containing dozens of unrelated responsibilities.

Large classes are a warning sign.

---

# 134. AVOID GOD FILES

Do not create a single:

```text
MainActivity
MainNavHost
ApplicationManager
```

that coordinates the entire product.

Composition should happen through explicit components.

---

# 135. DOMAIN DEPENDENCY RULE

Domain code should not import Android UI components.

Avoid:

```text
domain → androidx.compose
```

or:

```text
domain → Activity
```

---

# 136. DATA DEPENDENCY RULE

Data implementations may depend on:

* Room
* Firebase
* serialization
* network libraries

but domain interfaces should remain stable.

---

# 137. SYSTEM DEPENDENCY RULE

Android-specific code belongs in system/infrastructure adapters.

Example:

```text
UsageStatsManager
```

should not be called directly from:

```text
CommitmentViewModel
```

Instead:

```text
CommitmentViewModel
 ↓
Domain / Use Case
 ↓
Usage Observation Interface
 ↓
Android Implementation
```

---

# 138. AI DEPENDENCY RULE

The domain should depend on an abstraction such as:

```text
IronMindAI
```

not directly on:

```text
Gemini SDK
```

---

# 139. BACKEND DEPENDENCY RULE

Domain repositories should not expose Firestore types.

Use domain models and repository interfaces.

---

# 140. COMPOSITION ROOT

Application wiring should happen in a controlled composition layer.

This layer can connect:

```text
Interfaces
+
Implementations
+
Dependencies
```

without spreading dependency creation throughout the application.

---

# 141. DEPENDENCY INJECTION

Use dependency injection where useful.

The exact DI framework is an implementation decision.

Do not introduce a framework solely because it is popular.

---

# 142. TIME ABSTRACTION

Logic involving:

* scheduling
* expiration
* cooldown
* pattern decay
* nightly review

should be testable without depending directly on the system clock.

Prefer a time abstraction such as:

```text
Clock
```

where appropriate.

---

# 143. ID GENERATION

IDs should be generated through a consistent mechanism.

Do not mix random formats arbitrarily.

The exact strategy will be defined in `DATA_CONTRACT.md`.

---

# 144. SERIALIZATION

Structured cross-boundary objects should use explicit serialization models.

AI requests/responses should have schemas.

Backend documents should have defined models.

Do not depend on arbitrary serialization of internal implementation objects.

---

# 145. VERSIONING

Persisted structures should support versioning where needed.

Examples:

```text
EventSchema v1
MemorySchema v1
InterventionSchema v1
```

Breaking changes require migration planning.

---

# 146. DATABASE MIGRATION RULE

Never casually modify a persisted schema without considering existing data.

Any migration must define:

```text
Old schema
↓
Migration
↓
New schema
↓
Validation
```

---

# 147. API CONTRACTS

Remote backend interfaces should use explicit contracts.

Do not make the Android application depend on undocumented backend behavior.

---

# 148. FEATURE BOUNDARIES

Features should communicate through:

* domain use cases
* repositories
* event interfaces
* explicit services

Avoid direct feature-to-feature internal database access.

---

# 149. EVENT BUS CAUTION

An event system is useful.

But do not turn the application into an uncontrolled global event bus where everything subscribes to everything.

Event types and ownership must remain deliberate.

---

# 150. SYNCHRONOUS VS ASYNCHRONOUS

Use synchronous execution for small deterministic operations where practical.

Use asynchronous processing for:

* network calls
* AI calls
* heavy analysis
* background work
* speech transcription
* synchronization

Do not introduce asynchronous complexity without reason.

---

# 151. THREADING

Long-running work must not block the main thread.

Use appropriate coroutine/background mechanisms.

Threading decisions should respect Android lifecycle behavior.

---

# 152. BATTERY EFFICIENCY

Background intelligence should minimize unnecessary:

* polling
* wakeups
* network activity
* database churn
* AI calls

Prefer:

```text
event-driven
scheduled
batched
context-triggered
```

approaches.

---

# 153. AI COST EFFICIENCY

Before invoking AI:

```text
Can deterministic logic solve this?
Can local processing solve this?
Does it actually require reasoning?
Can multiple operations be batched?
Is this worth the cost?
```

---

# 154. AI CONTEXT EFFICIENCY

Do not send entire historical memory to the model for every request.

Generate relevant context.

Example:

For a gym intervention:

```text
Current commitment
Recent gym outcomes
Relevant gym pattern
User preference
Recent intervention history
```

may be sufficient.

---

# 155. BACKGROUND AI

Background AI processing should be carefully controlled.

Do not run expensive analysis continuously.

Prefer:

```text
new meaningful evidence
 ↓
schedule analysis
 ↓
process
 ↓
update
```

rather than constant inference.

---

# 156. EVENT PROCESSING

Events may trigger different processors.

Example:

```text
COMMITMENT_COMPLETED
        ↓
Outcome Processor
        ↓
Pattern Processor
        ↓
Memory Processor
```

But each processor should have a clear responsibility.

---

# 157. EVENT PROCESSOR IDEMPOTENCY

If the same event is processed twice, processors should avoid producing duplicate side effects.

Use event IDs or equivalent mechanisms.

---

# 158. SYSTEM RESILIENCE

IronMind should tolerate:

* network outages
* AI outages
* Android process termination
* permission loss
* background job retries
* app restart
* device restart
* partial synchronization failure

where technically applicable.

---

# 159. APP RESTART

Critical state must survive app process death.

Do not rely solely on in-memory variables for important product state.

---

# 160. DEVICE RESTART

Scheduled/background capabilities should restore themselves appropriately after device restart where supported and required.

---

# 161. PROCESS DEATH

If Android terminates the process:

```text
Persistent state
+
scheduled work
+
recoverable jobs
```

should allow IronMind to recover gracefully.

---

# 162. PARTIAL FAILURE PRINCIPLE

If one subsystem fails, unrelated subsystems should continue where possible.

Example:

```text
AI unavailable
```

should not prevent:

```text
Viewing commitments
```

from working.

---

# 163. DEGRADED MODES

The system should recognize useful degraded modes.

Example:

```text
FULL
LOCAL_ONLY
OFFLINE
AI_UNAVAILABLE
PERMISSION_LIMITED
SYNC_PENDING
```

Exact state model can be defined later.

---

# 164. CORE EXECUTION PATH

The core action path should remain as simple and reliable as possible:

```text
USER
 ↓
COMMITMENT
 ↓
ACTION
 ↓
OUTCOME
```

AI and advanced intelligence should augment this path, not make basic action dependent on AI.

---

# 165. CORE PRODUCT MUST WORK WITHOUT FULL AI

At least the foundational IronMind loop should work without requiring the AI provider for every operation.

This ensures:

* reliability
* lower cost
* offline capability
* faster response
* testability

---

# 166. PRODUCT INTELLIGENCE LAYERING

Intelligence should progress approximately as:

```text
DETERMINISTIC
 ↓
STATISTICAL
 ↓
PATTERN
 ↓
AI REASONING
 ↓
AUTONOMOUS ADAPTATION
```

Do not force everything into generative AI.

---

# 167. DECISION PRIORITY

When multiple signals compete:

```text
Explicit user instruction
        ↓
Active system state
        ↓
Explicit user preference
        ↓
Recent reliable evidence
        ↓
Stable patterns
        ↓
Weak inference
```

The exact policy must eventually be defined in `AUTONOMY_POLICY.md`.

---

# 168. CURRENT INTENT VS OLD HISTORY

Current explicit user intent should generally have stronger contextual relevance than old inferred patterns.

Example:

Old pattern:

```text
User prefers morning exercise.
```

Current statement:

```text
"I want to exercise in the evening now."
```

The current explicit preference should influence the decision.

---

# 169. USER CONFIRMATION STATE

Important inferred information should support a confirmation state such as:

```text
UNCONFIRMED
USER_CONFIRMED
USER_REJECTED
```

This allows the intelligence system to distinguish hypotheses from facts.

---

# 170. PERSONAL MODEL SAFETY

Do not allow an AI response to directly overwrite high-authority user information without validation.

For example:

AI should not silently replace:

```text
User Goal:
Build business
```

with:

```text
User Goal:
Stop business
```

because of one ambiguous reflection.

---

# 171. MEMORY CONFLICTS

When new information conflicts with old memory:

```text
Old memory
+
New evidence
 ↓
Conflict evaluation
 ↓
Possible update
 ↓
Record change
```

Do not blindly overwrite history.

---

# 172. INTERVENTION SAFETY

Before an autonomous intervention:

```text
Is it allowed?
Is it appropriate?
Is it necessary?
Is it reversible?
Has it happened recently?
Will it meaningfully help?
```

---

# 173. NOTIFICATION ARCHITECTURE

Notification generation should be separated from notification delivery.

Preferred:

```text
Decision
 ↓
Notification Intent
 ↓
Notification Policy
 ↓
Android Notification Adapter
```

This makes testing easier.

---

# 174. NOTIFICATION POLICY

Notification decisions should consider:

* urgency
* relevance
* user preferences
* cooldown
* current context
* recent notifications
* intervention priority

---

# 175. USER RESPONSE TRACKING

Where technically feasible, intervention responses should become events.

Examples:

```text
OPENED
ACCEPTED
DISMISSED
IGNORED
OVERRIDDEN
```

This enables learning.

---

# 176. EXTERNAL INTEGRATIONS

Future integrations may include:

* calendar
* health/activity data
* location
* other productivity tools

Each integration must have an explicit adapter boundary.

---

# 177. INTEGRATION RULE

An external integration should not spread its vendor-specific model throughout the codebase.

Prefer:

```text
External System
 ↓
Integration Adapter
 ↓
IronMind Domain Model
```

---

# 178. IMPORTED DATA

External data should be normalized before becoming part of IronMind's domain model.

---

# 179. EXTERNAL ACTIONS

Any future external action such as:

```text
calendar modification
task creation in another service
message sending
```

must have stricter autonomy controls than passive observation.

These actions require explicit policy.

---

# 180. OBSERVATION PERMISSIONS

Each observation source should have an explicit capability boundary.

Conceptually:

```text
Usage Observation
Notification Observation
Location Observation
Calendar Observation
Voice Observation
Activity Observation
```

Each can be independently enabled or disabled where practical.

---

# 181. CONFIGURABLE DATA COLLECTION

IronMind should eventually expose meaningful controls for observation sources.

Example:

```text
App Usage
ON

Location Context
OFF

Calendar
ON

Voice Reflection
ON
```

---

# 182. DATA SOURCE STATUS

Subsystems should know when a source is:

```text
AVAILABLE
DISABLED
PERMISSION_DENIED
TEMPORARILY_UNAVAILABLE
ERROR
```

---

# 183. DEBUGGING ARCHITECTURE

When something goes wrong, engineers should be able to trace:

```text
SOURCE
 ↓
OBSERVATION
 ↓
EVENT
 ↓
STATE
 ↓
CONTEXT
 ↓
AI RECOMMENDATION
 ↓
DECISION
 ↓
INTERVENTION
 ↓
EXECUTION
 ↓
OUTCOME
```

---

# 184. DEBUGGING RULE

Do not debug exclusively from the UI.

The UI is the final manifestation of a much larger system.

Trace the full lifecycle.

---

# 185. DEVELOPMENT DIAGNOSTICS

Development builds should be able to show:

```text
Current state
Last event
Recent event stream
Active workers
AI calls
Decision results
Intervention state
Sync state
Permission state
```

where safe.

---

# 186. ARCHITECTURAL TEST

Every major feature should be answerable using:

```text
Where does state live?
Who changes it?
Who observes it?
Who reasons about it?
Who decides?
Who executes?
```

If those questions cannot be answered clearly, the feature architecture is probably insufficiently defined.

---

# 187. CHANGE MANAGEMENT

Architectural changes must be deliberate.

If changing:

* domain boundaries
* event model
* database architecture
* backend architecture
* AI architecture
* autonomy
* intervention
* permission model

the developer/AI agent must provide:

```text
WHY
WHAT CHANGES
WHY CURRENT ARCHITECTURE IS INSUFFICIENT
RISKS
MIGRATION
TEST PLAN
```

---

# 188. NO CASUAL REFACTORING

Do not refactor large portions of the codebase during a narrow feature sprint unless the refactor is required to safely implement that feature.

---

# 189. NO GLOBAL "FIXES"

Avoid broad modifications such as:

* changing compiler settings globally
* changing Gradle configuration unnecessarily
* disabling warnings globally
* suppressing errors globally
* changing all files for formatting
* replacing architecture globally

unless explicitly required.

---

# 190. DEPENDENCY CHANGES

Adding a dependency requires justification:

```text
Why needed?
Why existing APIs are insufficient?
Maintenance cost?
Security implications?
App size?
Licensing?
```

---

# 191. ARCHITECTURAL BOUNDARY REVIEW

Before introducing a new service/module, ask:

```text
Does it own a distinct responsibility?
Can it be tested independently?
Does it reduce coupling?
Does it justify its complexity?
```

---

# 192. SIMPLE IS BETTER WHEN CAPABLE

If two architectures satisfy the same requirements:

Prefer the simpler one.

Do not create abstractions merely for theoretical future possibilities.

---

# 193. ABSTRACTION SHOULD FOLLOW REAL BOUNDARIES

Good abstractions:

```text
AI provider
Speech-to-text provider
Usage observation provider
Calendar provider
Repository
```

Poor abstractions:

```text
UniversalThing
AbstractManager
GenericEngine
BaseEverything
```

---

# 194. DOMAIN LOGIC FIRST

When implementing a meaningful behavior:

```text
Define domain rule
 ↓
Test domain rule
 ↓
Connect data
 ↓
Connect UI/system
```

Do not start by putting logic inside a UI callback.

---

# 195. UI-FIRST DEVELOPMENT IS DISCOURAGED

A visually complete screen with weak domain behavior does not constitute a completed feature.

The underlying state and action flow must exist.

---

# 196. FEATURE IMPLEMENTATION ORDER

A typical feature should be implemented approximately as:

```text
DOMAIN
 ↓
DATA
 ↓
USE CASE
 ↓
SYSTEM / AI
 ↓
UI
 ↓
TESTS
 ↓
OBSERVABILITY
```

The exact order may vary, but domain semantics should not be accidentally defined by the UI.

---

# 197. SPRINT ARCHITECTURE

Every coding sprint should explicitly identify:

```text
OBJECTIVE

IN SCOPE

OUT OF SCOPE

ALLOWED FILES / MODULES

FORBIDDEN AREAS

DEPENDENCIES

DOMAIN IMPACT

DATA IMPACT

AI IMPACT

SYSTEM IMPACT

TESTS

LOGGING

ACCEPTANCE CRITERIA
```

---

# 198. AI CODING AGENT WORKFLOW

Every AI coding agent should follow:

```text
1. READ
2. SCAN
3. UNDERSTAND
4. PLAN
5. IMPLEMENT
6. TEST
7. REVIEW DIFF
8. VERIFY LOGS
9. REPORT
10. STOP
```

---

# 199. READ BEFORE MODIFYING

The coding agent must read:

```text
IRONMIND_MASTER_BLUEPRINT.md
PRODUCT_CONSTITUTION.md
SYSTEM_ARCHITECTURE.md
```

and any relevant subordinate contract.

---

# 200. SCAN BEFORE MODIFYING

The agent must inspect:

* existing package structure
* current implementations
* dependencies
* relevant tests
* current architecture
* current behavior

before making substantial changes.

---

# 201. PLAN BEFORE MODIFYING

The agent should describe:

```text
Files to change
Why
Expected flow
Potential risks
Tests
```

before broad implementation.

---

# 202. DIFF REVIEW

After implementation:

```text
git status
git diff
```

must be reviewed.

Look for:

* unrelated modifications
* deleted code
* changed dependencies
* Gradle modifications
* manifest modifications
* permission changes
* architecture drift
* removed tests
* changed autonomy
* changed logging

---

# 203. STOP RULE

When sprint acceptance criteria are met:

> STOP.

Do not continue improving unrelated code.

---

# 204. FUTURE ARCHITECTURE DOCUMENTS

This document establishes architecture at a high level.

The following documents will provide more precise contracts:

```text
DATA_CONTRACT.md
AI_BEHAVIOR_CONTRACT.md
AUTONOMY_POLICY.md
INTERVENTION_RULES.md
DEVELOPMENT_RULES.md
```

They must remain consistent with this architecture.

---

# 205. ARCHITECTURAL HIERARCHY

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
IMPLEMENTATION
```

When two documents appear to conflict, the higher-level document takes precedence unless the hierarchy is explicitly changed.

---

# 206. IMPORTANT ARCHITECTURAL INVARIANTS

The following should remain true throughout development:

```text
1. Domain logic is not owned by UI.

2. AI does not directly execute device actions.

3. User autonomy is explicit.

4. Important state transitions are observable.

5. Historical events are distinguishable from current state.

6. Observation is distinguishable from inference.

7. User-confirmed information is distinguishable from inference.

8. Core action does not depend entirely on AI availability.

9. Critical local functionality does not unnecessarily depend on network availability.

10. Backend implementation does not leak into domain logic.

11. Android APIs do not leak throughout domain logic.

12. New dependencies require justification.

13. Large unrelated refactors are not part of narrow feature sprints.

14. Background work must be bounded and observable.

15. Important autonomous behavior must be explainable.

16. Persistent data changes require schema consideration.

17. Product meaning must not be changed accidentally by implementation.
```

---

# 207. ARCHITECTURAL GOLDEN PATH

The ideal system flow is:

```text
USER / DEVICE
      ↓
OBSERVATION
      ↓
EVENT
      ↓
PERSISTENCE
      ↓
CURRENT STATE
      ↓
RELEVANT CONTEXT
      ↓
REASONING
      ↓
DECISION
      ↓
INTERVENTION
      ↓
EXECUTION
      ↓
OUTCOME
      ↓
EVENT
      ↓
LEARNING
```

---

# 208. THE CORE SEPARATION

The most important conceptual separation in IronMind is:

```text
OBSERVE
≠
INTERPRET
≠
DECIDE
≠
EXECUTE
```

These are four different responsibilities.

For example:

```text
OBSERVE:
YouTube opened during study session.

INTERPRET:
This may represent distraction.

DECIDE:
Protection appears useful under current policy.

EXECUTE:
Temporarily restrict YouTube.
```

No single uncontrolled component should own all four.

---

# 209. AI BOUNDARY SUMMARY

AI:

```text
UNDERSTANDS
ANALYZES
PLANS
HYPOTHESIZES
RECOMMENDS
```

Decision Engine:

```text
VALIDATES
APPLIES POLICY
CHECKS AUTONOMY
DECIDES
```

Execution layer:

```text
PERFORMS
```

---

# 210. DATA BOUNDARY SUMMARY

Observation:

```text
What happened?
```

Event:

```text
Record that it happened.
```

State:

```text
What is true now?
```

Memory:

```text
What may matter later?
```

Pattern:

```text
What repeated relationship appears to exist?
```

---

# 211. USER CONTROL BOUNDARY SUMMARY

User:

```text
Defines what matters.
```

IronMind:

```text
Helps with execution.
```

Autonomy policy:

```text
Defines what IronMind is allowed to do.
```

Decision Engine:

```text
Decides what happens now.
```

Execution layer:

```text
Carries it out.
```

---

# 212. LONG-TERM ARCHITECTURAL VISION

The mature IronMind architecture should eventually resemble:

```text
                     USER
                      │
                      ▼
                EXPERIENCE
                      │
                DOMAIN ACTION
                      │
        ┌─────────────┴─────────────┐
        │                           │
        ▼                           ▼
   OBSERVATION                   INPUT
        │                           │
        └─────────────┬─────────────┘
                      ▼
                    EVENTS
                      │
          ┌───────────┴───────────┐
          │                       │
          ▼                       ▼
      CURRENT STATE          LONG-TERM MEMORY
          │                       │
          └───────────┬───────────┘
                      ▼
                 CONTEXT ENGINE
                      │
                      ▼
                 AI REASONING
                      │
                      ▼
                DECISION ENGINE
                      │
            ┌─────────┴─────────┐
            │                   │
            ▼                   ▼
        STAY SILENT         INTERVENTION
                                │
                         ┌──────┴──────┐
                         │             │
                         ▼             ▼
                    NOTIFICATION   PROTECTION
                         │             │
                         └──────┬──────┘
                                ▼
                            EXECUTION
                                │
                                ▼
                              OUTCOME
                                │
                                ▼
                             LEARN
```

---

# 213. FINAL ARCHITECTURAL PRINCIPLE

IronMind should be a collection of clearly bounded systems working together.

It should NOT become:

```text
One giant AI
+
One giant database
+
One giant ViewModel
+
One giant background service
```

Instead:

```text
Small clear components
+
Explicit contracts
+
Structured events
+
Reliable state
+
Controlled AI
+
Deterministic policy
+
Observable execution
```

---

# 214. FINAL ARCHITECTURAL STATEMENT

> **IronMind's architecture must separate observation, understanding, decision, and execution while preserving a reliable path from user intention to real-world action and back into long-term learning.**

---

# 215. FINAL ENGINEERING RULE

> **Build the foundation so that IronMind can become more intelligent without becoming less understandable.**

Intelligence must be added on top of:

* reliable domain state
* trustworthy observations
* structured events
* explicit policies
* controlled autonomy
* deterministic execution
* strong observability

---

# 216. FINAL SYSTEM MODEL

```text
                    IRONMIND
                       │
      ┌────────────────┼────────────────┐
      │                │                │
      ▼                ▼                ▼
   EXPERIENCE       OBSERVATION       INPUT
      │                │                │
      └────────────────┼────────────────┘
                       ▼
                    DOMAIN
                       │
                ┌──────┴──────┐
                ▼             ▼
              STATE         EVENTS
                │             │
                └──────┬──────┘
                       ▼
                     DATA
                       │
                       ▼
                 PERSONAL MODEL
                       │
                       ▼
                  INTELLIGENCE
                       │
                       ▼
                  AI REASONING
                       │
                       ▼
                 DECISION ENGINE
                       │
                       ▼
               INTERVENTION ENGINE
                       │
                       ▼
                    EXECUTION
                       │
                       ▼
                    OUTCOME
                       │
                       ▼
                    LEARNING
                       │
                       └──────────────→ PERSONAL MODEL
```

---

# 217. FINAL ARCHITECTURAL INVARIANT

The following cycle must remain possible throughout the life of the product:

```text
INTENTION
   ↓
COMMITMENT
   ↓
ACTION
   ↓
OUTCOME
   ↓
OBSERVATION
   ↓
EVENT
   ↓
MEMORY
   ↓
LEARNING
   ↓
BETTER DECISION
   ↓
BETTER ACTION
```

That cycle is the technical embodiment of IronMind's product mission.

---

# END OF SYSTEM ARCHITECTURE

````
