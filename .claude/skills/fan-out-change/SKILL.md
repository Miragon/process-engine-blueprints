---
name: fan-out-change
description: Carry a change from one example of this monorepo to the other examples it applies to, in the same branch. Reads the change (current branch or a PR), finds the examples that must follow (drift.yml) and the ones it may apply to, asks which to change, applies it with engine-specific adaptation, regenerates the root files and verifies each touched example. Use when the user wants to propagate, sync or fan out a change across examples, or asks whether a change also applies to the other examples.
---

# Fan out a change across examples

Every example automates the same MiraVelo bike-leasing process. A change in one often belongs in others.
In this repository that is one branch and one pull request, not issues in other repositories.

## 1. Read the change

- Source is the current branch (`git diff origin/main...HEAD`) or a PR (`gh pr diff <n>`). With neither, stop.
- Summarise it in two sentences and classify it: `domain/process`, `architecture`, `tooling/ci`, `docs` or
  `engine-specific`. An engine-specific change (delegate style, worker wiring, engine or broker config) usually
  stays where it is: say so.

## 2. Find the targets

- **Must follow:** for every changed file, the rules in `drift.yml` that list it. Every example of such a rule
  gets the identical change, otherwise CI fails.
- **May follow:** the other examples (`examples/*/blueprint.yaml`). Check whether the touched files and concepts
  exist there (`git grep`, the same path in the other example, the other variant).
- Give each target one verdict with a one-line reason: `applies`, `adapt` (name what differs: engine API,
  `kotlin-gradle` vs `java-maven`, remote vs embedded modules) or `not-applicable`.

## 3. Confirm

Show `example | verdict | reason`, then ask with **AskUserQuestion** (`multiSelect`) which to change. Change
nothing the user did not pick. A business decision that differs per example (a gateway, a deadline, copy users
read) is the user's, never yours.

## 4. Apply

- Per picked example, change both variants the same way; their resources must stay identical
  (`identicalResources` in `blueprint.yaml`).
- Follow that example's own `AGENTS.md`; it is the authority inside the folder (generated process APIs, OpenAPI
  contract, architecture rules).

## 5. Verify

- `npm run generate` and `npm run drift` at the root.
- Per touched example and variant, the targeted check its `AGENTS.md` names (e.g.
  `cd examples/<x>/kotlin-gradle && ./gradlew build`, `cd examples/<x>/java-maven && ./mvnw verify`).
- If a file left a drift rule on purpose, move it out of the rule in `drift.yml` and say why in the summary.

## 6. Report

A table `example | done / skipped | verification` and the commands that ran. Commit or push only when asked.
