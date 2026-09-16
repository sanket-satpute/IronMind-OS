# IRONMIND MASTER BLUEPRINT

**Document:** `IRONMIND_MASTER_BLUEPRINT.md`
**Status:** AUTHORITATIVE PRODUCT + SYSTEM BLUEPRINT
**Purpose:** Single source of truth for product vision, behavior, architecture, data, AI, autonomy, development, and validation.

---

# 1. DOCUMENT PURPOSE

This document defines what IronMind is, what it is not, how it should behave, how it should be built, and how future AI coding agents must work on it.

This is the highest-level product and engineering contract for IronMind.

Every developer, AI coding agent, designer, architect, or future contributor working on IronMind MUST read and respect this document before making changes.

This document is authoritative for:

* Product purpose
* Product philosophy
* Core user problem
* Core product loop
* Core concepts
* User experience principles
* Personal memory model
* Behavioral learning model
* AI responsibilities
* Decision-making model
* Intervention model
* Autonomy model
* Android architecture
* Backend architecture
* Local intelligence
* Data architecture
* Voice/reflection architecture
* Background operation
* Security and privacy principles
* Development process
* Testing strategy
* Observability and logging
* Product roadmap
* Scope control
* AI coding-agent rules

This document does NOT mean every feature described here must exist immediately.

The blueprint defines the destination and architectural direction.

Implementation must happen incrementally through controlled sprints.

---

# 2. IRONMIND DEFINITION

## 2.1 Product definition

IronMind is an **autonomous personal action system** that learns how a person lives, identifies patterns that help or prevent them from acting, and proactively helps them move toward the life they have chosen.

IronMind can:

* observe
* understand
* remember
* plan
* schedule
* remind
* protect
* intervene
* adapt
* resurface neglected intentions
* learn from outcomes
* improve future decisions

IronMind is autonomous by default where appropriate, but the user always retains control over its authority.

The fundamental relationship is:

> **You choose the direction. IronMind helps you follow through.**

---

# 3. THE REAL PROBLEM

IronMind is not fundamentally a screen-time application.

It is not fundamentally a habit tracker.

It is not fundamentally a productivity application.

It is not fundamentally an AI chatbot.

The deeper problem is:

> **People know what they want to do, often intend to do it, sometimes even commit to doing it, but fail to consistently turn those intentions into action.**

This creates an:

> **Intention → Action Gap**

---

# 4. THE INTENTION → ACTION GAP

A person may say:

* "I'll go to the gym tomorrow."
* "I'll sleep early tonight."
* "I'll start my business."
* "I'll work on that idea."
* "I'll study for two hours."
* "I'll talk to that person."
* "I'll start travelling."
* "I'll stop wasting time."
* "I'll finish this project."
* "I'll learn this skill."

The intention may be genuine.

Yet the action may not happen.

The reason can vary.

Possible barriers include:

* distraction
* uncertainty
* fear of failure
* fear of judgment
* boredom
* loneliness
* overthinking
* lack of clarity
* low confidence
* low energy
* avoidance
* environmental friction
* excessive phone availability
* poor planning
* task size being too large
* lack of accountability
* lack of immediate reward
* changing priorities
* competing commitments
* emotional resistance
* habit
* fatigue
* context mismatch

IronMind must not assume that one explanation is always correct.

The system must learn the individual.

---

# 5. PHONE DISTRACTION IS A SYMPTOM, NOT ALWAYS THE ROOT CAUSE

IronMind may discover situations such as:

User opens WhatsApp to answer one message.

Then:

* sees statuses
* starts browsing
* loses time

Or:

User opens Instagram to check one thing.

Then:

* enters Explore/Reels
* keeps scrolling

Or:

User opens YouTube for learning/work.

Then:

* watches unrelated content
* continues for an hour

IronMind should be capable of protecting the user's attention.

However:

> **The phone is not always the root cause.**

For example:

A person may skip the gym even when the phone is unavailable because:

* going alone feels boring
* they do not feel motivated
* they are uncertain
* the task feels unpleasant
* they are tired
* they have another emotional barrier

Therefore:

> **IronMind must optimize the person using the phone, not merely optimize phone usage.**

---

# 6. PRODUCT PHILOSOPHY

IronMind follows these principles.

## 6.1 Real life comes first

IronMind exists to improve life outside the application.

The application itself is not the destination.

The user's actual:

* work
* studies
* health
* relationships
* projects
* experiences
* ambitions
* commitments
* personal development

are the destination.

---

## 6.2 The app should disappear when useful

The ideal outcome is often:

> User opens IronMind → makes a decision → acts in real life → does not need IronMind again until necessary.

IronMind must avoid creating unnecessary app dependence.

---

## 6.3 Action over appearance

IronMind should care more about:

* completion
* consistency
* follow-through
* meaningful progress
* recovery after failure
* learning

than:

* streaks
* scores
* badges
* engagement metrics
* cosmetic dashboards

---

## 6.4 Positive but truthful

IronMind must be:

* supportive
* constructive
* calm
* practical
* honest
* direct
* action-oriented

IronMind must NOT:

* shame
* guilt-trip
* insult
* manipulate
* threaten
* emotionally pressure
* pretend certainty
* fabricate psychological conclusions

---

## 6.5 No fake intelligence

IronMind must never claim to know something that it does not actually know.

For example, it must not say:

> "You are afraid of failure."

unless this has been explicitly confirmed by the user or strongly supported and properly framed as a hypothesis.

Preferred language:

> "You have postponed this several times. One possible reason is uncertainty about how to start. Does that fit?"

The distinction between:

* observed fact
* system inference
* user-confirmed fact

is fundamental.

---

## 6.6 One meaningful improvement compounds

"1% better" is a philosophy, not a score.

IronMind should encourage meaningful incremental improvement.

The system should not turn personal growth into a game.

The goal is:

> Do something meaningful today that moves life forward.

---

## 6.7 User chooses the life

IronMind may advise.

IronMind may suggest.

IronMind may protect.

IronMind may intervene.

IronMind may plan.

But the fundamental direction belongs to the user.

> **IronMind does not choose the user's life.**

---

# 7. PRODUCT POSITIONING

IronMind should NOT become:

* a generic productivity app
* a simple habit tracker
* a screen-time dashboard
* a conventional app blocker
* an AI therapist
* a social network
* a challenge platform
* a gamified self-improvement application
* a generic calendar replacement
* a generic task manager
* a generic health tracker
* a generic life dashboard

Individual mechanisms from these categories may exist where they serve the central mission.

But the product identity remains:

> **Closing the intention → action gap.**

---

# 8. IRONMIND'S FOUR ROLES

IronMind operates through four core roles.

## 8.1 Mirror

IronMind shows the user what they actually do.

It can reveal:

* intended actions
* actual actions
* postponements
* abandoned commitments
* repeated patterns
* distractions
* successful conditions
* failure conditions

The mirror should be factual before being interpretive.

---

## 8.2 Assistant

IronMind helps decide:

> "What should I actually do next?"

It can:

* break large intentions into actions
* suggest the next smallest step
* help schedule tasks
* identify realistic plans
* simplify overwhelming goals
* suggest recovery after missed commitments

---

## 8.3 Shield

IronMind protects important action from avoidable distraction.

It may:

* block selected applications
* restrict selected behavior during commitments
* delay access
* interrupt distraction
* activate protection automatically
* suggest protection when risk is high

Protection must remain user-controlled.

---

## 8.4 Learner

IronMind learns:

* what the user wants
* what the user actually does
* what helps
* what prevents action
* when they are effective
* what patterns repeat
* what interventions work
* what interventions fail
* which goals remain important
* which patterns become obsolete

The model must adapt over time.

---

# 9. CORE PRODUCT LOOP

The central IronMind loop is:

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
```

This loop is the foundation of the product.

Features should support this loop.

Features that do not support it should be treated as secondary and require explicit justification.

---

# 10. HIGH-LEVEL LIFE MODEL

The system should conceptually operate as:

```text
YOUR LIFE
    ↓
YOUR GOALS
    ↓
YOUR COMMITMENTS
    ↓
IRONMIND OBSERVES
    ↓
BEHAVIOR + CONTEXT + REFLECTION
    ↓
MEMORY
    ↓
PATTERN DETECTION
    ↓
UNDERSTANDING
    ↓
DECISION
    ↓
INTERVENTION
    ↓
ACTION
    ↓
RESULT
    ↓
