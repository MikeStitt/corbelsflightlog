# Installing

## JitPack (no setup)

In your FTC project's `TeamCode/build.gradle`:

```groovy
repositories {
    maven { url = "https://jitpack.io" }
}

dependencies {
    implementation 'com.github.MikeStitt.corbelsflightlog:corbelsflightlog-core:v0.2.1'
    // optional, if you use Pedro Pathing:
    implementation 'com.github.MikeStitt.corbelsflightlog:corbelsflightlog-pedro:v0.2.1'
    // optional, if you already use WPILib geometry types:
    implementation 'com.github.MikeStitt.corbelsflightlog:corbelsflightlog-wpilib:v0.2.1'
    // the Control Hub and Panels glue -- see the note below:
    implementation 'com.github.MikeStitt.corbelsflightlog:corbelsflightlog-ftc:v0.2.1'
}
```

### About `corbelsflightlog-ftc`

It is an Android library rather than a plain jar, so JitPack has to build it
with an Android SDK. **v0.2.1 is the first release that attempts this**, and the
attempt is allowed to fail without taking the other three modules with it.

Check whether it worked before relying on it:

```
https://jitpack.io/com/github/MikeStitt/corbelsflightlog/v0.2.1/build.log
```

If `-ftc` is not there, build it yourself -- clone this repository and run
`./gradlew publishToMavenLocal`, then add `mavenLocal()` to your repositories.
The other three modules are plain Java and are unaffected.

JitPack builds a tag the first time someone asks for it, so the first download
of a new version is slow. After that it's cached.

## Versions

`corbelsflightlog-core` needs nothing. `corbelsflightlog-pedro` and `corbelsflightlog-ftc` declare
Pedro Pathing, the FTC SDK and Panels as `compileOnly`, so **your project
chooses those versions** and this library can't force a different one on you.

Built and tested against Pedro Pathing 3.0.1, FTC SDK 12.0.0 and Panels 1.0.13.

## Java version

The jars are built for Java 8 bytecode, which is what FTC projects compile to,
so they work whatever JDK your laptop runs.
