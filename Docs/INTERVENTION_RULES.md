`INTERVENTION_RULES.md`

````md
# IRONMIND INTERVENTION RULES

**Document:** `INTERVENTION_RULES.md`  
**Status:** AUTHORITATIVE INTERVENTION CONTRACT  
**Parent Documents:**
- `IRONMIND_MASTER_BLUEPRINT.md`
- `PRODUCT_CONSTITUTION.md`
- `SYSTEM_ARCHITECTURE.md`
- `DATA_CONTRACT.md`
- `AI_BEHAVIOR_CONTRACT.md`
- `AUTONOMY_POLICY.md`

**Purpose:** Define when IronMind should intervene, what type of intervention it should use, when it should stay silent, how intervention priority and cooldowns work, how interventions are delivered, how user responses are handled, and how intervention outcomes improve future behavior.

---

# 1. PURPOSE

IronMind is not designed to intervene constantly.

The Intervention System exists to answer:

> **When should IronMind do something, what should it do, and when should it do nothing?**

The objective is not maximum intervention.

The objective is:

> **The smallest useful intervention at the right moment that meaningfully increases the probability of useful action.**

---

# 2. INTERVENTION NORTH STAR

The Intervention System should optimize for:

```text
RELEVANCE
+
TIMING
+
USEFULNESS
+
LOW FRICTION
+
LOW UNNECESSARY INTERRUPTION
````

---

# 3. INTERVENTION IS A SYSTEM ACTION

An intervention is something IronMind intentionally does in response to:

* user intent
* commitment state
* observed behavior
* context
* pattern
* outcome
* schedule
* reflection
* system condition

---

# 4. INTERVENTION IS NOT THE SAME AS A NOTIFICATION

An intervention is a product-level action.

It may be delivered through:

```text
Notification
In-app prompt
Protection
Task change
Schedule suggestion
Voice interaction
Reflection request
UI state
```

A notification is only one delivery mechanism.

---

# 5. CORE INTERVENTION TYPES

IronMind supports the following core intervention vocabulary:

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

These are semantic intervention types.

Implementation mechanisms may vary.

---

# 6. INTERVENTION TYPE: REMIND

`REMIND` is used when the user already knows what they intend to do, but timing or awareness may be the main issue.

Examples:

```text
Your study session starts in 10 minutes.
```

```text
You committed to the gym at 7 PM.
```

---

# 7. REMIND PRINCIPLE

A reminder should:

* reinforce an existing intention
* occur at useful timing
* remain concise
* not repeat unnecessarily

---

# 8. REMIND SHOULD NOT CREATE A NEW OBLIGATION

A reminder must not turn:

```text
suggestion
```

into:

```text
commitment
```

---

# 9. REMIND TRIGGERS

Potential triggers:

```text
scheduled commitment approaching
task deadline approaching
important user-defined event
previously requested reminder
user-configured reminder
```

---

# 10. REMIND SHOULD NOT FIRE IF ALREADY ACTING

If the user has already started the relevant action:

```text
do not remind them to start
```

---

# 11. REMIND SHOULD NOT FIRE AFTER COMPLETION

If the task is completed:

```text
suppress pending reminder
```

---

# 12. REMIND DUPLICATION

Equivalent reminders should be deduplicated.

---

# 13. REMIND TIMING

Reminder timing should eventually consider:

```text
scheduled start
historical response
user preference
context
previous reminders
```

---

# 14. REMIND MESSAGE RULE

Prefer:

> "Your study session starts in 10 minutes."

Avoid:

> "Don't forget! Don't procrastinate! You promised!"

---

# 15. INTERVENTION TYPE: REDIRECT

`REDIRECT` is used when the user's current behavior appears to conflict with an active intended action.

Example:

```text
Active study session
↓
User opens distracting app
↓
REDIRECT
```

---

# 16. REDIRECT PRINCIPLE

The objective is to return attention toward the intended action.

---

# 17. REDIRECT MUST BE CONTEXTUAL

Do not assume:

```text
app = bad
```

Instead evaluate:

```text
current commitment
current task
current context
application purpose
user history
```

---

# 18. REDIRECT EXAMPLE

During a study commitment:

> "Your study session is still active. Want to return to your study task?"

---

# 19. REDIRECT SHOULD NOT BE SHAMING

Never:

> "You're wasting time again."

Prefer:

> "Your study session is still active."

---

# 20. REDIRECT ESCALATION

A possible escalation path:

```text
No intervention
↓
Soft redirect
↓
Protection suggestion
↓
Protection
```

But escalation is never automatic merely because the first intervention was ignored.

Policy must determine eligibility.

---

# 21. REDIRECT REPEAT LIMIT

Do not repeatedly redirect every few seconds.

---

# 22. REDIRECT AFTER OVERRIDE

If the user overrides a redirect:

```text
do not immediately repeat identical redirect
```

unless a defined higher-priority condition exists.

---

# 23. INTERVENTION TYPE: PROTECT

`PROTECT` protects an active meaningful action from avoidable distraction.

Examples:

* temporary app protection
* focus-session protection
* selected application restriction

---

# 24. PROTECT PRINCIPLE

Protection exists to:

> **protect an important commitment, not punish the user.**

---

# 25. PROTECT REQUIRES A TARGET

Protection must identify what is being protected against.

Example:

```text
specific app
specific application category
specific context
```

---

# 26. PROTECT SHOULD BE TIME-BOUNDED

Where possible, protection should have:

```text
start
end
```

rather than indefinite automatic blocking.

---

# 27. PROTECT SHOULD BE CONTEXTUAL

Example:

```text
YouTube
+
learning session
=
potentially useful