LEARNING
    ↓
BETTER FUTURE DECISIONS
```

---

# 11. CORE DOMAIN CONCEPTS

The following concepts are fundamental.

## 11.1 Goal

A meaningful desired outcome or direction.

Examples:

* pass an exam
* build a business
* improve physique
* learn programming
* travel more
* improve social confidence

A goal answers:

> "What matters?"

---

## 11.2 Ambition

A larger desired direction that may not immediately have a deadline.

Example:

> "I want to build my own business."

Ambitions may remain in memory for long periods.

---

## 11.3 Commitment

A concrete promise or decision to perform an action.

Example:

> "Tomorrow at 7 AM I will go to the gym."

Commitments answer:

> "What will I actually do?"

---

## 11.4 Task

A discrete actionable unit.

Example:

> "Write the first section of the business landing page."

---

## 11.5 Plan

A structured sequence of actions toward a goal.

Example:

```text
Goal
↓
Milestone
↓
Task
↓
Scheduled action
```

---

## 11.6 Event

An observed occurrence.

Examples:

* app opened
* app closed
* notification received
* notification interacted with
* focus session started
* task completed
* task postponed
* commitment missed
* location/context changed
* reflection submitted

Events are observations.

Events are not automatically interpretations.

---

## 11.7 Observation

Something the system actually detected.

Example:

> Instagram opened 12 times today.

Not:

> User lacks discipline.

---

## 11.8 Inference

A hypothesis generated from observations.

Example:

> User may be more likely to postpone solo exercise sessions.

Every important inference should have:

* evidence
* confidence
* recency
* status

---

## 11.9 User-confirmed fact

A fact directly confirmed by the user.

Example:

> "I hate exercising alone."

This carries stronger semantic authority than a system inference.

---

## 11.10 Pattern

A repeated relationship between behavior and context.

Example:

```text
Pattern:
User tends to postpone solo gym sessions.

Confidence:
0.86

Evidence:
14 observations

Last observed:
Yesterday

Status:
ACTIVE
```

---

## 11.11 Intervention

An action taken by IronMind in response to a situation.

Examples:

* reminder
* redirect
* protection
* breakdown into smaller steps
* reassurance
* challenge
* question
* recovery prompt
* reschedule
* reflection request

---

## 11.12 Outcome

What happened after an intervention or commitment.

Examples:

* completed
* partially completed
* postponed
* abandoned
* ignored
* repeated
* recovered later

Intervention outcomes must be learnable.

---

# 12. OBSERVATION ≠ INFERENCE ≠ FACT

This is a hard architecture rule.

The system must preserve the distinction.

Example:

### Observation

```text
User postponed gym commitment 4 times this month.
```

### Inference

```text
User may have greater difficulty starting solo gym sessions.
```

### User-confirmed fact

```text
User explicitly said:
"I don't enjoy going to the gym alone."
```

These must never be collapsed into one generic data type without preserving their source.

---

# 13. PERSONAL MEMORY

IronMind should develop a structured long-term memory of the user.

Memory may contain:

### Goals

What the user wants.

### Ambitions

Long-term aspirations.

### Commitments

What the user said they would do.

### Preferences

How they prefer to work and interact.

### Patterns

Repeated behavior relationships.

### Barriers

Potential reasons actions fail.

### Strengths

Conditions that correlate with successful action.

### Contextual patterns

Examples:

* productive times
* distracting times
* effective environments
* successful social conditions
* high-risk situations

### History

What actually happened.

### Reflections

What the user says happened and why.

---

# 14. MEMORY MUST BE TEMPORAL

Memory cannot be treated as permanent truth.

Human behavior changes.

Therefore learned patterns must support:

* confidence
* evidence count
* last observed
* first observed
* decay
* expiration
* status
* source
* confirmation state

A previous pattern may eventually become inactive.

Example:

```text
Pattern:
User tends to procrastinate late at night.

Confidence:
0.82

Evidence:
21 observations

Last observed:
2026-09-01

Status:
ACTIVE
```

Later:

```text
Last observed:
2026-12-05

Recent evidence:
0

Status:
DECAYING
```

Eventually:

```text
Status:
INACTIVE
```

---

# 15. PATTERN CONFIDENCE

Patterns should not appear as absolute truths.

Conceptually:

```text
Pattern = Evidence + Confidence + Recency + Context
```

The system should prefer:

> "This appears to happen often."

over:

> "This is who you are."

The model should be probabilistic and revisable.

---

# 16. BEHAVIORAL LEARNING

IronMind should eventually learn relationships such as:

```text
Context → Behavior
Goal → Behavior
Time → Behavior
Environment → Behavior
Social condition → Behavior
Intervention → Outcome
Barrier → Postponement
Support → Completion
```

Examples:

```text
Friend present
→ gym completion probability increases
```

```text
Large task
→ postponement probability increases
```

```text
Clear first step
→ task start probability increases
```

```text
Instagram access
during planned study
→ distraction probability increases
```

These are learned relationships, not assumptions.

---

# 17. SUCCESS CONDITIONS

IronMind must learn not only failure patterns.

It should also learn:

> What helps this person succeed?

Examples:

* friend/accountability
* quiet environment
* morning schedule
* small starting step
* visible plan
* protected phone
* external deadline
* preparation beforehand
* working outside home
* voice planning
* short sessions

The system should discover:

```text
"What conditions make action easier for this person?"
```

---

# 18. BARRIER MODEL

IronMind may track potential barriers such as:

* distraction
* fear
* uncertainty
* boredom
* loneliness
* low energy
* lack of clarity
* overthinking
* avoidance
* environmental friction
* task complexity
* lack of accountability
* competing priorities

But the system must distinguish:

```text
Observed
Inferred
User confirmed
Unknown
```

The system must never pretend certainty where none exists.

---

# 19. GOAL RESURFACING

Long-term intentions must not disappear.

Example:

User says:

> "I want to start my business."

IronMind should remember this.

If no meaningful progress happens for an extended period, IronMind may proactively surface it.

Example:

> "You mentioned starting your business 37 days ago. You haven't worked on it recently. Is this still important to you?"

Possible outcomes:

* still important
* no longer important
* paused
* blocked
* waiting for something
* ready for action

This allows the model to update itself rather than assuming abandonment.

---

# 20. NIGHTLY REVIEW

Nightly review is a core feedback mechanism.

IronMind should eventually send a notification such as:

> **IronMind — let's close today.**

The review may contain:

* planned actions
* completed actions
* postponed actions
* missed commitments
* major progress
* important observations

Then the user can:

* type
* speak
* skip

Voice input should be fast.

---

# 21. VOICE REFLECTION FLOW

Conceptually:

```text
USER SPEAKS
     ↓
AUDIO CAPTURE
     ↓
SPEECH-TO-TEXT
     ↓
TEXT NORMALIZATION
     ↓
AI UNDERSTANDING
     ↓
STRUCTURED EXTRACTION
     ↓
FACTS / EVENTS / BARRIERS / SUCCESSES
     ↓
MEMORY UPDATE
     ↓
MODEL UPDATE
     ↓
TOMORROW'S PLAN
```

The reflection should not feel like writing a diary.

Its purpose is:

> **Synchronize IronMind's understanding with the user's real life.**

---

# 22. AI RESPONSIBILITY

AI is primarily the reasoning layer.

AI may perform:

* language understanding
* planning
* summarization
* pattern analysis
* hypothesis generation
* reflection interpretation
* goal decomposition
* recommendation generation
* intervention recommendation

AI must NOT independently have unrestricted authority over the device.

---

# 23. DECISION / INTERVENTION ENGINE

There must be a boundary between:

### AI reasoning

and:

### deterministic action execution.

Conceptually:

```text
AI
↓
Recommendation
↓
Decision Engine
↓
Policy / Context / User Settings / Safety
↓
Action
↓
Android
```

Example:

AI says:

```text
PROTECT_STUDY_SESSION
```

The Decision Engine determines:

* Is there an active study commitment?
* Is protection enabled?
* Is the app included?
* Is cooldown active?
* Has the user manually overridden protection?
* Is an intervention already active?
* Is this intervention appropriate?

Only then is an action executed.

---

# 24. INTERVENTION TYPES

The system should support an explicit intervention vocabulary.

Possible intervention types:

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

Additional types may be introduced later only through explicit architectural review.

---

# 25. STAY SILENT IS AN INTERVENTION

Intelligent systems should not constantly interrupt.

Therefore:

```text
STAY_SILENT
```

is a first-class decision.

The system must consider:

* intervention frequency
* user context
* recent interaction
* urgency
* expected value
* likelihood of annoyance
* current task
* user's autonomy settings

The correct intervention may be:

> Do nothing.

---

# 26. INTERVENTION COOLDOWNS

IronMind must avoid:

```text
notification
notification
notification
notification
```

Interventions should support:

* cooldown
* deduplication
* escalation rules
* priority
* context awareness

Example:

If the user ignores a reminder, IronMind should not blindly repeat the same message every 2 minutes.

---

# 27. INTERVENTION LEARNING

IronMind should eventually learn:

```text
Intervention
→ User response
→ Outcome
→ Future intervention adjustment
```

For example:

```text
Large motivational message
→ ignored
```

while:

```text
Small actionable instruction
→ completed
```

The system can learn that the second approach is more effective for that context.

---

# 28. AUTONOMY MODEL

IronMind should eventually support configurable authority.

Suggested levels:

```text
OFF
SUGGEST ONLY
ASK BEFORE ACTION
FULL AUTO
```

The user should be able to control autonomy by capability.

Example:

```text
Planning:
FULL AUTO

