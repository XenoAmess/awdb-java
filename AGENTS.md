# AGENTS.md

## Project
- Fork of https://gitee.com/aiwen_home/awdb-java (see README.md); respect the original copyright & LICENSE when modifying or redistributing.
- Single-module Maven library: parser for AWDB IP-geolocation files. Public entrypoint is `com.xenoamess.ipplus360.AwdbReader`; `com.xenoamess.example.AwdbApplicationTest` is a demo `main` (also the shade jar's mainClass).

## Build & verify
- Java 8 bytecode target. JDK 8 builds via source/target 8; JDK 9+ activates profile `jdk9-plus-release-8` which compiles with `--release 8`. Build: `mvn package` (shade plugin produces a fat jar). Compile check: `mvn compile`.
- No tests and no lint/typecheck config exist in this repo — don't invent commands for them.
- CI: `.github/workflows/build.yml` runs `mvn package` on a JDK 8/11/25 matrix (temurin) and uploads the shaded jar as an artifact.

## Conventions
- Package root is `com.xenoamess.*` (groupId `com.xenoamess`); the fork was re-branded from upstream's `io.github.aiwen.*`.
- README documents usage in both Chinese and English; keep doc edits bilingual.
- Git remote is named `origini` (not `origin`) and points to github.com/XenoAmess/awdb-java.
- After every change, commit and push immediately without asking for confirmation.