YouTube
+
writing session
=
potential distraction
```

Do not classify applications globally as good or bad.

---

# 28. PROTECT ELIGIBILITY

Automatic protection may require:

```text
active commitment
+
protection allowed
+
eligible target
+
required permission
+
no override
+
no cooldown conflict
```

---

# 29. PROTECT FAILURE

If the protection system cannot execute:

```text
record failure
inform user when useful
do not claim protection succeeded
```

---

# 30. PROTECT OVERRIDE

Users may override protection where allowed.

Override becomes potential learning evidence.

---

# 31. INTERVENTION TYPE: BREAK_DOWN

`BREAK_DOWN` is used when an action appears too large, ambiguous, or difficult to start.

---

# 32. BREAK_DOWN PRINCIPLE

The goal is:

> **Find the smallest meaningful action that creates forward movement.**

---

# 33. BREAK_DOWN EXAMPLE

User goal:

> Build a business.

Possible breakdown:

```text
Open notes.
↓
Write the target customer.
↓
Write one problem they have.
```

---

# 34. BREAK_DOWN SHOULD NOT CREATE BUSYWORK

The smaller tasks must remain meaningfully connected to the original goal.

---

# 35. BREAK_DOWN SHOULD PREFER STARTING

When the main problem is initiation, prioritize:

```text
first useful action
```

rather than creating a massive project plan.

---

# 36. BREAK_DOWN TRIGGERS

Potential signals:

```text
repeated postponement
large ambiguous task
user says "I don't know where to start"
repeated task abandonment
AI identifies high complexity
low start rate
```

---

# 37. BREAK_DOWN SHOULD CONSIDER EVIDENCE

Repeated evidence is stronger than a single event.

---

# 38. BREAK_DOWN MUST RESPECT USER INTENT

Do not simplify a goal into something that changes its meaning.

---

# 39. INTERVENTION TYPE: REASSURE

`REASSURE` is used when the user's language suggests they may benefit from reducing unnecessary pressure or uncertainty.

---

# 40. REASSURE PRINCIPLE

Reassurance should reduce friction without becoming empty motivational language.

---

# 41. GOOD REASSURANCE

> "You don't need to finish everything right now. Start with the first 10 minutes."

---

# 42. BAD REASSURANCE

> "You're amazing and everything will definitely work out!"

---

# 43. REASSURE MUST REMAIN TRUTHFUL

Do not promise outcomes the system cannot know.

---

# 44. REASSURE SHOULD NOT BECOME THERAPY

IronMind may provide practical emotional support but should not present itself as a therapist.

---

# 45. INTERVENTION TYPE: CHALLENGE

`CHALLENGE` is used when the system has enough evidence that the current behavior or plan may not be working and a direct observation could help.

---

# 46. CHALLENGE PRINCIPLE

Challenge behavior, not identity.

---

# 47. GOOD CHALLENGE

> "You've moved this task four times. The current plan isn't working. Should we change the approach?"

---

# 48. BAD CHALLENGE

> "You're making excuses again."

---

# 49. CHALLENGE EVIDENCE

A challenge should usually require:

```text
meaningful pattern
+
relevant context
+
sufficient evidence
```

---

# 50. CHALLENGE SHOULD NOT BE USED FOR EVERY FAILURE

One missed task does not automatically justify a challenge.

---

# 51. CHALLENGE PROPORTIONALITY

The strength of challenge should be proportional to:

```text
importance
+
evidence
+
repetition
+
user preferences
```

---

# 52. INTERVENTION TYPE: ASK

`ASK` is used when IronMind needs user input to make a useful decision.

---

# 53. ASK PRINCIPLE

Ask only when the answer materially improves the next action.

---

# 54. GOOD ASK

> "Is this goal still important to you?"

---

# 55. BAD ASK

> "Why did you do that?"

when the answer has no actionable purpose.

---

# 56. ASK SHOULD REDUCE UNCERTAINTY

Examples:

```text
Goal still relevant?
Reason for postponement?
Which task matters more?
Which schedule works?
```

---

# 57. ASK SHOULD NOT BECOME AN INTERVIEW

Do not ask many low-value questions.

---

# 58. ASK RESPONSE

User responses should be associated with the relevant:

```text
intervention
decision
entity/context
```

where appropriate.

---

# 59. INTERVENTION TYPE: RECOVER

`RECOVER` is used after missed, failed, or abandoned execution when a useful next step is available.

---

# 60. RECOVER PRINCIPLE

The system should help the user regain momentum.

---

# 61. RECOVER EXAMPLE

> "You missed the evening session. Do you want to move it to tomorrow morning or do a 20-minute version tonight?"

---

# 62. RECOVER MUST NOT PUNISH

Never:

> "You failed, so now you need to make up for it."

---

# 63. RECOVER OPTIONS

Potential recovery actions:

```text
retry
reschedule
reduce scope
change time
change environment
identify barrier
abandon intentionally
```

---

# 64. RECOVER AFTER REPEATED FAILURE

Repeated failure may trigger:

```text
RECOVER
+
ASK
```

or:

```text
CHALLENGE
```

rather than repeating the same schedule.

---

# 65. INTERVENTION TYPE: RESCHEDULE

`RESCHEDULE` helps adjust an action when the original timing no longer fits.

---

# 66. RESCHEDULE PRINCIPLE

A schedule exists to support action.

The user does not exist to obey the schedule.

---

# 67. RESCHEDULE TRIGGERS

Potential triggers:

```text
missed commitment
schedule conflict
user request
context change
repeated timing failure
```

---

# 68. RESCHEDULE AUTHORITY

Automatic rescheduling depends on:

`AUTONOMY_POLICY.md`

It must not be performed simply because AI suggested it.

---

# 69. RESCHEDULE LIMIT

Repeatedly moving the same commitment without changing the strategy is discouraged.

---

# 70. RESCHEDULE AFTER REPEATED FAILURE

Example:

```text
3 failed schedules
↓
Stop repeatedly rescheduling
↓
Ask what has changed
```

---

# 71. INTERVENTION TYPE: REFLECT

`REFLECT` invites the user to examine what happened.

---

# 72. REFLECT PRINCIPLE

Reflection should help IronMind learn and should help the user understand what happened.

---

# 73. REFLECT TRIGGERS

Possible:

```text
day ending
important missed commitment
meaningful milestone
repeated failure
major goal completion
user-requested review
```

---

# 74. REFLECTION SHOULD NOT ALWAYS FOLLOW FAILURE

Reflection can also follow:

* success
* change
* milestone
* major decision

---

# 75. NIGHTLY REFLECTION

The nightly review may use:

```text
REFLECT
```

as the core intervention.

---

# 76. INTERVENTION TYPE: CELEBRATE

`CELEBRATE` acknowledges meaningful progress.

---

# 77. CELEBRATE PRINCIPLE

Celebration should be:

```text
truthful
proportionate
specific
brief
```

---

# 78. GOOD CELEBRATION

> "You completed all three commitments you made yesterday."

---

# 79. BAD CELEBRATION

> "YOU ARE UNSTOPPABLE!!! LEGEND!!!"

---

# 80. CELEBRATE MUST NOT CREATE DEPENDENCE

Do not make the user's emotional reward dependent on IronMind's approval.

---

# 81. INTERVENTION TYPE: STAY_SILENT

`STAY_SILENT` means IronMind intentionally chooses not to intervene.

This is a core intervention type.

---

# 82. SILENCE PRINCIPLE

> **The best intervention is sometimes no intervention.**

---

# 83. SILENCE TRIGGERS

Stay silent when:

```text
user is already acting
action already completed
recent equivalent intervention exists
intervention benefit is low
confidence is weak
context is unclear
user is in a quiet period
interruption cost is high
user recently dismissed similar intervention
```

---

# 84. STAY SILENT UNDER FULL AUTO

Even when:

```text
FULL_AUTO
```

is enabled, IronMind may choose:

```text
STAY_SILENT
```

---

# 85. INTERVENTION DECISION MODEL

The conceptual intervention pipeline is:

```text
TRIGGER
   ↓
CONTEXT
   ↓
RELEVANT MEMORY
   ↓
RELEVANT PATTERNS
   ↓
AI / RULE RECOMMENDATION
   ↓
AUTONOMY CHECK
   ↓
INTERVENTION POLICY
   ↓
PRIORITY
   ↓
COOLDOWN
   ↓
DUPLICATION CHECK
   ↓
DELIVERY
   ↓
USER RESPONSE
   ↓
OUTCOME
   ↓
