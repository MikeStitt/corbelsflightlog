package io.github.mikestitt.corbelsflightlog.wpilib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Quaternion;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.math.geometry.Twist2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.MecanumDriveWheelSpeeds;

import io.github.mikestitt.corbelsflightlog.FlightLog;
import io.github.mikestitt.corbelsflightlog.RecordedLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

/** WPILib values in, the matching struct out -- with nothing converted. */
public class WpiGeometryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File saved;

    @Before
    public void setUp() {
        saved = FlightLog.directory;
        FlightLog.directory = tmp.getRoot();
    }

    @After
    public void tearDown() {
        FlightLog.directory = saved;
    }

    @Test
    public void everyTypeIsWrittenAsItsStructWithTheSameNumbers() throws IOException {
        FlightLog log = FlightLog.open("Wpi");
        WpiGeometry.recordOutput(log, "t2", new Translation2d(1.5, -2.5));
        WpiGeometry.recordOutput(log, "r2", new Rotation2d(0.75));
        WpiGeometry.recordOutput(log, "p2", new Pose2d(1.5, -2.5, new Rotation2d(0.75)));
        WpiGeometry.recordOutput(log, "tw", new Twist2d(0.1, 0.2, 0.3));
        WpiGeometry.recordOutput(log, "cs", new ChassisSpeeds(1, 2, 3));
        WpiGeometry.recordOutput(log, "mw", new MecanumDriveWheelSpeeds(1, 2, 3, 4));
        WpiGeometry.recordOutput(log, "t3", new Translation3d(1, 2, 3));
        WpiGeometry.recordOutput(log, "q", new Quaternion(1, 0, 0, 0));
        WpiGeometry.recordOutput(log, "r3", new Rotation3d(new Quaternion(1, 0, 0, 0)));
        WpiGeometry.recordOutput(log, "p3", new Pose3d(new Translation3d(1, 2, 3),
                new Rotation3d(new Quaternion(1, 0, 0, 0))));
        log.close();

        File[] files = tmp.getRoot().listFiles((d, n) -> n.endsWith(".wpilog"));
        assertEquals(1, files.length);
        RecordedLog read = RecordedLog.of(files[0]);
        String[][] expected = {{"/t2", "struct:Translation2d"}, {"/r2", "struct:Rotation2d"},
                {"/p2", "struct:Pose2d"}, {"/tw", "struct:Twist2d"}, {"/cs", "struct:ChassisSpeeds"},
                {"/mw", "struct:MecanumDriveWheelSpeeds"}, {"/t3", "struct:Translation3d"},
                {"/q", "struct:Quaternion"}, {"/r3", "struct:Rotation3d"}, {"/p3", "struct:Pose3d"}};
        for (String[] e : expected) assertEquals(e[0], e[1], read.entry(e[0]).type);

        double[] pose = RecordedLog.pose(read.entry("/p2").last());
        assertEquals("no conversion: metres in, metres out", 1.5, pose[0], 1e-12);
        assertEquals(-2.5, pose[1], 1e-12);
        assertEquals(0.75, pose[2], 1e-12);
    }

    @Test
    public void nullsAreIgnored() throws IOException {
        FlightLog log = FlightLog.open("Nulls");
        WpiGeometry.recordOutput(log, "a", (Pose2d) null);
        WpiGeometry.recordOutput(null, "b", new Pose2d(1, 2, new Rotation2d(0)));
        log.close();
        File[] files = tmp.getRoot().listFiles((d, n) -> n.endsWith(".wpilog"));
        assertFalse(RecordedLog.of(files[0]).has("/a"));
    }
}
