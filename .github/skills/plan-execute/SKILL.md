---
name: plan-execute
description: "Use when asked to implement one approved work package or the next bounded step from doc/plan/plan.md. Ask first if requirements, rules, or acceptance criteria are ambiguous."
disable-model-invocation: true
argument-hint: "Name one work package in doc/plan/plan.md, or specify the next ready step"
---

# Execute One Plan Slice

## Procedure

1. Read the root `AGENTS.md` and `doc/plan/plan.md`.
2. Select exactly the work package or bounded step named by the user. If none is named, use a clearly identified next step in the plan. If multiple choices are plausible, ask which one to execute.
3. Before editing, inspect `git status --short` and the relevant code/tests. Preserve all existing modified and untracked files.
4. If any requirement, game rule, ranking, tariff behavior, asset source/rights, or acceptance criterion has more than one plausible interpretation, stop and ask focused questions before editing.
5. Implement only that slice. Do not expand into adjacent work packages, add unapproved variants or dependencies, or change the offline requirement.
6. Run the narrowest relevant tests first, then the required module validation from `AGENTS.md`. For visible Android changes, use the device check when available and record if it was not possible.
7. Update `doc/plan/plan.md` with what changed, actual test/device evidence, limitations, and remaining work. Mark work complete only when its acceptance criteria are supported by evidence.
8. Do not create commits, branches, worktrees, PRs, tickets, or usage logs unless the user asks.
9. Stop after the selected slice and report changed files, checks, blockers, and the next plan step.

## Constraints

- Hard legality and scoring stay deterministic; policies select only legal actions.
- Test final decisions, not only intermediate feature values.
- Keep Ober and Unter distinct and obey contract-specific ranks.
- Do not claim strategic strength from one game or aggregate fitness alone; include role-specific evidence and uncertainty.
- No destructive Git commands or overwriting pre-existing user changes.