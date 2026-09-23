package io.github.spiresfrc9106.flightlog;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * One WPILOG file per OpMode run, for AdvantageScope.
 *
 * <p>Files are named {@code <run>-<date>-<time>.wpilog} in {@link #directory}
 * ({@code logs} by default). On an FTC robot, flightlog-ftc points that at
 * {@code /sdcard/FIRST/logs}. Open the files with AdvantageScope.
 *
 * <p><b>Only changes are written.</b> Each channel remembers the last value it
 * wrote and skips a call whose value is identical. AdvantageScope holds a value
 * until the next record, so nothing is lost -- a boolean that's true for the
 * whole match is one record, not one per loop. The exception is
 * {@link #event}, which records every call, since two identical events are
 * two things that happened.
 *
 * <p><b>Never throws into the OpMode.</b> If the file can't be opened or a
 * write fails, logging switches itself off and {@link #status()} says why.
 *
 * <p><b>One type per channel.</b> A channel's type is fixed by its first
 * value. A later call with a different type is ignored, and a one-time note
 * is added to {@code /Events} so it's easy to spot.
 *
 * <p><b>Pose frame.</b> Pose methods take Pedro coordinates (inches, origin in
 * a field corner, radians) and write WPILib {@code Pose2d} structs in the
 * frame AdvantageScope's FTC fields use: meters, origin at field center,
 * turned by {@link #fieldQuarterTurns} -- VERIFY that turn for your season.
 */
public final class FlightLog {

    /** Where log files go. Public so tests can point it at a temp folder. */
    public static File directory = new File("logs");

    /**
     * Counter-clockwise quarter turns from Pedro's axes to AdvantageScope's FTC
     * axes. UNVERIFIED for BIOBUZZ: 1 is what another team used for DECODE's
     * layout. Display only -- nothing on the robot reads it.
     */
    public static int fieldQuarterTurns = 1;

    private static final double FIELD_CENTER_IN = 72.0;
    private static final double METERS_PER_INCH = 0.0254;
    private static final long FLUSH_INTERVAL_NS = 1_000_000_000L;
    private static final int POSE_BYTES = 24;

    /** WPILib's own struct definitions, exactly as WPILib 2026's
     *  {@code addStructSchema} writes them (no trailing semicolons), in
     *  dependency order. AdvantageScope resolves Pose2d's parts by these names,
     *  so neither names nor order may change. Checked against WPILib by
     *  tools/logcheck. */
    static final String[][] SCHEMAS = {
            {"Translation2d", "double x;double y"},
            {"Rotation2d", "double value"},
            {"Pose2d", "Translation2d translation;Rotation2d rotation"},
    };

    /** Opens the file's output stream. Replaced by tests to simulate storage
     *  failures; package-private so robot code can't see it. */
    interface Opener {
        OutputStream open(File file) throws IOException;
    }

    /** Test hook: how files are opened. */
    static Opener opener = FileOutputStream::new;

    /** Test hook: the clock, in nanoseconds. */
    static LongSupplier clock = System::nanoTime;

    /** Test hook: wall-clock milliseconds, for file names. */
    static LongSupplier wallClock = System::currentTimeMillis;

    /** The log most recently opened, for {@link #event}. */
    private static volatile FlightLog current;

    /** One named channel: its entry ID, type, and the last value written. */
    private static final class Channel {
        final int id;
        final String type;
        boolean written;
        long bits;          // last scalar, as raw bits (double, float, int64, boolean)
        double x, y, h;     // last pose, in Pedro units, before conversion
        Object last;        // last String, or a copy of the last array

        Channel(int id, String type) {
            this.id = id;
            this.type = type;
        }
    }

    private WpiLogWriter writer;      // null once disabled
    private final File file;
    private String problem;           // why it's disabled, if it is
    private final long startNs;
    private long lastFlushNs;
    private final Map<String, Channel> channels = new HashMap<>();
    private final Set<String> typeWarnings = new HashSet<>();
    private final byte[] poseBuffer = new byte[POSE_BYTES];

    private FlightLog(WpiLogWriter writer, File file, String problem) {
        this.writer = writer;
        this.file = file;
        this.problem = problem;
        this.startNs = clock.getAsLong();
        this.lastFlushNs = startNs;
    }

    /** A log that records nothing. Safe to call every method on. */
    public static FlightLog disabled(String reason) {
        return new FlightLog(null, null, reason);
    }

    /** Opens a new file named after {@code runName} ({@code "OpMode"} if
     *  null). Never throws. */
    public static FlightLog open(String runName) {
        File file = null;
        if (runName == null) runName = "OpMode";
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                return disabled("can't create " + directory);
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(wallClock.getAsLong()));
            String base = runName.replaceAll("[^A-Za-z0-9_-]", "_") + "-" + stamp;
            file = new File(directory, base + ".wpilog");
            for (int n = 2; file.exists(); n++) {        // Control Hub clocks can be wrong
                file = new File(directory, base + "-" + n + ".wpilog");
            }
            WpiLogWriter w = new WpiLogWriter(
                    new BufferedOutputStream(opener.open(file), 1 << 16),
                    "Corbels FlightLog");
            FlightLog log = new FlightLog(w, file, null);
            for (String[] s : SCHEMAS) {
                int id = w.start("/.schema/struct:" + s[0], "structschema", "", 0);
                w.appendString(id, s[1], 0);
            }
            current = log;
            return log;
        } catch (IOException | RuntimeException e) {
            return disabled("can't open " + (file != null ? file : directory) + ": " + e);
        }
    }

    public boolean isRecording() {
        return writer != null;
    }

    /** File name while recording, otherwise why not -- for the Driver Station. */
    public String status() {
        return writer != null ? file.getName() : "off (" + problem + ")";
    }

    // ------------------------------------------------------------ scalars

    /** A {@code double}. NaN and infinities are skipped (a gap in the graph). */
    public void number(String key, double value) {
        if (writer == null || !isFinite(value)) return;
        long bits = Double.doubleToRawLongBits(value + 0.0);   // + 0.0: -0.0 == 0.0
        try {
            Channel ch = channel(key, "double");
            if (ch == null || (ch.written && ch.bits == bits)) return;
            writer.appendDouble(ch.id, value + 0.0, now());
            remember(ch, bits);
        } catch (IOException e) {
            fail(e);
        }
    }

    /**
     * A {@code float}, stored as a 4-byte float so {@code 0.1f} reads as 0.1.
     * Deliberately not an overload of {@link #number}: Java would pick a
     * float overload for an {@code int} argument, silently storing
     * {@code number("x", 5)} as a float.
     */
    public void float32(String key, float value) {
        if (writer == null || Float.isNaN(value) || Float.isInfinite(value)) return;
        long bits = Float.floatToRawIntBits(value + 0.0f);
        try {
            Channel ch = channel(key, "float");
            if (ch == null || (ch.written && ch.bits == bits)) return;
            writer.appendFloat(ch.id, value + 0.0f, now());
            remember(ch, bits);
        } catch (IOException e) {
            fail(e);
        }
    }

    /** An integer: counts, encoder ticks, states as numbers. */
    public void integer(String key, long value) {
        if (writer == null) return;
        try {
            Channel ch = channel(key, "int64");
            if (ch == null || (ch.written && ch.bits == value)) return;
            writer.appendInt64(ch.id, value, now());
            remember(ch, value);
        } catch (IOException e) {
            fail(e);
        }
    }

    /** A true/false: limit switches, "has game piece", "at speed". */
    public void bool(String key, boolean value) {
        if (writer == null) return;
        long bits = value ? 1 : 0;
        try {
            Channel ch = channel(key, "boolean");
            if (ch == null || (ch.written && ch.bits == bits)) return;
            writer.appendBoolean(ch.id, value, now());
            remember(ch, bits);
        } catch (IOException e) {
            fail(e);
        }
    }

    /** Text, e.g. a state name. {@code null} is written as "null". */
    public void text(String key, String value) {
        if (writer == null) return;
        String v = String.valueOf(value);
        try {
            Channel ch = channel(key, "string");
            if (ch == null || (ch.written && v.equals(ch.last))) return;
            writer.appendString(ch.id, v, now());
            ch.last = v;
            ch.written = true;
        } catch (IOException e) {
            fail(e);
        }
    }

    // ------------------------------------------------------------ poses

    /** A Pedro pose, drawn on AdvantageScope's 2D field. */
    public void pose(String key, double xIn, double yIn, double headingRad) {
        if (writer == null || !isFinite(xIn) || !isFinite(yIn) || !isFinite(headingRad)) return;
        try {
            Channel ch = channel(key, "struct:Pose2d");
            if (ch == null) return;
            if (ch.written && same(ch.x, xIn) && same(ch.y, yIn) && same(ch.h, headingRad)) return;
            encodePose(poseBuffer, 0, xIn, yIn, headingRad);
            writer.appendRaw(ch.id, poseBuffer, POSE_BYTES, now());
            ch.x = xIn;
            ch.y = yIn;
            ch.h = headingRad;
            ch.written = true;
        } catch (IOException e) {
            fail(e);
        }
    }

    /**
     * Several Pedro poses -- a path, a list of targets -- as one
     * {@code Pose2d[]}, which AdvantageScope draws as a trajectory. The array
     * is {@code {x0, y0, heading0, x1, y1, heading1, ...}} in Pedro inches and
     * radians. An empty array clears it. Skipped if any value is non-finite.
     */
    public void poses(String key, double[] xyh) {
        if (writer == null || xyh.length % 3 != 0) return;
        for (double v : xyh) {
            if (!isFinite(v)) return;
        }
        try {
            Channel ch = channel(key, "struct:Pose2d[]");
            if (ch == null || (ch.written && Arrays.equals(xyh, (double[]) ch.last))) return;
            int n = xyh.length / 3;
            byte[] buf = new byte[POSE_BYTES * n];
            for (int i = 0; i < n; i++) {
                encodePose(buf, POSE_BYTES * i, xyh[3 * i], xyh[3 * i + 1], xyh[3 * i + 2]);
            }
            writer.appendRaw(ch.id, buf, buf.length, now());
            ch.last = xyh.clone();
            ch.written = true;
        } catch (IOException e) {
            fail(e);
        }
    }

    // ------------------------------------------------------------ arrays

    /** Several doubles as one value, e.g. four motor powers. */
    public void numbers(String key, double[] values) {
        if (writer == null) return;
        try {
            Channel ch = channel(key, "double[]");
            if (ch == null || (ch.written && Arrays.equals(values, (double[]) ch.last))) return;
            writer.appendDoubleArray(ch.id, values, now());
            ch.last = values.clone();
            ch.written = true;
        } catch (IOException e) {
            fail(e);
        }
    }

    /** Several integers as one value, e.g. four encoder positions. */
    public void integers(String key, long[] values) {
        if (writer == null) return;
        try {
            Channel ch = channel(key, "int64[]");
            if (ch == null || (ch.written && Arrays.equals(values, (long[]) ch.last))) return;
            writer.appendInt64Array(ch.id, values, now());
            ch.last = values.clone();
            ch.written = true;
        } catch (IOException e) {
            fail(e);
        }
    }

    /** Same as {@link #integers(String, long[])}, for {@code int[]}. */
    public void integers(String key, int[] values) {
        if (writer == null) return;
        try {
            Channel ch = channel(key, "int64[]");
            if (ch == null) return;
            long[] prev = (long[]) ch.last;
            if (ch.written && prev.length == values.length) {
                boolean same = true;
                for (int i = 0; i < values.length && same; i++) same = prev[i] == values[i];
                if (same) return;
            }
            long[] wide = new long[values.length];
            for (int i = 0; i < values.length; i++) wide[i] = values[i];
            writer.appendInt64Array(ch.id, wide, now());
            ch.last = wide;
            ch.written = true;
        } catch (IOException e) {
            fail(e);
        }
    }

    /** Several true/falses as one value, e.g. which of three slots are full. */
    public void bools(String key, boolean[] values) {
        if (writer == null) return;
        try {
            Channel ch = channel(key, "boolean[]");
            if (ch == null || (ch.written && Arrays.equals(values, (boolean[]) ch.last))) return;
            writer.appendBooleanArray(ch.id, values, now());
            ch.last = values.clone();
            ch.written = true;
        } catch (IOException e) {
            fail(e);
        }
    }

    // ------------------------------------------------------------ events

    /**
     * Records a one-off event in the current log's {@code /Events} channel,
     * with its exact time. Every call is recorded, even a repeat. Callable from
     * anywhere -- OpModes, subsystems, commands. Does nothing if no log is open.
     */
    public static void event(String message) {
        FlightLog log = current;
        if (log == null || log.writer == null) return;
        log.appendEvent(String.valueOf(message));
    }

    private void appendEvent(String message) {
        try {
            Channel ch = channel("Events", "string");
            if (ch != null) writer.appendString(ch.id, message, now());
        } catch (IOException e) {
            fail(e);
        }
    }

    // ------------------------------------------------------------ lifecycle

    /** Call once per loop. Flushes to storage about once a second. */
    public void endLoop() {
        if (writer == null) return;
        long t = clock.getAsLong();
        if (t - lastFlushNs < FLUSH_INTERVAL_NS) return;
        lastFlushNs = t;
        try {
            writer.flush();
        } catch (IOException e) {
            fail(e);
        }
    }

    /** Flushes and closes the file. Safe to call more than once. */
    public void close() {
        if (current == this) current = null;
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException e) {
            // nothing more to do; the file keeps whatever reached storage
        }
        writer = null;
        problem = "closed";
    }

    // ------------------------------------------------------------ internals

    /** The channel for {@code key}, created on first use. {@code null} if the
     *  key already holds a different type. */
    private Channel channel(String key, String type) throws IOException {
        Channel ch = channels.get(key);
        if (ch == null) {
            ch = new Channel(writer.start("/" + key, type, "", now()), type);
            channels.put(key, ch);
            return ch;
        }
        if (ch.type.equals(type)) return ch;
        if (typeWarnings.add(key)) {
            appendEvent("log.data(\"" + key + "\"): first logged as " + ch.type
                    + ", then given " + type + " -- later values ignored");
        }
        return null;
    }

    private static void remember(Channel ch, long bits) {
        ch.bits = bits;
        ch.written = true;
    }

    private void encodePose(byte[] buf, int offset, double xIn, double yIn, double headingRad) {
        double x = (xIn - FIELD_CENTER_IN) * METERS_PER_INCH;
        double y = (yIn - FIELD_CENTER_IN) * METERS_PER_INCH;
        int turns = Math.floorMod(fieldQuarterTurns, 4);
        double fx;
        double fy;
        switch (turns) {
            case 1:  fx = -y; fy = x;  break;
            case 2:  fx = -x; fy = -y; break;
            case 3:  fx = y;  fy = -x; break;
            default: fx = x;  fy = y;  break;
        }
        double heading = (headingRad + turns * Math.PI / 2) % (2 * Math.PI);
        if (heading < 0) heading += 2 * Math.PI;
        putDouble(buf, offset, fx + 0.0);   // + 0.0 turns -0.0 into 0.0
        putDouble(buf, offset + 8, fy + 0.0);
        putDouble(buf, offset + 16, heading);
    }

    private long now() {
        return (clock.getAsLong() - startNs) / 1000L;
    }

    private void fail(IOException e) {
        problem = "write failed: " + e;
        try {
            writer.close();
        } catch (IOException ignored) {
            // already failing
        }
        writer = null;
    }

    private static void putDouble(byte[] buf, int offset, double value) {
        long bits = Double.doubleToRawLongBits(value);
        for (int i = 0; i < 8; i++) {
            buf[offset + i] = (byte) (bits >>> (8 * i));
        }
    }

    private static boolean same(double a, double b) {
        return Double.doubleToRawLongBits(a + 0.0) == Double.doubleToRawLongBits(b + 0.0);
    }

    private static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }
}
