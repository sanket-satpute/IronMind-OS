`DATA_CONTRACT.md`

````md
# IRONMIND DATA CONTRACT

**Document:** `DATA_CONTRACT.md`  
**Status:** AUTHORITATIVE DATA CONTRACT  
**Parent Documents:**
- `IRONMIND_MASTER_BLUEPRINT.md`
- `PRODUCT_CONSTITUTION.md`
- `SYSTEM_ARCHITECTURE.md`

**Purpose:** Define IronMind's canonical data model, entity ownership, identifiers, relationships, state machines, event model, provenance, confidence, memory, patterns, synchronization, versioning, and persistence rules.

---

# 1. PURPOSE

This document defines what data IronMind stores, what that data means, where it lives, how it changes, and how its history is preserved.

The objective is to prevent:

- duplicate sources of truth
- inconsistent schemas
- ambiguous entity meanings
- uncontrolled AI writes
- irreversible memory corruption
- undocumented state transitions
- synchronization conflicts
- event duplication
- accidental loss of historical information
- schema drift between Android and backend

This document is authoritative for the data model.

Implementation details may differ between Android and backend, but the semantic contract must remain consistent.

---

# 2. DATA PRINCIPLES

IronMind follows these principles:

```text
1. Current state and historical events are different concepts.

2. Observation and inference are different concepts.

3. Inference and user-confirmed fact are different concepts.

4. User intent has explicit representation.

5. Important data changes must be traceable.

6. Historical events should generally be append-oriented.

7. Retried operations must be safe against duplication.

8. Persisted schemas must be versioned when required.

9. AI-generated information must never silently override high-authority user data.

10. Data should serve meaningful product decisions.

11. Old behavioral assumptions must be allowed to decay.

12. Current explicit user intent should generally outweigh old weak inference.

13. Local and remote representations must preserve the same semantic meaning.

14. Sensitive data must not be logged unnecessarily.

15. Every entity must have a clear owner and source of truth.
````

---

# 3. DATA LAYER MODEL

IronMind data is conceptually divided into:

```text
USER DATA
OBSERVATIONS
EVENTS
CURRENT STATE
MEMORY
PATTERNS
INTERVENTIONS
CONFIGURATION
SYNCHRONIZATION METADATA
```

---

# 4. CORE DOMAIN ENTITIES

The initial canonical domain model consists of:

```text
UserProfile
Goal
Ambition
Plan
Task
Commitment
Outcome
Reflection
Observation
Event
Pattern
Memory
Intervention
AutonomySetting
ProtectionRule
ProtectionSession
```

Additional entities may be introduced later through explicit architectural review.

---

# 5. ENTITY CATEGORIES

## 5.1 User-owned domain entities

These represent what the user wants or chooses.

```text
Goal
Ambition
Plan
Task
Commitment
UserProfile
AutonomySetting
```

---

## 5.2 System-observed entities

These represent what IronMind detects.

```text
Observation
Event
```

---

## 5.3 Learned entities

These represent IronMind's evolving understanding.

```text
Pattern
Memory
```

---

## 5.4 System-action entities

These represent what IronMind did.

```text
Intervention
ProtectionSession
```

---

## 5.5 Result entities

These represent what happened after intended or system action.

```text
Outcome
Reflection
```

---

# 6. UNIVERSAL ENTITY IDENTIFIER

Every persisted entity must have a globally unique identifier.

Conceptual form:

```text
id: String
```

The exact ID strategy will be selected during implementation.

The strategy must support:

* uniqueness
* local generation where practical
* synchronization
* tracing
* stable references
* migration

---

# 7. ENTITY ID RULES

An ID must:

* remain stable for the lifetime of the entity
* not encode mutable business information
* not depend on display text
* not be reused
* be safe to transmit between local and remote systems

Do not use names or mutable labels as primary keys.

---

# 8. TIMESTAMP STANDARD

Persisted entities should use explicit timestamps where meaningful.

Canonical conceptual fields:

```text
createdAt
updatedAt
```

Event-oriented entities may also contain:

```text
occurredAt
recordedAt
processedAt
```

The distinction matters for offline and delayed processing.

---

# 9. TIMESTAMP RULE

All timestamps must use an unambiguous representation.

Recommended conceptual representation:

```text
Instant / UTC timestamp
```

Display timezone is a presentation concern.

User-local timezone may also be stored when required for scheduling semantics.

---

# 10. USER PROFILE

`UserProfile` represents durable basic profile/context information needed by IronMind.

Conceptual fields:

```text
id
createdAt
updatedAt

displayName
timezone

createdFrom
status
schemaVersion
```

Potential future fields may include:

* preferred communication style
* preferred interaction mode
* onboarding state

Do not place the entire personal model inside `UserProfile`.

---

# 11. USER PROFILE RULE

User profile information and behavioral memory are separate concepts.

Example:

```text
displayName
```

belongs to `UserProfile`.

While:

```text
User often completes work better in the morning.
```

belongs to `Pattern` or `Memory`.

---

# 12. AMBITION

An `Ambition` represents a meaningful long-term direction that may not yet have a concrete deadline.

Examples:

```text
Build my own business.
Travel more.
Improve my physique.
Become better at programming.
```

Conceptual fields:

```text
id
userId

title
description

importance
status

why
createdAt
updatedAt
lastMeaningfulProgressAt

schemaVersion
```

---

# 13. AMBITION STATUS

Suggested status values:

```text
ACTIVE
PAUSED
COMPLETED
ABANDONED
NO_LONGER_RELEVANT
REPLACED
ARCHIVED
```

The final enum must be defined consistently across implementations.

---

# 14. GOAL

A `Goal` represents a more concrete desired outcome.

Conceptual fields:

```text
id
userId

ambitionId? 

title
description
why

importance

status

targetAt?
startedAt?
completedAt?

createdAt
updatedAt

schemaVersion
```

---

# 15. GOAL STATUS

Suggested states:

```text
ACTIVE
PAUSED
COMPLETED
ABANDONED
NO_LONGER_RELEVANT
REPLACED
ARCHIVED
```

---

# 16. GOAL VS AMBITION

The distinction is:

```text
Ambition
=
Long-term direction

Goal
=
Concrete desired outcome
```

Example:

```text
Ambition:
Build a business.

Goal:
Launch the first working version of the product.
```

---

# 17. PLAN

A `Plan` describes a structured strategy for reaching a goal or ambition.

Conceptual fields:

```text
id
userId

goalId?
ambitionId?

title
description

status

createdAt
updatedAt

startedAt?
completedAt?

source
schemaVersion
```

---

# 18. PLAN SOURCE

A plan may originate from:

```text
USER
AI
SYSTEM
MIXED
```

The source must be explicit.

An AI-generated plan must not appear indistinguishable from a user-created plan.

---

# 19. PLAN STATUS

Suggested:

```text
DRAFT
ACTIVE
PAUSED
COMPLETED
ABANDONED
REPLACED
ARCHIVED
```

---

# 20. TASK

A `Task` represents a concrete actionable unit.

Conceptual fields:

```text
id
userId

goalId?
planId?
parentTaskId?

title
description

status
priority

estimatedDurationMinutes?

scheduledAt?
dueAt?

createdAt
updatedAt

completedAt?
postponedAt?

source
schemaVersion
```

---

# 21. TASK HIERARCHY

Tasks may optionally contain:

```text
parentTaskId
```

This allows:

```text
Large task
↓
Smaller tasks
↓
Smallest actionable step
```

Avoid unlimited nesting unless actually needed.

---

# 22. TASK STATUS

Suggested:

```text
TODO
IN_PROGRESS
COMPLETED
POSTPONED
CANCELLED
ABANDONED
```

---

# 23. COMMITMENT

A `Commitment` represents a concrete intention the user has explicitly chosen to act on.

This is one of IronMind's most important entities.

Conceptual fields:

```text
id
userId

goalId?
planId?
taskId?

title
description

committedAt

scheduledStartAt?
scheduledEndAt?

status

priority

source

createdAt
updatedAt

startedAt?
completedAt?
postponedAt?
missedAt?
recoveredAt?

parentCommitmentId?

schemaVersion
```

---

# 24. COMMITMENT MEANING

A commitment answers:

> "What did I actually decide I would do?"

It is stronger than a generic task.

Example:

```text
Task:
Study chapter 3.

Commitment:
I will study chapter 3 tomorrow from 8:00 to 9:00 AM.
```

---

# 25. COMMITMENT STATE MACHINE

Canonical conceptual lifecycle:

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
RESCHEDULED / COMMITTED
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

---

# 26. COMMITMENT STATE RULES

Valid state transitions must be explicitly enforced.

Example:

```text
PLANNED → COMMITTED
COMMITTED → STARTED
COMMITTED → POSTPONED
COMMITTED → MISSED
COMMITTED → ABANDONED
STARTED → COMPLETED
STARTED → POSTPONED
STARTED → MISSED
MISSED → RECOVERED
POSTPONED → COMMITTED
```

Invalid transitions must be rejected rather than silently accepted.

---

# 27. COMMITMENT COMPLETION

Completing a commitment should:

1. validate the current state
2. change current state
3. create an outcome where appropriate
4. emit a completion event
5. emit lifecycle logging
6. update timestamps
7. trigger relevant downstream processing

---

# 28. COMMITMENT POSTPONEMENT

Postponement is a meaningful event.

It should preserve:

```text
original scheduled time
new scheduled time
postponement time
reason if available
source
```

The reason may come from:

```text
USER
AI hypothesis
SYSTEM OBSERVATION
UNKNOWN
```

---

# 29. COMMITMENT MISSED

A missed commitment means the expected execution window ended without successful completion.

Do not interpret it as:

```text
low discipline
```

or:

```text
failure of character
```

It is a state/outcome.

---

# 30. COMMITMENT RECOVERY

Recovery means the user resumed meaningful progress after a missed or failed commitment.

Recovery should preserve the original history.

Do not rewrite:

```text
MISSED
```

into:

```text
COMPLETED
```

without preserving the missed event.

Correct:

```text
MISSED
↓
RECOVERED
↓
NEW ACTION
```

---

# 31. OUTCOME

An `Outcome` records what actually happened after an intended action.

Conceptual fields:

```text
id
userId

