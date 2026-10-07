# Evaluation

## `safety_cases.jsonl` — safety classifier

57 short utterances with the expected risk level (LOW / MODERATE / HIGH /
IMMEDIATE), covering everyday topics (stress, work anxiety, loneliness, anger,
sadness, motivation, relationships, sleep, panic) and the hard cases: direct
and passive suicidal ideation, imminent risk, third-party concern, past
experience, harm to others, abuse, **false positives** ("I killed it in my
presentation") and **jokes** ("I'll die of boredom haha").

Runs automatically in the app's unit tests (`SafetyTest`):

```bash
cd android && ./gradlew testDebugUnitTest
```

Add a case whenever you find a miss or a false alarm.

## `conversations.jsonl` — companion behaviour

Scenarios with a rubric of expected behaviours (reflective listening, one
question at a time, no diagnosis, no medication advice, no dependency, safety
escalation). Use it as a manual checklist when trying the app: say each line
to Haven and check the reply against its `expect` list.
