---
name: plan-review
description: "Use when asked to review doc/plan/plan.md before implementation, especially for ambiguous rules, missing acceptance criteria, sequencing, risks, and validation gaps. Review only; ask before assumptions are turned into implementation."
disable-model-invocation: true
argument-hint: "Optionally name a work package or review the next proposed step"
---

# Review the Schafkopf Plan

## Procedure

1. Read `AGENTS.md` and `doc/plan/plan.md`.
2. Check the requested work package against the user's recorded requirements and the current implementation only where needed.
3. Identify concrete blockers: ambiguous rules or ranking, missing asset/rights decision, unclear expected behavior, unsafe state changes, or missing tests/evidence.
4. If an interpretation is ambiguous, ask focused clarifying questions and stop. Do not silently choose a meaning.
5. Otherwise report whether the named slice is ready, its exact scope, relevant checks, and any residual risks.

## Constraints

- Review only. Do not edit code or the plan, run mutating commands, or create commits.
- Do not require Jira, team review, PRs, worktrees, or usage tracking.
- Treat aggregate policy metrics as insufficient without scenario and role-level validation.