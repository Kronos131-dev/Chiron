---
name: [skill-name]
description: [What the skill does, in the third person, then when to use it, loaded with the concrete file, class and command names a real request would contain. Max 1024 characters. Closes with: Do not use for [neighbouring task] (see [other-skill]).]
---

# [Verb phrase title]

[Zero to four lines naming the one thing that makes this task expensive or easy to get wrong — the
cost of guessing, a silent failure mode, a tool that does not do what its name suggests. Omit if
there is nothing load-bearing to say.]

## Procedures

**Step 1: [Action phase]**
1. [Third-person imperative instruction, e.g. "Read chiron-back/pom.xml and confirm the version."]
2. [Pointer loaded just in time, naming the exact file, e.g. "Read references/failure-modes.md to
   match the symptom."]

**Step 2: [Action phase]**
1. [Explicit decision tree, e.g. "If the route is new, apply the add-angular-feature skill.
   Otherwise continue at Step 3."]
2. [Deterministic action, e.g. "Execute scripts/next-migration.sh to compute the number."]

**Step N: Audit before hand-off**
1. Confirm every item in `references/checklist.md`.

## Error Handling

* If [concrete failure state], [recovery step].
* If [symptom the tooling actually produces], [the skill or command that resolves it].