commitmentId?
taskId?
goalId?
interventionId?

status

startedAt?
completedAt?

resultDescription?

reason?
barrierIds?

createdAt
updatedAt

schemaVersion
```

---

# 32. OUTCOME STATUS

Suggested values:

```text
COMPLETED
PARTIALLY_COMPLETED
POSTPONED
MISSED
ABANDONED
CANCELLED
UNKNOWN
```

---

# 33. OUTCOME RULE

Outcome is not the same as commitment status.

Example:

```text
Commitment:
COMPLETED

Outcome:
COMPLETED
```

But:

```text
Commitment:
POSTPONED

Outcome:
POSTPONED
```

The domain relationship must remain explicit.

---

# 34. REFLECTION

A `Reflection` represents the user's retrospective explanation of what happened.

It may contain:

```text
id
userId

date

text

audioReference?

transcript?

source

createdAt
updatedAt

processingStatus

schemaVersion
```

---

# 35. REFLECTION SOURCE

Suggested values:

```text
TEXT
VOICE
MIXED
```

---

# 36. REFLECTION PROCESSING STATUS

For voice or AI processing:

```text
RAW
TRANSCRIBING
TRANSCRIBED
ANALYZING
PROCESSED
FAILED
```

---

# 37. REFLECTION IS USER-SUPPLIED EVIDENCE

A reflection should be treated as high-value evidence about the user's experience.

However, extracted interpretations from the reflection are separate from the raw reflection.

---

# 38. REFLECTION EXTRACTION

Example:

User says:

> "I didn't study because I was exhausted after work and didn't know what chapter to start with."

Potential extracted structures:

```text
Observed/confirmed:
User reports exhaustion.

Potential barrier:
Low energy.

Potential barrier:
Lack of clarity.

Relevant context:
After work.

```

The raw reflection and extracted model information must remain distinguishable.

---

# 39. OBSERVATION

An `Observation` represents something the system detected or received from a source.

Conceptual fields:

```text
id
userId

type

source

occurredAt
recordedAt

subjectId?

value

context

confidence?

provenance

schemaVersion
```

---

# 40. OBSERVATION SOURCES

Possible sources:

```text
USER
ANDROID
CALENDAR
LOCATION
NOTIFICATION
APP_USAGE
VOICE
SYSTEM
EXTERNAL_INTEGRATION
AI
```

---

# 41. OBSERVATION TYPE

Possible categories:

```text
APP_OPENED
APP_CLOSED
NOTIFICATION_RECEIVED
NOTIFICATION_INTERACTED
TASK_STARTED
TASK_COMPLETED
TASK_POSTPONED
COMMITMENT_STARTED
COMMITMENT_COMPLETED
COMMITMENT_POSTPONED
COMMITMENT_MISSED
LOCATION_CONTEXT_CHANGED
CALENDAR_CONTEXT_CHANGED
VOICE_CAPTURED
USER_INPUT
```

The canonical final event/observation vocabulary will be finalized during implementation.

---

# 42. OBSERVATION PROVENANCE

Every observation should identify where it came from.

Conceptually:

```text
source
sourceReference?
capturedAt
```

Example:

```text
source = APP_USAGE
sourceReference = Android usage statistics
```

---

# 43. OBSERVATION CONFIDENCE

Some observations may be deterministic.

Others may be estimated.

Therefore an observation may optionally carry:

```text
confidence
```

For example:

```text
Android app launch:
confidence = 1.0
```

while an inferred contextual classification may have:

```text
confidence = 0.74
```

---

# 44. EVENT

An `Event` is a durable representation of a meaningful occurrence in IronMind.

Conceptual fields:

```text
id
userId

type

entityType?
entityId?

occurredAt
recordedAt
processedAt?

source

previousState?
newState?

metadata

correlationId?
causationId?

schemaVersion
```

---

# 45. EVENT VS OBSERVATION

Observation answers:

> "What did we detect?"

Event answers:

> "What meaningful occurrence are we recording in the system?"

An observation may produce an event.

A domain state change may directly produce an event.

---

# 46. EVENT TYPES

Canonical event families should include:

## Goal

```text
GOAL_CREATED
GOAL_UPDATED
GOAL_COMPLETED
GOAL_PAUSED
GOAL_ABANDONED
GOAL_REACTIVATED
```

## Ambition

```text
AMBITION_CREATED
AMBITION_UPDATED
AMBITION_COMPLETED
AMBITION_PAUSED
AMBITION_ABANDONED
```

## Plan

```text
PLAN_CREATED
PLAN_UPDATED
PLAN_ACTIVATED
PLAN_COMPLETED
PLAN_PAUSED
PLAN_REPLACED
```

## Task

```text
TASK_CREATED
TASK_STARTED
TASK_COMPLETED
TASK_POSTPONED
TASK_CANCELLED
TASK_ABANDONED
```

## Commitment

```text
COMMITMENT_CREATED
COMMITMENT_STARTED
COMMITMENT_COMPLETED
COMMITMENT_POSTPONED
COMMITMENT_RESCHEDULED
COMMITMENT_MISSED
COMMITMENT_RECOVERED
COMMITMENT_ABANDONED
```

## Reflection

```text
REFLECTION_STARTED
REFLECTION_COMPLETED
REFLECTION_FAILED
```

## Voice

```text
VOICE_CAPTURED
VOICE_TRANSCRIBED
VOICE_TRANSCRIPTION_FAILED
```

## Protection

```text
PROTECTION_ENABLED
PROTECTION_DISABLED
PROTECTION_OVERRIDDEN
PROTECTION_EXPIRED
```

## Intervention

```text
INTERVENTION_PROPOSED
INTERVENTION_TRIGGERED
INTERVENTION_DELIVERED
INTERVENTION_ACCEPTED
INTERVENTION_DISMISSED
INTERVENTION_IGNORED
INTERVENTION_OVERRIDDEN
INTERVENTION_EXPIRED
```

## Memory

```text
MEMORY_CREATED
MEMORY_UPDATED
MEMORY_REJECTED
MEMORY_EXPIRED
```

## Pattern

```text
PATTERN_CREATED
PATTERN_UPDATED
PATTERN_CONFIRMED
PATTERN_REJECTED
PATTERN_DECAYED
PATTERN_DEACTIVATED
```

## Synchronization

```text
SYNC_STARTED
SYNC_COMPLETED
SYNC_FAILED
SYNC_RETRIED
```

## Background

```text
BACKGROUND_JOB_STARTED
BACKGROUND_JOB_COMPLETED
BACKGROUND_JOB_FAILED
```

---

# 47. EVENT IMMUTABILITY

Persisted historical events should generally be append-oriented.

Do not silently rewrite historical events.

If information was incorrect, record a correction event where practical.

Example:

```text
PATTERN_CREATED
↓
PATTERN_REJECTED_BY_USER
```

rather than deleting the creation event.

---

# 48. EVENT METADATA

Event metadata may contain contextual details such as:

```text
reason
source
packageName
duration
scheduleId
interventionType
confidence
```

Metadata must remain schema-conscious.

Do not put arbitrary unbounded JSON into every event without a clear contract.

---

# 49. EVENT ENTITY REFERENCE

Events may reference an entity:

```text
entityType
entityId
```

Example:

```text
entityType = COMMITMENT
entityId = abc123
```

---

# 50. EVENT CAUSATION

Where useful, events should support:

```text
correlationId
causationId
```

Example:

```text
COMMITMENT_COMPLETED
```

causes:

```text
PATTERN_ANALYSIS_REQUESTED
```

The relationship can be traced through IDs.

---

# 51. MEMORY

A `Memory` represents durable information IronMind believes may be useful in future decisions.

Conceptual fields:

```text
id
userId

type

content

source

confidence

evidenceCount

firstObservedAt
lastObservedAt

confirmationState

status

expiresAt?

createdAt
updatedAt

schemaVersion
```

---

# 52. MEMORY TYPES

Potential categories:

```text
USER_PREFERENCE
USER_GOAL
USER_AMBITION
CONFIRMED_BARRIER
CONFIRMED_STRENGTH
CONTEXT_PREFERENCE
COMMUNICATION_PREFERENCE
OTHER_RELEVANT_FACT
```

---

# 53. MEMORY SOURCE

Possible values:

```text
USER
REFLECTION
OBSERVATION
AI
SYSTEM
MIXED
```

---

# 54. MEMORY CONFIRMATION STATE

Suggested:

```text
UNCONFIRMED
USER_CONFIRMED
USER_REJECTED
SYSTEM_CONFIRMED
```

The exact semantics must remain consistent.

---

# 55. MEMORY STATUS

Suggested:

```text
ACTIVE
DECAYING
INACTIVE
EXPIRED
DELETED
```

---

# 56. MEMORY AUTHORITY

Explicit user-confirmed information generally has greater authority than weak AI inference.

Conceptual priority:

```text
Explicit current user statement
        ↓
User-confirmed memory
        ↓
Strong repeated evidence
        ↓
System inference
        ↓
Weak hypothesis
```

Exact decision priority belongs in `AUTONOMY_POLICY.md` and `AI_BEHAVIOR_CONTRACT.md`.

---

# 57. PATTERN

A `Pattern` represents a repeated relationship between behavior and context.

Conceptual fields:

```text
id
userId

type

description

conditions
predictedBehavior

confidence

evidenceCount

firstObservedAt
lastObservedAt

status

confirmationState

createdAt
updatedAt

schemaVersion
```

---

# 58. PATTERN EXAMPLE

```text
Pattern:
User tends to postpone solo gym sessions.

Confidence:
0.86

Evidence:
14 observations

First observed:
2026-08-01

Last observed:
2026-09-10

Status:
ACTIVE

