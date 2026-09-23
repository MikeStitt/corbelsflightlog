# Plan: splitting flightlog into its own repository

## What this is

This tree is a ready-to-push repository. It holds the WPILOG logging from the
Corbels robot code, split into three modules, with CI, releases and versioned
documentation. Nothing here depends on the robot repository.

## The three parts

| Module | Artifact | Depends on | Holds |
|---|---|---|---|
| `core` | `flightlog-core` | nothing | `WpiLogWriter`, `FlightLog` |
| `pedro` | `flightlog-pedro` | Pedro Pathing (`compileOnly`) | `PedroFlightLog` |
| `ftc` | `flightlog-ftc` | FTC SDK + Panels (`compileOnly`) | `FtcFlightLog`, `PanelsMirror` |

The split follows the dependencies, so a team takes only what it uses. `core`
is plain Java and runs anywhere, including desktop tests. Pedro, the FTC SDK
and Panels are `compileOnly`, so this library can never force a version on a
robot project that already chose one.

`:ftc` is an Android library. `settings.gradle` includes it only when an
Android SDK is present, so `./gradlew build` works on any machine and CI still
builds it on a runner that has the SDK.

## Steps to get it running

1. **Create the repo** `spiresfrc9106/flightlog` on GitHub, public (JitPack
   only serves public repos) and push this tree to `main`.
2. **Turn on GitHub Pages**: Settings → Pages → source "Deploy from a branch",
   branch `gh-pages`, folder `/`. The branch appears the first time the docs
   workflow runs.
3. **Push to main.** CI runs: `core` and `pedro` tests, the WPILib parity tests,
   and the `ftc` build. The docs workflow publishes `dev` docs.
4. **Release**: set `version=` in `gradle.properties` (e.g. `0.1.0`), update
   `CHANGELOG.md`, commit, then `git tag v0.1.0 && git push origin v0.1.0`.
   The release workflow checks the tag matches the version, runs the tests,
   attaches the jars to a GitHub Release, and publishes that version's docs.
5. **Tell teams to depend on it** through JitPack (see README). JitPack builds
   the tag the first time someone asks for it; no publishing step is needed.
6. **Point the robot code at it**: delete the `logging` package there, add the
   dependency, and change imports. `PanelsLogger` keeps the Panels drawing and
   uses `PedroFlightLog` for the rest.

## Releasing, in one line

`gradle.properties` holds the version; the tag must match it. The release
workflow fails the build if they differ, so a mistyped tag can't ship.

## Documentation

Sphinx has no working Java autodoc -- `javasphinx` has been unmaintained since
the Python 2 era -- so the prose pages are hand-written (Markdown via MyST,
Furo theme) and the API reference is Javadoc, built by Gradle. `build_site.py`
puts both under one directory per version:

```
<pages root>/
    index.html        redirects to the newest release
    versions.json     the switcher's list
    v0.1.0/           pages + javadoc/core + javadoc/pedro
    dev/              built from main
```

Publishing uses `keep_files: true`, so each release adds a directory and leaves
the others alone. `versions.json` is rebuilt from what's already published plus
what's in `_site`, so a new release doesn't drop older ones from the switcher;
if the site can't be read, the script says so in the build log rather than
quietly shortening the list.

## Verified here

- `core` compiles at Java 8 with no dependencies; **72 tests pass**.
- `pedro` compiles against real Pedro Pathing 3.0.1; **9 tests pass**, driving
  a real follower through a full path and through teleop.
- The **WPILib parity tests pass**: our writer's output is byte-for-byte
  identical to WPILib's native writer.
- The docs build with `-W` (warnings are errors), and three versions were built
  in sequence to confirm the switcher accumulates them.
- All three workflow files parse as valid YAML.

## Not verified

- **Gradle itself has never run** on this tree: this sandbox has no Gradle
  distribution and no Android SDK. The Java sources, tests and docs were built
  directly with `javac`, JUnit and Sphinx. Expect to fix small Gradle issues on
  the first CI run.
- **The `ftc` module has never been compiled** -- it needs the Android SDK, the
  FTC SDK and Panels.
- **The workflows have never run.** Actions are pinned to major versions
  (`@v4`), which is the usual trade-off between churn and drift.
- **JitPack has never built it.** Its first build of a tag is the real test of
  the Gradle setup.

## Decisions worth revisiting

- **JitPack rather than Maven Central.** No signing, no namespace verification,
  works immediately. Maven Central is more robust and is where Pedro publishes;
  moving later means adding signing and a `sonatype` publish step, not
  restructuring.
- **Group `io.github.spiresfrc9106`.** JitPack serves it as
  `com.github.spiresfrc9106.flightlog:flightlog-core` regardless; the group in
  `gradle.properties` matters if you later publish to Maven Central.
- **`PanelsLogger` stays in the robot repo.** Its field drawing is a team
  choice, not library behaviour. `PanelsMirror` here is only the value
  mirroring.
