# Testing

```sh
./gradlew build                                  # core and pedro
./gradlew :ftc:assembleRelease                   # needs the Android SDK
pip install -r tools/logcheck/requirements.txt
python -m unittest discover -s tools/logcheck -v  # after the Gradle tests
```

## What's checked

**Against WPILib itself, not our reading of the spec.** The Java tests read
every file back with WPILib's own Java reader, copied unmodified into
`core/src/test` under its BSD licence. The Python tests in `tools/logcheck`
write the same scenario with WPILib's *native* writer and require the bytes to
match, and check the struct schemas, encoding and pose geometry against
WPILib's own code through `robotpy`.

**Pedro is exercised for real.** The `pedro` tests drive an actual Pedro
`Follower` with a simulated drivetrain through a full path and through teleop,
including the end-of-path window that used to throw.

## Adding to the parity scenario

`tools/logcheck/scenario.tsv` is read by both halves — the Java test writes it
with our writer, the Python test with WPILib's. Add a line and both pick it up.
A field missing at the end of a line counts as empty, so editors that strip
trailing whitespace can't change its meaning.