LEARNING
```

---

# 86. INTERVENTION TRIGGER

A trigger is an event or state that causes IronMind to consider whether action is needed.

Triggers can originate from:

```text
Schedule
Event
Observation
Goal
Commitment
Pattern
Reflection
User request
Background process
```

---

# 87. TRIGGER DOES NOT GUARANTEE INTERVENTION

This is mandatory.

Example:

```text
Task approaching
↓
Candidate trigger
↓
Decision
↓
STAY_SILENT
```

---

# 88. CANDIDATE INTERVENTION

Before delivery, a candidate should contain:

```text
type
target
reason
priority
confidence where relevant
trigger
context
```

---

# 89. INTERVENTION ELIGIBILITY

A candidate becomes eligible only if:

```text
target still exists
+
context still valid
+
action still relevant
+
autonomy permits it
+
policy allows it
+
no cooldown conflict
+
no duplicate
```

---

# 90. STALE INTERVENTION

If the situation changes:

```text
cancel / suppress candidate
```

Example:

```text
Reminder generated
↓
User completes task
↓
Reminder becomes stale
↓
Do not deliver
```

---

# 91. INTERVENTION PRIORITY

Each intervention candidate should have a priority.

Suggested:

```text
CRITICAL
HIGH
NORMAL
LOW
```

---

# 92. CRITICAL INTERVENTION

"Critical" must be rare.

Do not use critical priority merely to increase delivery probability.

---

# 93. HIGH PRIORITY

May include:

* important commitment at immediate risk
* explicit user-requested reminder
* time-sensitive user-defined action

---

# 94. NORMAL PRIORITY

Typical reminders and contextual support.

---

# 95. LOW PRIORITY

Non-urgent insights or optional suggestions.

---

# 96. PRIORITY DOES NOT OVERRIDE AUTONOMY

A high-priority candidate cannot bypass:

```text
AUTONOMY = OFF
```

---

# 97. PRIORITY DOES NOT OVERRIDE USER SETTINGS

Notification preferences remain authoritative.

---

# 98. PRIORITY DOES NOT OVERRIDE SAFETY

Hard constraints remain authoritative.

---

# 99. INTERVENTION INVASIVENESS

Each intervention may conceptually have:

```text
LOW
MEDIUM
HIGH
```

invasiveness.

---

# 100. LOW INVASIVENESS

Examples:

```text
in-app suggestion
brief reminder
small prompt
```

---

# 101. MEDIUM INVASIVENESS

Examples:

```text
notification
interruptive prompt
temporary interface interruption
```

---

# 102. HIGH INVASIVENESS

Examples:

```text
application protection
restriction
external write action
```

---

# 103. INVASIVENESS RULE

Higher-invasiveness interventions require stronger policy conditions.

---

# 104. LEAST INVASIVE PRINCIPLE

When two interventions are similarly useful:

> Prefer the less invasive one.

---

# 105. INTERVENTION ESCALATION

Escalation may occur when:

```text
first intervention ineffective
+
higher intervention still useful
+
policy allows it
+
context remains relevant
```

---

# 106. ESCALATION MUST NOT BE AUTOMATIC PRESSURE

Example:

```text
Reminder ignored
↓
Immediately send 10 more reminders
```

is forbidden.

---

# 107. ESCALATION EXAMPLE

Potential:

```text
No action
↓
Reminder
↓
Redirect
↓
Protection suggestion
```

Only when each step is independently justified.

---

# 108. MAXIMUM ESCALATION

Each intervention pathway should eventually define a maximum escalation level.

Do not create unlimited escalation loops.

---

# 109. COOLDOWN

A cooldown suppresses repeated intervention attempts for a defined period.

---

# 110. COOLDOWN PURPOSE

Prevent:

```text
REMIND
REMIND
REMIND
REMIND
```

and:

```text
PROTECT
UNPROTECT
PROTECT
UNPROTECT
```

loops.

---

# 111. COOLDOWN KEY

A cooldown may be scoped by:

```text
user
capability
intervention type
target
context
```

depending on the situation.

---

# 112. COOLDOWN SHOULD BE CONTEXTUAL

A reminder about:

```text
Gym
```

does not necessarily suppress:

```text
Important exam deadline
```

unless policy defines broader suppression.

---

# 113. COOLDOWN AFTER USER DISMISSAL

A dismissed intervention should usually suppress an equivalent intervention temporarily.

---

# 114. COOLDOWN AFTER USER OVERRIDE

A user override should usually suppress automatic repetition of the same intervention for the current context.

---

# 115. COOLDOWN AFTER ACCEPTANCE

Acceptance may not require suppression of all future interventions because the user may need continued support.

---

# 116. COOLDOWN AFTER COMPLETION

Once the target action is complete:

```text
suppress pending intervention
```

---

# 117. DEDUPLICATION

Interventions with equivalent semantic purpose should not be delivered repeatedly.

---

# 118. DEDUPLICATION IS NOT ONLY STRING COMPARISON

These should potentially count as equivalent:

```text
"Study now."
"Your study session is active."
"Return to your study."
```

if they serve the same purpose in the same context.

---

# 119. INTERVENTION GROUPING

A mature system may group intervention candidates by:

```text
goal
commitment
purpose
target
context
time window
```

---

# 120. INTERVENTION SUPPRESSION

A candidate should be suppressed when:

```text
target completed
target cancelled
target no longer relevant
user opted out
quiet period
cooldown
duplicate
permission unavailable
context changed
```

---

# 121. USER PREFERENCE SUPPRESSION

User preferences can suppress entire intervention categories.

Example:

```text
User:
No motivational notifications.
```

The system must respect this.

---

# 122. QUIET PERIOD SUPPRESSION

Non-critical interventions should normally be suppressed during user-defined quiet periods.

---

# 123. INTERVENTION BURDEN

The system should track how much it has recently interrupted the user.

Conceptually:

```text
recent intervention count
recent dismissal count
recent override count
recent interaction rate
```

---

# 124. INTERRUPTION BUDGET

A mature system may maintain an interruption budget.

If the budget is exceeded:

```text
prefer STAY_SILENT
```

---

# 125. INTERRUPTION BUDGET IS NOT A SCORE

This is internal policy, not gamification.

---

# 126. USER RESPONSE

Possible intervention responses:

```text
ACCEPTED
DISMISSED
IGNORED
OVERRIDDEN
COMPLETED
REJECTED
```

Exact status vocabulary is defined consistently with `DATA_CONTRACT.md`.

---

# 127. IGNORED VS DISMISSED

These are not necessarily equivalent.

```text
Ignored
=
No observed response.
```

```text
Dismissed
=
User intentionally dismissed it.
```

---

# 128. OVERRIDDEN

An override means the user actively chose to bypass or change the system action.

---

# 129. ACCEPTED

Accepted means the user interacted positively with the offered intervention.

It does not automatically mean the desired real-world outcome occurred.

---

# 130. INTERVENTION OUTCOME

The eventual outcome may be:

```text
goal progress
task started
task completed
task postponed
no change
technical failure
```

---

# 131. DELIVERY OUTCOME VS BEHAVIOR OUTCOME

Separate:

```text
Intervention delivered successfully
```

from:

```text
User behavior improved
```

---

# 132. TECHNICAL FAILURE

Example:

```text
Notification generation:
success

Notification delivery:
failed

