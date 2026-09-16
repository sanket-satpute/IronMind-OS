`AUTONOMY_POLICY.md`

````md
# IRONMIND AUTONOMY POLICY

**Document:** `AUTONOMY_POLICY.md`  
**Status:** AUTHORITATIVE AUTONOMY CONTRACT  
**Parent Documents:**
- `IRONMIND_MASTER_BLUEPRINT.md`
- `PRODUCT_CONSTITUTION.md`
- `SYSTEM_ARCHITECTURE.md`
- `DATA_CONTRACT.md`
- `AI_BEHAVIOR_CONTRACT.md`

**Purpose:** Define exactly what IronMind is allowed to do automatically, what requires user confirmation, what is suggestion-only, what is forbidden, how overrides work, how autonomy changes are controlled, and how autonomous behavior remains explainable and reversible.

---

# 1. PURPOSE

IronMind is intended to become highly autonomous.

However:

> **Autonomy exists to reduce friction, not to remove user authority.**

This document defines the authority boundary between:

```text
WHAT IRONMIND CAN UNDERSTAND
````

and:

```text
WHAT IRONMIND IS ACTUALLY ALLOWED TO DO
```

The AI Behavior Contract defines how AI reasons.

This document defines whether IronMind may act on that reasoning.

---

# 2. AUTONOMY NORTH STAR

The goal of autonomy is:

> **Let IronMind handle predictable operational work automatically while keeping meaningful decisions under the user's control.**

---

# 3. FUNDAMENTAL RULE

No AI model, background worker, heuristic, or future component may grant itself additional authority.

Authority comes from:

```text
USER CONFIGURATION
+
SYSTEM POLICY
+
CURRENT CONTEXT
+
PERMISSION
+
VALIDATION
```

---

# 4. AUTONOMY MODEL

IronMind supports four conceptual autonomy levels:

```text
OFF
SUGGEST_ONLY
ASK_BEFORE_ACTION
FULL_AUTO
```

These levels apply to capabilities individually.

The system should not use one global switch as the only form of autonomy control.

---

# 5. AUTONOMY LEVEL DEFINITIONS

## 5.1 OFF

IronMind may observe and maintain internal state as allowed by other settings, but it must not perform the corresponding automated capability.

Example:

```text
Automatic Protection = OFF
```

means IronMind cannot automatically activate protection.

It may still show:

> "Protection could help during this session."

if another allowed capability supports a suggestion.

---

# 6. SUGGEST_ONLY

IronMind can analyze and recommend.

It cannot perform the action automatically.

Example:

```text
AI:
Protection recommended.

Autonomy:
SUGGEST_ONLY.

Result:
Show recommendation.
Do not activate protection.
```

---

# 7. ASK_BEFORE_ACTION

IronMind may prepare the action but must obtain user confirmation before execution.

Example:

> "You have an active study session. Would you like me to enable protection?"

No execution occurs until the required confirmation is received.

---

# 8. FULL_AUTO

IronMind may execute the capability automatically when all policy conditions are satisfied.

Example:

```text
Protection = FULL_AUTO
+
active study commitment
+
eligible app
+
required permission
+
no cooldown conflict
=
automatic protection
```

---

# 9. FULL_AUTO DOES NOT MEAN UNLIMITED

`FULL_AUTO` means:

> IronMind can act automatically within the boundaries of the specific capability and policy.

It does NOT mean:

> IronMind can do anything.

---

# 10. CAPABILITY-SPECIFIC AUTONOMY

Autonomy must be configured independently for important capabilities.

Initial conceptual capability set:

```text
PLANNING
SCHEDULING
PROTECTION
PROACTIVE_NOTIFICATIONS
BACKGROUND_LEARNING
GOAL_RESURFACING
REFLECTION_PROCESSING
```

Additional capabilities may be added later through explicit review.

---

# 11. DEFAULT AUTONOMY PRINCIPLE

Defaults must be intentionally selected.

Coding agents must not invent autonomy defaults.

No capability should silently default to invasive full automation merely because it is technically convenient.

The exact production defaults must be established as part of implementation/product review.

---

# 12. AUTHORITY HIERARCHY

When making an autonomous decision, authority should conceptually follow:

```text
SYSTEM SAFETY / HARD CONSTRAINTS
        ↓
CURRENT EXPLICIT USER INSTRUCTION
        ↓
EXPLICIT USER SETTINGS
        ↓
USER-CONFIRMED INFORMATION
        ↓
CURRENT DOMAIN STATE
        ↓
RELEVANT RECENT EVIDENCE
        ↓
STABLE LEARNED PATTERNS
        ↓
AI RECOMMENDATION
        ↓
WEAK INFERENCE
```

This does not permit lower layers to override higher layers.

---

# 13. EXPLICIT USER CONTROL OVERRIDES LEARNED BEHAVIOR

If the user explicitly changes a setting:

```text
Automatic Protection = OFF
```

then a learned pattern cannot override it.

---

# 14. CURRENT USER INTENT OVERRIDES STALE HISTORY

If historical behavior suggests:

```text
User prefers morning exercise.
```

but the user says:

> "I now exercise in the evening."

the current explicit choice should guide current autonomy decisions.

---

# 15. USER OVERRIDE IS AUTHORITATIVE

An immediate user override should take precedence over an automated action in the current context, subject to higher-level safety/system constraints.

Example:

```text
Protection active
↓
User overrides protection
↓
Protection stops or enters override state
```

The system should not immediately reactivate it merely because the underlying trigger still exists.

---

# 16. OVERRIDE IS NOT FAILURE

A user override is not inherently a negative outcome.

It may represent:

* legitimate context change
* incorrect IronMind decision
* missing exception
* temporary need
* changed priority

Treat it as evidence.

---

# 17. OVERRIDE SHOULD BE TRACEABLE

Where useful, record:

```text
override
timestamp
capability
target
context
reason if provided
```

---

# 18. OVERRIDE COOLDOWN

After a user overrides an automated action, the system should generally avoid immediately reapplying the same action unless policy explicitly requires it.

---

# 19. AUTONOMY CHANGE AUTHORITY

Only authorized product/system pathways can change autonomy.

The following must NOT silently change autonomy:

```text
AI
Pattern Engine
Background Worker
Intervention Engine
Remote configuration
```

unless the product explicitly defines such behavior and the user's authority model allows it.

---

# 20. AI CANNOT GRANT ITSELF MORE ACCESS

Example:

```text
AI:
I think automatic protection would help.