Scheduling:
ASK BEFORE ACTION

App Protection:
FULL AUTO

Proactive Notifications:
FULL AUTO

Pattern Detection:
FULL AUTO

Goal Resurfacing:
ASK BEFORE ACTION
```

---

# 29. AUTONOMY CATEGORIES

At minimum:

### Planning

May IronMind generate plans automatically?

### Scheduling

May IronMind schedule actions automatically?

### App Protection

May IronMind automatically protect focus periods?

### Notifications

May IronMind proactively intervene?

### Learning

May IronMind analyze behavior in the background?

### Goal resurfacing

May IronMind bring old intentions back to the user's attention?

---

# 30. AUTOMATION EXPLAINABILITY

Important autonomous actions should be explainable.

The user should eventually be able to see:

```text
WHAT HAPPENED?
WHY DID IRONMIND DO THIS?
WHAT INFORMATION INFLUENCED THE DECISION?
WHAT DID IRONMIND CHANGE?
CAN I UNDO IT?
```

Example:

```text
IronMind protected YouTube during your study session.

Why:
You had an active study commitment.
YouTube was selected as a distraction risk.
Automatic protection is enabled.

Observed evidence:
You previously opened YouTube during similar study sessions.

Action:
Temporary protection enabled.

[Undo]
```

---

# 31. MANUAL CONTROL

Autonomy must never mean loss of control.

The user should be able to:

* disable automation
* override an intervention
* skip protection
* change autonomy level
* edit goals
* delete memories
* correct patterns
* reject AI suggestions
* disable background learning
* disable notifications

---

# 32. CORE DATA PRINCIPLE

Data architecture must preserve provenance.

Every meaningful learned object should conceptually answer:

```text
WHAT IS IT?
WHERE DID IT COME FROM?
WHEN WAS IT OBSERVED?
HOW CONFIDENT ARE WE?
IS IT USER-CONFIRMED?
WHAT EVIDENCE SUPPORTS IT?
IS IT STILL VALID?
```

---

# 33. EVENT-FIRST OBSERVABILITY MODEL

The system should produce structured events for important state transitions.

Examples:

```text
APP_OPENED
APP_CLOSED
MISSION_CREATED
MISSION_STARTED
MISSION_COMPLETED
MISSION_POSTPONED
MISSION_FAILED
MISSION_RECOVERED
PROTECTION_ENABLED
PROTECTION_DISABLED
INTERVENTION_TRIGGERED
INTERVENTION_ACCEPTED
INTERVENTION_IGNORED
REFLECTION_STARTED
REFLECTION_COMPLETED
VOICE_CAPTURED
VOICE_TRANSCRIBED
MEMORY_UPDATED
PATTERN_CREATED
PATTERN_UPDATED
PATTERN_DECAYED
BACKGROUND_JOB_STARTED
BACKGROUND_JOB_COMPLETED
SYNC_STARTED
SYNC_COMPLETED
```

Exact event vocabulary must be defined in implementation contracts before production-scale use.

---

# 34. LOGGING STANDARD

Important system state transitions MUST use structured logs.

Standard format:

```text
IronMindLifecycle [Component] [EVENT] key=value ...
```

Examples:

```text
IronMindLifecycle Mission CREATED missionId=123
```

```text
IronMindLifecycle Protection ENABLED package=com.example.app reason=active_mission
```

```text
IronMindLifecycle Intervention TRIGGERED type=REDIRECT source=pattern
```

```text
IronMindLifecycle Reflection COMPLETED reflectionId=42
```

```text
IronMindLifecycle Pattern UPDATED patternId=17 confidence=0.86
```

---

# 35. LOGGING RULES

Logs should be:

* structured
* searchable
* consistent
* meaningful
* low-noise

Do not log sensitive raw content unnecessarily.

Do not log:

* passwords
* authentication tokens
* API keys
* raw private content when not required
* sensitive secrets

Logs should describe lifecycle state, not become an uncontrolled dump of personal data.

---

# 36. LOCAL VS BACKEND RESPONSIBILITIES

IronMind consists conceptually of four major layers.

```text
ANDROID CLIENT
      ↓
LOCAL INTELLIGENCE
      ↓
BACKEND / MEMORY
      ↓
AI / REASONING
```

with:

```text
DECISION + INTERVENTION ENGINE
```

acting between intelligence and execution.

---

# 37. ANDROID CLIENT

Android is responsible for:

* user interface
* device observations
* notifications
* local scheduling
* focus/protection mechanisms
* voice capture
* permissions
* local state
* foreground/background execution where appropriate
* user controls
* local execution of approved actions

---

# 38. LOCAL INTELLIGENCE

Local intelligence exists for decisions that should be immediate or should not depend on network availability.

Examples:

* active focus session
* local app protection
* current commitment state
* local intervention cooldown
* device-level execution

Real-time protection should not depend on a remote round trip whenever that would make the system unreliable.

---

# 39. BACKEND

The backend should provide long-term persistent memory and cross-session state.

Potential backend responsibilities:

* authentication
* goals
* ambitions
* commitments
* tasks
* event history
* reflections
* patterns
* memory
* intervention history
* configuration
* AI outputs
* long-term analytics

The exact backend implementation may evolve.

---

# 40. AI PROVIDER ABSTRACTION

The application must not hard-code its business logic directly to one AI provider.

Create an abstraction conceptually similar to:

```text
IronMindAI
```

Possible implementation:

```text
GeminiIronMindAI
```

or another provider later.

This allows:

* provider replacement
* model experimentation
* local model integration
* fallback strategies
* testing with mocks

The provider must remain an implementation detail.

---

# 41. AI OUTPUTS MUST BE STRUCTURED

AI should not directly produce arbitrary uncontrolled actions.

Prefer structured outputs such as:

```json
{
  "recommendation": "BREAK_DOWN",
  "reason": "Task appears too large",
  "confidence": 0.78,
  "suggested_action": "Define the first 10-minute step"
}
```

The Decision Engine then evaluates this recommendation.

---

# 42. AI MUST BE TREATED AS FALLIBLE

AI may:

* misunderstand reflections
* misclassify intent
* infer incorrectly
* produce poor plans
* miss context

Therefore:

* validate structured output
* enforce schemas
* apply deterministic policy
* track confidence
* preserve user correction
* never allow uncontrolled execution

---

# 43. AI SHOULD ASK WHEN UNCERTAIN

For important ambiguity:

```text
UNKNOWN
↓
ASK
↓
USER CLARIFICATION
↓
UPDATE MEMORY
```

Do not manufacture certainty.

---

# 44. USER CORRECTION IS HIGH-VALUE DATA

If the user says:

> "No, that's not why I postponed it."

IronMind must treat this as meaningful information.

Potentially:

* correct the inference
* reduce confidence
* update the barrier model
* preserve the correction

The user's correction should generally carry strong authority.

---

# 45. DAILY EXPERIENCE

The exact UI may evolve, but the conceptual user experience should be:

```text
TODAY
 ↓
WHAT MATTERS
 ↓
WHAT I COMMITTED TO
 ↓
WHAT I NEED TO DO
 ↓
IRONMIND PROTECTS / SUPPORTS
 ↓
REAL-LIFE ACTION
 ↓