Behavior:
unknown
```

Do not infer user rejection.

---

# 133. BEHAVIORAL FAILURE

Example:

```text
Notification delivered
User ignored
Task remained incomplete
```

This is different from technical delivery failure.

---

# 134. INTERVENTION EFFECTIVENESS

A mature system may estimate:

```text
Was the intervention helpful in this context?
```

---

# 135. EFFECTIVENESS MUST BE CONTEXTUAL

An intervention can be useful in one context and ineffective in another.

---

# 136. EFFECTIVENESS SHOULD NOT BE BINARY

Useful signals may include:

```text
accepted
started action
completed action
ignored
dismissed
overridden
```

---

# 137. INTERVENTION LEARNING

IronMind can learn:

```text
Intervention
+
Context
+
Outcome
```

---

# 138. INTERVENTION LEARNING EXAMPLE

```text
BREAK_DOWN
+
Large ambiguous task
+
User started task
```

This may strengthen the recommendation for similar situations.

---

# 139. INTERVENTION FAILURE EXAMPLE

```text
Long motivational notification
+
Evening
+
Repeatedly dismissed
```

This may reduce future use of similar interventions in similar conditions.

---

# 140. DO NOT OVERFIT

One ignored intervention does not prove the intervention is universally bad.

---

# 141. SAMPLE SIZE

Use multiple observations before creating strong intervention-response patterns.

---

# 142. RECENCY

Recent intervention outcomes should generally have more relevance than old outcomes.

---

# 143. INTERVENTION MEMORY

Useful intervention-response patterns may eventually become personal memory/pattern information.

---

# 144. USER FEEDBACK

Explicit user feedback is especially valuable.

Example:

> "That reminder was actually useful."

This should be stronger evidence than simply observing notification interaction.

---

# 145. USER DISLIKE

Example:

> "Stop sending me messages like this."

The system should treat it as explicit preference data where appropriate.

---

# 146. USER FEEDBACK AUTHORITY

Explicit user feedback about intervention style should have high authority.

---

# 147. INTERVENTION COPY PRINCIPLES

Messages should generally:

```text
state reality
identify relevance
provide next step
avoid manipulation
```

---

# 148. COPY FORMAT: REMINDER

```text
<What is happening?>
<Optional next action>
```

Example:

> "Your study session starts in 10 minutes."

---

# 149. COPY FORMAT: REDIRECT

```text
<Current intended action>
+
<gentle redirect>
```

Example:

> "Your study session is still active. Return to your study task?"

---

# 150. COPY FORMAT: RECOVERY

```text
<What happened?>
+
<available options>
```

Example:

> "You missed the gym session. Move it to tomorrow or do a shorter session tonight?"

---

# 151. COPY FORMAT: BREAK_DOWN

```text
<reduce scope>
+
<first action>
```

Example:

> "Don't solve the whole problem now. Start by writing the first requirement."

---

# 152. COPY FORMAT: CHALLENGE

```text
<observed pattern>
+
<practical question>
```

Example:

> "You've postponed this three times. Should we change the plan?"

---

# 153. COPY FORMAT: ASK

Ask one useful question whenever possible.

---

# 154. COPY FORMAT: REASSURE

Use realistic reassurance connected to action.

Example:

> "You don't need to finish it all today. Start with 15 minutes."

---

# 155. COPY FORMAT: CELEBRATE

State the evidence.

Example:

> "You completed all three commitments today."

---

# 156. COPY FORMAT: REFLECT

Invite concise reflection.

Example:

> "What got in the way today?"

---

# 157. NO MANIPULATIVE COPY

Do not use:

```text
You promised.
Don't disappoint me.
No excuses.
You failed again.
If you really cared...
```

---

# 158. NO IDENTITY ATTACKS

Never:

```text
You're lazy.
You're undisciplined.
You're inconsistent.
```

---

# 159. NO FALSE CERTAINTY

Avoid:

```text
You are afraid.
You obviously don't care.
You clearly wanted to avoid it.
```

unless explicitly confirmed and appropriately contextualized.

---

# 160. INTERVENTION CONTEXT REQUIREMENT

Every intervention should have enough context to know:

```text
what it is about
why now
which entity/context it targets
```

where applicable.

---

# 161. TARGET

Possible targets:

```text
Goal
Ambition
Task
Commitment
Application
Focus Session
Reflection
General User Context
```

---

# 162. TARGET VALIDITY

Before delivery:

```text
Does target still exist?
Does target still matter?
Is target still active?
```

---

# 163. CONTEXT WINDOW

Intervention decisions should use relevant current context.

Potential:

```text
current time
current application
active commitment
recent events
relevant patterns
user settings
recent interventions
```

---

# 164. INTERVENTION SHOULD NOT USE ALL USER DATA

Only relevant context should be considered.

---

# 165. CONTEXT STALENESS

A candidate created minutes ago may become invalid if:

```text
task completed
commitment changed
user override
schedule changed
goal abandoned
```

---

# 166. REVALIDATION BEFORE DELIVERY

For important interventions:

```text
revalidate current state
```

before delivery/execution.

---

# 167. AUTOMATION RACE CONDITION

Example:

```text
IronMind prepares reminder.
User completes task.
Reminder worker executes.
```

The reminder must be suppressed.

---

# 168. INTERVENTION EXECUTION

Delivery should follow:

```text
candidate
↓
policy
↓
eligibility
↓
delivery
↓
verify
```

---

# 169. DELIVERY VERIFICATION

Where technically possible, determine whether delivery succeeded.

---

# 170. INTERVENTION FAILURE LOG

Example:

```text
IronMindLifecycle Intervention FAILED
type=PROTECT
reason=PERMISSION_UNAVAILABLE
```

---

# 171. INTERVENTION LIFECYCLE LOGGING

Use:

```text
IronMindLifecycle Intervention PROPOSED ...
IronMindLifecycle Intervention APPROVED ...
IronMindLifecycle Intervention TRIGGERED ...
IronMindLifecycle Intervention DELIVERED ...
IronMindLifecycle Intervention ACCEPTED ...
IronMindLifecycle Intervention DISMISSED ...
IronMindLifecycle Intervention IGNORED ...
IronMindLifecycle Intervention OVERRIDDEN ...
IronMindLifecycle Intervention COMPLETED ...
IronMindLifecycle Intervention FAILED ...
```

Only events actually supported by the implementation should be emitted.

---

# 172. LOGGING MINIMUM

At minimum, meaningful intervention execution should allow engineers to determine:

```text
type
target
trigger
decision
outcome
```

without logging sensitive raw content.

---

# 173. INTERVENTION ID

Every persisted intervention should have a stable unique identifier.

---

# 174. DECISION LINK

Where the Decision Engine exists, intervention should reference the decision that authorized it.

---

# 175. TRIGGER LINK

Where appropriate, intervention should reference the triggering event/condition.

---

# 176. OUTCOME LINK

The intervention should eventually reference or generate its outcome.

---

# 177. INTERVENTION CHAIN

The ideal trace is:

```text
Trigger
↓
Candidate
↓
Decision
↓
Intervention
↓
Delivery
↓
Response
↓
Outcome
```

---

# 178. NO ORPHANED AUTONOMOUS ACTIONS

Important automatic actions should not exist without a traceable decision path where the architecture requires one.

---

# 179. INTERVENTION POLICY AND AUTONOMY

Intervention policy decides:

```text
Is this a good intervention?
```

Autonomy policy decides:

```text
May IronMind perform it automatically?
```

---

# 180. AI VS INTERVENTION ENGINE

AI may recommend:

```text
BREAK_DOWN
```

The Intervention Engine determines:

```text
How should that recommendation actually be delivered?
```

---

# 181. DECISION ENGINE VS INTERVENTION ENGINE

Decision Engine:

```text
Should anything happen?
What type?
Is it allowed?
```

Intervention Engine:

```text
How should the approved action happen?
```

---

# 182. EXECUTION ENGINE

Execution layer:

```text
Actually perform the operation.
```

---

# 183. INTERVENTION BOUNDARY SUMMARY

```text
AI
↓
Recommendation

Decision Engine
↓
Allowed / Ask / Suggest / Silence

Intervention Engine
↓
Construct intervention