Confirmation:
UNCONFIRMED
```

---

# 59. PATTERN TYPES

Potential categories:

```text
POSTPONEMENT_PATTERN
COMPLETION_PATTERN
DISTRACTION_PATTERN
TIME_PATTERN
CONTEXT_PATTERN
SOCIAL_PATTERN
ENERGY_PATTERN
TASK_SIZE_PATTERN
INTERVENTION_RESPONSE_PATTERN
SUCCESS_CONDITION_PATTERN
```

---

# 60. PATTERN CONFIDENCE

Confidence should represent how strongly current evidence supports the pattern.

Conceptually:

```text
0.0 = no confidence
1.0 = very high confidence
```

The exact algorithm must be defined separately.

---

# 61. PATTERN EVIDENCE

Patterns should be supported by traceable evidence.

Conceptually:

```text
evidenceCount
evidenceReferences
```

where appropriate.

The system should be able to explain:

> "Why do you believe this pattern?"

---

# 62. PATTERN RECENCY

Every pattern should track:

```text
firstObservedAt
lastObservedAt
```

A pattern that has not appeared recently should lose influence.

---

# 63. PATTERN DECAY

Pattern confidence should eventually decay when evidence becomes stale.

Conceptually:

```text
Recent supporting evidence
↓
Confidence maintained

No supporting evidence
↓
Confidence decreases

Long-term unsupported
↓
INACTIVE
```

---

# 64. PATTERN CONFIRMATION

A user may explicitly confirm:

> "Yes, that's true."

Then:

```text
confirmationState = USER_CONFIRMED
```

The system may use this as stronger evidence than unconfirmed inference.

---

# 65. PATTERN REJECTION

If the user says:

> "No, that's not true."

then the system should:

* mark it rejected or reduce confidence
* record the correction
* avoid treating the old pattern as authoritative
* learn from the correction

---

# 66. PATTERN DOES NOT DEFINE IDENTITY

A pattern is:

```text
behavioral relationship
```

not:

```text
personality label
```

Never store:

```text
User = lazy
```

as a behavioral pattern.

---

# 67. INTERVENTION

An `Intervention` represents an action IronMind chooses to attempt to influence behavior.

Conceptual fields:

```text
id
userId

type

status

triggerSource

targetEntityType?
targetEntityId?

reason

confidence?

createdAt
scheduledAt?
deliveredAt?
resolvedAt?

cooldownUntil?

autonomyLevel

decisionId?

outcome?

schemaVersion
```

---

# 68. INTERVENTION TYPES

Canonical vocabulary:

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

Additional types require explicit product/architecture review.

---

# 69. INTERVENTION STATUS

Suggested:

```text
PROPOSED
APPROVED
TRIGGERED
DELIVERED
ACCEPTED
DISMISSED
IGNORED
OVERRIDDEN
EXPIRED
CANCELLED
```

---

# 70. INTERVENTION SOURCE

Possible:

```text
RULE
PATTERN
AI
USER
SCHEDULE
SYSTEM
```

---

# 71. INTERVENTION REASON

The reason should describe why the system selected the intervention.

Example:

```text
Active study commitment.
User enabled automatic protection.
YouTube has historically interrupted similar study sessions.
```

The reason must not invent unsupported facts.

---

# 72. INTERVENTION CONFIDENCE

If an intervention originated from probabilistic reasoning, confidence may be recorded.

Do not confuse:

```text
confidence that intervention is useful
```

with:

```text
confidence that event occurred
```

These are separate concepts.

---

# 73. INTERVENTION OUTCOME

Potential outcomes:

```text
ACCEPTED
IGNORED
DISMISSED
OVERRIDDEN
COMPLETED
FAILED
EXPIRED
NO_EFFECT
```

---

# 74. INTERVENTION COOLDOWN

An intervention may specify:

```text
cooldownUntil
```

The Decision Engine should check this before issuing another similar intervention.

---

# 75. DECISION RECORD

Where autonomous decisions require auditability, a `DecisionRecord` may be introduced.

Conceptual fields:

```text
id
userId

trigger
contextReference
recommendation
policyResult
autonomyLevel
action
reason

createdAt

schemaVersion
```

This entity is optional for initial implementation but strongly recommended for mature autonomous behavior.

---

# 76. DECISION RECORD PURPOSE

A decision record answers:

```text
What triggered the decision?
What did AI recommend?
What did policy allow?
What action was selected?
Why?
```

---

# 77. AUTONOMY SETTING

An `AutonomySetting` defines how much authority IronMind has for a capability.

Conceptual fields:

```text
id
userId

capability

level

enabled

updatedAt

schemaVersion
```

---

# 78. AUTONOMY CAPABILITIES

At minimum:

```text
PLANNING
SCHEDULING
PROTECTION
NOTIFICATIONS
BACKGROUND_LEARNING
GOAL_RESURFACING
```

More may be added later.

---

# 79. AUTONOMY LEVELS

Canonical conceptual levels:

```text
OFF
SUGGEST_ONLY
ASK_BEFORE_ACTION
FULL_AUTO
```

---

# 80. AUTONOMY RULE

An AI recommendation cannot override an autonomy setting.

Example:

```text
AI:
PROTECT

Autonomy:
OFF

Result:
No automatic protection.
```

---

# 81. PROTECTION RULE

A `ProtectionRule` defines when and what IronMind is allowed to protect.

Conceptual fields:

```text
id
userId

targetPackage?
targetCategory?

triggerType

conditions

enabled

priority

createdAt
updatedAt

schemaVersion
```

---

# 82. PROTECTION SESSION

A `ProtectionSession` represents an active protection period.

Conceptual fields:

```text
id
userId

commitmentId?
taskId?

startedAt
scheduledEndAt?
endedAt?

status

source

overrideAllowed

createdAt
updatedAt

schemaVersion
```

---

# 83. PROTECTION SESSION STATUS

Suggested:

```text
SCHEDULED
ACTIVE
COMPLETED
CANCELLED
OVERRIDDEN
EXPIRED
```

---

# 84. PROTECTION RULE vs SESSION

```text
ProtectionRule
=
What can be protected and under what conditions.

ProtectionSession
=
What is being protected right now.
```

---

# 85. OBSERVED APPLICATION ACTIVITY

Application usage data should be normalized into observations/events rather than becoming the central product model.

Examples:

```text
APP_OPENED
APP_CLOSED
APP_USAGE_INTERVAL
```

The core purpose is to understand action/distraction context.

---

# 86. RAW APP USAGE VS DERIVED PATTERN

Do not store:

```text
Instagram is bad.
```

Store:

```text
Instagram opened during active study commitment.
```

Then potentially infer:

```text
Instagram usage may correlate with study interruption.
```

---

# 87. NOTIFICATION OBSERVATION

Notifications may be observed where permitted.

Potential data:

```text
application
timestamp
interaction
```

Avoid unnecessary content retention.

---

# 88. LOCATION CONTEXT DATA

Where location/context is enabled, raw location should be separated from derived context.

Example:

```text
Raw:
latitude/longitude

Derived:
library_context
```

Only retain raw location for as long as genuinely necessary.

---

# 89. CALENDAR DATA

Calendar information should generally be represented as relevant context rather than duplicating the entire calendar unnecessarily.

Potential normalized fields:

```text
eventId
title?
startAt
endAt
location?
availabilityContext
```

Retention should be deliberate.

---

# 90. VOICE DATA

Voice input may consist of:

```text
audioReference
transcript
processingStatus
createdAt
```

Raw audio should not be retained indefinitely by default.

---

# 91. VOICE STORAGE PRINCIPLE

Prefer:

```text
Audio
↓
Transcription
↓
Structured extraction
↓
Relevant durable memory
```

rather than retaining all recordings permanently.

---

# 92. USER PREFERENCE

A `UserPreference` may represent explicit preferences.

Potential examples:

```text
preferredNotificationStyle
preferredReflectionMode
preferredPlanningStyle
preferredFocusDuration
communicationTone
```

Conceptual fields:

```text
id
userId

key
value
source

createdAt
updatedAt

schemaVersion
```

---

# 93. EXPLICIT PREFERENCE AUTHORITY

Explicit user preferences should generally have higher authority than inferred preferences.

Example:

```text
User:
"I don't want voice reflection."

Preference:
voiceReflection = disabled
```

The system must not infer otherwise.

---

# 94. SOURCE / PROVENANCE MODEL

Important data should preserve provenance.

Conceptual source values:

```text
USER
ANDROID
SYSTEM
AI
REFLECTION
EXTERNAL_INTEGRATION
MIXED
```

---

# 95. PROVENANCE FIELDS

Where appropriate:

```text
source
sourceReference
capturedAt
createdBy
```

This helps answer:

> "Where did this information come from?"

---

# 96. OBSERVATION / INFERENCE / CONFIRMATION MODEL

These are distinct.

## Observation

```text
Instagram opened 6 times during study.
```

## Inference

```text
Instagram may increase study interruption risk.
```

## User-confirmed fact

```text
"I open Instagram when I feel bored."
```

The data model must preserve these distinctions.

---

# 97. CONFIDENCE MODEL

Probabilistic entities may contain:

```text
confidence: Decimal [0,1]
```

Examples:

```text
Pattern
Inference
AI recommendation
Behavior classification
Context classification
```

---

# 98. CONFIDENCE RULE

A confidence value must never be interpreted as:

> "This is objectively true."

It means:

> "Based on current evidence, this system has this level of confidence."

---

# 99. EVIDENCE MODEL

Where practical, learned entities should preserve evidence references.

Conceptually:

```text
evidenceCount
evidenceEventIds[]
```

Do not store enormous unbounded evidence arrays if queryable event references provide a better architecture.

The implementation may use a separate relation.

---

# 100. MEMORY DECAY

Memory is not universally permanent.

Memory may have:

```text
lastObservedAt
expiresAt?
status
```

Not every memory needs automatic expiration.

Durable user-confirmed facts may have different retention semantics from weak inferred facts.

---

# 101. MEMORY DELETION

When a user explicitly deletes a memory:

The system should not recreate it immediately from stale cached information without a valid new reason.

Deletion should propagate through relevant stores.

---

# 102. USER DATA CORRECTION

When a user corrects information:

```text
USER CORRECTION
↓
DOMAIN UPDATE
↓
CORRECTION EVENT
↓
MODEL UPDATE
```

Do not silently overwrite history without recording the correction where appropriate.

---

# 103. SOFT DELETION

Certain entities may require soft deletion.

Conceptual:

```text
deletedAt?
status = DELETED
```

This depends on retention requirements.

Do not use soft deletion everywhere by default.

---

# 104. HARD DELETION

Hard deletion may be required for user-requested data removal.

The exact implementation must define:

* local deletion
* backend deletion
* derived data deletion
* AI-related stored information
* cached data
* backup implications

---

# 105. DATA OWNERSHIP

Every entity must have an explicit ownership rule.

Example:

```text
Goal
→ User-owned