Current:
Protection = ASK_BEFORE_ACTION.
```

Result:

```text
Ask user.
```

Not:

```text
Change to FULL_AUTO.
```

---

# 21. AUTONOMY STATES MUST BE EXPLICIT

Avoid ambiguous states such as:

```text
enabled = true
```

without understanding whether this means:

* enabled for suggestions
* enabled for automatic execution
* permission granted
* feature enabled

The model should clearly distinguish:

```text
Feature enabled
+
Autonomy level
+
Permission
+
Current eligibility
```

---

# 22. AUTONOMY DECISION MODEL

A conceptual autonomous action is allowed only if:

```text
Capability enabled
AND
Autonomy level permits action
AND
Policy permits action
AND
Current context is eligible
AND
Required permission exists
AND
No active suppression/cooldown
AND
Action is valid
```

---

# 23. AUTONOMY CHECK ORDER

Conceptually:

```text
1. Is capability enabled?
2. What autonomy level is configured?
3. Is action allowed at this level?
4. Is current context eligible?
5. Is required permission available?
6. Is there a user override?
7. Is cooldown active?
8. Is there a duplicate action?
9. Is the action safe and reversible where required?
10. Execute or do nothing.
```

---

# 24. OFF BEHAVIOR

When autonomy is:

```text
OFF
```

the system must not execute that capability automatically.

It may:

* observe
* calculate
* store relevant data
* provide manual controls

according to the separate feature/data settings.

---

# 25. SUGGEST_ONLY BEHAVIOR

When:

```text
SUGGEST_ONLY
```

IronMind may:

```text
observe
analyze
recommend
explain
```

but not automatically execute the action.

---

# 26. ASK_BEFORE_ACTION BEHAVIOR

When:

```text
ASK_BEFORE_ACTION
```

IronMind may prepare the proposed action.

It must:

```text
ask
wait
validate response
execute only after valid confirmation
```

---

# 27. FULL_AUTO BEHAVIOR

When:

```text
FULL_AUTO
```

IronMind may execute an allowed action when policy conditions are satisfied.

However:

```text
FULL_AUTO
≠
no validation
```

Policy checks still apply.

---

# 28. PLANNING AUTONOMY

Planning autonomy determines whether IronMind may create plans.

### OFF

No automatic plan creation.

### SUGGEST_ONLY

IronMind may recommend a plan.

### ASK_BEFORE_ACTION

IronMind may generate the plan and ask whether to apply it.

### FULL_AUTO

IronMind may create a plan automatically within configured limits.

---

# 29. PLANNING LIMITS

Even in `FULL_AUTO`, IronMind should not:

* invent major life goals
* change user's fundamental priorities
* create extreme schedules
* create unreasonable workloads
* discard important user commitments silently

---

# 30. AUTOMATIC PLAN CREATION

Automatic planning should generally operate from an existing user goal/ambition or clear user intent.

Do not create major goals from weak behavioral inference.

---

# 31. SCHEDULING AUTONOMY

Scheduling autonomy determines whether IronMind can create or modify scheduled actions.

### OFF

No automatic scheduling.

### SUGGEST_ONLY

Suggest schedule.

### ASK_BEFORE_ACTION

Ask before applying schedule.

### FULL_AUTO

Apply schedule when policy allows.

---

# 32. SCHEDULING LIMITS

Even with `FULL_AUTO`, IronMind must:

* respect explicit user commitments
* avoid obvious conflicts
* respect fixed external events
* avoid impossible timing
* preserve user-defined constraints

---

# 33. AUTOMATIC RESCHEDULING

IronMind may eventually automatically reschedule eligible actions.

However, automatic rescheduling should respect:

```text
user preferences
goal importance
deadline
number of prior postponements
current constraints
autonomy level
```

---

# 34. REPEATED RESCHEDULING LIMIT

IronMind should not repeatedly move an action forever.

After repeated unsuccessful scheduling:

```text
analyze
↓
ask / suggest
↓
change plan
```

instead of:

```text
reschedule
reschedule
reschedule
```

indefinitely.

---

# 35. PROTECTION AUTONOMY

Protection controls whether IronMind can automatically protect important actions.

### OFF

No automatic protection.

### SUGGEST_ONLY

Recommend protection.

### ASK_BEFORE_ACTION

Ask before enabling.

### FULL_AUTO

Enable protection when all eligibility conditions are satisfied.

---

# 36. PROTECTION ELIGIBILITY

Automatic protection may require:

```text
active commitment
+
protection capability enabled
+
eligible target
+
policy match
+
required permission
+
no override
+
no cooldown conflict
```

---

# 37. PROTECTION SCOPE

Protection must be bounded.

Possible targets:

```text
specific application
specific application category
specific user-defined context
specific focus session
```

Avoid global unrestricted protection by default.

---

# 38. PROTECTION DURATION

Protection should have an explicit lifecycle.

Avoid indefinite automatic blocking unless specifically configured.

---

# 39. PROTECTION EXPLANATION

When automatic protection occurs, IronMind should be able to explain:

```text
What:
Temporary protection enabled.

Why:
Active study commitment.

Evidence:
This application has interrupted similar sessions.

Policy:
Automatic protection is enabled.

Duration:
Until the scheduled session ends.
```

---

# 40. PROTECTION REVERSIBILITY

Where technically and product-wise appropriate, temporary automated protection should be reversible.

---

# 41. PROTECTION OVERRIDE

A user may override protection where the product allows it.

The override should affect current behavior immediately and may influence future learning.

---

# 42. PROTECTION PERMISSION FAILURE

If required Android capability is unavailable:

```text
Do not pretend protection is active.
```

Instead:

```text
record failure
inform user where useful
offer settings/help
continue without false state
```

---

# 43. PROACTIVE NOTIFICATION AUTONOMY

This controls whether IronMind may initiate notifications.

### OFF

No proactive notifications from this capability.

### SUGGEST_ONLY

Create notification recommendations for user-approved delivery paths or display inside the app, depending on implementation.

### ASK_BEFORE_ACTION

Ask for confirmation when appropriate.

### FULL_AUTO

Send eligible notifications according to notification policy.

---

# 44. NOTIFICATION AUTONOMY IS NOT NOTIFICATION PERMISSION

The Android notification permission and IronMind's autonomy setting are separate concepts.

Both must allow delivery.

---

# 45. NOTIFICATION ELIGIBILITY

A proactive notification should generally require:

```text
meaningful reason
+
current relevance
+
policy permission
+
autonomy permission
+
notification permission
+
no cooldown
+
acceptable interruption cost
```

---

# 46. NOTIFICATION LIMITS

Even in `FULL_AUTO`:

```text
Do not spam.
Do not repeatedly interrupt.
Do not manufacture urgency.
```

---

# 47. NOTIFICATION COOLDOWN

Notifications should have configurable or policy-controlled cooldown behavior.

---

# 48. NOTIFICATION DEDUPLICATION

Equivalent notifications should be suppressed when a recent intervention already served the same purpose.

---

# 49. BACKGROUND LEARNING AUTONOMY

Background learning controls whether IronMind processes observed information to improve its model.

### OFF

Do not perform optional behavioral learning.

Core required operation may still continue according to separate system requirements.

### SUGGEST_ONLY

Collect/analyze and surface candidate insights without automatically changing important user state.

### ASK_BEFORE_ACTION

Ask before certain meaningful model changes.

### FULL_AUTO

Allow appropriate background pattern/memory updates within defined data policy.

---

# 50. BACKGROUND LEARNING LIMITS

Background learning must not:

* invent user goals
* change autonomy
* silently rewrite important user preferences
* create strong psychological labels
* retain unlimited raw personal data

---

# 51. GOAL RESURFACING AUTONOMY

Goal resurfacing controls whether IronMind may bring older goals back to attention.

### OFF

Do not proactively resurface.

### SUGGEST_ONLY

Show candidate resurfacing.

### ASK_BEFORE_ACTION

Ask whether to resurface.

### FULL_AUTO

Resurface when policy criteria are met.

---

# 52. GOAL RESURFACING SAFETY

Resurfacing must not imply:

> "You are obligated to continue this."

It should instead ask whether the goal remains relevant.

---

# 53. REFLECTION PROCESSING AUTONOMY

User-submitted reflections may be processed automatically according to user settings.

However:

```text
processing
≠
automatic reinterpretation of user identity
```

---

# 54. MEMORY AUTONOMY

Memory updates are a special category.

IronMind may automatically update low-risk derived knowledge where policy permits.

High-authority user preferences and significant identity-related information should require stronger evidence and, where appropriate, explicit user confirmation.

---

# 55. MEMORY AUTHORITY LEVELS

Conceptually:

```text
LOW
MEDIUM
HIGH
```

Examples:

LOW:
temporary context.

MEDIUM:
repeated preference pattern.

HIGH:
explicit user-confirmed durable preference.

The exact memory authority implementation may be defined later.

---

# 56. PATTERN AUTONOMY

Patterns are inferred knowledge.

IronMind may automatically create/update low-risk patterns based on sufficient evidence.

Patterns should remain:

```text
confidence-aware
evidence-backed
time-aware
correctable
```

---

# 57. AI RECOMMENDATION VS AUTONOMOUS ACTION

An AI recommendation does not automatically imply an action.

Example:

```text
AI Recommendation:
PROTECT

Autonomy:
SUGGEST_ONLY

