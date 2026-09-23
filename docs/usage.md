# Logging values

## Opening and closing a log

One file per run. On an FTC robot:

```java
import io.github.spiresfrc9106.flightlog.FlightLog;
import io.github.spiresfrc9106.flightlog.ftc.FtcFlightLog;

private FlightLog log;

@Override public void start() { log = FtcFlightLog.open(this); }   // /sdcard/FIRST/logs
@Override public void loop()  { /* ... */ log.endLoop(); }
@Override public void stop()  { log.close(); }
```

Without the FTC module, set the folder yourself:

```java
FlightLog.directory = new File("logs");
FlightLog log = FlightLog.open("MyRun");
```

`open` never throws. If the folder can't be used, logging turns itself off and
`log.status()` says why — useful on a Driver Station line.

## The value types

| Call | Stored as | In AdvantageScope |
|---|---|---|
| `number(key, double)` | `double` | Line Graph |
| `float32(key, float)` | `float` | Line Graph |
| `integer(key, long)` | `int64` | Line Graph |
| `bool(key, boolean)` | `boolean` | Line Graph, Table |
| `text(key, String)` | `string` | Table, Console |
| `pose(key, xIn, yIn, headingRad)` | `Pose2d` | **2D Field** |
| `poses(key, double[])` | `Pose2d[]` | 2D Field, as a trajectory |
| `numbers` / `integers` / `bools` | arrays | Table |
| `FlightLog.event(String)` | `string`, in `/Events` | Table, Console |

`float32` is deliberately not an overload of `number`: Java would resolve
`number("x", 5)` — an `int` — to a float overload and quietly store a float.

## Only changes are recorded

A value is written when it differs from the last one under that key.
AdvantageScope holds each value until the next record, so the graph looks the
same while a boolean that stays true all match costs one record instead of
thousands.

`FlightLog.event(...)` is the exception: every call is recorded, because two
identical events are two things that happened.

## One type per key

A key's type is fixed by its first value. A later call with a different type is
ignored and a one-time note naming the key appears in `/Events`.

## Flushing

`endLoop()` writes to storage about once a second. Call it once a loop. If the
robot loses power, the last second or so is lost; `close()` finishes cleanly.