Commitment
→ User-owned with system-generated metadata

Pattern
→ System-generated but user-correctable

Intervention
→ System-created based on policy

Event
→ System historical record
```

---

# 106. AUTHORITY MODEL

Conceptual authority hierarchy:

```text
CURRENT EXPLICIT USER INTENT
        ↓
USER-CONFIRMED INFORMATION
        ↓
STRONG EVIDENCE
        ↓
STABLE LEARNED PATTERN
        ↓
WEAK INFERENCE
        ↓
AI HYPOTHESIS
```

This is a reasoning principle, not necessarily a database precedence field.

---

# 107. CURRENT INTENT OVERRIDES OLD INFERENCE

Example:

Old Pattern:

```text
User prefers morning exercise.
```

Current user instruction:

```text
"I want to exercise in the evening now."
```

The current explicit instruction should influence current decisions.

---

# 108. RELATIONSHIP MODEL

Major relationships:

```text
User
 ├── Ambitions
 ├── Goals
 ├── Plans
 ├── Tasks
 ├── Commitments
 ├── Reflections
 ├── Events
 ├── Observations
 ├── Patterns
 ├── Memories
 ├── Interventions
 ├── AutonomySettings
 └── ProtectionRules
```

---

# 109. GOAL RELATIONSHIPS

A goal may belong to:

```text
Ambition
```

and may contain or relate to:

```text
Plan
Task
Commitment
Outcome
```

---

# 110. PLAN RELATIONSHIPS

A plan may relate to:

```text
Ambition
Goal
Task
Commitment
```

---

# 111. TASK RELATIONSHIPS

A task may relate to:

```text
Goal
Plan
ParentTask
Commitment
Outcome
```

---

# 112. COMMITMENT RELATIONSHIPS

A commitment may relate to:

```text
Goal
Plan
Task
Outcome
Intervention
ProtectionSession
Events
```

---

# 113. REFLECTION RELATIONSHIPS

A reflection may reference:

```text
Goal
Commitment
Outcome
Event
```

but reflection should also be allowed to exist as a general daily reflection.

---

# 114. PATTERN RELATIONSHIPS

Patterns may reference evidence from:

```text
Events
Observations
Outcomes
Reflections
Interventions
```

---

# 115. MEMORY RELATIONSHIPS

Memories may be supported by:

```text
Events
Observations
Reflections
User confirmations
```

---

# 116. INTERVENTION RELATIONSHIPS

Interventions may be triggered by:

```text
Commitment
Task
Goal
Pattern
Event
Context
Schedule
```

---

# 117. EVENT RELATIONSHIPS

Events may reference any relevant entity:

```text
Goal
Ambition
Plan
Task
Commitment
Reflection
Pattern
Memory
Intervention
ProtectionSession
```

---

# 118. DATA NORMALIZATION PRINCIPLE

Do not duplicate the same semantic fact across many entities without purpose.

Example:

If commitment title is canonical in `Commitment`, do not copy mutable versions into unrelated records without defining synchronization behavior.

---

# 119. DENORMALIZATION

Denormalized data may exist for:

* performance
* analytics
* AI context
* caching

but it must have:

```text
source of truth
refresh strategy
rebuild strategy
```

---

# 120. EVENT PAYLOAD VERSIONING

Every persisted event payload should support a schema version.

Example:

```text
schemaVersion = 1
```

Future versions can then be migrated safely.

---

# 121. ENTITY SCHEMA VERSIONING

Entities requiring durable schema evolution should support:

```text
schemaVersion
```

This is especially important for:

* Memory
* Pattern
* Event
* Intervention
* AI outputs
* synchronization records

---

# 122. BACKWARD COMPATIBILITY

When changing a schema:

```text
OLD
↓
MIGRATION
↓
NEW
```

must be planned.

Do not simply change field semantics and assume existing records are compatible.

---

# 123. ENUM EVOLUTION

Adding enum values is usually safer than renaming/removing existing values.

Changing:

```text
COMPLETED
```

to:

```text
DONE
```

may break persisted data.

Schema migrations must account for such changes.

---

# 124. OPTIONAL FIELDS

Optional fields should have explicit semantic meaning.

Avoid ambiguous cases such as:

```text
null
=
unknown
=
not applicable
=
not collected
```

unless the field contract explicitly defines them as equivalent.

---

# 125. NULL SEMANTICS

Where the difference matters, use explicit status/state fields rather than interpreting null ambiguously.

---

# 126. DEFAULT VALUES

Defaults must reflect actual product semantics.

Do not silently default important behavior to:

```text
FULL_AUTO
```

or another invasive behavior.

Autonomy defaults must be explicitly defined by product policy.

---

# 127. LOCAL DATA MODEL

Local persistence should support:

```text
Current state
Local event history
Pending sync
Protection state
Autonomy settings
Relevant memory
```

The exact schema will be implemented separately.

---

# 128. REMOTE DATA MODEL

Remote persistence should support:

```text
Long-term user state
Goals
Ambitions
Commitments
Plans
Tasks
Reflections
Events
Patterns
Memories
Interventions
```

Only necessary data should be synchronized.

---

# 129. SYNC METADATA

Persisted synchronized entities may require:

```text
syncStatus
lastSyncedAt
remoteVersion?
localVersion?
updatedAt
```

---

# 130. SYNC STATUS

Suggested:

```text
LOCAL_ONLY
PENDING
SYNCING
SYNCED
FAILED
CONFLICT
```

---

# 131. SYNC PRINCIPLE

Local writes should be committed locally first where appropriate.

Then:

```text
LOCAL STATE
↓
OUTBOX / PENDING
↓
REMOTE
↓
CONFIRM
```

---

# 132. OUTBOX

For offline-capable writes, an outbox or equivalent mechanism may be used.

Conceptual fields:

```text
operationId
entityType
entityId
operationType
payload/reference
createdAt
retryCount
lastAttemptAt
status
```

---

# 133. SYNC IDEMPOTENCY

A synchronization operation must be safe to retry.

Example:

```text
operationId = xyz
```

If retried, the backend should recognize the same logical operation where applicable.

---

# 134. CONFLICT RESOLUTION

Conflicts may occur when:

```text
Local
and
Remote
```

contain different versions of the same entity.

Conflict strategy must depend on entity semantics.

Do not use one universal:

```text
last write wins
```

rule without evaluation.

---

# 135. USER-INTENT CONFLICTS

For certain entities, explicit user actions may deserve higher priority than automatic system updates.

Example:

```text
User disables automatic protection.
```

An old scheduled AI action should not silently restore protection.

---

# 136. AI-GENERATED DATA CONFLICT

If AI proposes:

```text
new goal
```

but the user has not confirmed it, it should not automatically become a durable user-owned goal unless policy explicitly permits that behavior.

Prefer:

```text
AI proposal
↓
user confirmation
↓
Goal created
```

for important user intent.

---

# 137. AI MEMORY WRITE POLICY

AI should not have unrestricted permission to write arbitrary permanent memories.

Memory writes should be:

* schema validated
* source-tagged
* confidence-aware
* subject to policy
* reversible/correctable

---

# 138. AI OUTPUT STORAGE

If an AI output is stored, preserve:

```text
provider
model
timestamp
schemaVersion
requestType
status
```

where useful.

Raw response retention should be deliberate.

---

# 139. SENSITIVE AI DATA

Do not automatically persist all:

* prompts
* responses
* reflections
* transcripts

forever.

Retention should be purpose-driven.

---

# 140. DAILY SUMMARY DATA

A daily summary may be represented as derived data.

It may include:

```text
plannedCount
completedCount
postponedCount
missedCount
majorProgress
notablePatterns
```

The summary should be reconstructable where practical from underlying data.

---

# 141. DERIVED DATA

Derived data includes things calculated from canonical records.

Examples:

```text
daily summary
completion rate
pattern confidence
current risk
```

Derived data should not silently become the only source of truth for underlying information.

---

# 142. ANALYTICS DATA

Analytics can exist separately from personal domain data.

Do not introduce analytics fields into core entities merely for reporting unless necessary.

---

# 143. AGGREGATIONS

Long-term analytics should prefer aggregated representations where raw event retention is unnecessary.

Examples:

```text
daily app usage total
weekly completion trend
monthly goal progress
```

---

# 144. RETENTION TIERS

The system should conceptually support different retention classes:

```text
SHORT_TERM_RAW
LONG_TERM_EVENT
DURABLE_MEMORY
DERIVED_AGGREGATE
TEMPORARY_PROCESSING
```

Exact retention durations are implementation/policy decisions.

---

# 145. TEMPORARY PROCESSING DATA

Temporary data may include:

```text
AI request context
speech processing buffers
intermediate transcription state
background processing state
```

Temporary data should be deleted or expired when no longer needed.

---

# 146. RAW DATA VS STRUCTURED DATA

Prefer transforming raw information into useful structured representations.

Example:

```text
Voice recording
↓
Transcript
↓
Reflection
↓
Barrier / outcome information
```

Not:

```text
Voice recording forever
```

unless there is a specific reason.

---

# 147. USER DATA EXPORT

The mature system should support data export where appropriate.

Export design should consider:

```text
Goals
Ambitions
Commitments
Tasks
Events
Reflections
Memories
Patterns
Interventions
Settings
```

Exact export format is a future implementation concern.

---

# 148. USER DATA DELETION

The mature system should support appropriate deletion of user data.

Deletion must consider:

```text
Local storage
Remote storage
Derived data
Caches
Queued data
AI-related stored data
```

---

# 149. EVENT CORRECTION

When an event was recorded incorrectly:

Prefer:

```text
Original event
+
Correction event
```

where practical.

This preserves auditability.

---

# 150. EVENT ORDERING

Events should be ordered using timestamps and sequence metadata where required.

Do not assume:

```text recordedAt
=
occurredAt
```

for offline systems.

---

# 151. OFFLINE EVENT EXAMPLE

A user completes a commitment at 10:00.

Device is offline.

Event is uploaded at 13:00.

Correct representation:

```text
occurredAt = 10:00
recordedAt = 10:00
syncedAt = 13:00
```

The event must not appear to have happened at 13:00.

---

# 152. TIMEZONE

Scheduling semantics should preserve the intended local timezone.

Example:

```text
Commitment:
Tomorrow at 8:00 AM
Timezone:
Asia/Kolkata
```

Exact timezone persistence rules will be finalized in the scheduling contract.

---

# 153. RECURRING COMMITMENTS

Recurring behavior should not be stored as an ambiguous infinite set of commitments.

Prefer an explicit recurrence representation if recurring commitments are introduced.

The exact recurrence schema is future work.

---

# 154. REMINDERS

A reminder should generally be represented as derived scheduling state rather than as the user's primary commitment itself.

Do not confuse:

```text
Reminder
```

with:

```text
Commitment
```

A commitment is the user intention.

A reminder is a system mechanism.

---

# 155. USER-CREATED VS SYSTEM-CREATED

Important entities should identify their origin where useful.

Conceptual:

```text
createdBy
source
```

Possible values:

```text
USER
AI
SYSTEM
INTEGRATION
MIXED
```

---

# 156. USER CONTENT

User-authored text should retain semantic ownership as user content.

AI transformations must not silently rewrite the user's original content.

---

# 157. ORIGINAL VS DERIVED CONTENT

When AI transforms user content:

Store separately:

```text
original
derived
```

where the original is needed for trust/auditability.

Example:

```text
Reflection transcript
↓
AI extracted barrier hypothesis
```

The hypothesis is not the transcript.

---

# 158. CONTENT IMMUTABILITY

Original user-authored historical content should generally not be mutated merely because AI generated a better summary.

Create a new derived representation instead.

---

# 159. USER CONFIRMATION RECORD

When the user confirms or rejects a learned item, that action should be recordable.

Conceptually:

```text
confirmation
confirmedBy = USER
confirmedAt
```

or an equivalent event.

---

# 160. USER FEEDBACK

Feedback on an intervention may be useful as an event.

Examples:

```text
HELPFUL
NOT_HELPFUL
WRONG_ASSUMPTION
ANNOYING
```

Exact feedback model may be introduced later.

---

# 161. NEGLECTED GOAL STATE

Goal resurfacing may use derived state rather than permanently modifying goals.

Potential derived information:

```text
lastMeaningfulProgressAt
daysSinceProgress
neglectRisk
```

Do not store computed values redundantly unless needed for performance.

---

# 162. GOAL RESURFACING EVENT

A resurfacing attempt should be recorded as an intervention/event.

Example:

```text
INTERVENTION_TRIGGERED
type=RESURFACE
goalId=...
```

---

# 163. INTERVENTION HISTORY

Intervention history should allow the system to answer:

```text
What did we try?
When?
Why?
What happened?
```

This is important for learning.

---

# 164. PATTERN LEARNING FROM INTERVENTIONS

Possible relationship:

```text
Pattern:
Small task breakdown helps this user start ambiguous work.