Execution Layer
↓
Perform action
```

---

# 184. INTERVENTION PRIORITY DECISION

When several candidates exist:

```text
1. remove invalid candidates
2. remove suppressed candidates
3. remove duplicates
4. apply cooldown
5. apply autonomy
6. rank remaining candidates
7. select appropriate intervention
8. optionally choose STAY_SILENT
```

---

# 185. MULTIPLE CANDIDATES

Do not automatically deliver every eligible intervention.

Example:

```text
REMIND
+
REDIRECT
+
REFLECT
```

may all be candidates.

The system should choose the most useful one.

---

# 186. INTERVENTION COMPOSITION

Multiple interventions may be combined only when there is a clear reason.

Example:

```text
RECOVER
+
ASK
```

could be one coherent interaction.

---

# 187. AVOID INTERVENTION STACKING

Do not send:

```text
notification
+
another notification
+
popup
+
sound
```

for the same event without strong justification.

---

# 188. ONE PRIMARY INTERVENTION

Prefer one primary intervention per immediate situation.

---

# 189. SECONDARY ACTION

Secondary actions may exist when directly useful.

Example:

```text
Recovery prompt
+
reschedule button
```

---

# 190. INTERVENTION TIMING

Timing should consider:

```text
current context
urgency
user activity
historical response
quiet periods
recent interventions
```

---

# 191. TIMING SHOULD NOT BE PERFECTLY RIGID

A reminder scheduled for 8:00 may be better delivered at a slightly different time if the user is already acting, depending on policy.

---

# 192. TIMING ADAPTATION

A mature system may learn:

```text
best intervention time
```

for the individual.

---

# 193. TIMING ADAPTATION MUST REMAIN WITHIN USER POLICY

Learning cannot override quiet periods or explicit preferences.

---

# 194. INTERVENTION CHANNEL SELECTION

A mature system may select:

```text
notification
in-app
voice
protection
```

based on context and user preference.

---

# 195. CHANNEL PERSONALIZATION

Example:

```text
User frequently ignores notifications
but responds to in-app prompts.
```

This may influence future intervention channel selection.

---

# 196. CHANNEL LEARNING

Track outcomes by channel where useful.

---

# 197. INTERVENTION DELIVERY AND BATTERY

Background interventions should use efficient Android scheduling mechanisms.

---

# 198. INTERVENTION DELIVERY AND NETWORK

Routine local interventions should not require network where unnecessary.

---

# 199. OFFLINE INTERVENTION

If the required data is available locally:

```text
local decision
→ local intervention
```

may continue without network.

---

# 200. AI UNAVAILABLE

If AI cannot generate a recommendation:

Use deterministic intervention rules when sufficient.

Otherwise:

```text
STAY_SILENT
```

or another safe fallback.

---

# 201. DATA UNAVAILABLE

If critical context is unavailable:

```text
do not perform high-impact intervention
```

---

# 202. PERMISSION UNAVAILABLE

Do not execute interventions requiring missing permissions.

---

# 203. INTERVENTION FAILURE RECOVERY

If intervention execution fails:

```text
record failure
+
optional retry
+
avoid duplicate spam
```

---

# 204. RETRY POLICY

Retries must be bounded.

---

# 205. RETRY VS RE-INTERVENTION

A technical delivery retry is not the same as generating another behavioral intervention.

---

# 206. TECHNICAL RETRY

Example:

```text
Notification API temporary failure
→ retry technical delivery
```

---

# 207. BEHAVIORAL RE-INTERVENTION

Example:

```text
User ignored reminder
→ future reminder decision
```

These are different.

---

# 208. INTERVENTION EXPIRATION

Some interventions should expire.

Examples:

```text
time-specific reminder
session protection
temporary redirect
```

---

# 209. EXPIRED INTERVENTION

Do not deliver expired interventions merely because a worker eventually ran.

---

# 210. INTERVENTION CANCELLATION

Cancellation may occur if:

```text
target completed
target removed
user disabled capability
context changed
```

---

# 211. INTERVENTION HISTORY

Persist enough history to learn:

```text
what happened
when
why
response
outcome
```

---

# 212. INTERVENTION EFFECTIVENESS SCORE

A future implementation may calculate an internal effectiveness estimate.

This should not automatically become a user-facing score.

---

# 213. EFFECTIVENESS SIGNALS

Possible signals:

```text
action started
action completed
time-to-start
user acceptance
user dismissal
override
goal progress
```

---

# 214. INTERVENTION EFFECTIVENESS IS CONTEXTUAL

An intervention may be ineffective because:

```text
wrong timing
wrong context
wrong message
wrong target
wrong intervention type
technical failure
```

The system should distinguish these.

---

# 215. TECHNICAL FAILURE MUST NOT LOWER BEHAVIORAL EFFECTIVENESS

If the notification never delivered:

Do not infer:

> "The user ignored the reminder."

---

# 216. USER REJECTION SHOULD AFFECT FUTURE SELECTION

Repeated explicit rejection can reduce future recommendation priority.

---

# 217. USER ACCEPTANCE SHOULD NOT CREATE UNLIMITED TRUST

One successful intervention does not justify endless repetition.

---

# 218. INTERVENTION EXPLAINABILITY

For autonomous interventions, the user should eventually be able to inspect:

```text
What happened?
Why?
Which goal/task?
What evidence?
What setting allowed it?
```

---

# 219. USER-FACING EXPLANATION EXAMPLE

> "I protected YouTube because your study commitment was active and automatic protection is enabled for this type of session."

---

# 220. INTERVENTION EXPLANATION SHOULD NOT EXPOSE INTERNAL JARGON

Avoid:

> "Pattern confidence 0.83 triggered the InterventionPolicy evaluator."

Prefer:

> "This has interrupted similar study sessions recently."

---

# 221. ADVANCED DEBUG EXPLANATION

Development builds may expose:

```text
pattern ID
confidence
decision ID
policy version
event ID
```

when useful.

---

# 222. USER TRUST

Interventions should build trust through:

```text
correctness
restraint
transparency
control
```

---

# 223. INTERVENTION SHOULD NEVER BECOME PUNISHMENT

Even protection should be framed as assistance.

---

# 224. PROTECTION AFTER REPEATED DISTRACTION

Possible:

> "You've opened Instagram several times during this study session. Automatic protection is enabled, so Instagram is temporarily restricted until the session ends."

Not:

> "You can't be trusted."

---

# 225. USER OVERRIDE AFTER PROTECTION

If the user overrides:

```text
record override
respect current context
avoid immediate aggressive reactivation
```

---

# 226. REPEATED OVERRIDE

Repeated overrides may suggest:

```text
protection target wrong
context classification wrong
commitment not meaningful
permission/policy mismatch
```

Investigate rather than escalating punishment.

---

# 227. INTERVENTION TYPE SELECTION

Selection should consider:

```text
problem type
goal
context
pattern
confidence
user preference
history
invasiveness
```

---

# 228. EXAMPLE SELECTION

Problem:

```text
Task too large
```

Prefer:

```text
BREAK_DOWN
```

rather than:

```text
REMIND
```

if reminders already failed.

---

# 229. EXAMPLE SELECTION

Problem:

```text
User forgot timing
```

Prefer:

```text
REMIND
```

---

# 230. EXAMPLE SELECTION

Problem:

```text
Active commitment + distracting behavior
```

Possible:

```text
REDIRECT
```

or:

```text
PROTECT
```

depending on policy.

---

# 231. EXAMPLE SELECTION

Problem:

```text
Repeated failed schedule
```

Prefer:

```text
ASK
```

or:

```text
CHALLENGE
```

rather than another blind reminder.

---

# 232. EXAMPLE SELECTION

Problem:

```text
Missed commitment
```

Prefer:

```text
RECOVER
```

rather than:

```text
CELEBRATE
```

---

# 233. EXAMPLE SELECTION

Problem:

```text
Long-term goal ignored
```

Prefer:

```text
REFLECT
```

or:

```text
ASK
```

or:

```text
GOAL RESURFACING
```

depending on the exact context.

---

# 234. INTERVENTION TYPE MUST MATCH PROBLEM

Do not use the same intervention for every problem.

---

# 235. REMINDER FAILURE

If reminders repeatedly fail:

```text
Do not simply increase reminder frequency.
```

Investigate whether the underlying problem is different.

---

# 236. REDIRECT FAILURE

If redirects repeatedly fail:

Potentially evaluate:

```text
task too difficult
goal not important
protection needed
context incorrectly classified
```

---

# 237. PROTECTION FAILURE

If protection repeatedly fails:

Investigate:

```text
permission
technical implementation
wrong target
user override
context
```

---

# 238. BREAKDOWN FAILURE

If task breakdown repeatedly fails:

Potentially reconsider:

```text
wrong task
wrong level of decomposition
goal ambiguity
motivation/context
```

---

# 239. RECOVERY FAILURE

If recovery prompts repeatedly fail:

Ask:

> "Is this goal still important?"

rather than endlessly rescheduling.

---

# 240. INTERVENTION LOOP PREVENTION

Never allow:

```text
intervention
→ ignored
→ intervention
→ ignored
→ intervention
```

without bounded policy.

---

# 241. INFINITE LOOP PREVENTION

Every automated intervention path must eventually reach:

```text
success
silence
user decision
expiration
or bounded stop
```

---

# 242. INTERVENTION STATE MACHINE

Conceptual lifecycle:

```text
CANDIDATE
   ↓
PROPOSED
   ↓
APPROVED
   ↓
TRIGGERED
   ↓
DELIVERED
   ↓
RESPONSE
   ↓
OUTCOME
```

Alternative:

```text
PROPOSED
   ↓
SUPPRESSED
```

or:

```text
APPROVED
   ↓
CANCELLED
```

or:

```text
DELIVERY
   ↓
