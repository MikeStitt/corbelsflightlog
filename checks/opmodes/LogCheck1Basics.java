package io.github.mikestitt.corbelsflightlog.checks;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import io.github.mikestitt.corbelsflightlog.FlightLog;
import io.github.mikestitt.corbelsflightlog.ftc.FtcFlightLog;

import java.io.File;

/**
 * Check 1: does logging work at all, and where do the files go?
 *
 * <p>Needs no hardware -- it runs on any configuration. Drive the sticks and
 * press A/B; every value goes into the log. The Driver Station shows the file
 * name and the folder in use, which is the answer to "is there anywhere to
 * write without an SD card".
 *
 * <p>Afterwards: open http://192.168.43.1:8080/corbelsflightlog and download it.
 */
@TeleOp(name = "Log 1: basics", group = "LogCheck")
public class LogCheck1Basics extends OpMode {

    private FlightLog log;
    private long loops;
    private long lastNs;

    @Override
    public void init() {
        telemetry.addData("Folder", FtcFlightLog.logDirectory());
        telemetry.addData("Writable", FtcFlightLog.logDirectory().canWrite());
        telemetry.update();
    }

    @Override
    public void start() {
        log = FtcFlightLog.open(this);
        loops = 0;
        lastNs = 0;
    }

    @Override
    public void loop() {
        long now = System.nanoTime();
        double loopMs = lastNs == 0 ? 0 : (now - lastNs) / 1e6;
        lastNs = now;
        loops++;

        // One of every kind of value, so the log has something to look at.
        log.recordOutput("loop/count", loops);
        log.recordOutput("loop/ms", loopMs);
        log.recordOutput("stick/leftY", (double) -gamepad1.left_stick_y);
        log.recordOutput("stick/leftX", (double) gamepad1.left_stick_x);
        log.recordOutput("stick/rightX", (double) gamepad1.right_stick_x);
        log.recordOutput("button/a", gamepad1.a);
        log.recordOutput("button/b", gamepad1.b);
        log.recordOutput("state", gamepad1.a ? "A HELD" : "IDLE");
        log.recordOutput("sticks", new double[]{-gamepad1.left_stick_y, gamepad1.left_stick_x});
        log.recordOutput("buttons", new boolean[]{gamepad1.a, gamepad1.b, gamepad1.x, gamepad1.y});
        if (gamepad1.x) FlightLog.event("driver pressed X at loop " + loops);
        log.endLoop();

        File folder = FtcFlightLog.logDirectory();
        File[] files = folder.listFiles((d, n) -> n.endsWith(".wpilog"));
        telemetry.addData("Log", log.status());
        telemetry.addData("Folder", folder);
        telemetry.addData("Logs on disk", files == null ? 0 : files.length);
        telemetry.addData("Loop", "#%d  %.1f ms", loops, loopMs);
        telemetry.addLine("X logs an event. Download: 192.168.43.1:8080/corbelsflightlog");
        telemetry.update();
    }

    @Override
    public void stop() {
        log.close();
    }
}
