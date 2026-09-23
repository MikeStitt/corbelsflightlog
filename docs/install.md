# Installing

## JitPack (no setup)

In your FTC project's `TeamCode/build.gradle`:

```groovy
repositories {
    maven { url = "https://jitpack.io" }
}

dependencies {
    implementation 'com.github.MikeStitt.corbelsflightlog:corbelsflightlog-core:v0.1.0'
    // optional, if you use Pedro Pathing:
    implementation 'com.github.MikeStitt.corbelsflightlog:corbelsflightlog-pedro:v0.1.0'
    // optional, for the Control Hub and Panels glue:
    implementation 'com.github.MikeStitt.corbelsflightlog:corbelsflightlog-ftc:v0.1.0'
}
```

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