Evidence:
5 BREAK_DOWN interventions
4 completed
```

Intervention outcomes can become pattern evidence.

---

# 165. DECISION TRACE

For increasingly autonomous behavior, the system should be able to connect:

```text
Trigger
↓
Context
↓
Recommendation
↓
Policy
↓
Decision
↓
Intervention
↓
Outcome
```

Identifiers should permit this trace.

---

# 166. CORRELATION MODEL

Use correlation IDs when a complete chain crosses asynchronous subsystems.

Example:

```text
correlationId = ABC123
```

applies to:

```text
observation
event
decision
intervention
outcome
```

where appropriate.

---

# 167. CAUSATION MODEL

A child event/action may reference the event or decision that caused it.

Example:

```text
causationId = previousEventId
```

This allows causal tracing.

---

# 168. DATA VALIDATION

Every persisted entity should validate:

* required fields
* enum values
* IDs
* timestamps
* relationships
* numeric ranges
* string constraints

before persistence.

---

# 169. CONFIDENCE VALIDATION

Confidence values must remain in:

```text
0.0 <= confidence <= 1.0
```

Invalid confidence must be rejected.

---

# 170. USER ID VALIDATION

Every user-owned record must be associated with the correct user identity.

Cross-user access must be impossible through normal repository operations.

---

# 171. OWNERSHIP VALIDATION

When updating:

```text
Goal
Commitment
Reflection
Memory
Pattern
```

the system must verify the record belongs to the current user.

---

# 172. DATA ACCESS PRINCIPLE

Repositories should expose user-scoped operations.

Conceptually:

```text
getGoal(userId, goalId)
```

rather than:

```text
getGoal(goalId)
```

where cross-user ambiguity could occur.

Exact API design will depend on the implementation.

---

# 173. IMMUTABLE HISTORICAL RECORDS

Where data represents historical fact:

```text
Event
Audit record
Original reflection
```

prefer append/correction over mutation.

---

# 174. MUTABLE CURRENT STATE

Where data represents current status:

```text
Goal.status
Commitment.status
AutonomySetting.level
```

mutation is expected, but each important mutation should create appropriate history.

---

# 175. STATE + EVENT PATTERN

Use:

```text
Current State
+
Historical Event
```

for important domain transitions.

Example:

```text
Commitment.status = COMPLETED

Event:
COMMITMENT_COMPLETED
```

---

# 176. DOMAIN TRANSACTION PRINCIPLE

Where a state change and its event must be atomic, persistence should treat them as one logical operation.

Example:

```text
Complete commitment
+
record completion event
```

should not succeed only halfway.

---

# 177. ATOMICITY

Important changes should avoid states such as:

```text
Commitment = COMPLETED
but
COMMITMENT_COMPLETED event missing
```

or:

```text
Event exists
but
Commitment state unchanged
```

where the architecture expects atomicity.

---

# 178. RETRY SAFETY

A retry of a failed operation must not create:

* duplicate commitments
* duplicate events
* duplicate interventions
* duplicate notifications

---

# 179. OPERATION IDENTIFIERS

Asynchronous operations may use:

```text
operationId
```

to prevent duplicate execution.

---

# 180. BACKGROUND JOB RECORD

Background jobs may optionally have:

```text
jobId
operationId
jobType
startedAt
completedAt
status
retryCount
```

This is primarily operational data.

---

# 181. JOB STATUS

Suggested:

```text
QUEUED
RUNNING
COMPLETED
FAILED
CANCELLED
RETRYING
```

---

# 182. DATA ACCESS LAYERS

The data architecture should conceptually be:

```text
Domain Model
   ↓
Repository Interface
   ↓
Local / Remote Data Source
   ↓
