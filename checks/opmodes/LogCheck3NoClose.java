package io.github.mikestitt.corbelsflightlog.checks;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import io.github.mikestitt.corbelsflightlog.FlightLog;
import io.github.mikestitt.corbelsflightlog.ftc.FtcFlightLog;

/**
 * Check 3: the log closes when a well-behaved OpMode simply forgets to.
 *
 * <p>No crash, no close() -- just a normal stop. The last second of data is the
 * part at risk, because it is still in the write buffer when the OpMode ends.
 *
 * <p>PASS: the file contains data right up to when you pressed stop, including
 * the "final value" event.
 */
@TeleOp(name = "Log 3: forgot to close", group = "LogCheck")
public class LogCheck3NoClose extends OpMode {

    private FlightLog log;
    private long loops;

    @Override
    public void init() {
        telemetry.addLine("Records until you press stop, and never calls close().");
        telemetry.update();
    }

    @Override
    public void start() {
        log = FtcFlightLog.open(this);
        loops = 0;
    }

    @Override
    public void loop() {
        loops++;
        log.recordOutput("loop/count", loops);
        log.recordOutput("ramp", loops / 100.0);
        FlightLog.event("final value so far: " + loops);   // every loop, on purpose
        log.endLoop();
        telemetry.addData("Log", log.status());
        telemetry.addData("Loops", loops);
        telemetry.addLine("Press STOP. Nothing here closes the log.");
        telemetry.update();
    }

    @Override
    public void stop() {
        // Nothing. The Robot Controller hook is what closes it.
    }
}
