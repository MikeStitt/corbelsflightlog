package io.github.spiresfrc9106.flightlog.ftc;

import com.bylazar.telemetry.PanelsTelemetry;
import com.bylazar.telemetry.TelemetryManager;

import io.github.spiresfrc9106.flightlog.FlightLog;

/**
 * Sends a value to the Panels dashboard (live) and a {@link FlightLog} (kept),
 * under the same name.
 *
 * <p>Panels gets every call, because its telemetry is rebuilt each frame; the
 * file only gets values that changed. Call {@link #update()} once a loop, after
 * the values.
 *
 * <p>Panels' graph plots any telemetry line of the form {@code name: number},
 * which is what {@code addData} writes -- so numbers are graphable and
 * booleans and text are not.
 */
public final class PanelsMirror {

    private final TelemetryManager panels = PanelsTelemetry.INSTANCE.getTelemetry();
    private final FlightLog log;

    public PanelsMirror(FlightLog log) {
        if (log == null) throw new IllegalArgumentException("log is null");
        this.log = log;
    }

    public TelemetryManager panels() {
        return panels;
    }

    public void data(String key, double value) {
        panels.addData(key, value);
        log.number(key, value);
    }

    /** Also takes {@code int}: whole numbers are stored as int64. */
    public void data(String key, long value) {
        panels.addData(key, value);
        log.integer(key, value);
    }

    public void data(String key, boolean value) {
        panels.addData(key, value);
        log.bool(key, value);
    }

    public void data(String key, String value) {
        panels.addData(key, String.valueOf(value));
        log.text(key, value);
    }

    public void data(String key, Enum<?> value) {
        data(key, value == null ? "null" : value.name());
    }

    /** Flushes Panels and the log file. Once a loop. */
    public void update() {
        log.endLoop();
        panels.update();
    }
}
