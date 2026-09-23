# flightlog

WPILOG logging for FIRST Tech Challenge robots -- the format
[AdvantageScope](https://docs.advantagescope.org) opens.

```java
log.number("shooter/rpm", rpm);
log.bool("intake/hasSample", hasSample);
FlightLog.event("kicker fired");
```

Values are written **only when they change**, so a boolean that holds all match
costs one record rather than thousands, and a file survives the match for
review afterwards -- unlike a dashboard, which samples and forgets.

| Module | Depends on | For |
|---|---|---|
| `flightlog-core` | nothing | The WPILOG writer and logger |
| `flightlog-pedro` | Pedro Pathing | Pose, path, aim point and Pedro's debug data |
| `flightlog-ftc` | FTC SDK, Panels | The Control Hub's storage; mirroring to Panels |

## Install

```groovy
repositories { maven { url = "https://jitpack.io" } }

dependencies {
    implementation 'com.github.spiresfrc9106.flightlog:flightlog-core:v0.1.0'
    implementation 'com.github.spiresfrc9106.flightlog:flightlog-pedro:v0.1.0'  // optional
    implementation 'com.github.spiresfrc9106.flightlog:flightlog-ftc:v0.1.0'    // optional
}
```

Pedro Pathing, the FTC SDK and Panels are `compileOnly`, so your project picks
their versions.

## Documentation

<https://spiresfrc9106.github.io/flightlog/> -- one copy per release, plus the
Javadoc.

## Building

```sh
./gradlew build                  # core and pedro
./gradlew :ftc:assembleRelease   # needs the Android SDK
```

`:ftc` is only included when an Android SDK is present, so the rest builds
anywhere.

## Testing

The tests check our files against **WPILib's own code**: the Java tests read
them back with WPILib's Java reader, and `tools/logcheck` requires the bytes to
match WPILib's native writer exactly.

```sh
./gradlew build
pip install -r tools/logcheck/requirements.txt
python -m unittest discover -s tools/logcheck -v
```

## Licence

BSD 3-Clause. See `LICENSE`.
