package io.github.spiresfrc9106.flightlog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes tools/logcheck/scenario.tsv with OUR writer, into
 * TeamCode/build/logcheck/, for tools/logcheck/test_wpilib_parity.py to
 * compare against WPILib's native writer byte for byte.
 *
 * <p>It also checks its own output with WPILib's Java reader, so it's a real
 * test even when the Python side isn't run.
 *
 * <p>Paths can be overridden with -Dlogcheck.scenario=... and
 * -Dlogcheck.out=...; the defaults assume Gradle's working directory for unit
 * tests, which is the TeamCode module. Skipped if the scenario isn't found.
 */
public class ParityFilesTest {

    private static final int QUARTER_TURNS = 4;

    private File scenario;
    private File out;
    private File savedDirectory;
    private int savedTurns;

    @Before
    public void setUp() {
        scenario = new File(System.getProperty("logcheck.scenario", "../tools/logcheck/scenario.tsv"));
        out = new File(System.getProperty("logcheck.out", "build/logcheck"));
        Assume.assumeTrue("scenario not found at " + scenario.getAbsolutePath(), scenario.isFile());
        savedDirectory = FlightLog.directory;
        savedTurns = FlightLog.fieldQuarterTurns;
    }

    @After
    public void tearDown() {
        if (savedDirectory != null) FlightLog.directory = savedDirectory;
        FlightLog.fieldQuarterTurns = savedTurns;
        FlightLog.clock = System::nanoTime;
        FlightLog.wallClock = System::currentTimeMillis;
    }

    private List<String[]> rows() throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(scenario), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                rows.add(line.split("\t", -1));
            }
        }
        return rows;
    }

    /** Field {@code i}, or "" if the line ends before it -- so editors that
     *  strip trailing whitespace can't change or break the scenario. */
    private static String field(String[] row, int i) {
        return i < row.length ? row[i] : "";
    }

    private static String[] items(String field) {
        return field.isEmpty() ? new String[0] : field.split(",");
    }

    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    private static void append(WpiLogWriter w, int id, String type, long ts, String v) throws IOException {
        switch (type) {
            case "double": w.appendDouble(id, Double.parseDouble(v), ts); break;
            case "float": w.appendFloat(id, Float.parseFloat(v), ts); break;
            case "int64": w.appendInt64(id, Long.parseLong(v), ts); break;
            case "boolean": w.appendBoolean(id, Boolean.parseBoolean(v), ts); break;
            case "string": w.appendString(id, v, ts); break;
            case "raw": {
                byte[] b = hex(v);
                w.appendRaw(id, b, b.length, ts);
                break;
            }
            case "double[]": {
                String[] s = items(v);
                double[] a = new double[s.length];
                for (int i = 0; i < s.length; i++) a[i] = Double.parseDouble(s[i]);
                w.appendDoubleArray(id, a, ts);
                break;
            }
            case "int64[]": {
                String[] s = items(v);
                long[] a = new long[s.length];
                for (int i = 0; i < s.length; i++) a[i] = Long.parseLong(s[i]);
                w.appendInt64Array(id, a, ts);
                break;
            }
            case "boolean[]": {
                String[] s = items(v);
                boolean[] a = new boolean[s.length];
                for (int i = 0; i < s.length; i++) a[i] = Boolean.parseBoolean(s[i]);
                w.appendBooleanArray(id, a, ts);
                break;
            }
            default: throw new IllegalArgumentException("scenario uses unknown type " + type);
        }
    }

    @Test
    public void writeTheScenarioWithOurWriter() throws IOException {
        assertTrue(out.isDirectory() || out.mkdirs());
        List<String[]> rows = rows();
        String header = "";
        for (String[] row : rows) if (row[0].equals("header")) header = field(row, 1);
        File file = new File(out, "writer.wpilog");
        Map<String, Integer> ids = new HashMap<>();
        Map<String, String> types = new HashMap<>();
        int records = 0;
        try (FileOutputStream fos = new FileOutputStream(file)) {
            WpiLogWriter w = new WpiLogWriter(fos, header);
            for (String[] row : rows) {
                switch (row[0]) {
                    case "start":
                        ids.put(row[2], w.start(row[2], row[3], field(row, 4), Long.parseLong(row[1])));
                        types.put(row[2], row[3]);
                        records++;
                        break;
                    case "data":
                        append(w, ids.get(row[2]), types.get(row[2]), Long.parseLong(row[1]), field(row, 3));
                        records++;
                        break;
                    case "finish":
                        w.finish(ids.get(row[2]), Long.parseLong(row[1]));
                        records++;
                        break;
                    default:
                        break;
                }
            }
            w.flush();
        }
        // WPILib's Java reader agrees with the scenario, record for record.
        RecordedLog log = RecordedLog.of(file);
        assertEquals(header, log.reader.getExtraHeader());
        assertEquals(records, log.all.size());
        assertEquals(types.size(), log.entries.size());
        for (Map.Entry<String, String> t : types.entrySet()) {
            assertEquals(t.getKey(), t.getValue(), log.entry(t.getKey()).type);
        }
    }

    @Test
    public void writeTheScenarioPosesForEveryQuarterTurn() throws IOException {
        List<double[]> poses = new ArrayList<>();
        for (String[] row : rows()) {
            if (row[0].equals("pose")) {
                poses.add(new double[]{Double.parseDouble(row[1]), Double.parseDouble(row[2]),
                        Math.toRadians(Double.parseDouble(row[3]))});
            }
        }
        double[] all = new double[poses.size() * 3];
        for (int i = 0; i < poses.size(); i++) System.arraycopy(poses.get(i), 0, all, 3 * i, 3);

        FlightLog.clock = new TestStreams.FakeClock(1_000_000_000L);
        FlightLog.wallClock = () -> 1_790_000_000_000L;
        for (int q = 0; q < QUARTER_TURNS; q++) {
            File dir = new File(out, "poses-q" + q);
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) assertTrue(f.delete());
            FlightLog.directory = dir;
            FlightLog.fieldQuarterTurns = q;
            FlightLog log = FlightLog.open("Poses");
            for (int i = 0; i < poses.size(); i++) {
                double[] p = poses.get(i);
                log.pose("Pose/" + i, p[0], p[1], p[2]);
            }
            log.poses("Path", all);
            log.close();
            File[] written = dir.listFiles();
            assertEquals(1, written.length);
            RecordedLog read = RecordedLog.of(written[0]);
            assertEquals(poses.size(), read.entries.keySet().stream().filter(n -> n.startsWith("/Pose/")).count());
            assertEquals(24 * poses.size(), read.entry("/Path").last().getSize());
        }
    }
}
