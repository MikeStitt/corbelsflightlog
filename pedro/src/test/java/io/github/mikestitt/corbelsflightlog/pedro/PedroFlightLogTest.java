package io.github.mikestitt.corbelsflightlog.pedro;

import static com.pedropathing.api.Paths.line;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.pedropathing.follower.Follower;
import com.pedropathing.follower.FollowerLog;
import com.pedropathing.math.Pose;
import com.pedropathing.math.Twist;
import com.pedropathing.math.Vector2D;
import com.pedropathing.math.Velocity;

import io.github.mikestitt.corbelsflightlog.FlightLog;
import io.github.mikestitt.corbelsflightlog.RecordedLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pedro Pathing's state and debug data, written to a {@link FlightLog}. */
public class PedroFlightLogTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File savedDirectory;
    private FlightLog log;
    private PedroFlightLog pedro;

    @Before
    public void setUp() {
        savedDirectory = FlightLog.directory;
        FlightLog.directory = tmp.getRoot();
        log = FlightLog.open("Test");
        pedro = new PedroFlightLog(log);
    }

    @After
    public void tearDown() {
        log.close();
        FlightLog.directory = savedDirectory;
    }

    private RecordedLog read() throws IOException {
        log.close();
        File[] files = tmp.getRoot().listFiles((d, n) -> n.endsWith(".wpilog"));
        assertEquals(1, files.length);
        return RecordedLog.of(files[0]);
    }

    private static final Object UNKNOWN = new Object() {
        @Override
        public String toString() {
            return "custom thing";
        }
    };

    private static FollowerLog algorithmOnly(Map<String, Object> algorithm) {
        return FollowerLog.of(new HashMap<>(), new HashMap<>(), new HashMap<>(), algorithm);
    }

    private static Map<String, Object> everyType() {
        Map<String, Object> m = new HashMap<>();
        m.put("aDouble", 1.5);
        m.put("aFloat", 2.5f);
        m.put("anInt", 3);
        m.put("aLong", 4L);
        m.put("aShort", (short) 5);
        m.put("aBool", true);
        m.put("anEnum", Follower.Mode.HOLD);
        m.put("aString", "text");
        m.put("aPose", SimRobot.POSES.of(36, 72, 90));
        m.put("aVector", Vector2D.cartesian(1.25, -2.5));
        m.put("aVelocity", new Velocity(10, 20, 0.5));
        m.put("aTwist", new Twist(-1, -2, -0.25));
        m.put("aNull", null);
        m.put("anUnknown", UNKNOWN);
        return m;
    }

    // ------------------------------------------------------------ debug maps

    @Test
    public void everyValueTypeBecomesTheRightChannel() throws IOException {
        pedro.record(algorithmOnly(everyType()));
        RecordedLog r = read();
        String p = "/Pedro/algorithm/";
        assertEquals("double", r.entry(p + "aDouble").type);
        assertEquals("floats widen to double", "double", r.entry(p + "aFloat").type);
        assertEquals(2.5, r.entry(p + "aFloat").last().getDouble(), 0);
        for (String n : new String[]{"anInt", "aLong", "aShort"}) {
            assertEquals(n, "int64", r.entry(p + n).type);
        }
        assertEquals(3, r.entry(p + "anInt").last().getInteger());
        assertEquals("boolean", r.entry(p + "aBool").type);
        assertEquals(Collections.singletonList("HOLD"), r.strings(p + "anEnum"));
        assertEquals(Collections.singletonList("text"), r.strings(p + "aString"));
        assertEquals("struct:Pose2d", r.entry(p + "aPose").type);
        assertEquals(1.25, r.entry(p + "aVector/x").last().getDouble(), 0);
        assertEquals(-2.5, r.entry(p + "aVector/y").last().getDouble(), 0);
        assertEquals(20, r.entry(p + "aVelocity/vy").last().getDouble(), 0);
        assertEquals(-0.25, r.entry(p + "aTwist/omega").last().getDouble(), 0);
        assertFalse("null entries are skipped", r.has(p + "aNull"));
        assertEquals(Collections.singletonList("custom thing"), r.strings(p + "anUnknown"));
    }

    @Test
    public void theFourMapsGetTheirOwnPrefixes() throws IOException {
        Map<String, Object> f = Collections.singletonMap("n", (Object) 1.0);
        Map<String, Object> l = Collections.singletonMap("n", (Object) 2.0);
        Map<String, Object> d = Collections.singletonMap("n", (Object) 3.0);
        Map<String, Object> a = Collections.singletonMap("n", (Object) 4.0);
        pedro.record(FollowerLog.of(f, l, d, a));
        RecordedLog r = read();
        assertEquals(1.0, r.entry("/Pedro/follow/n").last().getDouble(), 0);
        assertEquals(2.0, r.entry("/Pedro/localizer/n").last().getDouble(), 0);
        assertEquals(3.0, r.entry("/Pedro/drivetrain/n").last().getDouble(), 0);
        assertEquals(4.0, r.entry("/Pedro/algorithm/n").last().getDouble(), 0);
    }

    @Test
    public void aCustomPrefixIsUsedEverywhere() throws IOException {
        new PedroFlightLog(log, "Drive").record(algorithmOnly(
                Collections.singletonMap("n", (Object) 1.0)));
        assertTrue(read().has("/Drive/algorithm/n"));
    }

    @Test
    public void unchangedValuesAreWrittenOnce() throws IOException {
        for (int i = 0; i < 5; i++) pedro.record(algorithmOnly(everyType()));
        RecordedLog r = read();
        for (Map.Entry<String, RecordedLog.Entry> e : r.entries.entrySet()) {
            if (e.getKey().startsWith("/Pedro/")) assertEquals(e.getKey(), 1, e.getValue().records.size());
        }
    }

    @Test
    public void nullsAreIgnored() throws IOException {
        pedro.record((FollowerLog) null);
        pedro.record((Follower) null);
        pedro.record(FollowerLog.of(null, null, null, null));
        for (String name : read().entries.keySet()) assertFalse(name, name.startsWith("/Pedro/"));
    }

    @Test
    public void describeIsNullSafeUnlikeFollowerLogToString() {
        assertEquals("", PedroFlightLog.describe(null));
        String text = PedroFlightLog.describe(FollowerLog.of(null, null, null, null));
        assertTrue(text, text.contains("follow=null") && text.contains("drivetrain=null"));
    }

    @Test
    public void aLogIsRequired() {
        try {
            new PedroFlightLog(null);
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // ------------------------------------------------------------ follower state

    @Test
    public void followingAPathLogsPoseAimPathAndMode() throws IOException {
        SimRobot robot = new SimRobot();
        Follower f = robot.follower.withLogger(pedro::record);
        Pose start = SimRobot.POSES.of(0, 0, 0);
        f.setPose(start);
        f.update();
        f.follow(line(start, SimRobot.POSES.of(24, 0, 0)).constant(start));
        boolean sawEndOfPathWindow = false;
        long t0 = System.nanoTime();
        while (System.nanoTime() - t0 < 3_000_000_000L) {
            f.update();
            if (f.currentPath() != null && f.currentSegment() == null) sawEndOfPathWindow = true;
            pedro.record(f);                       // used to throw in that window
            if (f.mode() == Follower.Mode.HOLD) break;
            sleep(5);
        }
        assertTrue("the end-of-path window must occur for this test to mean anything", sawEndOfPathWindow);
        RecordedLog r = read();
        List<String> modes = r.strings("/Pedro/Mode");
        assertEquals("FOLLOW", modes.get(0));
        assertEquals("HOLD", modes.get(modes.size() - 1));
        assertEquals("struct:Pose2d", r.entry("/Pedro/Pose").type);
        assertTrue(r.count("/Pedro/AimPose") > 0);
        assertEquals("the path: drawn once, then cleared", 2, r.count("/Pedro/Path"));
        assertEquals(21 * 24, r.entry("/Pedro/Path").records.get(0).getSize());
        assertEquals(0, r.entry("/Pedro/Path").records.get(1).getSize());
        assertTrue(r.has("/Pedro/vel/tangential_ips"));
        assertTrue(r.has("/Pedro/algorithm/headingError"));   // from withLogger
        List<Double> remaining = r.doubles("/Pedro/algorithm/remainingDistance");
        assertTrue("ends near the end of the path", remaining.get(remaining.size() - 1) < 1);
    }

    @Test
    public void teleopLogsPoseButNoAimPathOrTangentialSpeed() throws IOException {
        SimRobot robot = new SimRobot();
        Follower f = robot.follower.withLogger(pedro::record);
        for (int i = 0; i < 10; i++) {
            f.manual(0.5, 0, 0);
            f.update();
            pedro.record(f);
        }
        RecordedLog r = read();
        assertEquals(Collections.singletonList("MANUAL"), r.strings("/Pedro/Mode"));
        assertTrue(r.count("/Pedro/Pose") > 1);
        assertFalse("no aim point without a path", r.has("/Pedro/AimPose"));
        assertFalse("no path in teleop", r.has("/Pedro/Path"));
        assertFalse("tangential speed is undefined in teleop", r.has("/Pedro/vel/tangential_ips"));
        assertFalse("isBusy only exists while following or holding", r.has("/Pedro/follow/isBusy"));
        assertEquals(Collections.singletonList("MANUAL"), r.strings("/Pedro/follow/mode"));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