Result:
Recommendation only.
```

---

# 58. POLICY DECISION

The Decision Engine must be authoritative over execution.

Conceptually:

```text
AI says:
ACT

Policy says:
ASK

Result:
ASK
```

---

# 59. POLICY OVERRIDES AI

The AI cannot bypass policy.

---

# 60. USER SETTINGS OVERRIDE AI RECOMMENDATIONS

Example:

```text
AI:
Send proactive reminder.

Notifications:
OFF.

Result:
No notification.
```

---

# 61. USER SETTINGS OVERRIDE LEARNED PATTERNS

Example:

```text
Pattern:
User responds well to frequent reminders.

Setting:
User disabled frequent reminders.

Result:
Do not use frequent reminders.
```

---

# 62. CURRENT USER ACTION OVERRIDES AUTOMATION

Example:

```text
User manually starts the task.
↓
Automatic "start task" suggestion becomes unnecessary.
```

---

# 63. DUPLICATE-ACTION PREVENTION

Before acting automatically, check whether the action has already occurred.

Example:

```text
Task completed
↓
Do not send completion reminder
```

---

# 64. AUTONOMY COOL DOWN

After a significant automated action, the same capability may enter a cooldown.

This prevents oscillation:

```text
protect
↓
unprotect
↓
protect
↓
unprotect
```

---

# 65. AUTOMATION OSCILLATION

The system must detect or prevent repeated conflicting automated actions.

Example:

```text
AI A:
Move schedule to 8 PM

AI B:
Move schedule to 7 PM

Loop
```

Such behavior is forbidden.

---

# 66. CONFLICT RESOLUTION

If two autonomous policies conflict:

```text
higher authority
+
explicit priority
+
current user intent
```

must determine the outcome.

---

# 67. PRIORITY

Autonomous actions should have explicit priority.

Conceptually:

```text
CRITICAL
HIGH
NORMAL
LOW
```

Not every capability should be allowed to interrupt another.

---

# 68. CRITICAL AUTONOMY

"Critical" should be reserved for actions genuinely requiring immediate handling.

Do not label ordinary productivity reminders as critical merely to bypass quiet periods.

---

# 69. QUIET PERIODS

Proactive autonomy must respect configured quiet periods unless a clearly defined higher-priority condition exists.

---

# 70. AUTONOMY DURING USER ACTIVITY

IronMind should avoid unnecessary intervention when the user is already:

```text
actively performing the intended action
```

---

# 71. AUTONOMY DURING DISTRACTION

When active commitments and distraction conditions coexist, the Decision Engine may evaluate protection or redirect according to:

```text
commitment
context
protection settings
learned risk
autonomy
cooldown
```

---

# 72. AUTOMATIC REDIRECT

Redirect may be:

```text
notification
system interruption
protection
UI prompt
```

depending on the implementation.

---

# 73. REDIRECT AUTHORITY

Redirection must be proportionate.

A weak inference should not trigger an invasive action.

---

# 74. INVASIVENESS LEVEL

Autonomous actions should conceptually have an invasiveness level:

```text
LOW
MEDIUM
HIGH
```

Examples:

LOW:
show a suggestion.

MEDIUM:
send notification.

HIGH:
modify device/application access.

Higher invasiveness requires stronger policy constraints.

---

# 75. HIGH-IMPACT ACTION RULE

Higher-impact actions should require stronger conditions than low-impact actions.

Conceptually:

```text
Higher invasiveness
→
higher confidence / stronger authorization / clearer context
```

---

# 76. IRREVERSIBILITY RULE

Irreversible actions require stronger authorization.

---

# 77. EXTERNAL ACTIONS

Actions affecting external systems should have stricter autonomy controls.

Examples:

```text
create calendar event
send message
modify external task
publish content
```

These are not equivalent to showing a recommendation.

---

# 78. EXTERNAL ACTION DEFAULT

Until explicitly specified by product policy:

> External write actions should not be treated as unrestricted `FULL_AUTO`.

They require an explicit capability-specific policy.

---

# 79. FINANCIAL / LEGAL / MEDICAL ACTIONS

IronMind must not autonomously perform consequential professional-domain actions merely because AI recommends them.

This contract does not replace platform or domain-specific safety requirements.

---

# 80. AUTONOMY AND USER CONFIRMATION

User confirmation must be meaningful.

A generic:

> "Continue?"

should not hide the actual action.

Prefer:

> "Enable YouTube protection for this 60-minute study session?"

---

# 81. CONFIRMATION CONTENT

Where asking for permission, provide:

```text
What will happen?
Why?
Duration/scope?
```

where relevant.

---

# 82. CONFIRMATION EXPIRATION

Confirmation should be tied to a relevant action/context.

Do not treat an old confirmation as permanent permission for unrelated actions unless the setting explicitly establishes that authority.

---

# 83. CONFIRMATION REUSE

A prior confirmation may be reusable only when:

* same capability
* same scope
* same policy
* user settings permit reuse

The exact rule must be implemented explicitly.

---

# 84. IMPLIED CONSENT IS NOT A SUBSTITUTE FOR SETTINGS

Silence should not automatically mean consent for higher-impact automated actions.

---

# 85. USER DISMISSAL

Dismissing a confirmation request should generally mean:

```text
do not perform this action now
```

It does not necessarily mean:

```text
never ask again
```

unless the user chooses that.

---

# 86. "DON'T ASK AGAIN"

If implemented, this should create an explicit preference or autonomy configuration rather than hidden behavior.

---

# 87. TEMPORARY AUTHORITY

Some autonomous actions may be authorized only for a specific context.

Example:

```text
Automatic protection
during this study session only.
```

After the session:

```text
authority expires
```

---

# 88. SESSION-BOUND AUTONOMY

Session-specific authority should not automatically become permanent autonomy.

---

# 89. TIME-BOUND AUTONOMY

Some future capability may allow:

```text
FULL_AUTO
during weekdays 8 AM–5 PM
```

This is a policy feature and must be explicit.

---

# 90. SCOPE-BOUND AUTONOMY

Autonomy may be limited to:

```text
specific goal
specific task
specific application
specific context
specific time window
```

---

# 91. AUTONOMY INHERITANCE

Do not casually inherit global autonomy into all capabilities.

Example:

```text
Global:
FULL_AUTO
```

does not automatically mean every future external action is fully automated.

Capabilities need explicit definitions.

---

# 92. NEW CAPABILITY DEFAULT

When a new autonomous capability is introduced:

> It must have an explicitly defined autonomy policy before automatic execution is enabled.

---

# 93. UNKNOWN CAPABILITY

If no autonomy rule exists:

```text
Do not execute automatically.
```

Preferred behavior:

```text
suggest / ask
```

depending on context.

---

# 94. POLICY FAIL-SAFE

If the autonomy engine encounters an unexpected error:

```text
Prefer no automatic action.
```

unless a previously established safe fallback exists.

---

# 95. CONFIGURATION FAIL-SAFE

If autonomy configuration cannot be loaded reliably:

```text
Do not assume FULL_AUTO.
```

Use the safest defined fallback.

---

# 96. PERMISSION FAIL-SAFE

If required permission state is unknown:

```text
Do not assume permission granted.
```

---

# 97. CONTEXT FAIL-SAFE

If required context cannot be determined reliably:

```text
Do not perform high-impact automatic action.
```

---

# 98. STATE FAIL-SAFE

If current domain state is inconsistent:

```text
Do not perform consequential automatic action.
```

Repair/ask rather than guessing.

---

# 99. AI FAIL-SAFE

If AI is unavailable:

```text
Use deterministic rules where sufficient.
Otherwise no autonomous action.
```

---

# 100. DUPLICATION FAIL-SAFE

If it is unclear whether an action has already happened:

```text
Prefer no duplicate action.
```

---

# 101. AUTONOMY AUDITABILITY

Every meaningful autonomous action should produce sufficient information to answer:

```text
What happened?
Why?
Under which autonomy level?
What policy allowed it?
What context was used?
What action executed?
What was the outcome?
```

---

# 102. AUTONOMY DECISION RECORD

Where appropriate, the system should maintain a `DecisionRecord`.

Conceptual fields:

```text
id
userId

