package io.github.mikestitt.corbelsflightlog;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The WPILib struct types. Byte layouts and schema strings are checked against
 * WPILib's own packer by tools/logcheck; these check the logging behaviour --
 * types, sizes, change-only writing and non-finite handling.
 */
public class StructTypesTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File savedDirectory;

    @Before
    public void setUp() {
        savedDirectory = FlightLog.directory;
        FlightLog.directory = tmp.getRoot();
    }

    @After
    public void tearDown() {
        FlightLog.directory = savedDirectory;
    }

    private RecordedLog record(Body body) throws IOException {
        FlightLog log = FlightLog.open("Structs");
        body.run(log);
        log.close();
        File[] files = tmp.getRoot().listFiles((d, n) -> n.endsWith(".wpilog"));
        assertEquals(1, files.length);
        return RecordedLog.of(files[0]);
    }

    private interface Body {
        void run(FlightLog log);
    }

    @Test
    public void everyTypeIsWrittenWithItsWpilibNameAndSize() throws IOException {
        RecordedLog log = record(l -> {
            l.translation2d("t2", 1, 2);
            l.rotation2d("r2", 0.5);
            l.pose2d("p2", 1, 2, 0.5);
            l.twist2d("tw", 1, 2, 0.5);
            l.chassisSpeeds("cs", 1, 2, 0.5);
            l.mecanumWheelSpeeds("mw", 1, 2, 3, 4);
            l.translation3d("t3", 1, 2, 3);
            l.quaternion("q", 1, 0, 0, 0);
            l.rotation3d("r3", 1, 0, 0, 0);
            l.pose3d("p3", 1, 2, 3, 1, 0, 0, 0);
        });
        String[][] expected = {{"/t2", "struct:Translation2d", "16"}, {"/r2", "struct:Rotation2d", "8"},
                {"/p2", "struct:Pose2d", "24"}, {"/tw", "struct:Twist2d", "24"},
                {"/cs", "struct:ChassisSpeeds", "24"}, {"/mw", "struct:MecanumDriveWheelSpeeds", "32"},
                {"/t3", "struct:Translation3d", "24"}, {"/q", "struct:Quaternion", "32"},
                {"/r3", "struct:Rotation3d", "32"}, {"/p3", "struct:Pose3d", "56"}};
        for (String[] e : expected) {
            assertEquals(e[0], e[1], log.entry(e[0]).type);
            assertEquals(e[0], Integer.parseInt(e[2]), log.entry(e[0]).last().getSize());
        }
    }

    @Test
    public void everySchemaIsDeclaredInDependencyOrder() throws IOException {
        RecordedLog log = record(l -> { });
        List<String> schemas = new ArrayList<>();
        for (String name : log.entries.keySet()) {
            if (name.startsWith("/.schema/struct:")) schemas.add(name.substring("/.schema/struct:".length()));
        }
        assertEquals(Arrays.asList("Translation2d", "Rotation2d", "Pose2d", "Twist2d", "ChassisSpeeds",
                "MecanumDriveWheelSpeeds", "Translation3d", "Quaternion", "Rotation3d", "Pose3d"), schemas);
        assertEquals("Quaternion q", log.strings("/.schema/struct:Rotation3d").get(0));
        assertEquals("Translation3d translation;Rotation3d rotation", log.strings("/.schema/struct:Pose3d").get(0));
    }

    @Test
    public void structsAreWrittenOnlyWhenTheyChange() throws IOException {
        RecordedLog log = record(l -> {
            for (int i = 0; i < 20; i++) l.chassisSpeeds("cs", 1, 2, 3);
            l.chassisSpeeds("cs", 1, 2, 3.5);
            l.chassisSpeeds("cs", 1, 2, 3.5);
            for (int i = 0; i < 5; i++) l.mecanumWheelSpeeds("mw", 0, 0, 0, 0);
        });
        assertEquals(2, log.count("/cs"));
        assertEquals(1, log.count("/mw"));
    }

    @Test
    public void nonFiniteValuesAreSkipped() throws IOException {
        RecordedLog log = record(l -> {
            l.pose3d("p3", Double.NaN, 0, 0, 1, 0, 0, 0);
            l.chassisSpeeds("cs", 1, Double.POSITIVE_INFINITY, 0);
            l.twist2d("tw", 0, 0, Double.NEGATIVE_INFINITY);
            l.mecanumWheelSpeeds("mw", 1, 2, 3, Double.NaN);
            l.rotation2d("r2", Double.NaN);
            l.translation3d("t3", 1, 2, Double.NaN);
            l.quaternion("q", Double.NaN, 0, 0, 0);
            l.pose3d("ypr", 1, 2, 3, Double.NaN, 0, 0);
            l.chassisSpeeds("cs", 1, 2, 3);          // the only finite call
        });
        for (String k : new String[]{"/p3", "/tw", "/mw", "/r2", "/t3", "/q", "/ypr"}) {
            assertFalse(k, log.has(k));
        }
        assertEquals(1, log.count("/cs"));
    }

    @Test
    public void yawPitchRollGivesTheSameBytesAsTheQuaternionForm() throws IOException {
        // Rotating only about yaw: q = (cos(y/2), 0, 0, sin(y/2)).
        double yaw = 0.8;
        RecordedLog log = record(l -> {
            l.pose3d("angles", 1, 2, 3, yaw, 0, 0);
            l.pose3d("quat", 1, 2, 3, Math.cos(yaw / 2), 0, 0, Math.sin(yaw / 2));
        });
        assertArrayEquals(log.entry("/angles").last().getRaw(), log.entry("/quat").last().getRaw());
    }

    @Test
    public void aStructKeepsItsTypeLikeAnyOtherKey() throws IOException {
        RecordedLog log = record(l -> {
            l.chassisSpeeds("k", 1, 2, 3);
            l.pose2d("k", 1, 2, 3);          // different struct type: refused
            l.recordOutput("k", 1.0);              // not a struct at all: refused
            l.chassisSpeeds("k", 4, 5, 6);   // same type: accepted
        });
        assertEquals("struct:ChassisSpeeds", log.entry("/k").type);
        assertEquals(2, log.count("/k"));
        assertEquals(1, log.count("/Events"));
    }

    @Test
    public void aDisabledLogAcceptsEveryStructCall() {
        FlightLog log = FlightLog.disabled("test");
        log.translation2d("a", 1, 2);
        log.rotation2d("b", 1);
        log.pose2d("c", 1, 2, 3);
        log.twist2d("d", 1, 2, 3);
        log.chassisSpeeds("e", 1, 2, 3);
        log.mecanumWheelSpeeds("f", 1, 2, 3, 4);
        log.translation3d("g", 1, 2, 3);
        log.quaternion("h", 1, 0, 0, 0);
        log.rotation3d("i", 1, 0, 0, 0);
        log.pose3d("j", 1, 2, 3, 1, 0, 0, 0);
        log.pose3d("k", 1, 2, 3, 0.1, 0.2, 0.3);
        assertTrue(log.status().contains("test"));
        assertEquals(0, tmp.getRoot().listFiles().length);
    }
}