RESULT
 ↓
NIGHTLY CLOSE
```

The home screen should not become a massive dashboard unless there is strong product justification.

The primary experience should answer:

> **What matters now?**

---

# 46. CURRENT ACTION PRIORITY

IronMind should eventually identify:

```text
What is important?
What is due?
What is currently happening?
What is at risk?
What is the smallest meaningful next action?
```

This is more important than showing every possible piece of information.

---

# 47. MISSION / COMMITMENT STATE MACHINE

Commitments must eventually have explicit state transitions.

Conceptually:

```text
PLANNED
   ↓
COMMITTED
   ↓
STARTED
   ↓
COMPLETED
```

Alternative paths:

```text
COMMITTED
   ↓
POSTPONED
   ↓
RESCHEDULED
   ↓
STARTED
```

or:

```text
COMMITTED
   ↓
MISSED
   ↓
RECOVERED
```

or:

```text
COMMITTED
   ↓
ABANDONED
```

The exact domain naming may change, but state transitions must be explicit.

---

# 48. FAILURE MODEL

Failure must not be treated as identity.

IronMind should record:

> "This action did not happen."

not:

> "You are lazy."

The important question after failure is:

> **What should happen next?**

Possible recovery:

* retry
* reschedule
* reduce scope
* identify barrier
* ask user
* abandon intentionally
* change plan

---

# 49. RECOVERY IS A CORE SYSTEM

The system must not only optimize successful execution.

It must also support:

```text
MISS
↓
UNDERSTAND
↓
ADAPT
↓
RECOVER
↓
CONTINUE
```

One failed commitment must not break the entire system.

---

# 50. PROTECTION SYSTEM

App protection is a mechanism, not the product itself.

Potential capabilities:

* selected-app blocking
* focus-session restrictions
* temporary access limits
* distraction interception
* automatic protection triggered by commitments

Protection must be:

* explicit
* measurable
* configurable
* observable
* overridable

---

# 51. PROTECTION SHOULD BE CONTEXTUAL

The same app may be acceptable in one context and undesirable in another.

Example:

```text
8 PM Saturday
→ YouTube may be normal.

8 AM active study commitment
→ YouTube may be protected.
```

Therefore protection should eventually consider:

* active commitment
* goal
* schedule
* context
* user settings
* recent behavior
* intervention history

---

# 52. BACKGROUND OPERATION

IronMind should be largely autonomous.

The user should not have to constantly open it for the system to function.

Background capabilities may eventually include:

* usage observation
* pattern processing
* event processing
* plan generation
* task evaluation
* reminder scheduling
* nightly review scheduling
* goal resurfacing
* synchronization
* intervention evaluation

Android system restrictions must always be respected.

The implementation must use supported Android background mechanisms appropriate to the job.

Never assume unlimited background execution.

---

# 53. BACKGROUND JOB PRINCIPLES

Background work should be:

* bounded
* observable
* retryable where appropriate
* cancellable
* idempotent where possible
* failure-tolerant
* battery-conscious
* network-aware

Each important job should log lifecycle events.

Example:

```text
IronMindLifecycle BackgroundWorker STARTED job=PatternAnalysis
```

```text
IronMindLifecycle BackgroundWorker COMPLETED job=PatternAnalysis durationMs=4210
```

---

# 54. PERMISSIONS

IronMind may eventually require powerful permissions.

Examples may include permissions associated with:

* usage/app activity
* notifications
* accessibility
* location/context
* calendar
* microphone
* activity/health data
* other integrations

Every permission must have:

* clear purpose
* user control
* graceful denial path
* feature fallback where possible

Do not collect a permission merely because it is technically available.

---

# 55. DATA MINIMIZATION PRINCIPLE

IronMind has a broad learning vision.

However:

> More data does not automatically mean more intelligence.

Every data source must eventually answer:

```text
What decision does this data improve?
How frequently is it required?
Can the same result be achieved with less data?
How long should it be retained?
Who can access it?
```

---

# 56. USER DATA OWNERSHIP MODEL

The architecture should conceptually treat user data as belonging to the user's personal model.

The system should support:

* viewing
* correcting
* deleting
* exporting where appropriate
* disabling selected learning sources

The exact production implementation of export/deletion must be defined later.

---

# 57. SECURITY PRINCIPLES

Never hard-code:

* API keys
* service credentials
* tokens
* secrets

Use secure configuration mechanisms appropriate to each environment.

Backend credentials must not be exposed in the Android client.

---

# 58. DEVELOPMENT ENVIRONMENTS

At minimum, maintain clear distinction between:

```text
Development
Testing
Production
```

AI coding agents must never assume development credentials are production credentials.

---

# 59. IRONMIND DEVELOPMENT PHILOSOPHY

IronMind must be built incrementally.

Do NOT tell an AI coding agent:

> "Build IronMind."

This is too broad.

Instead:

```text
MASTER BLUEPRINT
↓
ARCHITECTURE
↓
SMALL SPRINT
↓
IMPLEMENT
↓
TEST
↓
DIFF REVIEW
↓
HUMAN APPROVAL
↓
COMMIT
↓
NEXT SPRINT
```

---

# 60. AI CODING AGENT RULE

Every coding AI agent MUST:

1. Read `IRONMIND_MASTER_BLUEPRINT.md`.
2. Read relevant architecture/contracts.
3. Inspect the existing implementation.
4. Explain its implementation plan.
5. Modify only the permitted scope.
6. Run appropriate tests.
7. Inspect its own diff.
8. Report changed files.
9. Report test results.
10. Report unresolved issues.
11. Stop when the requested sprint is complete.

---

# 61. AI CODING AGENT MUST NOT

An AI coding agent must NOT:

* invent unrelated features
* redesign unrelated UI
* rewrite the application architecture casually
* rename large portions of the project without reason
* change product meaning
* silently change autonomy behavior
* remove safety controls
* disable logs
* weaken tests
* remove existing features just because they are inconvenient
* introduce unnecessary dependencies
* globally modify compiler/build configuration without justification
* change unrelated files
* refactor huge areas during a narrow feature sprint
* substitute its own product vision
* make broad "improvements" outside scope

---

# 62. SCOPE BOUNDARY

Every sprint must explicitly contain:

```text
IN SCOPE
OUT OF SCOPE
FILES / MODULES ALLOWED TO CHANGE
FORBIDDEN AREAS
EXPECTED BEHAVIOR
TEST REQUIREMENTS
LOGGING REQUIREMENTS
ACCEPTANCE CRITERIA
```

---

# 63. ARCHITECTURE LOCK

The following require explicit explanation before modification:

* database architecture
* backend architecture
* AI architecture
* authentication
* security
* permissions
* mission/commitment state machine
* autonomy model
* intervention system
* event model
* personal memory model
* background architecture
* package/module boundaries

When an architecture change is proposed, the agent must explain:

```text
WHY
WHAT CHANGES
WHY CURRENT DESIGN IS INSUFFICIENT
RISKS
MIGRATION IMPACT
TEST PLAN
```

---

# 64. GIT DISCIPLINE

Every focused sprint should generally produce one focused commit.

Before commit:

```text
git status
git diff
tests
build
```

The agent must verify that the diff contains only intended work.

Large unexpected diffs should be treated as a problem.

---

# 65. SPRINT WORKFLOW

Standard sprint process:

```text
1. SCAN
2. UNDERSTAND
3. PLAN
4. IMPLEMENT
5. TEST
6. REVIEW DIFF
7. VERIFY LOGS
8. REPORT
9. COMMIT
```

---

# 66. TESTING PHILOSOPHY

Testing must validate:

### Product behavior

Does it do what the user expects?

### Domain behavior

Are state transitions correct?

### AI behavior

Are outputs structured and validated?

### Background behavior

Do workers execute correctly?

### Intervention behavior

Are actions triggered appropriately?

### Android behavior

Do permissions and device mechanisms function?

### Failure behavior

Does the system recover gracefully?

---

# 67. MINIMUM SPRINT TEST REQUIREMENT

Every implementation sprint must have tests appropriate to the changed behavior.

At minimum, agents should report:

```text
Tests run:
X

Passed:
X

Failed:
X

Skipped:
X
```

No claim of completion should be made without verification evidence.

---

# 68. LOG VERIFICATION

When lifecycle logging is introduced or modified, tests should verify important expected logs.

Examples:

```text
Mission created
→ CREATED log exists

Mission started
→ STARTED log exists