capability
trigger
contextReference

recommendation
recommendationSource

autonomyLevel
policyResult

selectedAction

reason

createdAt
schemaVersion
```

---

# 103. AUTONOMY LIFECYCLE LOGGING

Important autonomy transitions should log using:

```text
IronMindLifecycle [Component] [EVENT] key=value
```

Examples:

```text
IronMindLifecycle Autonomy DECISION capability=PROTECTION level=FULL_AUTO action=ALLOW
```

```text
IronMindLifecycle Autonomy DECISION capability=NOTIFICATIONS level=OFF action=BLOCK
```

```text
IronMindLifecycle Autonomy OVERRIDE capability=PROTECTION source=USER
```

---

# 104. ACTION EXECUTION LOGGING

After an automated decision:

```text
IronMindLifecycle Action EXECUTION_STARTED ...
```

then:

```text
IronMindLifecycle Action EXECUTION_COMPLETED ...
```

or:

```text
IronMindLifecycle Action EXECUTION_FAILED ...
```

where appropriate.

---

# 105. AUTONOMY DECISION VS EXECUTION

Do not record:

```text
Decision = successful execution
```

They are separate.

A decision may be allowed but execution may fail.

---

# 106. EXECUTION FAILURE EXAMPLE

```text
Decision:
ENABLE_PROTECTION

Execution:
FAILED

Reason:
Required permission unavailable
```

The system must not claim that protection is active.

---

# 107. AUTONOMY OUTCOME

Autonomous actions should eventually track outcomes.

Example:

```text
Decision
↓
Action
↓
User response
↓
Outcome
```

This supports learning.

---

# 108. AUTONOMY LEARNING

IronMind may eventually learn:

```text
context
+
autonomous action
+
outcome
```

to improve future decisions.

---

# 109. AUTOMATION SHOULD NOT LEARN TO BYPASS POLICY

Learning may adjust:

```text
which action is recommended
```

but must not redefine:

```text
what action is permitted
```

unless policy itself is explicitly changed.

---

# 110. POLICY VS LEARNING

```text
Learning:
"What tends to work?"

Policy:
"What am I allowed to do?"
```

Learning must not replace policy.

---

# 111. POLICY VS AI

```text
AI:
"What might help?"

Policy:
"What may IronMind do?"

Execution:
"How do we perform it?"
```

---

# 112. AUTONOMY VS INTELLIGENCE

A smarter model does not automatically receive more authority.

Model upgrades must not implicitly upgrade autonomy.

---

# 113. MODEL CHANGE SAFETY

Replacing:

```text
AI Model A
```

with:

```text
AI Model B
```

must not automatically alter autonomy level.

---

# 114. PROMPT CHANGE SAFETY

Changing an AI prompt must not change autonomy configuration.

---

# 115. PATTERN CHANGE SAFETY

Changing confidence in a pattern must not automatically change the user's autonomy settings.

---

# 116. AUTONOMY SETTING HISTORY

Important autonomy changes should be recorded as events.

Example:

```text
AUTONOMY_SETTING_UPDATED
capability=PROTECTION
oldLevel=ASK_BEFORE_ACTION
newLevel=FULL_AUTO
source=USER
```

---

# 117. AI-INITIATED AUTONOMY PROPOSAL

An AI may eventually say:

> "Automatic protection appears useful in these situations. Would you like to enable it?"

But the policy change requires explicit user action unless the product explicitly defines another authorized mechanism.

---

# 118. USER-INITIATED AUTONOMY

The preferred authority flow is:

```text
USER
↓
SETTING CHANGE
↓
POLICY UPDATE
↓
FUTURE ACTIONS
```

---

# 119. SETTINGS UI

The Settings interface should eventually make autonomy understandable.

Example:

```text
Protection
[Full Auto]

Scheduling
[Ask Before Action]

Goal Resurfacing
[Suggest Only]
```

---

# 120. SETTINGS LANGUAGE

Avoid ambiguous wording such as:

```text
Smart Mode
```

without explanation.

Prefer explicit levels:

```text
Off
Suggestions only
Ask before acting
Automatic
```

---

# 121. ADVANCED SETTINGS

Advanced users may eventually configure:

```text
notification cooldown
protection targets
automation scope
quiet hours
specific contexts
```

But defaults should remain manageable.

---

# 122. GRANULAR SETTINGS

Granularity should be introduced only when it provides meaningful control.

Do not expose hundreds of switches unnecessarily.

---

# 123. AUTONOMY SUMMARY SCREEN

A future screen may show:

```text
IronMind currently has permission to:

Plan automatically
No

Schedule automatically
Ask

Protect distracting apps
Yes

Send proactive notifications
Yes

Learn patterns in background
Yes
```

This supports transparency.

---

# 124. "WHAT CAN IRONMIND DO?" VIEW

The product should eventually provide an easy-to-understand view of current authority.

---

# 125. "WHY DID IRONMIND DO THIS?" VIEW

Autonomous actions should eventually link to:

```text
Reason
Context
Policy
Setting
Outcome
```

---

# 126. "UNDO" WHERE APPROPRIATE

Where actions are reversible, provide an appropriate undo mechanism.

Examples:

```text
Disable temporary protection
Undo automatic reschedule
Dismiss suggested change
```

---

# 127. UNDO SHOULD NOT CORRUPT HISTORY

Undoing an action should create a new state transition/event rather than erasing the fact that the original action happened.

---

# 128. EXAMPLE UNDO

```text
PROTECTION_ENABLED
↓
USER_UNDO
↓
PROTECTION_DISABLED
```

History remains intact.

---

# 129. AUTONOMOUS RESCHEDULING UNDO

```text
Original:
8 PM

IronMind:
moved to 8:30 PM

User:
Undo

Result:
8 PM restored
```

The system may record:

```text
AUTOMATIC_RESCHEDULE
+
USER_UNDO
```

---

# 130. AUTONOMY COOLDOWN AFTER UNDO

Where appropriate, user undo should temporarily suppress the same automated action.

---

# 131. AUTONOMY CONFLICT WITH MANUAL ACTION

Manual user actions generally take precedence for the current context.

Example:

```text
IronMind recommends reschedule.
User manually schedules a different time.
```

Do not immediately overwrite it.

---

# 132. AUTOMATIC SUGGESTION STALENESS

A proposal may become invalid if context changes.

Before executing an old approved proposal, verify that it remains valid.

---

# 133. APPROVAL EXPIRATION

An approval may expire when:

```text
context changed
target changed
scheduled time changed
policy changed
user setting changed
```

---

# 134. STALE DECISION PREVENTION

Do not execute a decision based on obsolete state.

---

# 135. AUTONOMY RACE CONDITIONS

If two processes make decisions concurrently:

```text
Decision A
Decision B
```

they must be reconciled against the latest canonical state.

---

# 136. CONCURRENCY RULE

Never assume the state used to generate an action remains unchanged until execution.

Revalidate before high-impact execution.

---

# 137. REVALIDATION

Before execution:

```text
current state
current setting
current permission
current context
```

should be checked again where appropriate.

---

# 138. AUTOMATED ACTION EXPIRATION

A proposed action should have an expiration where stale execution would be harmful.

---

# 139. BACKGROUND AUTONOMY

Background automation must follow the same policy rules as foreground automation.

A background worker does not receive extra authority simply because the user is not currently looking at the app.

---

# 140. BACKGROUND WORKER AUTHORITY

Background workers can:

```text
process
analyze
schedule
```

only within their defined capability permissions.

---

# 141. BACKGROUND WORKER MUST NOT BYPASS DECISION ENGINE

Do not implement:

```text
Worker
↓
direct device action
```

for autonomous behavior.

Use:

```text
Worker
↓
Decision Engine
↓
Execution
```

---

# 142. AUTONOMY FROM NOTIFICATIONS

Receiving a notification does not itself authorize any action.

---

# 143. AUTONOMY FROM AI CONFIDENCE

High AI confidence does not override autonomy policy.

Example:

```text
AI confidence = 0.99
Protection autonomy = OFF

