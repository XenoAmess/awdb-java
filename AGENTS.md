# AGENTS.md

## Project
- Fork of https://gitee.com/aiwen_home/awdb-java (see README.md); respect the original copyright & LICENSE when modifying or redistributing.
- Single-module Maven library: parser for AWDB IP-geolocation files. Public entrypoint is `com.xenoamess.ipplus360.AwdbReader`.
- Since 3.0.0 the fork intentionally breaks API vs upstream (rebrand, fastjson removal, dead-code deletion).

## Build & verify
- Java 8 bytecode target. JDK 8 builds via source/target 8; JDK 9+ activates profile `jdk9-plus-release-8` which compiles with `--release 8`. Build: `mvn package`. Compile check: `mvn compile`.
- Tests: `mvn test` (JUnit 5). Fixtures are synthetic files solidified at `src/test/resources/test_20260717*.awdb`, produced by `com.xenoamess.ipplus360.fixture.AwdbTestFixture#main` — they encode the format as the code implements it, NOT verified against a real official .awdb (none is publicly available).
- The >2GB large-file path is tested end-to-end via Linux sparse files (`AwdbReaderSparseFileTest`): positioned-write extends the file past 2GB without disk cost (`FileChannel.truncate` cannot extend — it only shrinks). Tests assume Linux via JUnit `assumeTrue`.
- Gotcha: modern JDKs normalize `::ffff:a.b.c.d` to `Inet4Address` via BOTH `InetAddress.getByName` and `getByAddress` — never round-trip IPv4-mapped addresses through `InetAddress`; build the 16 raw bytes directly (see `AwdbReader.findIpLocation`).
- No lint/typecheck config exists in this repo — don't invent commands for them.
- CI: `.github/workflows/build.yml` runs `mvn verify` (which includes tests + JaCoCo report) on a JDK 8/11/25 matrix (temurin) and uploads the jar as an artifact. The `coverage-pages` job (master only) deploys the JDK 11 leg's JaCoCo report to GitHub Pages and feeds the shields.io endpoint badge in README; report entry is `report/coverage.html` (index.html slot deliberately freed). Keep `coverage-pages` OUT of any required check (Pitfall 11).
- `.github/workflows/auto-merge.yml` approves + auto-merges dependabot PRs (patch/minor always, major only for github-actions). MYTOKEN lives in the **dependabot** secret namespace, not actions (Pitfall 16).
- Branch protection on master: required checks `build (8)`, `build (11)`, `build (25)` + `strict: true` + linear history. Changing the JDK matrix renames those checks — update protection in the same change or every PR gets stuck. Merge PRs with `--rebase`/`--squash`, never `--merge`.
- dependabot.yml must NOT contain a `groups:` block (per dependabot-automerge-skill Pitfall 13: grouped PRs make CI failures un-attributable). One PR per dependency per cycle; logback pinned to 1.3.x and junit to 5.x via `ignore` (Java 8 constraint).

## Known issues (intentionally left)
- The small-file parser (`AwdbDataParser`) and large-file parser (`AwdbDataParserLarge`) still have behavior drift (e.g. TEXT length cap, `buffer2Long` returning -1 on error, null-on-error vs throw). Fixing requires a merge that was explicitly postponed.
- Real-format compatibility is unverified; if a genuine .awdb sample arrives, put it in `src/test/resources` (gitignored if proprietary) and add tests against it.

## Conventions
- Package root is `com.xenoamess.*` (groupId `com.xenoamess`); the fork was re-branded from upstream's `io.github.aiwen.*`.
- README documents usage in both Chinese and English; keep doc edits bilingual.
- Git remote is named `origini` (not `origin`) and points to github.com/XenoAmess/awdb-java.
- After every change, commit and push immediately without asking for confirmation.