Mission completed
→ COMPLETED log exists
```

Logging is part of system observability, not optional decoration.

---

# 69. ERROR HANDLING

Every meaningful subsystem should define failure behavior.

Examples:

### AI unavailable

The application should continue operating in degraded mode.

### Network unavailable

Local operations should continue where possible.

### Permission denied

The feature should fail gracefully.

### Background worker fails

The job should log failure and retry when appropriate.

### Speech transcription fails

The user should be able to retry or use text input.

### Backend unavailable

Queued synchronization may occur later where appropriate.

---

# 70. OFFLINE-FIRST PRINCIPLE FOR CRITICAL ACTION

Critical execution mechanisms should not require the backend whenever avoidable.

For example:

```text
Active local focus session
+
Selected protected application
```

should be enforceable using local state.

The user should not lose protection merely because the network is unavailable.

---

# 71. PRODUCT AI VS DEVELOPMENT AI

Two separate concepts must remain distinct.

### Development AI

Tools such as Gemini Antigravity help build the product.

### Product AI

The intelligence inside IronMind reasons about the user.

These are not the same system and must not be architecturally confused.

---

# 72. RECOMMENDED TECHNICAL FOUNDATION

Initial technology direction:

## Android

* Kotlin
* Jetpack Compose
* Android SDK
* lifecycle-aware architecture
* Room
* WorkManager where appropriate

Architecture should maintain strong separation between:

```text
UI
DOMAIN
DATA
SYSTEM INTEGRATIONS
```

---

# 73. ANDROID ARCHITECTURE PRINCIPLES

Avoid a giant central class or navigation file containing all behavior.

Prefer modular separation such as:

```text
feature/
domain/
data/
system/
background/
ai/
intervention/
memory/
```

Exact package structure may evolve.

The principle is:

> **Each subsystem should have a clear responsibility.**

---

# 74. DATA LAYER

The data layer should isolate storage implementation from domain behavior.

Conceptually:

```text
UI
↓
ViewModel / Presentation
↓
Domain
↓
Repository
↓
Local / Remote Data Sources
```

Domain logic should not know whether persistence uses a specific database implementation.

---

# 75. ROOM / LOCAL PERSISTENCE

Room is an initial recommended local database technology.

Potential local data includes:

* commitments
* tasks
* current mission state
* local events
* protection state
* intervention cooldowns
* pending synchronization
* selected personal model information

The exact schema should be created incrementally.

---

# 76. BACKEND DIRECTION

An initial pragmatic backend may use:

* Firebase Authentication
* Firestore
* Cloud Functions
* scheduled/backend jobs where appropriate

This is an implementation direction, not an immutable requirement.

Backend choices may be revisited if product requirements justify it.

---

# 77. DATA SYNCHRONIZATION

Synchronization should account for:

* offline data
* retries
* duplicate events
* ordering
* conflict resolution
* partial failure

Events and writes should ideally be designed to be idempotent where possible.

---

# 78. EVENT IDEMPOTENCY

Background systems often retry.

Therefore operations should avoid producing duplicate effects.

Example:

```text
MISSION_COMPLETED event
```

should not accidentally trigger five identical completion actions because a worker retried.

Use appropriate identifiers and idempotency controls.

---

# 79. AI COST CONTROL

AI should not run unnecessarily.

Before invoking AI ask:

```text
Can this be solved deterministically?
Can local logic solve this?
Does this actually require reasoning?
Does this analysis need to happen now?
Can it be batched?
```

AI calls should be meaningful.

---

# 80. AI CONTEXT CONTROL

Do not send the entire user's life history to the model for every request.

Construct relevant context.

For a gym decision, relevant context might include:

* current commitment
* recent gym history
* recent postponements
* known patterns
* user's current settings

Not every historical event in the database.

---

# 81. CONTEXT WINDOW / MEMORY STRATEGY

Long-term memory should be summarized and structured.

Conceptually:

```text
RAW EVENTS
↓
AGGREGATION
↓
PATTERNS
↓
MEMORY
↓
RELEVANT CONTEXT
↓
AI
```

This improves:

* performance
* cost
* reasoning quality
* privacy
* maintainability

---

# 82. PERSONAL MODEL

The eventual personal model should conceptually include:

```text
Identity / profile
Goals
Ambitions
Commitments
Tasks
Preferences
Patterns
Barriers
Strengths
Context relationships
Intervention history
Reflection history
Outcome history
Autonomy preferences
```

It should be dynamic.

---

# 83. MODEL UPDATE LOOP

The model should update from:

```text
OBSERVATION
+
OUTCOME
+
USER REFLECTION
+
USER CORRECTION
```

Conceptually:

```text
New evidence
↓
Evaluate existing belief
↓
Increase / decrease confidence
↓
Create / update / retire pattern
```

---

# 84. PATTERN DECAY

Patterns should naturally become less trusted when unsupported by recent evidence.

Conceptually:

```text
high confidence
↓
no new evidence
↓
confidence decay
↓
inactive
```

Exact mathematical decay is an implementation decision and must be tested.

---

# 85. NEGLECT DETECTION

IronMind should eventually detect when meaningful goals are neglected.

Possible signals:

* repeated postponement
* no action for long period
* no recent discussion
* expired plan
* repeatedly rescheduled task
* goal remains marked important

This should produce an opportunity for:

```text
RESURFACE
```

not automatically assume failure.

---

# 86. NEGLECTED GOAL RESPONSE

Possible response:

> "You still have this goal marked as important, but there hasn't been progress recently. What changed?"

The system may then identify:

* no longer important
* temporarily paused
* blocked
* overwhelmed
* forgotten
* still important but unclear next step

---

# 87. PLAN GENERATION

IronMind should eventually be able to convert:

```text
Goal
↓
Milestones
↓
Actions
↓
Schedule
↓
Commitments
```

The plan must remain realistic.

AI should avoid generating impressive but impractical plans.

---

# 88. PLAN ADAPTATION

When reality diverges from the plan:

```text
PLAN
↓
REALITY
↓
DIFFERENCE
↓
ANALYZE
↓
ADAPT
```

The system should not repeatedly insist on an obviously failing plan.

---

# 89. DYNAMIC PERSONALIZATION

The same intervention should not necessarily be used for every person.

Eventually IronMind should learn:

```text
Person
+
Context
+
Goal
+
History
+
Intervention history
→
Best next intervention
```

---

# 90. USER COMMUNICATION STYLE

Communication should be:

* concise when action is obvious
* explanatory when autonomy is involved
* calm when the user fails
* direct when avoidance is obvious
* non-judgmental
* practical

Avoid motivational clichés unless they are genuinely useful.

---

# 91. EXAMPLES OF GOOD COMMUNICATION

### Reminder

> "Your study session starts in 10 minutes."

### Redirect

> "You opened Instagram during your protected study session. Your study commitment is still active."

### Breakdown

> "You do not need to finish the whole project now. Start by opening the document and writing the first heading."

### Recovery

> "You missed the 7 PM gym commitment. Do you want to move it to 8:30 PM or schedule tomorrow?"

### Resurfacing

> "You said building the business mattered to you. There has been no progress for 3 weeks. Is it still a priority?"

---

# 92. BAD COMMUNICATION

Avoid:

> "You failed again."

> "You're being lazy."

> "You obviously don't care."

> "You always procrastinate."

> "You are afraid of success."

> "If you really wanted it, you would do it."

These are not acceptable IronMind behavior.

---

# 93. THE SYSTEM MUST PRESERVE HUMAN AGENCY

IronMind should help the user make decisions.

It must not create psychological dependency through:

* emotional manipulation
* artificial guilt
* exaggerated praise
* fear of disappointing the system
* constant surveillance-style messaging

The relationship is:

```text
Assistant
+
Advisor
+
Shield
+
Learner
```

not:

```text
Controller
```

---

# 94. SYSTEM OF RECORD

Important user decisions should have one authoritative source.

Avoid:

```text
Goal exists in UI state
AND in another local object
AND in backend object
AND in another memory system
```

without clear synchronization semantics.

Define canonical ownership for each domain entity.

---

# 95. DOMAIN OWNERSHIP

Before implementing a feature, determine:

```text
Who owns this state?
Who can modify it?
Who can observe it?
Where is it persisted?
What is the source of truth?
```

This is especially important for:

* commitments
* protection state
* autonomy settings
* patterns
* memories
* interventions

---

# 96. OBSERVATION PIPELINE

Long-term architecture should conceptually support:

```text
DEVICE / USER INPUT
       ↓
