# Dependabot Optimization Notes — awdb-java

## Context
- Project: XenoAmess/awdb-java (Java 8 / Maven, single-module library)
- Default branch: master
- CI: `.github/workflows/build.yml`, matrix JDK 8/11/25 (temurin), checks `build (8)`, `build (11)`, `build (25)`
- Starting state: bare dependabot.yml (weekly, no prefix/labels/limits), no auto-merge workflow, `allow_auto_merge: false`, no branch protection, no secrets

## What was done
- `.github/workflows/auto-merge.yml`: approve + `gh pr merge --auto --rebase` for semver-patch/minor and github-actions semver-major; maven majors left for humans
- Repo: `allow_auto_merge=true`; master protection = required checks `build (8)/(11)/(25)` + `strict: true` + `required_linear_history`
- `MYTOKEN` = user's `gho_` OAuth token (repo+workflow scope, admin) stored in the **dependabot** secret namespace (Pitfall 16)
- `dependabot.yml` per skill Step 6: weekly monday 04:00 Asia/Shanghai, PR limits 10/5, `build(deps)`/`build(deps-dev)`/`ci` prefixes, labels `dependencies`/`java`/`github-actions` (created beforehand, Pitfall 17), **no `groups:`** (Pitfall 13)
- Java 8 `ignore` pins: logback-classic major+minor (1.4+ needs Java 11), junit-jupiter major (JUnit 6 needs Java 17)
- Post-merge fix: `skip-commit-verification: true` added after fetch-metadata v3 auto-merged itself (Pitfall 19 variant, see below)

## What worked first time
- Branch protection + `allow_auto_merge` + secret in one pass; auto-merge merged #19 (actions major) within ~1 min of creation
- The `gho_` OAuth-as-MYTOKEN shortcut worked end-to-end, including on the workflow-touching fetch-metadata PR

## What was tricky / required iteration
- PR #17 (surefire patch) was created by dependabot's own schedule **5 minutes before** the auto-merge.yml push, so its `pull_request.opened` saw no workflow → zero auto-merge runs. Fixed with `@dependabot rebase` (Pitfall 14 in the wild)
- `strict: true` BEHIND chain: #19 merging made #20 BEHIND; #20 merging made #17 BEHIND. Each needed one rebase nudge (Pitfall 7)
- Earlier in the session (before the skill was consulted) a `groups:` block was added and a grouped PR (logback 1.5 + compiler + surefire) failed CI with the logback bump un-attributable until log inspection — a live demonstration of Pitfall 13. History was reset by the user and redone per the skill

## Key findings
- **Pitfall 19 variant (new)**: with "actions majors auto-merge" policy, dependabot's own `fetch-metadata` 2→3 PR auto-merges itself. The bump PR passes because it runs the *old* v2 workflow from its base; the *next* dependabot PR would hit v3's signature verification. Fix immediately after the v3 PR merges: `skip-commit-verification: true`, or ignore `dependabot/fetch-metadata` major bumps to stay on v2
- Java 8 projects must pin Java-baseline-breaking deps via `ignore` (logback ≥1.4 = Java 11, JUnit ≥6 = Java 17); dependabot does not know your `maven.compiler.release`

## Verification results
- 19 checks: all pass. (6) maven-major-not-merged verified by policy + ignore pins (no live maven-major PR this cycle); all others observed live: #19 actions-major auto-merged, #17 patch and #20 minor auto-merged after rebase, history linear, open PR list empty
