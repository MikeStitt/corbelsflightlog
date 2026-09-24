package io.github.mikestitt.corbelsflightlog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A Pedro pose with a height must land in the same place as the flat one.
 * Converting by hand instead put the 3D robot 90 degrees away from the 2D one
 * on the field, because the hand-written version left out the quarter turn.
 */
public class PedroPose3dTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File savedDirectory;
    private int savedTurns;

    @Before
    public void setUp() {
        savedDirectory = FlightLog.directory;
        savedTurns = FlightLog.fieldQuarterTurns;
        FlightLog.directory = tmp.getRoot();
    }

    @After
    public void tearDown() {
        FlightLog.directory = savedDirectory;
        FlightLog.fieldQuarterTurns = savedTurns;
    }

    private RecordedLog record(Body body) throws IOException {
        FlightLog log = FlightLog.open("Pose3d");
        body.run(log);
        log.close();
        return RecordedLog.of(tmp.getRoot().listFiles((d, n) -> n.endsWith(".wpilog"))[0]);
    }

    private interface Body {
        void run(FlightLog log);
    }

    private static double[] pose3d(byte[] raw) {
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        double x = b.getDouble(), y = b.getDouble(), z = b.getDouble();
        double qw = b.getDouble(), qx = b.getDouble(), qy = b.getDouble(), qz = b.getDouble();
        return new double[]{x, y, z, qw, qx, qy, qz, 2 * Math.atan2(qz, qw)};
    }

    @Test
    public void everyQuarterTurnPutsTheThreeDPoseWhereTheTwoDOneIs() throws IOException {
        // The four axis crossings of a circle around the middle of the field.
        double[][] crossings = {{96, 72, Math.PI / 2}, {72, 96, Math.PI},
                {48, 72, 3 * Math.PI / 2}, {72, 48, 0}};
        for (int turns = 0; turns < 4; turns++) {
            FlightLog.fieldQuarterTurns = turns;
            File folder = tmp.newFolder("turns" + turns);
            FlightLog.directory = folder;
            FlightLog log = FlightLog.open("Turn");
            for (int i = 0; i < crossings.length; i++) {
                log.pose("flat/" + i, crossings[i][0], crossings[i][1], crossings[i][2]);
                log.pose("high/" + i, crossings[i][0], crossings[i][1], 39.3700787401575,
                        crossings[i][2]);
            }
            log.close();

            RecordedLog read = RecordedLog.of(folder.listFiles((d, n) -> n.endsWith(".wpilog"))[0]);
            for (int i = 0; i < crossings.length; i++) {
                double[] flat = RecordedLog.pose(read.entry("/flat/" + i).last());
                double[] high = pose3d(read.entry("/high/" + i).last().getRaw());
                String where = "turns " + turns + ", crossing " + i;
                assertEquals(where + " x", flat[0], high[0], 1e-12);
                assertEquals(where + " y", flat[1], high[1], 1e-12);
                assertEquals(where + " height", 1.0, high[2], 1e-12);
                assertEquals(where + " heading", Math.cos(flat[2]), Math.cos(high[7]), 1e-12);
                assertEquals(where + " heading", Math.sin(flat[2]), Math.sin(high[7]), 1e-12);
            }
        }
    }

    @Test
    public void itIsAPose3dAndOnlyRollsAboutTheVertical() throws IOException {
        RecordedLog log = record(l -> l.pose("p", 96, 72, 12, Math.PI / 2));
        assertEquals("struct:Pose3d", log.entry("/p").type);
        double[] p = pose3d(log.entry("/p").last().getRaw());
        assertEquals("no pitch", 0.0, p[4], 1e-12);
        assertEquals("no roll", 0.0, p[5], 1e-12);
        assertEquals("12 inches up", 12 * 0.0254, p[2], 1e-12);
    }

    @Test
    public void itIsWrittenOnlyWhenSomethingChanges() throws IOException {
        RecordedLog log = record(l -> {
            for (int i = 0; i < 10; i++) l.pose("p", 96, 72, 39.37, Math.PI / 2);
            l.pose("p", 96, 72, 40.0, Math.PI / 2);        // height changed
            l.pose("p", 96, 73, 40.0, Math.PI / 2);        // position changed
        });
        assertEquals(3, log.count("/p"));
    }

    @Test
    public void nonFiniteValuesAreSkipped() throws IOException {
        RecordedLog log = record(l -> {
            l.pose("a", Double.NaN, 72, 10, 0);
            l.pose("b", 96, Double.POSITIVE_INFINITY, 10, 0);
            l.pose("c", 96, 72, Double.NaN, 0);
            l.pose("d", 96, 72, 10, Double.NaN);
        });
        for (String key : new String[]{"/a", "/b", "/c", "/d"}) {
            assertFalse(key, log.has(key));
        }
    }

    @Test
    public void aDisabledLogAcceptsIt() {
        FlightLog.disabled("test").pose("p", 96, 72, 10, 0);
    }
}