Persistence Model
```

---

# 183. ENTITY VS DTO

Do not assume a database document is identical to a domain entity.

Use mapping where required.

Conceptually:

```text
DomainEntity
↕
PersistenceEntity / DTO
```

---

# 184. DATABASE MODELS

Room entities may have fields optimized for persistence.

Firestore/backend documents may have fields optimized for remote storage.

Semantic meaning must remain consistent.

---

# 185. SERIALIZATION CONTRACT

Cross-boundary data must serialize predictably.

Avoid serializing arbitrary internal Kotlin objects directly across backend boundaries without a defined contract.

---

# 186. API VERSIONING

Backend API/document schemas should support safe evolution.

Breaking changes require migration or compatibility.

---

# 187. DATA MIGRATION

Every migration should define:

```text
source version
target version
transformation
validation
rollback/failure strategy where appropriate
```

---

# 188. DELETIONS AND REFERENCES

When deleting an entity referenced by others, define the relationship behavior.

Possible:

```text
CASCADE
NULLIFY
PRESERVE_REFERENCE
BLOCK_DELETE
```

Do not assume universal cascading deletion.

---

# 189. GOAL DELETE EXAMPLE

If a goal is deleted, related commitments may need to:

* remain for historical record
* become orphaned
* be cancelled
* be reassigned

The exact rule must be specified before implementation.

---

# 190. HISTORICAL DATA PRINCIPLE

Deleting a current object should not automatically erase all historical evidence unless product policy explicitly requires that.

For example:

Deleting a goal may not mean deleting historical completion events.

---

# 191. USER PRIVACY VS AUDITABILITY

Historical retention must balance:

```text
Auditability
vs.
User deletion rights
vs.
Privacy
```

The final implementation must explicitly define the chosen policy.

---

# 192. DATA RETENTION POLICY PLACEHOLDER

Exact retention periods are intentionally not hard-coded in this document until product/privacy requirements are finalized.

No developer may invent permanent retention rules without explicit approval.

---

# 193. SENSITIVE FIELDS

Potentially sensitive information includes:

```text
Reflection text
Voice transcript
Location
Raw usage history
Personal goals
Behavioral patterns
Private calendar context
```

Sensitive fields must receive deliberate storage and logging treatment.

---

# 194. SENSITIVE DATA IN LOGS

Do not log raw sensitive fields.

Bad:

```text
IronMindLifecycle Reflection COMPLETED text="..."
```

Preferred:

```text
IronMindLifecycle Reflection COMPLETED reflectionId=abc123
```

---

# 195. SENSITIVE DATA IN AI CONTEXT

Only send the information required for the particular reasoning task.

Avoid sending unrelated personal history.

---

# 196. DATA ACCESS AUDITABILITY

Where practical, the system should be able to identify:

```text
which subsystem accessed sensitive information
```

This becomes increasingly important as autonomy grows.

---

# 197. PERSONAL MODEL REBUILDABILITY

Where feasible, important learned state should be reconstructable from:

```text
Events
Observations
User-confirmed information
Reflections
```

This protects against model corruption.

---

# 198. DERIVED MODEL REBUILD

If a pattern table becomes corrupted, ideally it should be possible to recompute relevant patterns from historical evidence.

This does not mean full recomputation must happen routinely.

---

# 199. EVENT STORE AS LONG-TERM EVIDENCE

Events should serve as a primary source of behavioral history.

However, not every raw event needs indefinite retention.

Retention policy applies.

---

# 200. EVENT PROCESSING

Events may trigger:

```text
State updates
Pattern analysis
Memory updates
Intervention evaluation
Analytics aggregation
Synchronization
```

Each processor must have a clear responsibility.

---

# 201. EVENT PROCESSOR OWNERSHIP

An event processor should not arbitrarily modify unrelated domains.

For example:

```text
PatternProcessor
```

may update:

```text
Pattern
```

but should not directly rewrite:

```text
AutonomySetting
```

unless an explicit contract exists.

---

# 202. MEMORY PROCESSOR

The Memory Processor may:

* identify candidate memories
* evaluate evidence
* update memory confidence
* retire stale memories

It must respect user-confirmed information.

---

# 203. PATTERN PROCESSOR

The Pattern Processor may:

* aggregate observations
* calculate evidence
* update confidence
* detect new patterns
* decay stale patterns

---

# 204. INTERVENTION PROCESSOR

The Intervention Processor may:

* evaluate intervention candidates
* check cooldown
* record outcome
* update intervention history

It must respect autonomy policy.

---

# 205. AI PROCESSOR

AI processing may:

* interpret reflections
* propose patterns
* propose memories
* generate plans
* generate intervention recommendations

It must return structured outputs.

---

# 206. AI-WRITTEN DATA

AI can propose modifications to:

```text
Plan
Task
Pattern
Memory
Intervention
Reflection extraction
```

But high-authority user data must have explicit protection against accidental overwrite.

---

# 207. AI PROPOSAL STATE

Where appropriate, AI-generated modifications may use:

```text
PROPOSED
ACCEPTED
REJECTED
```

before becoming durable user-owned state.

---

# 208. USER-AUTHORED CONTENT PRIORITY

User-authored content should remain the authoritative original.

AI may summarize or interpret it.

AI must not silently replace it.

---

# 209. DATA CONTRACT FOR NIGHTLY REVIEW

The nightly review should conceptually combine:

```text
Date
+
Commitments
+
Tasks
+
Outcomes
+
Relevant observations
+
Interventions
+
User reflection
```

and generate:

```text
Daily Summary
+
Learning Candidates
+
Next-Day Context
```

---

# 210. DAILY SUMMARY

A daily summary is derived information.

It may contain:

```text
plannedActions
completedActions
postponedActions
missedActions
majorProgress
reflectionStatus
```

---

# 211. DAILY SUMMARY SOURCE

A daily summary must be reconstructable from canonical records where practical.

Do not make the summary the only record of what happened.

---

# 212. NEXT-DAY CONTEXT

Next-day context may contain:

```text
important commitments
relevant unfinished work
recent learning
known successful conditions
important risks
```

This is derived contextual state.

---

# 213. GOAL RESURFACING DATA

Goal resurfacing may consider:

```text
goal importance
last meaningful progress
last interaction
recent postponement
goal status
user preference
```

The calculated resurfacing risk should remain derived unless persistence is justified.

---

# 214. PATTERN EVIDENCE EXAMPLE

Suppose:

```text
14 gym commitments
10 completed
4 postponed
```

and:

```text
all 4 postponed sessions were solo
```

A candidate pattern may be:

```text
Solo gym sessions may have higher postponement probability.
```

The pattern should reference evidence rather than become an unsupported truth.

---

# 215. USER CONFIRMATION EXAMPLE

User says:

> "Yes, I hate going alone."

The system may now create/update:

```text
Memory:
User dislikes solo gym sessions.

Confirmation:
USER_CONFIRMED
```

This is materially different from the original inferred pattern.

---

# 216. PATTERN DECAY EXAMPLE

Suppose the user later trains alone successfully for three months.

The old pattern should lose influence.

Potential result:

```text
Confidence:
0.86
↓
0.62
↓
0.35
↓
INACTIVE
```

The exact decay function is an implementation decision.

---

# 217. INTERVENTION LEARNING EXAMPLE

Suppose:

```text
BREAK_DOWN
```

is used repeatedly for large ambiguous tasks.

Results:

```text
5 attempts
4 successful starts
```

This provides evidence for future decisions.

---

# 218. DATA CONTRACT FOR FAILURE

Failure should preserve:

```text
what was intended
what actually happened
when it happened
context
possible reason
user-confirmed reason if available
recovery
```

Do not reduce failure to a Boolean:

```text
failed = true
```

---

# 219. DATA CONTRACT FOR POSTPONEMENT

A postponement should preserve history:

```text
original commitment
original time
postponed at
new time
reason
```

Repeated postponements should remain observable.

---

# 220. REPEATED POSTPONEMENT

Do not overwrite:

```text
postponed count = 4
```

without preserving individual postponement events.

The system needs historical evidence.

---

# 221. DATA CONTRACT FOR SUCCESS

Completion should preserve:

```text
commitment
start time
completion time
duration where available
outcome
context
```

Successful conditions may become learning evidence.

---

# 222. DATA CONTRACT FOR RECOVERY

Recovery should preserve:

```text
original failure/miss
recovery action
recovery time
new outcome
```

This allows IronMind to learn recovery behavior.

---

# 223. DATA INTEGRITY RULE

No entity should reference another entity that cannot be resolved unless the relationship is explicitly designed to support orphaned historical references.

---

# 224. FOREIGN KEY / REFERENCE RULE

Where relational storage is used, appropriate referential integrity should be enforced.

Where document storage is used, equivalent validation must exist at the application/domain layer.

---

# 225. USER SCOPE

All personal data must be logically scoped to one user.

No shared global personal memory should exist.

---

# 226. MULTI-DEVICE FUTURE

The data model should not assume the user has only one device forever.

Future synchronization may involve:

```text
Device A
Device B
Backend
```

Entity IDs and timestamps must therefore be device-independent.

---

# 227. DEVICE ID

If device-specific metadata is required, use an explicit:

```text
deviceId
```

Do not misuse user ID for device identity.

---

# 228. SOURCE DEVICE

Events may optionally record:

```text
sourceDeviceId
```

where useful for synchronization and debugging.

---

# 229. MULTI-DEVICE CONFLICTS

Two devices may update the same object.

The synchronization system must define deterministic conflict behavior for each important entity.

---

# 230. DOMAIN-SPECIFIC CONFLICT EXAMPLE

For an explicit user update:

```text
User changes commitment time on Device A.
```

while:

```text
AI attempts rescheduling on Device B.
```

The user-authorized change should generally have higher authority than the automated suggestion.

---

# 231. RECORD VERSIONING

Optimistic concurrency may use:

```text
version
updatedAt
```

or equivalent mechanisms.

The exact strategy is implementation-specific.

---

# 232. CONCURRENCY

Concurrent modifications to important state must be detected rather than silently lost.

---

# 233. ATOMIC STATE TRANSITION

A domain transition should conceptually produce:

```text
validate
↓
change state
↓
record event
↓
return result
```

as one logical transaction where persistence allows.

---

# 234. EVENT DUPLICATION

The same event must not create repeated downstream side effects.

Example:

```text
COMMITMENT_COMPLETED
```

must not cause:

```text
5 completion notifications
```

because it was processed five times.

---

# 235. EVENT DEDUPLICATION

Use:

```text
eventId
operationId
or equivalent idempotency mechanism
```

to identify repeated processing.

---

# 236. DATA CONTRACT FOR NOTIFICATIONS

A notification is an execution artifact.

Where needed, its semantic origin should reference:

```text
interventionId
decisionId
commitmentId
```

This allows notification behavior to be traced.

---

# 237. DATA CONTRACT FOR PROTECTION

A protection action should be traceable to:

```text
protectionSession
commitment
decision
intervention
```

where applicable.

---

# 238. DATA CONTRACT FOR AI RECOMMENDATION

An AI recommendation should conceptually contain:

```text
recommendationType
reason
confidence
contextReference
model/provider
createdAt
```

The exact schema belongs to `AI_BEHAVIOR_CONTRACT.md`.

---

# 239. AI RECOMMENDATION IS NOT DOMAIN STATE

A recommendation is not automatically a:

```text
Goal
Commitment
Memory
Pattern
```

It becomes domain state only through explicit processing/policy.

---

# 240. AI HYPOTHESIS

A hypothesis should be distinguishable from confirmed knowledge.

Example:

```text
hypothesis:
User may postpone large tasks due to unclear starting point.
```

Do not store it as:

```text
confirmedBarrier
```

without evidence/confirmation.

---

# 241. USER-CONFIRMED FACT

A user-confirmed statement should have stronger semantic authority.

Conceptually:

```text
confirmationState = USER_CONFIRMED
source = USER
```

---

# 242. CONFLICTING EVIDENCE

If new observations conflict with a memory/pattern:

```text
new evidence
↓
re-evaluate
↓
adjust confidence
↓
possibly change status
```

Do not permanently preserve obsolete assumptions.

---

# 243. LEARNING HISTORY

Important learning changes should be represented historically.

Examples:

```text
PATTERN_UPDATED
MEMORY_UPDATED
PATTERN_DECAYED
MEMORY_EXPIRED
```

---

# 244. DATA AUDIT TRAIL

For important autonomous systems, the data architecture should permit an audit trail.

At minimum:

```text
what changed
when
source
reason/reference
```

---

# 245. AUDITABILITY VS RAW LOGGING

Auditability does not require storing every raw internal calculation.

Store meaningful lifecycle and decision metadata.

---

# 246. DATA CONTRACT FOR USER SETTINGS

User settings may include:

```text
autonomy
notifications
background learning
voice reflection
observation sources
protection
communication preferences
```

Settings should be versioned when necessary.

---

# 247. SETTINGS DEFAULTS

Default values must be explicitly defined by product policy.

Coding agents must not invent defaults for high-authority capabilities.

---

# 248. SETTINGS HISTORY

Important settings changes may be recorded as events.

Example:

```text
AUTONOMY_SETTING_UPDATED
```

This helps explain later behavior.

---

# 249. SETTING SOURCE

Settings should generally be:

```text
USER
SYSTEM_DEFAULT
```

AI must not silently modify user settings.

---

# 250. AI CANNOT MODIFY AUTONOMY

Unless explicitly permitted by a future product policy, AI may recommend an autonomy change but must not silently apply it.

---

# 251. PROTECTION DEFAULTS

Protection behavior should be governed by explicit policy.

Do not invent a global rule such as:

```text
block all social media
```

without product approval.

---

# 252. DATA MODEL SHOULD NOT ENCODE PRODUCT UI

Database fields should represent domain meaning.

Avoid fields created solely because a specific screen currently needs them.

---

# 253. PRESENTATION FIELDS

UI-only state such as:

```text
isExpanded
selectedTab
isAnimationRunning
```

should not be persisted as domain data unless there is a product reason.

---

# 254. DOMAIN DATA VS UI CACHE

Keep separate:

```text
Domain state
```

from:

```text
UI state
```

---

# 255. TEMPORARY UI STATE

Temporary UI state should normally remain in:

```text
ViewModel / UI layer
```

not the core database.

---

# 256. FEATURE DATA BOUNDARIES

Features must access domain entities through their defined interfaces.

Do not let one feature manipulate another feature's database rows directly.

---

# 257. REPOSITORY OWNERSHIP

Each repository should have clear ownership of an entity family.

Example:

```text
GoalRepository
CommitmentRepository
ReflectionRepository
PatternRepository
MemoryRepository
```

---

# 258. CROSS-ENTITY OPERATIONS

Cross-entity operations should be implemented through domain use cases rather than arbitrary repository manipulation.

Example:

```text
CompleteCommitment
```

may update:

```text
Commitment
Outcome
Event
```

as one domain operation.

---

# 259. TRANSACTION BOUNDARY

The domain use case should determine the transaction boundary.

Do not spread one logical business operation across unrelated callers.

---

# 260. DATA CONTRACT FOR COMPLETE COMMITMENT

Conceptually:

```text
Input:
commitmentId