Result:
No automatic protection.
```

---

# 144. AUTONOMY FROM PATTERN CONFIDENCE

Similarly:

```text
Pattern confidence = 0.98
Notification autonomy = OFF

Result:
No notification.
```

---

# 145. AUTONOMY FROM USER PREFERENCE

A preference may restrict behavior.

Example:

```text
User:
Do not interrupt me during work.

Result:
Notifications suppressed during configured work context.
```

---

# 146. PREFERENCE CONFLICT

If preferences conflict:

```text
more specific
+
more recent
+
explicit user configuration
```

should generally take precedence according to defined policy.

---

# 147. AUTONOMY AND QUIET HOURS

Quiet hours should be an explicit policy dimension.

Do not bypass them casually.

---

# 148. EMERGENCY EXCEPTION

Any future emergency override would require an explicit product/safety contract.

Do not invent emergency autonomy behavior.

---

# 149. AUTONOMY AND DEVICE RESTART

After device restart, autonomous behavior must recover from persisted policy/state rather than assuming old in-memory authority.

---

# 150. AUTONOMY AND APP RESTART

Same principle applies after process death.

---

# 151. AUTONOMY AND OFFLINE MODE

Offline operation must use local policy/configuration where possible.

Do not assume remote availability for authorization.

---

# 152. STALE AUTONOMY CONFIGURATION

If local and remote autonomy settings conflict:

The system must use an explicit synchronization/conflict policy.

Do not let whichever request finishes last silently win if that could surprise the user.

---

# 153. USER SETTING CONFLICT

If the backend reports:

```text
FULL_AUTO
```

but the user recently disabled it locally:

The local/user-authoritative change must be handled according to synchronization policy.

---

# 154. AUTONOMY CONFIGURATION SECURITY

Autonomy settings should be protected from unauthorized changes.

---

# 155. NO REMOTE SILENT AUTHORITY ESCALATION

A backend job must not silently turn a user's local capability from:

```text
OFF
```

to:

```text
FULL_AUTO
```

without a valid authorized policy.

---

# 156. NO AI AUTHORITY ESCALATION

AI cannot modify:

```text
autonomy
permissions
security
```

as an incidental side effect.

---

# 157. AUTONOMY AND DELETION

If a capability's supporting data is deleted, its automatic actions should not continue using stale data.

---

# 158. AUTONOMY AND DISABLED DATA SOURCES

If a user disables an observation source:

```text
Location = OFF
```

then decisions relying on location must not pretend that location exists.

---

# 159. AUTONOMY AND PERMISSION REVOCATION

If a permission is revoked:

```text
capability may become unavailable
```

The system must update behavior accordingly.

---

# 160. NO FALSE EXECUTION STATE

If action cannot be executed:

```text
state = FAILED / UNAVAILABLE
```

not:

```text
state = SUCCESS
```

---

# 161. AUTONOMY ACTION TYPES

The mature system may maintain a canonical action vocabulary.

Examples:

```text
CREATE_PLAN
CREATE_TASK
CREATE_COMMITMENT
RESCHEDULE
SEND_NOTIFICATION
ENABLE_PROTECTION
DISABLE_PROTECTION
RESURFACE_GOAL
REQUEST_REFLECTION
UPDATE_PATTERN
UPDATE_MEMORY
```

Exact action vocabulary should be finalized during implementation.

---

# 162. ACTION TYPE SAFETY

Every autonomous action type must have:

```text
allowed autonomy levels
scope
permission requirements
reversibility
logging
tests
```

---

# 163. ACTION REGISTRY

A mature implementation may use a centralized action registry defining:

```text
Action
→ Capability
→ Minimum autonomy level
→ Required permissions
→ Validation
→ Execution handler
```

---

# 164. UNKNOWN ACTION

If a decision references an unknown action type:

```text
Do not execute.
Log validation failure.
```

---

# 165. AUTONOMY POLICY VERSIONING

This policy should be versioned.

Example:

```text
AutonomyPolicy v1
```

---

# 166. POLICY VERSION IN DECISIONS

Important decision records should identify the policy version that authorized the action.

---

# 167. WHY POLICY VERSIONING MATTERS

If behavior changes later, we should be able to understand:

> Which rules were active when this action happened?

---

# 168. POLICY MIGRATION

Changing autonomy semantics requires:

```text
policy change
+
migration consideration
+
testing
```

---

# 169. AUTONOMY TEST MATRIX

Every capability should eventually be tested against:

```text
OFF
SUGGEST_ONLY
ASK_BEFORE_ACTION
FULL_AUTO
```

---

# 170. PROTECTION TEST MATRIX

Example:

```text
OFF
→ no automatic protection

SUGGEST_ONLY
→ recommendation only

ASK
→ confirmation

FULL_AUTO
→ execute when eligible
```

---

# 171. NOTIFICATION TEST MATRIX

Test:

```text
autonomy
+
notification permission
+
quiet hours
+
cooldown
+
duplicate
+
context
```

---

# 172. SCHEDULING TEST MATRIX

Test:

```text
explicit user schedule
+
existing conflict
+
AI recommendation
+
autonomy level
+
manual override
```

---

# 173. BACKGROUND TEST MATRIX

Test:

```text
worker runs
+
autonomy OFF
→ no action

