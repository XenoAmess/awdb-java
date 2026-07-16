# AGENTS.md

## Project
- Fork of https://gitee.com/aiwen_home/awdb-java (see README.md); respect the original copyright & LICENSE when modifying or redistributing.
- Single-module Maven library: parser for AWDB IP-geolocation files. Public entrypoint is `com.xenoamess.ipplus360.AwdbReader`; `com.xenoamess.example.AwdbApplicationTest` is a demo `main` (also the shade jar's mainClass).

## Build & verify
- Java 8 target. Build: `mvn package` (shade plugin produces a fat jar). Compile check: `mvn compile`.
- No tests, no CI, no lint/typecheck config exist in this repo — don't invent commands for them.

## Conventions
- Package root is `com.xenoamess.*` (groupId `com.xenoamess`); the fork was re-branded from upstream's `io.github.aiwen.*`.
- README documents usage in both Chinese and English; keep doc edits bilingual.
- Git remote is named `origini` (not `origin`) and points to github.com/XenoAmess/awdb-java.
- After every change, commit and push immediately without asking for confirmation.