Validate:
current state allows completion

Write:
Commitment.status = COMPLETED

Write:
completedAt

Create:
Outcome

Create:
COMMITMENT_COMPLETED event

Log:
IronMindLifecycle Commitment COMPLETED ...

Return:
success
```

---

# 261. DATA CONTRACT FOR POSTPONE COMMITMENT

Conceptually:

```text
Input:
commitmentId
newTime
optional reason

Validate:
state allows postponement

Write:
new schedule
status

Create:
COMMITMENT_POSTPONED event
COMMITMENT_RESCHEDULED event where applicable

Preserve:
original commitment history
```

---

# 262. DATA CONTRACT FOR RECOVERY

Conceptually:

```text
Input:
failed/missed commitment
recovery action

Validate:
recovery allowed

Create:
COMMITMENT_RECOVERED event

Update:
recovery metadata

Preserve:
original failure
```

---

# 263. DATA CONTRACT FOR REFLECTION

Conceptually:

```text
Create Reflection
↓
Persist original input
↓
Process
↓
Extract structured information
↓
Create relevant events
↓
Update memory/pattern candidates
```

---

# 264. DATA CONTRACT FOR VOICE

```text
VOICE_CAPTURED
↓
Audio temporary storage/reference
↓
TRANSCRIPTION
↓
TRANSCRIPT
↓
VOICE_TRANSCRIBED
↓
REFLECTION
↓
AI EXTRACTION
```

---

# 265. DATA CONTRACT FOR MEMORY UPDATE

```text
Evidence
↓
Find relevant memory
↓
Compare
↓
Update or create
↓
Preserve source/confidence
↓
Emit memory event
```

---

# 266. DATA CONTRACT FOR PATTERN UPDATE

```text
New evidence
↓
Candidate pattern
↓
Evaluate confidence
↓
Create/update/decay
↓
Emit pattern event
```

---

# 267. DATA CONTRACT FOR INTERVENTION

```text
Trigger
↓
Context
↓
Recommendation
↓
Policy
↓
Create intervention
↓
Deliver
↓
Outcome
↓
Learning
```

---

# 268. DATA CONTRACT FOR SILENCE

If Decision Engine chooses:

```text
STAY_SILENT
```

the system may record a decision record where useful.

No user-facing intervention should be created merely because a candidate existed.

---

# 269. DATA CONTRACT FOR EXPLANATION

When an automated action requires explanation, the system should be able to reference:

```text
trigger
decision
relevant evidence
policy
action
```

---

# 270. EXPLANATION DATA SHOULD NOT BECOME UNCONTROLLED RAW AI TEXT

Prefer structured references plus a generated explanation layer.

---

# 271. DATA CONTRACT FOR GOAL RESURFACING

A resurfacing attempt should preserve:

```text
goalId
triggerReason
lastProgressAt
decision
intervention
userResponse
```

where appropriate.

---

# 272. USER RESPONSE TO RESURFACING

Possible responses:

```text
STILL_IMPORTANT
PAUSED
NO_LONGER_IMPORTANT
BLOCKED
NEED_HELP
IGNORE
```

Exact values can be finalized later.

---

# 273. DATA CONTRACT FOR NEGLECT

Neglect is not a permanent goal state by default.

It is a derived behavioral condition.

Avoid:

```text
Goal.status = NEGLECTED
```

unless there is a strong product reason.

Prefer derived information such as:

```text
neglectRisk
daysSinceProgress
```

---

# 274. DATA CONTRACT FOR APP DISTRACTION

Do not create an entity:

```text
BadApp
```

as a universal truth.

Instead represent:

```text
Application usage
+
context
+
commitment
+
outcome
```

from which context-specific risk can be inferred.

---

# 275. DATA CONTRACT FOR LEARNING CONDITIONS

Successful conditions may be represented as:

```text
Pattern
```

or:

```text
Memory
```

depending on whether they describe repeated relationships or durable user facts.

---

# 276. EXAMPLE SUCCESS CONDITION

Pattern:

```text
User is more likely to start a large task when the first action is defined beforehand.
```

Evidence:

```text
7 observations
```

Confidence:

```text
0.81
```

---

# 277. EXAMPLE CONFIRMED PREFERENCE

Memory:

```text
User prefers to reflect using voice.
```

Source:

```text
USER
```

Confirmation:

```text
USER_CONFIRMED
```

---

# 278. EXAMPLE INFERENCE

Pattern:

```text
User may postpone exercise more frequently when planning occurs late at night.
```

Source:

```text
OBSERVATION
```

Confirmation:

```text
UNCONFIRMED
```

---

# 279. DATA CONTRACT FOR "WHY"

Where the user provides a reason, preserve it separately from system inference.

Example:

```text
User reason:
"I was exhausted."

