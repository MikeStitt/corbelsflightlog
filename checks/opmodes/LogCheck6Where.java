package io.github.mikestitt.corbelsflightlog.checks;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import io.github.mikestitt.corbelsflightlog.FlightLog;
import io.github.mikestitt.corbelsflightlog.ftc.FtcFlightLog;

import java.io.File;

/**
 * Check 6: where things are, without writing anything.
 *
 * <p>Run this first if something looks wrong. It reports the folder in use,
 * whether it can be written to, how much space is left, what is already there,
 * and the address of the download page.
 */
@TeleOp(name = "Log 6: where are the logs?", group = "LogCheck")
public class LogCheck6Where extends LinearOpMode {

    @Override
    public void runOpMode() {
        File folder = FtcFlightLog.logDirectory();
        File[] files = folder.listFiles((d, n) -> n.endsWith(".wpilog"));
        long total = 0;
        if (files != null) for (File f : files) total += f.length();

        telemetry.addData("Folder", folder);
        telemetry.addData("Exists", folder.isDirectory());
        telemetry.addData("Writable", folder.canWrite());
        telemetry.addData("Free space", folder.getUsableSpace() / (1024 * 1024) + " MB");
        telemetry.addData("Logs", files == null ? 0 : files.length);
        telemetry.addData("Using", total / 1024 + " KB of budget "
                + FlightLog.maxDirectoryBytes / (1024 * 1024) + " MB");
        telemetry.addLine("Download: 192.168.43.1:8080/corbelsflightlog");
        telemetry.addLine("");
        telemetry.addLine("If the folder is not <FIRST>/logs, the SDK's folder");
        telemetry.addLine("could not be used and this fell back to app storage.");
        telemetry.update();

        waitForStart();
        while (opModeIsActive()) {
            sleep(200);
        }
    }
}
