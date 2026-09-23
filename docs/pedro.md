# Pedro Pathing

`flightlog-pedro` logs two things, either or both.

```java
import io.github.spiresfrc9106.flightlog.pedro.PedroFlightLog;

PedroFlightLog pedro = new PedroFlightLog(log);
follower = Constants.create(hardwareMap).withLogger(pedro::record);   // debug data

public void loop() {
    follower.update();
    pedro.record(follower);                                           // state
    log.endLoop();
}
```

## The follower's state — `record(Follower)`

| Channel | What |
|---|---|
| `Pedro/Pose` | Where the robot is. Drawn as **Robot** on the 2D field |
| `Pedro/AimPose` | The nearest point on the path, at the heading the path wants. **Ghost** |
| `Pedro/Path` | The segment being followed. **Trajectory** |
| `Pedro/Mode` | `FOLLOW`, `HOLD`, `MANUAL`, `IDLE` |
| `Pedro/vel/*` | World-frame, body-frame and along-path speeds |

The gap between `Pose` and `AimPose` is the tracking error. Because the aim
point is the *nearest* point on the path rather than a time-based target, it
sits beside the robot, never ahead of it: it shows how far off the line the
robot is, not whether it's behind schedule.

`Pedro/Path` is written only when the segment changes, so a whole path costs one
record, and an empty one when the path ends.

## Pedro's own debug data — `record(FollowerLog)`

Pedro hands this over on every `follower.update()`. Each entry becomes
`Pedro/<map>/<entry>`:

- **`follow`** — `mode` always; `isBusy`, `atParametricEnd`, `pathIndex` while
  following or holding
- **`algorithm`** — `headingError`, `translationalError`, `remainingDistance`,
  `closestT`, `tangentialSpeed`, the algorithm's `pose`, vectors such as
  `driveVector/x`
- **`localizer`** — `pose`, and `velocity/vx`, `twist/omega` and the rest
- **`drivetrain`** — whatever the drivetrain reports, e.g. wheel powers

Types are chosen from the value, and an unrecognised type is stored as text, so
entries a future Pedro adds appear on their own.

:::{note}
In teleop the `algorithm` entries are stale. Pedro's algorithm doesn't run in
manual mode, so they hold whatever the last path left behind.
:::

## Two traps this handles for you

**`tangentialVelocity()` throws in teleop.** It is velocity dotted with the path
tangent, and Pedro only computes that tangent while following or holding. This
logs `NaN` instead, which is skipped.

**`poseAt()` throws for one loop at the end of a path.** Pedro 3.0.x empties the
segment queue but clears the path on the *next* update, so `currentPath()` is
non-null while `currentSegment()` is null. This checks the segment.