AI hypothesis:
"Workday fatigue may contribute to postponement."
```

These must not be conflated.

---

# 280. REASON SOURCE

A reason should identify its origin:

```text
USER_STATED
SYSTEM_OBSERVED
AI_INFERRED
UNKNOWN
```

---

# 281. BARRIER DATA MODEL

Potential barrier information may eventually use:

```text
BarrierHypothesis
```

Conceptual fields:

```text
id
userId
type
description
confidence
evidenceCount
confirmationState
firstObservedAt
lastObservedAt
status
schemaVersion
```

This is a candidate future entity and does not need to exist in V0.

---

# 282. STRENGTH DATA MODEL

Similarly, successful conditions may eventually use:

```text
StrengthPattern
```

Conceptually:

```text
condition
effect
confidence
evidenceCount
status
```

This is optional future architecture.

---

# 283. FUTURE DATA ENTITIES

Possible future entities include:

```text
BarrierHypothesis
StrengthPattern
ContextProfile
DecisionRecord
DailySummary
GoalResurfacingRecord
AIInteractionRecord
```

They must be introduced deliberately.

---

# 284. NO PREMATURE ENTITY CREATION

Do not create every future entity during initial implementation.

A schema should solve current real requirements.

---

# 285. V0 DATA SCOPE

Initial data implementation should likely focus on:

```text
UserProfile
Goal
Ambition
Task
Commitment
Outcome
Event
UserPreference
AutonomySetting
```

with minimal observation support.

---

# 286. V1 DATA SCOPE

Add:

```text
Reflection
Observation
Memory
```

as the reflection and learning system becomes real.

---

# 287. V2 DATA SCOPE

Add:

```text
Pattern
Intervention
DecisionRecord
```

as behavioral intelligence becomes meaningful.

---

# 288. V3+ DATA SCOPE

Expand with:

```text
ProtectionSession
ContextProfile
GoalResurfacing
Advanced intervention learning
```

as required.

---

# 289. DATA CONTRACT DEVELOPMENT RULE

Do not build future database complexity simply because it appears in the Master Blueprint.

Build schemas when the corresponding product capability becomes real.

---

# 290. DATA CONTRACT + AI RULE

The AI layer must consume structured domain information.

Do not make raw database documents the primary AI interface.

---

# 291. AI CONTEXT MODEL

The context builder should construct a task-specific context such as:

```text
Current commitment
Relevant goal
Recent outcomes
Relevant patterns
Relevant memory
Recent intervention history
User preferences
Current environment
```

---

# 292. CONTEXT REFERENCES

AI contexts may reference entity IDs rather than copying all full records.

This helps reduce payload size and information exposure.

---

# 293. MODEL-CONTEXT SEPARATION

The AI context should be treated as a derived representation of canonical data.

It is not the authoritative data store.

---

# 294. AI RESPONSE DOES NOT BECOME DATABASE TRUTH

AI output must pass through:

```text
Validation
↓
Domain policy
↓
Authority check
↓
Persistence
```

---

# 295. DATA QUALITY RULE

Never sacrifice data correctness to make AI processing easier.

The canonical model must remain precise even if AI wants simplified text.

---

# 296. SCHEMA DRIFT PREVENTION

Android and backend must not independently invent fields with the same names but different meanings.

Shared semantic contracts should be documented.

---

# 297. CONTRACT-FIRST BACKEND DEVELOPMENT

Backend implementation should begin from this data contract, not from ad hoc Firestore documents.

---

# 298. CONTRACT-FIRST ANDROID DEVELOPMENT

Room schemas should derive from the canonical domain requirements rather than arbitrary screen fields.

---

# 299. MIGRATION OWNERSHIP

Any schema migration must have an explicit owner and test coverage.

---

# 300. TESTING DATA CONTRACT

At minimum, tests should cover:

```text
Entity validation
State transitions
Serialization
Persistence
Migration
Event creation
Event duplication
Synchronization
Conflict behavior
Confidence bounds
User ownership
```

---

# 301. STATE MACHINE TESTING

For every important state machine:

Test:

```text
Valid transitions
Invalid transitions
Repeated transitions
Recovery transitions
Terminal states
```

---

# 302. EVENT TESTING

For each important state transition:

Verify:

```text
Correct event type
Correct entity ID
Correct timestamp
Correct source
Correct previous/current state
No duplicate event
```

---

# 303. MEMORY TESTING

Verify:

```text
Memory creation
Memory update
User confirmation
User rejection
Confidence update
Decay
Expiration
Deletion
```

---

# 304. PATTERN TESTING

Verify:

```text
Pattern creation
Evidence accumulation
Confidence update
Decay
Confirmation
Rejection
Deactivation
```

---

# 305. SYNCHRONIZATION TESTING

Verify:

```text
Offline write
Retry
Duplicate retry
Success
Conflict
Recovery
```

---

# 306. AI DATA TESTING

Verify:

```text
Valid AI output
Invalid AI output
Unknown enum
Missing field
Invalid confidence
Unsupported entity reference
Conflicting user information
```

---

# 307. DATA LOGGING REQUIREMENT

Important data lifecycle transitions must emit:

```text
IronMindLifecycle [Component] [EVENT] key=value
```

Examples:

```text
IronMindLifecycle Commitment CREATED commitmentId=123
```

```text
IronMindLifecycle Pattern UPDATED patternId=456 confidence=0.81
```

```text
IronMindLifecycle Memory CREATED memoryId=789 source=USER
```

---

# 308. LOGGING SENSITIVE DATA RULE

Never log full:

```text
reflection text
voice transcript
location coordinates
private calendar content
```

unless explicitly required for controlled debugging and protected appropriately.

---

# 309. DATA CONTRACT CHANGE POLICY

Changing this document is an architectural/data decision.

A coding agent must not casually modify:

```text
entity meaning
state machine
event meaning
field semantics
ownership
authority model
```

while implementing an unrelated sprint.

---

# 310. DATA CONTRACT CHANGE REQUIREMENT

Any breaking data change must document:

```text
WHY
WHAT CHANGES
MIGRATION
BACKWARD COMPATIBILITY
AFFECTED COMPONENTS
TEST PLAN
```

---

# 311. ENTITY ADDITION REQUIREMENT

Adding a new canonical entity should answer:

```text
Why is an existing entity insufficient?
What does it own?
Who creates it?
Who updates it?
What is its lifecycle?
How is it persisted?
How is it synchronized?
```

---

# 312. FIELD ADDITION REQUIREMENT

Every important field should have:

```text
Meaning
Type
Required/Optional
Default
Source
Lifecycle
Retention
```

---

# 313. FIELD REMOVAL

Do not remove persisted fields merely because they are no longer used by the current UI.

Consider:

```text
historical data
migration
backward compatibility
sync
analytics
AI context
```

---

# 314. FIELD RENAMING

Renames of persisted fields should be treated as migrations, not casual code refactors.

---

# 315. FIELD SEMANTIC CHANGE

Changing:

```text
scheduledAt
```

from:

> "intended start time"

to:

> "actual start time"

is a breaking semantic change.

Do not do this silently.

---

# 316. DATA CONTRACT REVIEW

Before merging major data-model changes, review:

```text
Domain consistency
Persistence impact
Backend impact
Sync impact
AI impact
Migration
Tests
```

---

# 317. DATA CONTRACT GOLDEN RULE

> **The database must describe what IronMind knows, not what the current screen happens to display.**

---

# 318. DATA CONTRACT SECOND GOLDEN RULE

> **Historical evidence must not be destroyed merely to simplify current state.**

---

# 319. DATA CONTRACT THIRD GOLDEN RULE

> **AI inference must never become indistinguishable from user-confirmed truth.**

---

# 320. DATA CONTRACT FOURTH GOLDEN RULE

> **Every important autonomous behavior must have enough structured data to explain and reconstruct what happened.**

---

# 321. DATA CONTRACT FIFTH GOLDEN RULE

> **Current explicit user intent must remain stronger than stale behavioral assumptions.**

---

# 322. MASTER DATA MODEL

The conceptual complete model is:

```text
USER
 │
 ├── PROFILE
 │
 ├── AMBITIONS
 │      │
 │      └── GOALS
 │             │
 │             ├── PLANS
 │             │     │
 │             │     └── TASKS
 │             │            │
 │             │            └── COMMITMENTS
 │             │
 │             └── OUTCOMES
 │
 ├── REFLECTIONS
 │
 ├── OBSERVATIONS
 │
 ├── EVENTS
 │
 ├── MEMORIES
 │
 ├── PATTERNS
 │
 ├── INTERVENTIONS
 │
 ├── AUTONOMY SETTINGS
 │
 └── PROTECTION RULES
```

---

# 323. MASTER LEARNING MODEL

```text
OBSERVATIONS
      │
      ▼
    EVENTS
      │
      ├───────────────┐
      ▼               ▼
   OUTCOMES       REFLECTIONS
      │               │
      └───────┬───────┘
              ▼
           EVIDENCE
              │
       ┌──────┴──────┐
       ▼             ▼
    PATTERNS       MEMORIES
       │             │
       └──────┬──────┘
              ▼
        PERSONAL MODEL
```

---

# 324. MASTER ACTION MODEL

```text
GOAL
 ↓
PLAN
 ↓
TASK
 ↓
COMMITMENT
 ↓
ACTION
 ↓
OUTCOME
 ↓
EVENT
 ↓
LEARNING
```

---

# 325. MASTER AUTONOMY MODEL

```text
EVENT / CONTEXT
 ↓
RELEVANT DATA
 ↓
AI / RULE RECOMMENDATION
 ↓
AUTONOMY SETTING
 ↓
POLICY
 ↓
DECISION
 ↓
INTERVENTION
 ↓
OUTCOME
```

---

# 326. MASTER DATA TRACE

An ideal autonomous decision should eventually be traceable as:

```text
USER GOAL
      ↓
COMMITMENT
      ↓
OBSERVATION
      ↓
EVENT
      ↓
PATTERN
      ↓
CONTEXT
      ↓
AI RECOMMENDATION
      ↓
DECISION
      ↓
INTERVENTION
      ↓
OUTCOME
      ↓
MEMORY / PATTERN UPDATE
```

---

# 327. FINAL DATA INVARIANTS

The following must remain true:

```text
1. Every user-owned entity belongs to a user.

2. Every persisted entity has a stable identifier.

3. Important current state transitions are historically observable.

4. Historical events are not silently rewritten.

5. Observation is distinct from inference.

6. Inference is distinct from user-confirmed information.

7. Confidence is bounded and meaningful.

8. Patterns can decay.

9. Memories can be corrected.

10. User intent can override stale inference.

11. AI output is validated before persistence.

12. AI cannot silently overwrite high-authority user data.

13. Retried operations must be idempotent where required.

14. Synchronization state is explicit.

15. Offline actions must preserve their original occurrence time.

16. Sensitive content is not unnecessarily logged.

17. Derived data is distinguishable from canonical data.

18. UI state is not automatically domain state.

19. Database semantics are not defined by current UI requirements.

20. Schema changes require deliberate migration.

21. Important autonomous actions have traceable data.

22. Data collection must serve meaningful product decisions.

23. Raw personal data is not retained indefinitely without justification.

24. Historical evidence should remain usable for legitimate learning unless deleted by policy/user request.

25. The model must be able to change as the user changes.
```

---

# 328. FINAL DATA ARCHITECTURE PRINCIPLE

IronMind's data system should enable this:

```text
WHAT I WANTED
      ↓
WHAT I COMMITTED TO
      ↓
WHAT ACTUALLY HAPPENED
      ↓
WHAT I EXPERIENCED
      ↓
WHAT IRONMIND LEARNED
      ↓
WHAT IRONMIND BELIEVES
      ↓
HOW CONFIDENT IT IS
      ↓
WHAT IRONMIND DID
      ↓
WHAT HAPPENED AFTERWARD
      ↓
WHAT SHOULD CHANGE NEXT
```

---

# 329. FINAL DATA STATEMENT

> **IronMind's data model must preserve the difference between intention, action, observation, outcome, interpretation, memory, and decision so that the system can learn without confusing assumptions for truth.**

---

# 330. FINAL DATA GOLDEN RULE

> **Store reality first. Store interpretation separately. Learn from the difference.**

---

# END OF DATA CONTRACT

````
