# Viewing a log

## Getting the file off the robot

With the laptop on the robot's Wi-Fi, open
**<http://192.168.43.1:8080/corbelsflightlog>** and click a log. That page is
served by the Robot Controller itself, on the same port as Program & Manage, so
nothing needs installing.

Or, over ADB (`adb connect 192.168.43.1:5555`):

```sh
adb pull /sdcard/FIRST/logs .
```

Files live in a `logs` folder inside the Robot Controller's FIRST folder --
internal storage on a Control Hub, not a removable card. If that folder can't
be used, `corbelsflightlog-ftc` falls back to the app's own private storage;
the page and the Driver Station both show which folder is in use. To log somewhere
else entirely -- a USB stick, say -- call `FtcFlightLog.useDirectory(folder)`
before opening.

The hub's **Download Logs** button, on its Manage page, fetches the SDK's
`robotControllerLog.txt`, not these.

## Housekeeping

Logs are capped at **10 GiB** in total. When a log is opened and the folder is
over that, the oldest `.wpilog` files are deleted until it fits. Change it with
`FlightLog.maxDirectoryBytes`. Files that aren't `.wpilog` are never touched.

## In AdvantageScope

1. Open the `.wpilog`. Channels appear in the sidebar as a tree.
2. **2D Field** tab: choose an FTC field (`FTC:Evergreen` if this season's image
   isn't available), then drag `Pedro/Pose` in as **Robot**, `Pedro/AimPose` as
   **Ghost**, `Pedro/Path` as **Trajectory**.
3. **Line Graph** tab: drag in numbers; strings and booleans go in its discrete
   section as labelled bands.
4. Dragging the timeline moves every tab together.

## Field orientation

`FlightLog.pose(...)` converts Pedro coordinates — inches, origin in a field
corner — into what AdvantageScope's FTC fields expect: metres, origin at the
field centre, turned by `FlightLog.fieldQuarterTurns`.

:::{warning}
That turn depends on which wall is red, so it changes with the season. If the
robot travels the wrong way on the field, set `fieldQuarterTurns` to 0, 1, 2 or
3 until it matches. It only affects the display, and it's applied when the file
is written, so it changes future logs and not old ones.
:::
