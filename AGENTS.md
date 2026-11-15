# Agent Instructions

These instructions apply to the entire repository.

## Source of Truth

- Read `doc/plan/plan.md` before implementing Schafkopf product or strategy work. Treat it as the reviewed backlog and update its status, evidence, and remaining work when a plan item changes.
- Preserve the user's stated rules and examples. If the plan and an older code comment disagree, verify the rule against the recorded user decisions before changing behavior.
- If a requirement is ambiguous, incomplete, or conflicts with another requirement, ask focused clarifying questions before editing. Do not guess game rules, ranking, tariff semantics, visual-asset rights, or acceptance criteria.
- Do not claim a strategy, model, or visual design is complete based only on an aggregate score, a successful build, or an approximate screenshot.

## Worktree Safety

- Inspect `git status --short` before editing. The repository may contain user-owned modified and untracked files.
- Never discard, reset, restore, or overwrite existing changes unless the user explicitly asks.
- Keep changes limited to the approved plan item. Do not silently add game variants, rules, dependencies, permissions, network services, or data collection.

## Game Rules and AI

- Keep hard legality and scoring rules deterministic and separate from strategic preferences. Bots may select only legal actions.
- Model each game type, player role, seat, and play phase explicitly. Do not assume one opening or bidding heuristic applies to all contracts.
- Distinguish Ober from Unter and preserve the contract-specific trump order. Add regression tests for the final chosen action, not only intermediate feature values.
- Use hidden-hand estimates only from distributions consistent with the cards and void information visible at that decision. Include the configured tariff when estimating expected return.
- Treat genetic optimization as tuning of existing features, not as a source of new rules. Do not activate a candidate without scenario tests and independent, role-specific validation; report uncertainty and weak roles.
- Preserve the bidding sequence: `Spiele!` / `Auch!` / `Weiter` first, then a legal contract choice. A higher call must obey `GameBiddingRules` and the user-approved ranking.
- Keep Klopfen, Stock, Kontra, Retour, and the total multiplier distinguishable in state, persistence, display, and tests.

## Android and Visual Assets

- The game must remain fully offline. Do not add internet permissions, remote APIs, analytics, or runtime asset downloads.
- Follow the Bavarian card-art decision in `doc/plan/plan.md`. Do not describe hand-drawn placeholders as authentic card scans; use complete, rights-cleared artwork or clearly label original artwork as such.
- Test Android UI changes on the connected-device flow when they affect card readability, bidding, deal/cut, animations, or score display. Preserve saved game state and verify interruption/restart behavior for new state transitions.

## Validation

- Core Java tests: `mvn -q test` from the repository root.
- Android tests and APK build: `cd android-app && gradle testDebugUnitTest assembleDebug`.
- Run the narrowest relevant tests first, then the required module build. Report any test or device check that could not be run.
- Update `doc/plan/plan.md` only with evidence: implementation status, tests, device checks, limitations, and decisions still needed. Do not mark an item complete merely because code was written.

## Compatibility

- Core Java sources are also compiled by the Android app with Java 17. Keep shared code compatible with Java 17 even though the Maven development environment may use a newer JDK.