worker runs
+
FULL_AUTO
→ allowed action if eligible
```

---

# 174. FAILURE TEST MATRIX

Test:

```text
AI unavailable
Permission unavailable
Network unavailable
Database unavailable
State stale
Duplicate action
Policy missing
Invalid AI recommendation
```

---

# 175. AUTONOMY LOGGING TESTS

Verify lifecycle logs for:

```text
DECISION
ALLOW
DENY
OVERRIDE
EXECUTION_STARTED
EXECUTION_COMPLETED
EXECUTION_FAILED
```

where relevant.

---

# 176. AUTONOMY SECURITY TESTS

Verify that:

```text
AI cannot change autonomy
Unauthorized process cannot change autonomy
Backend cannot bypass authorization
User A cannot modify User B settings
```

---

# 177. AUTONOMY RACE TESTS

Simulate:

```text
user override
+
background automation
```

at approximately the same time.

The final behavior must respect current authoritative state.

---

# 178. AUTONOMY RECOVERY TESTS

Test after:

```text
app restart
device restart
process death
network loss
permission revocation
```

---

# 179. AUTONOMY OBSERVABILITY

When a user asks:

> "Why did IronMind do that?"

the system should eventually be able to answer:

```text
Capability
Autonomy level
Trigger
Relevant context
Policy decision
Action
Execution result
```

---

# 180. AUTONOMY USER TRUST PRINCIPLE

Automatic behavior must be:

```text
Predictable
Understandable
Controllable
Reversible where practical
```

---

# 181. AUTONOMY SHOULD FEEL LIKE ASSISTANCE

The desired experience is:

> "IronMind handled that for me."

not:

> "IronMind took control from me."

---

# 182. AUTONOMY SHOULD REDUCE FRICTION

Good automation eliminates repetitive decisions.

Example:

```text
Every morning:
same user-configured protection behavior
```

can reasonably become automatic.

---

# 183. AUTONOMY SHOULD NOT REMOVE MEANINGFUL CHOICES

Choosing:

> "Should I continue pursuing this life goal?"

is a meaningful user decision.

IronMind should not automatically decide it.

---

# 184. AUTOMATION OF OPERATIONS VS VALUES

IronMind may automate:

```text
reminding
scheduling
protection
summarization
pattern processing
```

More cautiously than:

```text
life direction
major goal changes
core priorities
```

---

# 185. MAJOR VALUE CHANGES

Changing a fundamental user goal should generally require explicit user confirmation.

---

# 186. AUTOMATIC TASK GENERATION

Tasks derived from an already-authorized goal may be automatically generated under planning policy.

---

# 187. AUTOMATIC COMMITMENT CREATION

Commitments represent stronger user intent than tasks.

Therefore automatic commitment creation should have stricter conditions than automatic task generation.

Default policy should not assume that an AI suggestion equals an explicit commitment.

---

# 188. AUTOMATIC GOAL CREATION

Automatically turning vague language into a durable goal is higher-risk.

Prefer:

```text
suggest
+
confirm
```

unless an explicit product policy later permits automatic creation under clearly defined conditions.

---

# 189. AUTOMATIC AMBITION CREATION

Long-term ambitions are even more semantically important.

Do not create them casually from one conversational statement.

---

# 190. AUTOMATIC MEMORY CREATION

Low-risk memories may be automated when evidence is strong and policy permits.

---

# 191. HIGH-AUTHORITY MEMORY

Important user identity/preferences should receive stronger protection and, where appropriate, user confirmation.

---

# 192. AUTOMATIC PATTERN CREATION

Patterns may be created automatically because they are explicitly probabilistic and can decay.

But they must remain:

```text
inference
+
evidence
+
confidence
```

not fact.

---

# 193. AUTOMATIC PATTERN CONFIRMATION

The system should not automatically mark an inference:

```text
USER_CONFIRMED
```

without actual user confirmation.

---

# 194. USER CONFIRMATION IS LITERAL

Do not interpret:

> "Okay"

as confirmation of a complex unrelated action unless the current question clearly establishes that meaning.

---

# 195. CONFIRMATION CONTEXT

Confirmation must be attached to a specific proposed action.

---

# 196. AUTONOMY AND CHAT

Conversation itself does not automatically grant device authority.

Example:

User says:

> "Sure."

This does not authorize every pending autonomous action.

The system must know what the user is responding to.

---

# 197. AUTONOMY AND VOICE

Voice input follows the same authority rules as text input.

---

# 198. VOICE AMBIGUITY

If voice transcription is uncertain and the action is high-impact:

```text
Ask for confirmation.
```

---

# 199. AUTONOMY AND REFLECTION

A reflection is not automatically a command.

Example:

> "I wish I studied earlier."

does not mean:

```text
Create automatic study schedule.
```

---

# 200. AUTONOMY AND USER STATEMENTS

Natural language intent must be interpreted carefully.

Examples:

```text
"I want to study tomorrow."
```

may imply a desire.

```text
"I will study tomorrow at 8."
```

may imply a commitment.

The distinction must be preserved.

---

# 201. AUTONOMY AND COMMANDS

A direct user command may authorize a specific action.

Example:

> "Enable protection for this study session."

This is an explicit user action request.

Still validate:

```text
permissions
state
technical availability
```

before execution.

---

# 202. AUTONOMY AND USER SAFETY

Autonomous behavior must remain consistent with broader platform and safety constraints.

---

# 203. AUTONOMY AND PRODUCT ETHICS

The system must never use autonomy to:

```text
shame
guilt
threaten
manipulate
trap
```

the user.

---

# 204. AUTONOMY AND ATTENTION

Autonomous notifications should treat attention as scarce.

---

# 205. AUTONOMY AND INTERRUPTION BUDGET

The system may eventually maintain an interruption budget by:

```text
frequency
importance
recent interaction
context
```

---

# 206. AUTONOMY AND STAY SILENT

The Decision Engine must always be able to return:

```text
STAY_SILENT
```

even under:

```text
FULL_AUTO
```

---

# 207. FULL AUTO + SILENCE EXAMPLE

```text
Notifications:
FULL_AUTO

Current situation:
User already studying.

Decision:
STAY_SILENT.

Result:
No notification.
```

---

# 208. AUTOMATION QUALITY

The objective is:

```text
Correct action
+
Correct timing
+
Correct scope
+
Minimal unnecessary interruption
```

---

# 209. AUTONOMY QUALITY OVER QUANTITY

A system doing fewer useful things is better than a system doing many unnecessary things.

---

# 210. AUTONOMY SHOULD IMPROVE OVER TIME

Learning should improve:

```text
when to act
what to do
how much to do
when not to act
```

while policy continues to determine:

```text
what is allowed
```

---

# 211. LEARNED AUTONOMY OPTIMIZATION

A future system may optimize intervention timing and type within permitted autonomy boundaries.

It may not independently expand authority.

---

# 212. USER CONTROL IS A HARD BOUNDARY

The system cannot learn around explicit user restrictions.

---

# 213. USER CAN REDUCE AUTONOMY AT ANY TIME

A user action should be able to reduce automation without requiring AI approval.

---

# 214. EMERGENCY DISABLE

A future global:

```text
STOP AUTOMATION
```

mechanism may be useful.

If implemented, it should immediately disable supported autonomous actions.

---

# 215. GLOBAL KILL SWITCH

A mature system may provide:

```text
Pause all automation
```

for situations where the user wants immediate full manual control.

The exact implementation is future work.

---

# 216. GLOBAL PAUSE SEMANTICS

Pausing automation should not necessarily delete:

```text
goals
commitments
history
memory
```

It primarily pauses automated behavior.

---

# 217. AUTOMATION PAUSE SHOULD BE VISIBLE

The user should know automation is paused.

---

# 218. PAUSE + BACKGROUND LEARNING

The semantics of pausing automation versus background data collection must remain explicit.

They are not necessarily identical.

---

# 219. CAPABILITY STATUS

For each capability, the system should conceptually distinguish:

```text
ENABLED/DISABLED
+
AUTONOMY LEVEL
+
PERMISSION
+
CURRENT ELIGIBILITY
```

---

# 220. EXAMPLE

```text
Protection:
Enabled = true

Autonomy:
FULL_AUTO

Permission:
Granted

Current eligibility:
False
```

Result:

```text
No protection now.
```

---

# 221. AUTONOMY SHOULD NOT USE BOOLEAN-ONLY MODEL

Avoid:

```text
automaticProtection = true
```

as the only policy representation.

The system needs:

```text
level
scope
enabled
constraints
```

where applicable.

---

# 222. POLICY CONFIGURATION

Configuration should be versioned and validated.

---

# 223. INVALID CONFIGURATION

If an autonomy configuration is malformed:

```text
Do not assume permissive behavior.
```

Use safe fallback.

---

# 224. POLICY TESTING

Every policy rule must be testable independently from UI and AI.

---

# 225. POLICY DETERMINISM

For the same:

```text
state
context
settings
policy
recommendation
```

the Decision Engine should produce predictable results.

---

# 226. AI NON-DETERMINISM

AI may be probabilistic.

The policy layer should provide deterministic authority boundaries around that uncertainty.

---

# 227. POLICY EXAMPLE

```text
Input:
AI recommendation = PROTECT

Settings:
Protection = ASK_BEFORE_ACTION

Permission:
Granted

Context:
Active study commitment

Decision:
ASK
```

---

# 228. POLICY EXAMPLE — OFF

```text
AI recommendation = PROTECT

Settings:
Protection = OFF

Decision:
DENY
```

---

# 229. POLICY EXAMPLE — FULL AUTO

```text
AI recommendation = PROTECT

Settings:
Protection = FULL_AUTO

Context:
Active study commitment

Target:
Allowed app

Permission:
Granted

Cooldown:
None

Decision:
ALLOW
```

---

# 230. POLICY EXAMPLE — STAY SILENT

```text
AI recommendation = REMIND

Settings:
Notifications = FULL_AUTO

Context:
User already completed the task.

Decision:
STAY_SILENT
```

---

# 231. POLICY EXAMPLE — USER OVERRIDE

```text
Protection:
FULL_AUTO

User:
Override

