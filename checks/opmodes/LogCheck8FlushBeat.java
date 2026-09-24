package io.github.mikestitt.corbelsflightlog.checks;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import io.github.mikestitt.corbelsflightlog.FlightLog;
import io.github.mikestitt.corbelsflightlog.ftc.FtcFlightLog;

/**
 * Check 8: make the logger's storage writes visible, on purpose, at 3 Hz.
 *
 * <p>An earlier run showed the loop time carrying a strong 3.94 Hz impulse train
 * -- one brief cost every 254 ms, with harmonics out to 35 Hz -- while the
 * logger's own once-a-second flush sat at 9.7 microseconds, barely above the
 * noise floor. 254 ms is almost certainly the Driver Station telemetry
 * transmission, not us.
 *
 * <p>This OpMode settles it by writing a known amount of data per second, so the
 * 64 KB buffer fills exactly three times a second. If a 3 Hz line (and its
 * harmonics at 6, 9, 12 Hz) appears in the loop-time spectrum, storage writes
 * are visible in loop time and we know what they cost. If it does not, they are
 * below the noise even at that rate, and nothing about how we batch writes could
 * matter.
 *
 * <p>Three things make the signal clean:
 * <ul>
 *   <li>{@code endLoop()} is deliberately NOT called. Its one-second flush would
 *       interleave with the buffer fills and smear the beat -- measured at 3.95
 *       Hz with it on, a clean 3.05 Hz with it off.</li>
 *   <li>The Driver Station transmission interval is moved to 200 ms, putting the
 *       SDK's comb at 5, 10, 15 Hz and away from ours at 3, 6, 9, 12.</li>
 *   <li>Each block's contents change, so change-only writing cannot skip
 *       them.</li>
 * </ul>
 *
 * <p><b>This writes about 200 KB every second</b> -- roughly 4 MB in twenty
 * seconds. Run it briefly, and delete the files afterwards.
 *
 * <p>Afterwards, run an FFT of {@code /loop/ms} against time and look for a line
 * at 3 Hz with harmonics.
 */
@TeleOp(name = "Log 8: 3 Hz flush beat", group = "LogCheck")
public class LogCheck8FlushBeat extends OpMode {

    /** How many times a second the 64 KB buffer should fill. */
    private static final double FLUSHES_PER_SECOND = 3.0;

    /** BufferedOutputStream size inside the library. */
    private static final int BUFFER_BYTES = 64 * 1024;

    /** Payload of each filler record; its header adds about 8 more. */
    private static final int BLOCK_BYTES = 1024;

    private static final double BYTES_PER_SECOND = FLUSHES_PER_SECOND * BUFFER_BYTES;

    private final byte[] block = new byte[BLOCK_BYTES];

    private FlightLog log;
    private long startNs;
    private long lastNs;
    private double written;
    private long loops;
    private int counter;

    @Override
    public void init() {
        telemetry.addData("Writes", "%.0f KB/s, %.1f buffer fills per second",
                BYTES_PER_SECOND / 1024, FLUSHES_PER_SECOND);
        telemetry.addLine("About 4 MB per 20 seconds. Keep the run short.");
        telemetry.update();
    }

    @Override
    public void start() {
        // Move the SDK's own periodic cost away from 3, 6, 9 Hz.
        telemetry.setMsTransmissionInterval(200);
        log = FtcFlightLog.open(this);
        startNs = System.nanoTime();
        lastNs = startNs;
        written = 0;
        loops = 0;
        counter = 0;
    }

    @Override
    public void loop() {
        long now = System.nanoTime();
        double loopMs = (now - lastNs) / 1e6;
        lastNs = now;
        loops++;

        // Write enough filler to keep up with the target byte rate. Pacing on
        // elapsed time rather than per loop keeps the beat at 3 Hz whatever the
        // loop rate does.
        double target = BYTES_PER_SECOND * (now - startNs) / 1e9;
        while (written < target) {
            block[0] = (byte) counter;
            block[1] = (byte) (counter >> 8);
            counter++;
            log.recordOutput("filler", block);
            written += BLOCK_BYTES + 8;
        }

        log.recordOutput("loop/ms", loopMs);
        log.recordOutput("loop/count", loops);
        log.recordOutput("filler/bytes", written);
        // No endLoop(): its one-second flush would blur the 3 Hz beat.

        telemetry.addData("Log", log.status());
        telemetry.addData("Written", "%.1f MB", written / 1024 / 1024);
        telemetry.addData("Loop", "#%d  %.2f ms", loops, loopMs);
        telemetry.update();
    }

    @Override
    public void stop() {
        FlightLog.event(String.format("flush beat: %.0f bytes over %d loops",
                written, loops));
        log.close();
        telemetry.setMsTransmissionInterval(250);   // back to the SDK default
    }
}