FAILED
```

---

# 243. INTERVENTION STATE RULE

State transitions must be explicit and validated.

---

# 244. INTERVENTION HISTORY MUST PRESERVE FAILURE

If an intervention failed technically:

```text
do not erase it
```

Record:

```text
FAILED
```

and why.

---

# 245. INTERVENTION OUTCOME EVENT

Meaningful intervention outcomes should generate events.

Examples:

```text
INTERVENTION_DELIVERED
INTERVENTION_ACCEPTED
INTERVENTION_DISMISSED
INTERVENTION_OVERRIDDEN
INTERVENTION_FAILED
```

---

# 246. INTERVENTION LEARNING FROM EVENTS

Pattern processing may consume these events later.

---

# 247. INTERVENTION DATA PROVENANCE

Every intervention should preserve:

```text
source
trigger
decision
target
```

where appropriate.

---

# 248. INTERVENTION SOURCE

Potential:

```text
RULE
AI
SCHEDULE
USER
PATTERN
SYSTEM
```

---

# 249. AI-SOURCED INTERVENTION

An AI recommendation should remain identifiable as AI-derived.

---

# 250. RULE-SOURCED INTERVENTION

Deterministic rules should remain distinguishable.

This enables evaluation:

```text
AI vs rule effectiveness
```

if useful.

---

# 251. USER-REQUESTED INTERVENTION

A user may explicitly say:

> "Remind me in 30 minutes."

This is a high-authority user-requested intervention.

---

# 252. USER-REQUESTED ACTION VS AUTONOMOUS ACTION

User explicitly requesting an action is different from IronMind independently deciding to take it.

---

# 253. USER REQUEST SHOULD STILL BE VALIDATED

Check:

```text
target
time
permissions
technical capability
```

---

# 254. USER REQUEST SHOULD NOT REQUIRE AI

Simple explicit requests should be handled deterministically where possible.

---

# 255. INTERVENTION EXPLANATION FOR USER REQUEST

A user-requested reminder does not need a complex AI explanation.

---

# 256. SYSTEM-INITIATED REFLECTION

Nightly reflection can be system-initiated under configured policy.

---

# 257. REFLECTION FREQUENCY

Do not turn reflection into constant interruptions.

---

# 258. DAILY REFLECTION PRINCIPLE

The primary nightly reflection should help:

```text
close the day
+
capture missing information
+
update model
```

---

# 259. REFLECTION SKIP

The user should be able to skip where appropriate.

---

# 260. SKIP IS NOT FAILURE

Skipping reflection is not automatically a negative behavioral signal.

It may be useful evidence only in aggregate/context.

---

# 261. INTERVENTION AND GOAL IMPORTANCE

Higher importance can influence intervention priority.

But importance alone does not justify constant intervention.

---

# 262. USER PRIORITY

The user's explicit priorities should inform intervention selection.

---

# 263. ARTIFICIAL IMPORTANCE ESCALATION

Do not make low-priority tasks appear critical merely to trigger action.

---

# 264. DEADLINE AWARENESS

Actual deadlines may increase urgency.

---

# 265. DEADLINE VS SELF-TARGET

Distinguish:

```text
external deadline
```

from:

```text
flexible user target
```

when appropriate.

---

# 266. INTERVENTION AND TIME

Time urgency must reflect actual consequence.

---

# 267. NO FAKE COUNTDOWNS

Do not use dramatic countdowns for ordinary tasks.

---

# 268. USER CONTEXT

Interventions should account for:

```text
working
studying
sleeping
driving
meeting
already acting
```

where appropriate and available.

---

# 269. CONTEXT CONFIDENCE

If the system is unsure of context, avoid high-invasiveness intervention.

---

# 270. INTERVENTION SAFETY LEVEL

Conceptually:

```text
LOW RISK
MEDIUM RISK
HIGH RISK
```

Higher-risk interventions require stronger evidence/authority.

---

# 271. HIGH-RISK INTERVENTION EXAMPLE

Automatic application restriction.

Requires:

```text
clear context
+
explicit autonomy
+
valid permission
+
valid target
+
policy eligibility
```

---

# 272. LOW-RISK INTERVENTION EXAMPLE

In-app suggestion.

May require less evidence.

---

# 273. INTERVENTION PROPORTIONALITY

Match intervention strength to the problem.

---

# 274. USER EXPERIENCE PRINCIPLE

An intervention should feel like:

> "That was useful."

not:

> "Why is this app bothering me?"

---

# 275. INTERVENTION TRUST FAILURE

Repeated inaccurate interventions damage trust.

Therefore false positives should be treated seriously.

---

# 276. FALSE POSITIVE

Example:

```text
User opens YouTube for study.
IronMind treats it as distraction.
```

This is a false-positive intervention opportunity.

The system should learn from correction/outcome.

---

# 277. FALSE NEGATIVE

Example:

```text
User is repeatedly distracted during a protected session.
IronMind does nothing.
```

This may also be useful evaluation data.

---

# 278. INTERVENTION QUALITY EVALUATION

Mature evaluation should measure:

```text
true usefulness
false positives
false negatives
user annoyance
goal progress
```

---

# 279. USER ANNOYANCE

Explicit signals:

```text
"This is annoying."
"Stop."
"Don't send these."
```

must carry strong weight.

---

# 280. ANNOYANCE SHOULD NOT BE IGNORED

The system should adapt intervention frequency/style accordingly.

---

# 281. USER TRUST RECOVERY

If IronMind makes repeated poor interventions, the system should become less invasive rather than more aggressive.

---

# 282. SAFE DEGRADATION

When intervention confidence drops:

```text
lower invasiveness
+
more asking
+
more silence
```

may be appropriate.

---

# 283. INTERVENTION LEARNING LIMIT

Learning should optimize within policy.

It must not:

```text
increase autonomy
```

automatically.

---

# 284. INTERVENTION POLICY VERSIONING

Intervention rules should be versioned.

Example:

```text
InterventionRules v1
```

---

# 285. DECISION POLICY VERSION

Each significant autonomous intervention should be traceable to the policy version used.

---

# 286. PROMPT VERSION

If AI generated the recommendation, the relevant AI prompt/model version may also be recorded.

---

# 287. INTERVENTION REPRODUCIBILITY

For important behavior, retain enough structured data to understand:

```text
why this intervention happened
```

later.

---

# 288. INTERVENTION DEBUGGING

Debug in this order:

```text
Trigger
↓
Context
↓
Candidate
↓
Autonomy
↓
Policy
↓
Cooldown
↓
Deduplication
↓
Delivery
↓
Outcome
```

---

# 289. DO NOT DEBUG ONLY FROM MESSAGE TEXT

The message is the final output of a larger system.

---

# 290. INTERVENTION TEST REQUIREMENTS

Every intervention type must have tests for:

```text
valid trigger
invalid trigger
autonomy OFF
SUGGEST_ONLY
ASK_BEFORE_ACTION
FULL_AUTO
cooldown
duplicate
override
target completion
stale context
failure
```

---

# 291. REMIND TEST

Verify:

```text
scheduled commitment
→ reminder

already started
→ no reminder

completed
→ no reminder
```

---

# 292. REDIRECT TEST

Verify:

```text
active commitment
+
distraction
→ redirect
```

and:

```text
no active commitment
→ no unnecessary redirect
```

---

# 293. PROTECT TEST

Verify:

```text
eligible context
+
FULL_AUTO
+
permission
→ protection
```

and:

```text
permission missing
→ no false success
```

---

# 294. BREAK_DOWN TEST

Verify:

```text
ambiguous/large task
→ smaller actionable recommendation
```

---

# 295. REASSURE TEST

Verify that generated copy does not contain:

```text
shame
false certainty
manipulation
```

---

# 296. CHALLENGE TEST

Verify challenge requires meaningful evidence.

---

# 297. ASK TEST

Verify:

```text
important uncertainty
→ question

low-value uncertainty
→ no unnecessary question
```

---

# 298. RECOVER TEST

Verify:

```text
missed commitment
→ recovery path
```

---

# 299. RESCHEDULE TEST

Verify:

```text
eligible failure/context change
→ reschedule

repeated failures
→ stop blind rescheduling
```

---

# 300. REFLECT TEST

Verify:

```text
nightly review
→ reflection request
```

---

# 301. CELEBRATE TEST

Verify celebration reflects actual evidence.

---

# 302. SILENCE TEST

Verify:

```text
already completed
→ STAY_SILENT

recent duplicate
→ STAY_SILENT

