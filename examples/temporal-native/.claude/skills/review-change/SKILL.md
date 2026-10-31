---
name: review-change
argument-hint: "[PR number | branch | commit sha | empty for the working tree against origin/main]"
description: "Review a change in this repo for business correctness, architecture, security and tests, and report findings ranked by severity, each with file:line and a concrete failure scenario. Checks the repo-specific counterparts a change needs (migration, contract, regenerated process API, process tests, Bruno, the other variant) and delegates the model check to the agent review-process. Use when the user asks to review a PR, a branch or the current diff. Read-only; posts nothing unless asked."
allowed-tools: Read, Glob, Grep, Bash(git *), Bash(gh pr view *), Bash(gh pr diff *), Bash(gh pr checks *), Bash(gh run list *), Bash(gh run view *), Bash(gh issue view *), Bash(gh api repos/*/pulls/*/comments *), Agent
---

# Skill: review-change

A review that only ticks boxes is worthless; one finding with a reproducible scenario beats ten
style notes. The machine gates (architecture rules, contract drift, mutation 80) run in CI; do not restate them, check what they cannot see. Do check that a gate
actually ran: the mutation gate runs nightly on the domain, so a pull request never
exercises it. Generic code
review is what the built-in review tooling does; this skill adds what only this repo knows.

The agents `review-process` and `review-vertical-slice` are loaded at session start. If one is not
available, read its file in `.claude/agents/` and apply it yourself.

## 1. Collect the context

- Diff: `gh pr diff <n>` (open or merged), `git diff origin/main...<branch>`, `git show <sha>` for a
  single commit. For the working tree: `git fetch`, then `git diff $(git merge-base origin/main HEAD)`
  (committed, staged and unstaged) plus `git ls-files --others --exclude-standard` (new files).
- Intent: the PR body and the linked issue (`gh pr view <n> --json closingIssuesReferences`,
  `gh issue view <n>`) with its acceptance criteria. Without an issue, the PR body is the intent.
- `gh pr checks <n>` if it is a PR; `gh run view <id> --log-failed` for a red one. Leave existing
  review comments for step 5, so they don't bias you: `gh pr view <n> --comments` for the
  conversation, `gh api repos/{owner}/{repo}/pulls/<n>/comments --paginate` for inline comments.

## 2. Read the change in full

Read every changed file, not only the hunks, plus the callers and tests of changed code. Ask of each
change: is it what the issue asks for, and nothing else? Does it hold for the process's other paths
(withdrawal at any time, the bike-unavailable error, the signature escalation, compensation of
completed steps, a 409 because the clarification task is gone)? For a move or rename, `git grep` the
old paths and packages instead: a leftover reference is the typical defect.

## 3. Check the counterparts

| The change touches | It also needs |
|---|---|
| a JPA entity | a new `V<n>__*.sql` migration; no edited migration |
| a controller method or a DTO | `@Operation(operationId)` on the method, the re-exported `openapi/openapi.json`, a Bruno request |
| a workflow | a process test per new branch, a compensation registered only after its step completed, `Workflow.getVersion` when the order of activities or timers changes |
| the credit rule | a row in `CreditRatingTest` |
| an activity or the process adapter | its unit test, a process test for the path it serves |
| a whole feature | run the agent `review-vertical-slice` |
| a service throwing what an activity maps to an `ApplicationFailure` | `noRollbackFor` on `@Transactional` |
| a user-visible REST flow | the Bruno scenario, and the scenario list in `bruno/README.md` |
| the order retry | the outage still shows as a pending activity and the application stays withdrawable |
| an accepted ADR | a reversal is a new ADR that supersedes it; no "Update" sections |

A hand-edited generated file is a blocker.


## 4. Security and robustness

Input reaching the domain unvalidated, problem details leaking internals, secrets or credentials in
code or config, new dependencies without a pinned version, process variables carrying personal data
the engine does not need.

## 5. Report

Severity is absolute, not relative to this PR:

- **Blocker:** wrong business result, data loss, broken path, security hole, red gate.
- **Major:** a missing counterpart or test that lets a regression through, or a broken repo rule that
  loses information.
- **Minor:** maintainability, naming, a weak assertion.
- **Nit:** optional.

When unsure between two levels, pick the lower one and say why. Per finding: severity, `file:line`,
what goes wrong in which scenario, a suggested fix. Then: questions for the author, what looks good,
and whether existing review comments are already addressed.

Post to GitHub only when asked, and show the text first.
