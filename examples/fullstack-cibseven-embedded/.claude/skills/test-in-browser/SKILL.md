---
name: test-in-browser
argument-hint: "[issue number or flow, e.g. #18 or 'withdraw while the contract waits']"
description: "Check a user flow in the locally running app with the Playwright MCP browser and record evidence: verify the stack is up, act as customer or back office, take a screenshot per step into .context/, watch the console, and give a PASS/FAIL/BLOCKED verdict per acceptance criterion. Use when the user asks to test or check something in the browser, to verify a UI change, or as the browser step of implement-issue. Exploratory evidence only; lasting coverage belongs in a Playwright spec under frontend/e2e."
allowed-tools: Read, Write, Glob, Bash(docker compose *), Bash(podman compose *), Bash(curl *), Bash(mkdir *), Bash(npx --yes @usebruno/cli@4.0.0 *), Bash(npx operate inspect *), Bash(npx operate status), mcp__playwright
---

# Skill: test-in-browser

Two Playwright tools, two jobs: the **Playwright MCP** (`.mcp.json`) explores a flow and collects
evidence now; the **specs** in `frontend/e2e/` (`npm --prefix frontend run e2e`) guard it from then on.
This skill is the first. The UI is the source of truth, the console is the error channel, the
database is not part of the check.

## 1. Check the prerequisites, don't assume them

```bash
docker compose -f stack/docker-compose.yml ps             # Postgres running
curl -fs localhost:8080/actuator/health                   # {"status":"UP"}
curl -fs -o /dev/null -w "%{http_code}" localhost:5173    # 200
```

If one is missing, say which and give the command from `AGENTS.md` (Development Setup); start it only
if the user agrees. A missing prerequisite makes the run **BLOCKED**, not FAILED.

## 2. Plan the run

From the issue or the request, list the acceptance criteria and the steps that show each one. Pick
the persona per step: the **customer** (application list `/`, new application `/antraege/neu`, detail
page `/antraege/<id>`) or the **back office** (inbox `/aufgaben`). There is no login. A case in a
later state is reached through the UI; what the UI cannot drive (the 14-day timers, the form-only
*Clarify return* task) is driven by the matching scenario folder in `bruno/`, e.g.:

```bash
cd bruno && npx --yes @usebruno/cli@4.0.0 run 01-happy-path --env local
```

## 3. Run it

- Open <http://localhost:5173> in the MCP browser and act like a user: click, type, read.
- After each step take a screenshot to `.context/<issue-or-flow>/NN-<what>.png` and read the console
  messages. Every PASS needs something visible in a screenshot.
- The engine works asynchronously: the detail page polls every 2 s. Wait for the visible change, never
  for a fixed time.
- If a screenshot hangs (fonts), open a fresh tab and retry once.
- If the page does not change, ask the engine where the case waits: `npx operate inspect
  --business-key <applicationId>` (wait states, incidents with their root cause) or `npx operate
  status`. That is diagnosis for the report, not evidence for a PASS.
- **Never fix code during the run.** A defect is a finding; fixing it is a separate step the user
  decides on.

## 4. Report

| # | Acceptance criterion | Steps | Verdict (PASS / FAIL / BLOCKED) | Evidence |
|---|---|---|---|---|

Then console errors, unexpected behaviour outside the criteria, and which flows deserve a lasting
spec in `frontend/e2e/` (suggest the file and its steps; write it only when asked).