OBSERVATION
       ↓
NORMALIZATION
       ↓
EVENT
       ↓
STORAGE
       ↓
AGGREGATION
       ↓
PATTERN ANALYSIS
       ↓
MEMORY
```

This provides a clean basis for future learning.

---

# 97. USER INPUT PIPELINE

For text:

```text
USER TEXT
↓
UNDERSTANDING
↓
EXTRACTION
↓
CONFIRMATION IF REQUIRED
↓
DOMAIN UPDATE
↓
EVENT
```

For voice:

```text
VOICE
↓
STT
↓
TEXT
↓
UNDERSTANDING
↓
EXTRACTION
↓
DOMAIN UPDATE
↓
EVENT
```

---

# 98. EVENT + STATE MODEL

Events describe what happened.

State describes what is currently true.

Example:

Event:

```text
MISSION_POSTPONED
```

State:

```text
Mission.status = POSTPONED
```

Do not rely only on current state if historical learning requires event history.

---

# 99. AUDITABILITY

Important autonomous behavior should be reconstructable.

Given:

```text
Why did IronMind block this application?
```

the system should eventually be able to trace:

```text
Active commitment
→ user settings
→ protection rule
→ decision
→ intervention
→ execution
```

---

# 100. CONTROL CENTER / DEVELOPMENT GOD MODE

During development, a special diagnostic interface may be created.

It can expose:

* current mission
* current commitments
* protection state
* observed events
* detected patterns
* AI decisions
* interventions
* background workers
* synchronization
* permissions
* autonomy settings
* model state

This is a development tool.

It should be secured, hidden, or removed from production.

---

# 101. DEBUGGING PRINCIPLE

When behavior is wrong, investigate in order:

```text
Observed input
↓
Event
↓
Stored state
↓
Domain decision
↓
AI recommendation
↓
Decision Engine
↓
Android execution
↓
User-visible outcome
```

Avoid randomly editing UI code to fix systemic problems.

---

# 102. PRODUCT ROADMAP

IronMind should evolve in stages.

---

# 103. V0 — CORE ACTION LOOP

V0 goal:

> Make the intention → action loop work.

Core:

```text
Goal / Commitment
↓
Plan
↓
Protection
↓
Execution
↓
Result
```

Potential capabilities:

* create goal
* create commitment
* define task
* start action
* complete action
* postpone
* basic focus protection
* basic notification
* basic history

Do NOT attempt full intelligence yet.

---

# 104. V1 — MEMORY + REFLECTION

V1 adds:

* persistent personal memory
* event history
* nightly review
* text reflection
* voice reflection
* speech-to-text
* structured extraction
* basic personal history

Loop becomes:

```text
PLAN
↓
ACT
↓
RESULT
↓
REFLECT
↓
REMEMBER
```

---

# 105. V2 — PERSONAL UNDERSTANDING

V2 adds:

* pattern detection
* barrier hypotheses
* success conditions
* behavioral analysis
* personalized recommendations
* confidence
* evidence
* pattern decay
* user corrections

Now IronMind begins to:

> Learn the person.

---

# 106. V3 — AUTONOMOUS ACTION

V3 adds:

* automatic planning
* automatic scheduling
* automatic protection
* proactive interventions
* context-aware decisions
* intervention cooldowns
* intervention learning
* configurable autonomy levels

Now IronMind starts operating proactively.

---

# 107. V4 — DEEP ADAPTATION

V4 adds:

* long-term behavioral modeling
* context awareness
* neglected ambition resurfacing
* deeper personalized intervention selection
* cross-context learning
* increasingly adaptive planning
* autonomous life-management assistance

This is closer to the full long-term vision.

---

# 108. MVP DEFINITION

The first usable product does NOT need the full AI system.

A strong first version should prove:

> When I decide to do something important, IronMind helps me actually do it.

That is the core validation.

---

# 109. FIRST PRODUCT VALIDATION QUESTIONS

Before expanding, validate:

### Does the user create meaningful commitments?

### Does protection help action happen?

### Does the user return for recovery after failure?

### Does nightly reflection provide useful information?

### Does the user feel understood rather than monitored?

### Does the system reduce the intention → action gap?

These matter more than feature count.

---

# 110. EXPANSION RULE

A feature should be considered for addition only if it improves one or more of:

```text
Understanding
Planning
Commitment
Protection
Action
Recovery
Reflection
Learning
Adaptation
```

---

# 111. NO FEATURE CREEP

Examples of features that must NOT be added merely because they are interesting:

* social feed
* friends system
* chat platform
* public profiles
* leaderboard
* generic community
* unnecessary gamification
* unrelated entertainment features
* excessive analytics
* vanity metrics

Any new major feature must demonstrate how it supports IronMind's core mission.

---

# 112. UI PRINCIPLES

The interface should be:

* calm
* focused
* readable
* action-oriented
* low-friction

Avoid excessive:

* cards
* charts
* dashboards
* animations
* decorative gamification
* unnecessary navigation

The UI should make the next action obvious.

---

# 113. INFORMATION HIERARCHY

When showing information, prioritize:

```text
1. What matters
2. What happens now
3. What I committed to
4. What needs action
5. Relevant context
6. History
7. Analytics
```

---

# 114. NOTIFICATION PHILOSOPHY

Notifications must have a reason.

Before sending:

```text
Is this useful now?
Is it urgent?
Will this help action?
Has IronMind already interrupted recently?
Would silence be better?
```

---

# 115. NOTIFICATION PRIORITY

Conceptually:

```text
CRITICAL
HIGH
NORMAL
LOW
```

Examples:

Critical:
important time-sensitive action.

High:
active commitment risk.

Normal:
scheduled reminder.

Low:
non-urgent information.

---

# 116. NOTIFICATION DEDUPLICATION

Do not send multiple notifications with the same semantic purpose.

A notification system should understand:

```text
"Reminder to study"
```

and

```text
"Your study session starts now"
```

may be related events and should not necessarily both be shown.

---

# 117. INTERRUPTION BUDGET

The system should eventually maintain an implicit or explicit interruption budget.

Conceptually:

```text
High value
+
Low interruption frequency
=
Good intervention
```

not:

```text
More notifications
=
More assistance
```

---

# 118. CONTEXT ENGINE

Eventually IronMind should reason about context.

Potential context dimensions:

* time
* day
* current app
* recent app sequence
* location/context where permitted
* calendar
* current commitment
* goal
* task
* recent behavior
* recent intervention
* social/accountability context where available
* device state
* user-entered information

The context engine must only use data sources that are actually available and authorized.

---

# 119. CALENDAR / TASK INTEGRATION

Future integrations may allow IronMind to understand:

* appointments
* deadlines
* classes
* meetings
* scheduled work

But external systems must remain integration boundaries.

The user should know what IronMind is reading and how that information affects decisions.

---

# 120. LOCATION / CONTEXT LEARNING

Location may eventually help identify patterns such as:

```text
At library → high study completion
At home → higher distraction
```

This must remain subject to:

* permission
* user control
* data minimization
* clear purpose

Do not use location merely because it is available.

---

# 121. APP USAGE LEARNING

IronMind may eventually observe:

* app launches
* frequency
* duration
* sequences
* time-of-day
* relation to commitments

The goal is not to generate a screen-time score.

The goal is:

> Understand whether phone behavior is helping or preventing action.

---

# 122. NOTIFICATION LEARNING

IronMind may eventually analyze:

* number of notifications
* notification timing
* interruption patterns
* notification interactions

Potentially:

```text
High notification load
→ increased task switching
```

Such relationships must be learned from evidence.

---

# 123. SLEEP / ACTIVITY / OTHER SIGNALS

Future integrations may provide useful context.

Examples:

* sleep
* activity
* workouts
* calendar
* travel
* environmental context

These should be added only where they improve decisions meaningfully.

---

# 124. DATA RETENTION PRINCIPLE

Not every raw observation should be retained forever.

The system should eventually distinguish:

```text
Raw short-term events
Long-term summaries
Durable memories
Inactive patterns
Deleted data
```

Retention strategy should be explicit.

---

# 125. PRIVACY PRINCIPLE

IronMind is a highly personal system.

Privacy should therefore be considered architectural, not cosmetic.

The system should minimize unnecessary exposure of:

* personal reflections
* behavioral history
* location
* voice
* sensitive context
* personal goals

Especially avoid logging or transferring raw data when structured metadata is enough.

---

# 126. USER TRANSPARENCY

The user should eventually understand:

```text
What IronMind observes
What it learns
What it assumes
What it does automatically
What permissions it uses
```

This is essential for trust.

---

# 127. TRUST MODEL

Trust should come from:

```text
Predictable behavior
+
Explainable decisions
+
User control
+
Accurate memory
+
Honest uncertainty
+
Successful assistance
```

not from anthropomorphic claims.

---

# 128. NO PRETEND HUMANITY

IronMind can feel personal.

It should not pretend to be:

* a human friend
* a therapist
* a conscious entity
* emotionally dependent on the user

It is an intelligent personal system designed to help action.

---

# 129. PRODUCT SUCCESS METRIC

The most important product question is not:

> "How much time did users spend inside IronMind?"

It is:

> **"Did IronMind help users do things they actually wanted to do?"**

Potential product-level outcome metrics may eventually include:

* commitment completion
* recovery after missed commitments
* reduction in repeated postponement
* goal progress
* successful use of protection
* intervention effectiveness
* user-confirmed improvement

Avoid optimizing around app engagement alone.

---

# 130. IRONMIND SHOULD NOT MAXIMIZE ITS OWN ENGAGEMENT

The product should actively resist:

```text
engagement for engagement's sake
```

The ideal successful session may be very short.

---

# 131. EXPERIMENTATION PRINCIPLE

New behavior should first be tested narrowly.

Example:

Instead of:

> "Introduce autonomous AI intervention everywhere."

Test:

> "During active study commitments, detect repeated YouTube launches and offer a single redirect intervention."

Measure outcome.

Then iterate.

---

# 132. GRADUAL AUTONOMY

Autonomy should expand only as reliability improves.

Conceptually:

```text
Observe
↓
Suggest
↓
Ask
↓
Act with permission
↓
Automate
```

More authority requires evidence that the system makes good decisions.

---

# 133. FAIL-SAFE PRINCIPLE

When uncertain:

```text
Do less.
Ask.
Preserve user control.
```

Do not take aggressive autonomous action under uncertainty.

---

# 134. EXCEPTION PRINCIPLE

Some actions may require stronger certainty because they affect:

* user control
* device access
* important commitments
* external systems
* persistent data
* sensitive data

Those actions should have stricter policy checks.

---

# 135. VERSIONING

Important schemas and behavioral contracts should be versioned.

Examples:

```text
MemorySchema v1
EventSchema v1
InterventionSchema v1
AutonomyPolicy v1
```

Breaking changes require migration planning.

---

# 136. BACKWARD COMPATIBILITY

When persisted data already exists, do not casually change schema semantics.

Use:

```text
migration
versioning
backfill
compatibility
```

as appropriate.

---

# 137. OBSERVABILITY REQUIREMENT

Every important subsystem should make it possible to answer:

```text
Is it running?
What is it doing?
Why did it act?
What failed?
What happened last?
```

At minimum this requires:

* structured logs
* state tracking
* error reporting
* test coverage

---

# 138. PERFORMANCE PRINCIPLE

IronMind should remain lightweight enough for a personal phone.

Be cautious with:

* frequent background work
* excessive database writes
* large AI payloads
* high-frequency polling
* unnecessary network requests
* battery-intensive observation

---

# 139. BATTERY PRINCIPLE

Background intelligence should be efficient.

Prefer:

```text
event-driven
scheduled
batched
context-triggered
```

over uncontrolled continuous polling.

---

# 140. NETWORK PRINCIPLE

Network should be required only where justified.

Critical local mechanisms should function locally.

AI/backend features should degrade gracefully.

---

# 141. PRODUCT LAYERS

Conceptually:

```text
LAYER 1 — EXPERIENCE
UI / Notifications / Voice

