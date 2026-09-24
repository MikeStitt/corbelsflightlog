package io.github.mikestitt.corbelsflightlog.checks;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import io.github.mikestitt.corbelsflightlog.FlightLog;
import io.github.mikestitt.corbelsflightlog.ftc.FtcFlightLog;

/**
 * Check 2: the log survives an OpMode that throws.
 *
 * <p>Records for about three seconds, then deliberately throws. stop() never
 * runs, so nothing closes the log -- except the hook that registered with the
 * Robot Controller.
 *
 * <p>PASS: the Driver Station shows the crash, and the file downloads and opens
 * in AdvantageScope with about three seconds of data.
 * FAIL: the file is missing, empty, or unreadable.
 */
@TeleOp(name = "Log 2: crash mid-run", group = "LogCheck")
public class LogCheck2Crash extends OpMode {

    private static final double CRASH_AFTER_SECONDS = 3.0;

    private FlightLog log;
    private long startNs;
    private long loops;

    @Override
    public void init() {
        telemetry.addLine("Throws on purpose after 3 seconds.");
        telemetry.addLine("The log should still be complete afterwards.");
        telemetry.update();
    }

    @Override
    public void start() {
        log = FtcFlightLog.open(this);
        startNs = System.nanoTime();
        loops = 0;
        FlightLog.event("about to crash on purpose");
    }

    @Override
    public void loop() {
        double seconds = (System.nanoTime() - startNs) / 1e9;
        loops++;
        log.recordOutput("loop/count", loops);
        log.recordOutput("seconds", seconds);
        log.recordOutput("countdown", CRASH_AFTER_SECONDS - seconds);
        log.endLoop();

        telemetry.addData("Log", log.status());
        telemetry.addData("Crashing in", "%.1f s", CRASH_AFTER_SECONDS - seconds);
        telemetry.update();

        if (seconds > CRASH_AFTER_SECONDS) {
            FlightLog.event("crashing now, at loop " + loops);
            throw new IllegalStateException(
                    "LogCheck2Crash: deliberate crash to test that the log still closes");
        }
    }

    @Override
    public void stop() {
        // Deliberately does NOT close the log: the lifecycle hook must.
    }
}