Decision:
SUPPRESS_CURRENT_PROTECTION
```

---

# 232. POLICY EXAMPLE — PERMISSION MISSING

```text
Protection:
FULL_AUTO

Permission:
Unavailable

Decision:
DENY_EXECUTION

Reason:
PERMISSION_UNAVAILABLE
```

---

# 233. POLICY EXAMPLE — STALE STATE

```text
AI:
Protect study session

Current:
Commitment already completed

Decision:
STAY_SILENT / NO_ACTION
```

---

# 234. POLICY EXAMPLE — UNKNOWN

```text
AI:
unknown action type

Decision:
DENY
```

---

# 235. ACTION REASON CODES

The Decision Engine should eventually support standardized reason codes such as:

```text
AUTONOMY_OFF
AUTONOMY_SUGGEST_ONLY
CONFIRMATION_REQUIRED
POLICY_DENIED
PERMISSION_UNAVAILABLE
USER_OVERRIDE
COOLDOWN_ACTIVE
DUPLICATE_ACTION
STALE_CONTEXT
INVALID_ACTION
NO_MEANINGFUL_BENEFIT
STAY_SILENT
ALLOWED
```

Exact enum may evolve.

---

# 236. POLICY DECISION RESULT

Conceptually:

```text
ALLOW
DENY
ASK
SUGGEST
STAY_SILENT
```

---

# 237. ACTION DECISION EXAMPLE

```json
{
  "result": "ALLOW",
  "capability": "PROTECTION",
  "action": "ENABLE_PROTECTION",
  "reasonCode": "ACTIVE_COMMITMENT",
  "policyVersion": 1,
  "autonomyLevel": "FULL_AUTO"
}
```

The final schema belongs to implementation.

---

# 238. DECISION MUST BE EXPLAINABLE

Every non-trivial automated decision should have a structured reason.

---

# 239. POLICY REASON VS AI REASON

Keep them separate.

AI reason:

> "This appears likely to help."

Policy reason:

> "Automatic protection is enabled and the current session is eligible."

---

# 240. EXECUTION REASON

Execution reason answers:

> Why was the actual action performed?

It should be traceable to the decision.

---

# 241. POLICY MUST NOT BECOME AI PROMPT TEXT

Autonomy rules belong in deterministic application logic, not only in an LLM prompt.

---

# 242. AUTONOMY POLICY MUST NOT DEPEND ON MODEL BEHAVIOR

If the LLM fails, policy still exists.

---

# 243. POLICY SHOULD BE TESTABLE WITHOUT AI

Given a fixed recommendation and context:

```text
input
→ policy
→ expected decision
```

must be testable without live AI.

---

# 244. AI RECOMMENDATION IS OPTIONAL INPUT

Some policies can operate without AI.

Example:

```text
Active protection session
+
protected package launched
→
deterministic protection behavior
```

---

# 245. POLICY SHOULD SUPPORT DETERMINISTIC TRIGGERS

Examples:

```text
scheduled commitment starts
focus session active
quiet hours
user override
```

may be deterministic.

---

# 246. POLICY SHOULD SUPPORT AI TRIGGERS

Examples:

```text
possible barrier detected
intervention recommendation
pattern candidate
```

---

# 247. MIXED DECISION SOURCES

A decision may combine:

```text
rule
+
AI recommendation
+
user preference
```

The hierarchy must remain explicit.

---

# 248. AUTONOMY AND EVENT PROCESSING

Events may trigger evaluation.

Example:

```text
COMMITMENT_STARTED
↓
Evaluate Protection
```

But an event itself does not automatically authorize action.

---

# 249. EVENT ≠ PERMISSION

This distinction is mandatory.

---

# 250. AUTONOMY AND SCHEDULES

A schedule trigger should not automatically imply:

```text
FULL_AUTO
```

It only provides a trigger.

---

# 251. AUTONOMY AND PATTERNS

A pattern provides evidence.

It does not provide permission.

---

# 252. AUTONOMY AND MEMORY

Memory provides context.

It does not provide permission.

---

# 253. AUTONOMY AND AI

AI provides reasoning.

It does not provide permission.

---

# 254. FINAL AUTHORITY STACK

The conceptual control stack is:

```text
USER AUTHORITY
        ↓
AUTONOMY CONFIGURATION
        ↓
SYSTEM POLICY
        ↓
CURRENT STATE
        ↓
CONTEXT
        ↓
AI / RULE RECOMMENDATION
        ↓
DECISION
        ↓
EXECUTION
```

---

# 255. IMPORTANT INVARIANT

Never reverse this relationship:

```text
AI recommendation
```

must not become:

```text
Autonomy permission
```

---

# 256. AUTOMATION BOUNDARY

The line between:

```text
recommendation
```

and:

```text
execution
```

must always remain explicit in code and data.

---

# 257. AUTONOMY AND PRODUCT CONSTITUTION

No autonomy feature may violate:

`PRODUCT_CONSTITUTION.md`

particularly:

* user agency
* no manipulation
* no shame
* real-life focus
* attention respect

---

# 258. AUTONOMY AND SYSTEM ARCHITECTURE

The implementation must preserve:

```text
AI
↓
Decision Engine
↓
Intervention
↓
Execution
```

---

# 259. AUTONOMY AND DATA CONTRACT

All important autonomy state must be represented using structured data.

---

# 260. AUTONOMY AND AI CONTRACT

AI must remain:

```text
reasoning
+
recommendation
```

not unrestricted execution.

---

# 261. FUTURE EXTERNAL AUTOMATION

Potential future capabilities:

```text
calendar updates
external task management
message drafting
message sending
device configuration
```

must each receive their own autonomy policy before implementation.

---

# 262. NO AUTOMATION BY ASSUMPTION

When a new external integration is introduced:

```text
No autonomy policy
→
No automatic write action
```

---

# 263. AUTONOMY EXPANSION PROCESS

To add a new automated action:

```text
Define capability
↓
Define action
↓
Define scope
↓
Define autonomy levels
↓
Define permissions
↓
Define safety constraints
↓
Define explanation
↓
Define logging
↓
Define tests
↓
Implement
```

---

# 264. AUTONOMY CHANGE PROCESS

To increase automation:

```text
Proposed change
↓
Why?
↓
What benefit?
↓
What risk?
↓
What user controls?
↓
What safeguards?
↓
What tests?
↓
Product approval
```

---

# 265. AI AGENT AUTONOMY RULE

A coding AI agent must not introduce new autonomous behavior without explicit sprint scope.

---

# 266. AI AGENT MUST NOT CHANGE DEFAULT AUTONOMY

Even if the agent believes:

> "Full auto would be better."

it must not change the defaults.

---

# 267. AI AGENT MUST NOT REMOVE OVERRIDES

An implementation is incomplete if it removes an established user override path without explicit architectural approval.

---

# 268. AI AGENT MUST NOT BYPASS DECISION ENGINE

No feature may secretly execute actions directly from:

```text
ViewModel
Worker
AI callback
Repository
```

without the defined policy boundary where applicable.

---

# 269. AUTONOMY CODE REVIEW CHECKLIST

Review:

```text
Who triggered the action?
What autonomy level applied?
What policy allowed it?
Was permission verified?
Was current state revalidated?
Was there a user override?
Was cooldown checked?
Was execution verified?
Was it logged?
Can it be explained?
```

---

# 270. AUTONOMY RELEASE CHECKLIST

Before shipping an autonomous capability:

```text
Policy defined
Autonomy levels defined
Default defined
Override defined
Failure behavior defined
Permission behavior defined
Cooldown defined
Deduplication defined
Logging defined
Tests defined
User-facing explanation defined
```

---

# 271. AUTONOMY EXPANSION STAGES

Mature automation should generally progress:

```text
OBSERVE
↓
SUGGEST
↓
ASK
↓
ACT
↓
LEARN
↓
ADAPT
```

Not:

```text
OBSERVE
↓
FULL CONTROL
```

---

# 272. TRUST-BASED EXPANSION

Automation should expand only after sufficient reliability has been demonstrated.

---

# 273. USER TRUST IS NOT AUTOMATIC

A user choosing:

```text
FULL_AUTO
```

for one capability does not imply trust in every capability.

---

# 274. CAPABILITY-SPECIFIC TRUST

Example:

```text
Protection:
FULL_AUTO