LAYER 2 — ACTION
Commitments / Missions / Tasks / Protection

LAYER 3 — OBSERVATION
Events / Usage / Context / Outcomes

LAYER 4 — MEMORY
Goals / Patterns / Preferences / History

LAYER 5 — REASONING
AI / Analysis / Planning

LAYER 6 — DECISION
Intervention Engine / Policies / Autonomy

LAYER 7 — EXECUTION
Android / Backend / External Integrations
```

---

# 142. SEPARATION OF CONCERNS

AI must not directly manipulate Android.

UI must not directly implement complex business rules.

Repositories must not become business logic containers.

Background workers must not contain all product intelligence.

Intervention policy must not be hidden inside UI code.

Each responsibility must have a clear home.

---

# 143. DOMAIN LANGUAGE

Use consistent domain terminology.

Preferred:

* Goal
* Ambition
* Commitment
* Task
* Plan
* Event
* Observation
* Pattern
* Memory
* Intervention
* Outcome
* Autonomy
* Protection
* Reflection
* Recovery

Do not create multiple terms for the same concept without reason.

---

# 144. FEATURE NAMING

Feature names should describe behavior rather than implementation details.

Good:

```text
GoalResurfacing
InterventionEngine
PersonalMemory
ProtectionManager
ReflectionService
PatternAnalyzer
```

Avoid meaningless names such as:

```text
MagicManager
UltimateHelper
AIThing
SuperEngine
```

---

# 145. CODE QUALITY

Code should prioritize:

* correctness
* clarity
* testability
* maintainability
* explicit state
* clear interfaces

Do not optimize prematurely.

Do not write overly clever abstractions without need.

---

# 146. DEPENDENCY POLICY

New dependencies require justification.

Before adding a library:

```text
Why is it needed?
Why can't existing platform APIs solve this?
What maintenance risk does it introduce?
Does it increase app size?
Does it create licensing/security concerns?
```

---

# 147. AI AGENT DIFF REVIEW

After every AI implementation:

```text
git diff
```

must be reviewed.

Look for:

* unrelated changes
* deleted functionality
* accidental formatting of entire files
* dependency changes
* manifest changes
* Gradle changes
* permissions
* changed autonomy behavior
* removed logs
* weakened tests

---

# 148. AI AGENT REPORT FORMAT

Every sprint agent should report:

```text
SPRINT:
<name>

OBJECTIVE:
<what was implemented>

FILES CHANGED:
<list>

FILES NOT CHANGED:
<important protected areas>

BEHAVIOR:
<what now works>

TESTS:
<results>

LOGS:
<important lifecycle events>

DIFF REVIEW:
<summary>

KNOWN ISSUES:
<remaining issues>

ARCHITECTURAL CHANGES:
<none or explanation>

COMMIT:
<hash/message if committed>
```

---

# 149. STOP CONDITION FOR AI AGENTS

When the sprint acceptance criteria are met:

> STOP.

Do not continue improving unrelated areas.

---

# 150. NO SILENT SCOPE EXPANSION

If the agent discovers a separate problem:

```text
Problem discovered:
...

Not fixed because:
Outside sprint scope.

Recommended follow-up:
Sprint X
```

Do not silently fix unrelated architecture.

---

# 151. HUMAN APPROVAL GATE

Important product/architecture changes should pass a human review step.

Conceptually:

```text
AI IMPLEMENTS
↓
TESTS
↓
DIFF
↓
HUMAN REVIEW
↓
APPROVE
↓
COMMIT
```

---

# 152. MASTER BLUEPRINT CHANGE POLICY

This document itself should not be casually modified.

Changes to the Master Blueprint require an explicit product decision.

A coding agent should never rewrite this document as part of a normal feature sprint.

---

# 153. SUBORDINATE CONTRACTS

After the Master Blueprint is established, the project should eventually contain smaller authoritative documents.

Recommended:

```text
PRODUCT_CONSTITUTION.md
SYSTEM_ARCHITECTURE.md
DATA_CONTRACT.md
AI_BEHAVIOR_CONTRACT.md
AUTONOMY_POLICY.md
INTERVENTION_RULES.md
DEVELOPMENT_RULES.md
```

These documents must be subordinate to the Master Blueprint.

If conflict exists:

```text
MASTER BLUEPRINT
    ↓
SUBORDINATE CONTRACT
    ↓
