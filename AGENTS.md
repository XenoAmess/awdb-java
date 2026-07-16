# AGENTS.md

## Project
- Fork of https://gitee.com/aiwen_home/awdb-java (see README.md); respect the original copyright & LICENSE when modifying or redistributing.
- Single-module Maven library: parser for AWDB IP-geolocation files. Public entrypoint is `io.github.aiwen.ipplus360.AwdbReader`; `io.github.aiwen.example.AwdbApplicationTest` is a demo `main`.

## Build & verify
- Java 8 target. Build: `mvn package` (shade plugin produces a fat jar). Compile check: `mvn compile`.
- No tests, no CI, no lint/typecheck config exist in this repo — don't invent commands for them.
- Gotcha: `pom.xml` shade config sets mainClass to `io.github.aiwen.example.AwdbCommandRunner`, which does not exist in the source tree; the packaged jar's manifest main class is broken until that is fixed or the class is added.

## Conventions
- Package root `io.github.aiwen.*` (groupId `io.github.aiwen`) comes from upstream; keep it unless deliberately re-branding the fork.
- README documents usage in both Chinese and English; keep doc edits bilingual.
- Git remote is named `origini` (not `origin`) and points to github.com/XenoAmess/awdb-java.
- After every change, commit and push immediately without asking for confirmation.
