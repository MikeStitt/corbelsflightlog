package io.github.mikestitt.corbelsflightlog.ftc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D;
import org.firstinspires.ftc.robotcore.external.navigation.Position;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;
import org.junit.Test;

/**
 * The SDK-to-WPILib conversions. Expected quaternions were computed with
 * WPILib's own geometry (robotpy wpimath), composing yaw about Z, then pitch
 * about X, then roll about Y -- the order and axes the SDK documents.
 */
public class FtcGeometryTest {

    private static final double EPS = 1e-12;

    private static YawPitchRollAngles degrees(double yaw, double pitch, double roll) {
        return new YawPitchRollAngles(AngleUnit.DEGREES, yaw, pitch, roll, 0);
    }

    @Test
    public void theSdkDocumentationExampleConvertsToTheExpectedQuaternion() {
        // The 30/40/10 example from YawPitchRollAngles' own javadoc.
        assertArrayEquals(new double[]{0.8965042579838085, 0.3079117684189503,
                        0.16729342336556524, 0.2710781599182154},
                FtcGeometry.quaternion(degrees(30, 40, 10)), EPS);
    }

    @Test
    public void yawAloneRotatesAboutZ() {
        double[] q = FtcGeometry.quaternion(degrees(45, 0, 0));
        assertArrayEquals(new double[]{0.9238795325112867, 0.0, 0.0, 0.3826834323650898}, q, EPS);
    }

    @Test
    public void radiansConvertTheSameWay() {
        YawPitchRollAngles a = new YawPitchRollAngles(AngleUnit.RADIANS, 0.4, -0.2, 0.1, 0);
        assertArrayEquals(new double[]{0.9749428969727563, -0.10760083907197164,
                0.028929151907716128, 0.1925396355124775}, FtcGeometry.quaternion(a), EPS);
    }

    @Test
    public void pitchGoesAboutXAndRollAboutY_notTheOtherWayRound() {
        // Pitch alone must produce an x component; roll alone a y component.
        assertEquals(0.0, FtcGeometry.quaternion(degrees(0, 20, 0))[2], EPS);
        assertEquals(Math.sin(Math.toRadians(20) / 2), FtcGeometry.quaternion(degrees(0, 20, 0))[1], EPS);
        assertEquals(0.0, FtcGeometry.quaternion(degrees(0, 0, 20))[1], EPS);
        assertEquals(Math.sin(Math.toRadians(20) / 2), FtcGeometry.quaternion(degrees(0, 0, 20))[2], EPS);
    }

    @Test
    public void quaternionsAreUnitLength() {
        double[][] cases = {{30, 40, 10}, {-120, 15, -75}, {180, -90, 90}, {0, 0, 0}};
        for (double[] c : cases) {
            double[] q = FtcGeometry.quaternion(degrees(c[0], c[1], c[2]));
            assertEquals(1.0, Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]), 1e-12);
        }
    }

    @Test
    public void positionsConvertToMetresWhateverUnitTheyArriveIn() {
        Position inches = new Position(DistanceUnit.INCH, 10, -20, 5, 0);
        Position metres = inches.toUnit(DistanceUnit.METER);
        assertEquals(0.254, metres.x, 1e-12);
        assertEquals(-0.508, metres.y, 1e-12);
        assertEquals(0.127, metres.z, 1e-12);
    }

    @Test
    public void nullsAreIgnored() {
        FtcGeometry.recordOutput(null, "k", new Pose3D(new Position(), degrees(0, 0, 0)));
        FtcGeometry.recordOutput(null, "k", degrees(0, 0, 0));
        FtcGeometry.heading(null, "k", null);
    }
}