Calendar:
OFF
```

is valid.

---

# 275. AUTONOMY AND DIFFERENT USERS

Different users may want different levels of authority.

The architecture must support personalization without changing the underlying safety boundaries.

---

# 276. USER PROFILE DOES NOT EQUAL AUTONOMY

Do not encode autonomy purely as a personality trait.

It is an explicit configuration.

---

# 277. TEMPORARY AUTONOMY EXPERIMENTS

A user may optionally test automation temporarily.

Example:

```text
Enable automatic protection for the next 7 days.
```

This should have:

```text
start
end
scope
```

and automatically expire.

---

# 278. EXPIRING AUTOMATION

Temporary permissions should expire automatically when their defined scope ends.

---

# 279. EXPIRATION SAFETY

After expiration:

```text
Do not silently continue automation.
```

---

# 280. AUTONOMY REPORTING

A future system may provide a periodic summary:

```text
IronMind acted automatically 8 times this week.

Protection:
5

Notifications:
2

Goal resurfacing:
1

User overrides:
2
```

This supports transparency.

---

# 281. AUTONOMY HISTORY

Users should eventually be able to inspect important automated actions.

---

# 282. AUTONOMY HISTORY IS NOT A RAW DEBUG LOG

User-facing history should be understandable.

---

# 283. AUTONOMY HISTORY EXAMPLE

```text
Tuesday, 8:00 AM

IronMind protected YouTube during your study session.

Reason:
Automatic protection is enabled.
Your study commitment was active.

Duration:
60 minutes.

Outcome:
Session completed.
```

---

# 284. AUTONOMY EFFECTIVENESS

The product should eventually evaluate:

```text
Was the action useful?
```

rather than simply:

```text
Was the action executed?
```

---

# 285. AUTONOMY LEARNING LOOP

```text
Decision
↓
Action
↓
User response
↓
Outcome
↓
Learning
```

---

# 286. AUTOMATION SHOULD LEARN TIMING

If an intervention is repeatedly ignored at a certain time, its future timing may be adapted.

---

# 287. AUTOMATION SHOULD LEARN SCOPE

If broad protection causes repeated overrides, IronMind may learn that narrower protection works better, subject to policy.

---

# 288. AUTOMATION SHOULD LEARN CHANNEL

A user may prefer:

```text
in-app suggestion
```

over:

```text
notification
```

---

# 289. AUTOMATION SHOULD LEARN INTERVENTION TYPE

A user may respond better to:

```text
BREAK_DOWN
```

than:

```text
REMIND
```

in certain contexts.

---

# 290. LEARNING DOES NOT ALTER USER POLICY

Learning can optimize within the allowed autonomy level.

It cannot silently change:

```text
OFF
```

to:

```text
FULL_AUTO
```

---

# 291. SAFEST ACTION PRINCIPLE

When two actions have similar expected benefit but different invasiveness:

> Prefer the less invasive action.

---

# 292. LEAST AUTHORITY PRINCIPLE

When multiple autonomy configurations could satisfy a requirement:

> Use the minimum required authority.

---

# 293. LEAST DATA PRINCIPLE

Use the minimum relevant data needed to make the decision.

---

# 294. LEAST INTERRUPTION PRINCIPLE

Use the minimum intervention necessary to help.

---

# 295. LEAST CHANGE PRINCIPLE

Automatic actions should change as little as necessary.

Example:

Prefer:

```textTemporary protection for one app
```

over:

```textBlock the entire phone
```

when both would satisfy the same goal.

---

# 296. REVERSIBILITY PRINCIPLE

Prefer reversible automated changes where practical.

---

# 297. TRANSPARENCY PRINCIPLE

When IronMind acts automatically:

> The user should be able to understand what happened and why.

---

# 298. CONTROL PRINCIPLE

The user should be able to reduce autonomy without fighting the system.

---

# 299. TRUST PRINCIPLE

Autonomy should increase usefulness and trust together.

---

# 300. FINAL AUTONOMY INVARIANTS

The following are mandatory:

```text
1. Autonomy is capability-specific.

2. Four core levels exist:
   OFF
   SUGGEST_ONLY
   ASK_BEFORE_ACTION
   FULL_AUTO

3. FULL_AUTO is bounded, not unlimited.

4. AI cannot grant itself authority.

5. AI cannot silently change autonomy settings.

6. User explicit intent has high authority.

7. User settings override learned behavior.

8. User overrides must be respected.

9. High-impact actions require stronger policy.

10. Unknown actions must not execute.

11. Unknown policy must not produce permissive automation.

12. Missing permission must not be treated as granted.

13. Stale context must not drive consequential execution.

14. Background workers must follow the same autonomy rules.

15. AI recommendations must pass through the Decision Engine.

16. Decision and execution are separate states.

17. Execution failures must not be represented as successful actions.

18. Significant automated actions must be traceable.

19. Important autonomous actions should be explainable.

20. Reversible actions should remain reversible where practical.

21. User undo must be respected.

22. Automation must not oscillate.

23. Duplicate actions must be prevented.

24. Cooldowns must exist where required.

25. STAY_SILENT remains valid under FULL_AUTO.

26. Autonomy learning may optimize actions but cannot silently expand authority.

27. New capabilities require explicit autonomy policies.

28. Coding agents cannot invent autonomous behavior.

29. Autonomy defaults require deliberate product decisions.

30. The user remains the final authority over meaningful life direction.
```

---

# 301. FINAL AUTONOMY DECISION MODEL

The canonical conceptual flow is:

```text
TRIGGER
   ↓
CURRENT STATE
   ↓
RELEVANT CONTEXT
   ↓
USER SETTINGS
   ↓
AUTONOMY LEVEL
   ↓
AI / RULE RECOMMENDATION
   ↓
POLICY VALIDATION
   ↓
COOLDOWN / DUPLICATION CHECK
   ↓
PERMISSION CHECK
   ↓
REVALIDATE CURRENT STATE
   ↓
DECISION
   ↓
 ┌───────────────┬──────────────┬──────────────┐
 ▼               ▼              ▼
ALLOW           ASK          SUGGEST
 │               │              │
 ▼               ▼              ▼
EXECUTE        USER            USER
               DECISION        DECISION
 │
 ▼
EXECUTION RESULT
 │
 ▼
OUTCOME
 │
 ▼
LEARNING
```

---

# 302. FINAL AUTONOMY MODEL

```text
USER
  │
  ▼
AUTHORITY
  │
  ▼
AUTONOMY POLICY
  │
  ▼
DECISION ENGINE
  │
  ▼
INTERVENTION
  │
  ▼
EXECUTION
  │
  ▼
OUTCOME
  │
  ▼
LEARNING
```

---

# 303. FINAL AUTONOMY PHILOSOPHY

IronMind should eventually be capable of saying:

> "I noticed this, I understood the context, I knew what would probably help, I was allowed to act, so I handled it."

And the user should still be able to say:

> "No. Not this time."

IronMind should respect that.

---

# 304. FINAL AUTONOMY GOLDEN RULE

> **Autonomy should remove operational friction without removing meaningful human authority.**

---

# 305. FINAL AUTONOMY ARCHITECTURAL RULE

> **AI may recommend. Policy determines authority. The Decision Engine determines whether action is allowed. The Execution Layer performs the action. The outcome determines what IronMind learns.**

---

# 306. FINAL AUTONOMY PRODUCT RULE

> **The more power IronMind has, the more explicit, explainable, reversible, and controllable that power must become.**

---

# END OF AUTONOMY POLICY

````
