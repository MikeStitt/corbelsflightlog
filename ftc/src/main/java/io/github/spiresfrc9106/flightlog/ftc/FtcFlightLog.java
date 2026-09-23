package io.github.spiresfrc9106.flightlog.ftc;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;

import io.github.spiresfrc9106.flightlog.FlightLog;

import java.io.File;

/**
 * FlightLog for an FTC robot: files land where the Control Hub keeps them, and
 * are named after the OpMode.
 *
 * <pre>
 * private FlightLog log;
 *
 * public void start() { log = FtcFlightLog.open(this); }
 * public void loop()  { log.number("shooter/rpm", rpm); log.endLoop(); }
 * public void stop()  { log.close(); }
 * </pre>
 *
 * <p>Files go to {@code /sdcard/FIRST/logs} on the Control Hub. Pull them with
 * Android Studio's Device Explorer or {@code adb pull}, then open them in
 * AdvantageScope.
 */
public final class FtcFlightLog {

    /** Where the Robot Controller keeps its files. */
    public static final File LOG_DIRECTORY = new File("/sdcard/FIRST/logs");

    private FtcFlightLog() {
    }

    /** Opens a log named after the OpMode's class. Never throws. */
    public static FlightLog open(OpMode opMode) {
        return open(opMode == null ? "OpMode" : opMode.getClass().getSimpleName());
    }

    /** Opens a log with the given run name, in {@link #LOG_DIRECTORY}. */
    public static FlightLog open(String runName) {
        FlightLog.directory = LOG_DIRECTORY;
        return FlightLog.open(runName);
    }
}
