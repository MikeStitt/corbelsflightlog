# Viewing a log

## Getting the file off the robot

Files are in `/sdcard/FIRST/logs` on the Control Hub, one per run. With the
laptop on the robot's Wi-Fi, connect ADB (`adb connect 192.168.43.1:5555`),
then either use Android Studio's **Device Explorer** (right-click → Save As) or

```sh
adb pull /sdcard/FIRST/logs .
```

The **Download Logs** button on the hub's Manage page fetches the SDK's
`robotControllerLog.txt`, not these.

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