context unclear + high impact
→ STAY_SILENT
```

---

# 303. COOLDOWN TEST

Verify equivalent interventions are suppressed within cooldown.

---

# 304. DEDUPLICATION TEST

Verify semantically duplicate interventions are not repeatedly delivered.

---

# 305. OVERRIDE TEST

Verify user override suppresses inappropriate immediate repetition.

---

# 306. STALE CANDIDATE TEST

Verify:

```text
candidate created
↓
target completed
↓
candidate execution
```

results in:

```text
CANCELLED / SUPPRESSED
```

not delivery.

---

# 307. TECHNICAL FAILURE TEST

Verify:

```text
delivery failure
```

does not become:

```text
behavioral rejection
```

---

# 308. INTERVENTION LEARNING TEST

Verify successful/failed outcomes can update the appropriate learning pipeline.

---

# 309. NO LEARNING FROM TECHNICAL FAILURE

Do not incorrectly train behavioral models from infrastructure failures.

---

# 310. USER FEEDBACK TEST

Verify explicit negative feedback affects future intervention behavior where policy allows.

---

# 311. INTERVENTION LOGGING TEST

Verify expected lifecycle logs exist:

```text
PROPOSED
TRIGGERED
DELIVERED
OUTCOME
```

where applicable.

---

# 312. INTERVENTION SECURITY TEST

Verify:

```text
AI cannot bypass autonomy
Worker cannot bypass policy
Unauthorized user cannot trigger interventions for another user
```

---

# 313. INTERVENTION PRIVACY TEST

Verify lifecycle logs do not contain unnecessary:

```text
reflection text
voice transcript
location data
private content
```

---

# 314. PERFORMANCE

Intervention evaluation must remain lightweight enough for normal device operation.

Avoid expensive AI calls for every minor event.

---

# 315. EVENT THROTTLING

High-frequency observations may require aggregation before intervention analysis.

---

# 316. EXAMPLE

If an app generates many usage events:

```text
APP_OPENED
APP_CLOSED
APP_OPENED
APP_CLOSED
...
```

do not invoke AI for every event.

---

# 317. AGGREGATED INTERVENTION TRIGGERS

Use aggregated evidence where appropriate:

```text
Repeated distracting activity during active session.
```

rather than:

```text
One app launch.
```

---

# 318. INTERVENTION LATENCY

High-value real-time interventions should not be delayed unnecessarily.

---

# 319. LOCAL DECISION FOR REAL-TIME PROTECTION

When appropriate, local rules should handle immediate protection.

---

# 320. AI FOR HIGHER-LEVEL REASONING

AI is better suited to:

```text
why this keeps happening
which intervention may help
how to break down task
```

than every low-level app event.

---

# 321. INTERVENTION ARCHITECTURAL LAYERS

```text
Event / Observation
        ↓
Trigger Detection
        ↓
Context Builder
        ↓
Candidate Generation
        ↓
AI / Rule Recommendation
        ↓
Autonomy
        ↓
Intervention Policy
        ↓
Delivery
        ↓
Outcome
```

---

# 322. CANDIDATE GENERATION

Candidate generation can come from:

```text
Rules
AI
Schedule
User
Pattern
```

---

# 323. POLICY FILTERING

All candidates must pass intervention policy before delivery.

---

# 324. AI CANDIDATE DOES NOT GUARANTEE DELIVERY

A candidate may be discarded because:

```text
cooldown
duplicate
wrong timing
autonomy
user settings
stale context
```

---

# 325. INTERVENTION STATE IS PERSISTENT WHEN NECESSARY

Important intervention state must survive:

```text
process death
app restart
device restart
```

where appropriate.

---

# 326. BACKGROUND INTERVENTION RECOVERY

After restart, the system should not blindly execute expired interventions.

---

# 327. INTERVENTION EXPIRATION CHECK

Before executing a queued intervention:

```text
revalidate
```

---

# 328. INTERVENTION QUEUE

A future system may use a queue for pending interventions.

The queue must support:

```text
priority
expiration
deduplication
cancellation
retry
```

---

# 329. QUEUE SAFETY

A queued intervention does not automatically remain valid forever.

---

# 330. USER CONTROL

The user should eventually be able to see and configure major intervention behavior.

---

# 331. USER SETTINGS

Potential intervention settings:

```text
proactive notifications
automatic protection
motivational messages
reflection reminders
goal resurfacing
quiet hours
```

---

# 332. USER SHOULD CONTROL STYLE

Where appropriate:

```text
short
direct
detailed
voice
notification
in-app
```

---

# 333. STYLE IS NOT AUTHORITY

Changing communication style does not change autonomy level.

---

# 334. INTERVENTION TYPE DISABLE

A user may eventually disable selected intervention types.

Example:

```text
CHALLENGE = OFF
```

while:

```text
REMIND = FULL_AUTO
```

---

# 335. DISABLED INTERVENTION TYPE

The system must respect explicit disablement.

---

# 336. INTERVENTION CATEGORY DISABLEMENT

A broader setting may suppress:

```text
all proactive notifications
```

which should override individual notification intervention attempts.

---

# 337. USER-INITIATED VS SYSTEM-INITIATED

User-requested support should remain available even when some proactive automation is disabled, provided the request itself is explicitly made and technically supported.

---

# 338. EXAMPLE

User manually asks:

> "Remind me in 30 minutes."

Even if:

```text
Proactive Notifications = OFF
```

this explicit user request may still be allowed depending on product settings because it is user-initiated rather than autonomous.

---

# 339. POLICY MUST DISTINGUISH THIS

```text
USER REQUESTED
vs.
SYSTEM INITIATED
```

---

# 340. INTERVENTION AUDIENCE

IronMind's intervention system is designed primarily for the current user.

Do not introduce third-party recipients without separate product policy.

---

# 341. FUTURE SOCIAL ACCOUNTABILITY

If external accountability is ever introduced, it must receive separate:

```text
privacy
authorization
autonomy
recipient
```

policies.

---

# 342. INTERVENTION AND EXTERNAL COMMUNICATION

Sending messages to another person is materially higher impact than notifying the user.

It must not inherit ordinary notification autonomy.

---

# 343. EXTERNAL INTERVENTION DEFAULT

Absent an explicit policy:

```text
do not automatically send external messages
```

---

# 344. INTERVENTION ETHICS

Interventions must respect:

```text
user agency
privacy
attention
truthfulness
proportionality
```

---

# 345. NO MANIPULATIVE ESCALATION

Do not escalate from:

```text
reminder
```

to:

```text
shaming message
```

because the user ignored it.

---

# 346. NO PUNISHMENT ESCALATION

Do not increase restrictions simply because a user repeatedly misses commitments unless explicit user-configured protection policy supports such behavior.

---

# 347. NO GAMIFIED PUNISHMENT

Do not remove:

```text
points
streaks
levels
```

because core product should not depend on those mechanics.

---

# 348. INTERVENTION AND FAILURE

The system should ask:

```text
What happened?
```

not:

```text
How do we punish this?
```

---

# 349. INTERVENTION AND POSITIVE LEARNING

When successful:

```text
acknowledge
record
learn
continue
```

---

# 350. INTERVENTION AND NEGATIVE LEARNING

When unsuccessful:

```text
understand
adjust
avoid repetition
```

---

# 351. INTERVENTION AND USER AUTONOMY

The system must support:

```text
manual override
disable
pause
settings
```

where appropriate.

---

# 352. GLOBAL AUTOMATION PAUSE

A future global:

```text
PAUSE AUTOMATION
```

should suppress supported autonomous interventions.

---

# 353. GLOBAL PAUSE DOES NOT DELETE HISTORY

It pauses behavior; it does not erase the past.

---

# 354. INTERVENTION AND RESTART

After automation pause/restart:

```text
revalidate current state
```

before resuming queued actions.

---

# 355. INTERVENTION AND POLICY CHANGE

When policy changes, queued interventions created under an old policy may need revalidation.

---

# 356. POLICY VERSION REVALIDATION

Where necessary:

```text
old candidate
+
new policy
→
re-evaluate
```

---

# 357. INTERVENTION AND AI MODEL CHANGE

A model update does not automatically replay old intervention candidates.

---

# 358. OLD CANDIDATES

Stale candidates should expire or be revalidated.

---

# 359. INTERVENTION OBSERVABILITY

A mature development dashboard should expose:

```text
recent triggers
candidates
decisions
interventions
outcomes
suppressed actions
cooldowns
```

---

# 360. SUPPRESSED INTERVENTIONS

It may be useful to record why a candidate was suppressed:

```text
COOLDOWN
DUPLICATE
STALE
USER_SETTING
AUTONOMY
PERMISSION
NO_VALUE
SILENCE
```

---

# 361. SUPPRESSION SHOULD BE OBSERVABLE

This is especially important during debugging.

---

# 362. SUPPRESSION DOES NOT EQUAL FAILURE

A suppressed intervention may represent correct behavior.

Example:

```text
Task completed
→ reminder suppressed.
```

---

# 363. STAY SILENT DOES NOT EQUAL SYSTEM FAILURE

Silence may be the correct decision.

---

# 364. INTERVENTION QUALITY REVIEW

Periodic product review should examine:

```text
How often did IronMind intervene?
How often were interventions useful?
How often were they ignored?
How often were they overridden?
How often were they technically unsuccessful?
```

---

# 365. PRODUCT SUCCESS METRIC

The goal is not:

```text
more interventions
```

The goal is:

```text
better real-world action
```

---

# 366. INTERVENTION SUCCESS

A strong intervention may produce:

```text
task started
task completed
better plan
successful recovery
clearer understanding
```

---

# 367. INTERVENTION NON-SUCCESS

Not every intervention must produce immediate completion.

It may still provide useful information.

Example:

```text
ASK
→ user explains barrier
→ better model
```

This can be a successful information intervention even without immediate task completion.

---

# 368. INFORMATION VALUE

Interventions can create value by improving IronMind's understanding.

---

# 369. INFORMATION INTERVENTIONS

Examples:

```text
ASK:
"Is this goal still important?"