IMPLEMENTATION
```

Higher-level rules win.

---

# 154. PRODUCT CONSTITUTION

`PRODUCT_CONSTITUTION.md` should later contain:

* product philosophy
* tone
* principles
* forbidden product directions
* user agency
* core problem

---

# 155. SYSTEM ARCHITECTURE

`SYSTEM_ARCHITECTURE.md` should later contain:

* Android architecture
* backend architecture
* event architecture
* AI architecture
* local intelligence
* synchronization
* system boundaries

---

# 156. DATA CONTRACT

`DATA_CONTRACT.md` should later contain:

* entities
* schemas
* event definitions
* state machines
* identifiers
* versioning
* synchronization rules
* provenance

---

# 157. AI BEHAVIOR CONTRACT

`AI_BEHAVIOR_CONTRACT.md` should later define:

* prompts
* output schemas
* uncertainty rules
* context construction
* safety constraints
* hallucination controls
* memory writing rules
* user correction behavior

---

# 158. AUTONOMY POLICY

`AUTONOMY_POLICY.md` should later define:

* autonomy levels
* permitted automated actions
* permission boundaries
* manual overrides
* explainability
* escalation rules
* fail-safe behavior

---

# 159. INTERVENTION RULES

`INTERVENTION_RULES.md` should later define:

* intervention types
* trigger conditions
* priority
* cooldowns
* deduplication
* escalation
* stay-silent logic
* logging

---

# 160. DEVELOPMENT RULES

`DEVELOPMENT_RULES.md` should later define:

* sprint process
* Git rules
* tests
* logging
* code review
* AI agent behavior
* architecture-change process
* release rules

---

# 161. FIRST IMPLEMENTATION PRINCIPLE

Do not begin with:

```text
AI brain
behavior prediction
full autonomy
deep integrations
```

Build the foundation first.

A system cannot learn reliably if the underlying events, commitments, outcomes, and memory are unreliable.

---

# 162. FOUNDATIONAL IMPLEMENTATION ORDER

Recommended sequence:

```text
PROJECT FOUNDATION
↓
CORE DOMAIN
↓
COMMITMENT SYSTEM
↓
LOCAL STORAGE
↓
BASIC UI
↓
ACTION EXECUTION
↓
PROTECTION
↓
EVENTS + LOGGING
↓
BACKGROUND FOUNDATION
↓
BACKEND SYNC
↓
REFLECTION
↓
VOICE
↓
PERSONAL MEMORY
↓
PATTERN ANALYSIS
↓
INTERVENTION ENGINE
↓
AUTONOMY
↓
DEEP ADAPTATION
```

---

# 163. DO NOT BUILD THE AI FIRST

Without trustworthy structured data, AI will learn from noise.

Therefore:

```text
GOOD OBSERVATIONS
↓
GOOD MEMORY
↓
GOOD CONTEXT
↓
GOOD AI
```

not:

```text
AI FIRST
↓
everything else later
```

---

# 164. FOUNDATIONAL ENTITIES

Initial domain implementation will likely require some subset of:

```text
Goal
Ambition
Commitment
Task
Plan
Event
Outcome
Reflection
UserPreference
AutonomySetting
```

Additional entities should be introduced only when justified.

---

# 165. FUTURE LEARNING ENTITIES

Later:

```text
Pattern
BarrierHypothesis
StrengthPattern
Intervention
InterventionOutcome
PersonalMemory
ContextProfile
GoalResurfacing
```

---

# 166. FIRST USE CASES

Early implementation should focus on concrete real-life scenarios.

### Scenario 1 — Study

```text
I need to study for an exam.
↓
Create commitment
↓
Schedule session
↓
Protect distractions
↓
Study
↓
Complete / postpone
↓
Reflect
```

### Scenario 2 — Gym

```text
I will go to the gym tomorrow.
↓
Commit
↓
Reminder
↓
Action
↓
Outcome
↓
Learn
```

### Scenario 3 — Business

```text
I want to build a business.
↓
Define first step
↓
Commit
↓
Execute
↓
Track progress
↓
Resurface if neglected
```

---

# 167. CORE VALIDATION SCENARIO

The core system should eventually support:

```text
User:
"I'll study tomorrow at 8 AM."

IronMind:
Creates commitment.

8 AM:
Reminds user.

User opens distracting app:
IronMind recognizes active commitment.

Protection:
Activates if configured.

User studies:
Completion recorded.

Night:
IronMind summarizes the day.

User:
"I struggled because I didn't know what to study first."

AI:
Extracts barrier.

Memory:
"Unclear starting point may increase study postponement."

Next session:
IronMind suggests preparing the first task in advance.
```

This illustrates the complete philosophy.

---

# 168. LONG-TERM VISION EXAMPLE

Eventually the system may know:

```text
User wants:
Build business.

Observed:
No work for 24 days.

Historical pattern:
Large ambiguous tasks often get postponed.

User previously said:
"I get stuck because I don't know where to begin."

Current context:
Free evening.

IronMind decision:
Suggest a 15-minute concrete first action.

Autonomy:
FULL AUTO for suggestions.

Intervention:
"Your business goal is still important. Start with one 15-minute task: define the landing page sections."

Outcome:
User acts.

Learning:
Small concrete starts have high success probability in this context.
```

This is the eventual direction.

---

# 169. REAL SUCCESS DEFINITION

IronMind succeeds when:

```text
The user decides something meaningful.
        ↓
IronMind helps make it concrete.
        ↓
The user actually acts.
        ↓
IronMind learns what helped.
        ↓
The next decision becomes easier.
```

Repeated over time:

```text
Intention
↓
Action
↓
Learning
↓
Adaptation
↓
Better action
↓
Better life
```

---

# 170. FINAL PRODUCT PRINCIPLE

IronMind is not here to make the user better at using IronMind.

IronMind exists to help the user become better at living the life they have chosen.

The phone that once helped waste time should become a tool for:

* focus
* action
* follow-through
* growth
* learning
* execution

---

# 171. FINAL PRODUCT NORTH STAR

The North Star of IronMind is:

> **Close the gap between what a person says they want to do and what they actually do.**

---

# 172. FINAL PRODUCT PHILOSOPHY

```text
You choose the life.
        ↓
You decide what matters.
        ↓
You make commitments.
        ↓
IronMind helps turn them into action.
        ↓
IronMind protects important action.
        ↓
IronMind observes what actually happens.
        ↓
IronMind learns what helps and what gets in the way.
        ↓
IronMind adapts.
        ↓
You become better at following through.
```

---

# 173. FINAL ARCHITECTURAL PHILOSOPHY

The system should be understood as:

```text
ANDROID
= HANDS

BACKEND
= LONG-TERM MEMORY

AI
= REASONING

DECISION ENGINE
= POLICY / JUDGMENT

PERSONAL MODEL
= UNDERSTANDING OF THE USER

INTERVENTION SYSTEM
= ACTION SELECTION

USER
= FINAL AUTHORITY
```

---

# 174. FINAL ENGINEERING RULE

> **Never sacrifice architectural integrity for speed of implementation.**

Fast coding that creates an unmaintainable system is not progress.

The goal is:

```text
Small scope
+
Clear architecture
+
Reliable data
+
Observable behavior
+
Strong tests
+
Controlled AI
+
Human approval
=
IronMind
```

---

# 175. FINAL AI CODING RULE

Every coding AI working on IronMind must remember:

> **You are implementing IronMind, not redesigning IronMind.**

The AI must follow the blueprint.

It must not invent the product.

It must not expand scope.

It must not silently change behavior.

It must not remove controls.

It must not treat passing compilation as proof of correctness.

It must inspect, implement, test, review, report, and stop.

---

# 176. FINAL PRODUCT RULE

Whenever there is uncertainty about a proposed feature, architecture, AI behavior, or intervention, ask:

```text
Does this help close the intention → action gap?
```

If the answer is no, it should not automatically belong in IronMind.

---

# 177. FINAL NORTH STAR STATEMENT

> **IronMind is an autonomous personal action system that learns how you live, identifies patterns that help or prevent you from acting, and proactively helps you move toward the life you have chosen.**
>
> **It can observe, understand, remember, plan, protect, intervene, adapt, and learn — while keeping the user in control of its authority.**
>
> **You choose the direction. IronMind helps you follow through.**

---

# 178. DOCUMENT AUTHORITY

This document is the authoritative master blueprint for IronMind.

All future architecture, development, AI-agent prompts, feature decisions, and subsystem contracts should derive from it.

When implementation details conflict with this document, implementation details must be reconsidered.

When a new requirement conflicts with this document, the requirement must be explicitly evaluated before changing the blueprint.

**Do not silently drift.**

---

# END OF IRONMIND MASTER BLUEPRINT