REFLECT:
"What made today difficult?"
```

These may improve future decisions.

---

# 370. INTERVENTION OUTCOME TAXONOMY

A mature system may distinguish:

```text
ACTION_SUCCESS
ACTION_PARTIAL
INFORMATION_GAIN
NO_EFFECT
USER_REJECTION
TECHNICAL_FAILURE
STALE
SUPPRESSED
```

---

# 371. INTERVENTION EFFECTIVENESS SHOULD CONSIDER INFORMATION GAIN

An intervention may be useful even when it did not directly produce task completion.

---

# 372. INTERVENTION SYSTEM MUST REMAIN PRODUCT-ALIGNED

All intervention types ultimately serve:

```text
Understand
Plan
Commit
Protect
Act
Result
Reflect
Learn
Adapt
```

---

# 373. INTERVENTION FEATURE TEST

For every new intervention capability:

Ask:

```text
What real problem does it solve?
Why this intervention type?
Why this timing?
What evidence supports it?
What happens if it fails?
Can the user override it?
What happens after repeated failure?
```

---

# 374. NO INTERVENTION WITHOUT A PURPOSE

Every intervention candidate must have a reason.

---

# 375. NO INTERVENTION FROM WEAK NOISE

A single low-confidence observation should not trigger intrusive intervention.

---

# 376. NO HIGH-IMPACT INTERVENTION FROM WEAK INFERENCE

High-invasiveness actions require stronger evidence/authority.

---

# 377. INTERVENTION SOURCE HIERARCHY

Potentially:

```text
USER REQUEST
>
EXPLICIT POLICY
>
RELIABLE CURRENT STATE
>
STRONG REPEATED PATTERN
>
AI RECOMMENDATION
>
WEAK INFERENCE
```

---

# 378. USER REQUEST VS POLICY

Even user requests must remain within platform/system safety constraints.

---

# 379. AI RECOMMENDATION VS POLICY

AI cannot override policy.

---

# 380. PATTERN VS USER REQUEST

A pattern cannot override explicit current user intent.

---

# 381. INTERVENTION SELECTION SUMMARY

```text
What matters?
↓
What is happening?
↓
What is getting in the way?
↓
What could help?
↓
Is intervention worth the interruption?
↓
Which intervention is least invasive while useful?
↓
Is IronMind allowed to execute it?
↓
Act or stay silent.
```

---

# 382. FINAL INTERVENTION DECISION MATRIX

| Situation                                                  | Likely Intervention |
| ---------------------------------------------------------- | ------------------- |
| User may simply forget                                     | REMIND              |
| User is distracted during an active commitment             | REDIRECT            |
| Strong contextual distraction risk during protected action | PROTECT             |
| Task appears too large/ambiguous                           | BREAK_DOWN          |
| User needs practical pressure reduction                    | REASSURE            |
| Repeated pattern suggests current plan is failing          | CHALLENGE           |
| Important information is missing                           | ASK                 |
| Commitment was missed and recovery is possible             | RECOVER             |
| Timing no longer fits                                      | RESCHEDULE          |
| System needs user reflection/learning                      | REFLECT             |
| Meaningful progress occurred                               | CELEBRATE           |
| Intervention adds little value                             | STAY_SILENT         |

This table is a guide, not a deterministic substitute for policy.

---

# 383. FINAL INTERVENTION PIPELINE

```text
                    TRIGGER
                       ↓
                 BUILD CONTEXT
                       ↓
                FIND RELEVANT DATA
                       ↓
             GENERATE CANDIDATES
                       ↓
               AI / RULE REASONING
                       ↓
                AUTONOMY CHECK
                       ↓
             POLICY VALIDATION
                       ↓
              PRIORITY EVALUATION
                       ↓
                 COOLDOWN CHECK
                       ↓
               DUPLICATE CHECK
                       ↓
               STALE-STATE CHECK
                       ↓
          ┌────────────┴────────────┐
          │                         │
          ▼                         ▼
       INTERVENE                 SILENCE
          │
          ▼
       DELIVERY
          │
          ▼
      USER RESPONSE
          │
          ▼
        OUTCOME
          │
          ▼
        LEARNING
          │
          └──────────────→ FUTURE DECISIONS
```

---

# 384. FINAL INTERVENTION STATE MODEL

```text
CANDIDATE
   ↓
PROPOSED
   ↓
APPROVED
   ↓
TRIGGERED
   ↓
DELIVERED
   ↓
RESPONSE
   ↓
OUTCOME
```

Alternative exits:

```text
SUPPRESSED
CANCELLED
EXPIRED
FAILED
```

---

# 385. FINAL INTERVENTION LEARNING MODEL

```text
INTERVENTION
      ↓
USER RESPONSE
      ↓
BEHAVIORAL OUTCOME
      ↓
CONTEXT
      ↓
EVIDENCE
      ↓
INTERVENTION EFFECTIVENESS
      ↓
PATTERN / MEMORY UPDATE
      ↓
BETTER FUTURE INTERVENTION
```

---

# 386. FINAL INTERVENTION GOLDEN RULE

> **Intervene only when doing so is more useful than remaining silent.**

---

# 387. FINAL INTERVENTION SECOND GOLDEN RULE

> **Use the smallest intervention that has a reasonable chance of helping.**

---

# 388. FINAL INTERVENTION THIRD GOLDEN RULE

> **Never confuse repeated intervention with intelligent intervention.**

---

# 389. FINAL INTERVENTION FOURTH GOLDEN RULE

> **Challenge behavior, never attack identity.**

---

# 390. FINAL INTERVENTION FIFTH GOLDEN RULE

> **A user's override is information, not disobedience.**

---

# 391. FINAL INTERVENTION SIXTH GOLDEN RULE

> **An intervention is successful when it improves action, understanding, or future decisions — not merely when it is delivered.**

---

# 392. FINAL INTERVENTION SEVENTH GOLDEN RULE

> **When IronMind does not know enough to intervene responsibly, it should ask, use a lower-impact action, or stay silent.**

---

# 393. FINAL INTERVENTION ARCHITECTURAL RULE

> **AI recommends. The Decision Engine authorizes. The Intervention Engine constructs the intervention. The Execution Layer delivers it. The Outcome feeds learning.**

---

# 394. FINAL INTERVENTION PRODUCT RULE

> **IronMind should never become another source of distraction. It should earn the user's attention every time it asks for it.**

---

# 395. FINAL INTERVENTION NORTH STAR

```text
RIGHT PROBLEM
      +
RIGHT CONTEXT
      +
RIGHT INTERVENTION
      +
RIGHT TIME
      +
RIGHT AUTHORITY
      +
LOWEST NECESSARY INTRUSIVENESS
      ↓
BETTER ACTION
```

---

# 396. FINAL SYSTEM PRINCIPLE

The intervention system exists to move the user toward:

```text
INTENTION
   ↓
ACTION
   ↓
PROGRESS
```

while learning from:

```text
SUCCESS
+
FAILURE
+
USER FEEDBACK
```

and becoming progressively more useful without becoming progressively more intrusive.

---

# END OF INTERVENTION RULES

````
