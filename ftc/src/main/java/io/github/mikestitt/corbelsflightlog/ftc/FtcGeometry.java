package io.github.mikestitt.corbelsflightlog.ftc;

import io.github.mikestitt.corbelsflightlog.FlightLog;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D;
import org.firstinspires.ftc.robotcore.external.navigation.Position;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

/**
 * Logs the FTC SDK's own geometry types -- what AprilTag detections and the IMU
 * hand you -- as the WPILib structs AdvantageScope draws.
 *
 * <p>Two conversions happen here.
 *
 * <p><b>Distance.</b> A {@link Position} carries its own {@link DistanceUnit};
 * it is converted to metres, which is what WPILib structs hold.
 *
 * <p><b>Orientation.</b> {@link YawPitchRollAngles} is documented as yaw, then
 * pitch, then roll, applied intrinsically, with pitch about the X axis and roll
 * about the Y axis. WPILib's own yaw/pitch/roll constructor names the axes the
 * other way round -- roll about X, pitch about Y -- so the two cannot be passed
 * through directly. This composes the rotation in the SDK's order and axes and
 * writes the resulting quaternion, which is what a WPILib {@code Rotation3d}
 * stores.
 *
 * <p>The SDK notes that the axis mapping of a {@code Pose3D} is defined by
 * whatever produced it, so check the docs of the API you got it from.
 */
public final class FtcGeometry {

    private FtcGeometry() {
    }

    /** An AprilTag or other SDK pose, as a WPILib {@code Pose3d} in metres. */
    public static void pose3d(FlightLog log, String key, Pose3D pose) {
        if (log == null || pose == null) return;
        Position p = pose.getPosition();
        YawPitchRollAngles o = pose.getOrientation();
        if (p == null || o == null) return;
        Position metres = p.toUnit(DistanceUnit.METER);
        double[] q = quaternion(o);
        log.pose3d(key, metres.x, metres.y, metres.z, q[0], q[1], q[2], q[3]);
    }

    /** An orientation on its own, as a WPILib {@code Rotation3d}. */
    public static void rotation3d(FlightLog log, String key, YawPitchRollAngles angles) {
        if (log == null || angles == null) return;
        double[] q = quaternion(angles);
        log.rotation3d(key, q[0], q[1], q[2], q[3]);
    }

    /** Just the heading, as a WPILib {@code Rotation2d}. */
    public static void heading(FlightLog log, String key, YawPitchRollAngles angles) {
        if (log == null || angles == null) return;
        log.rotation2d(key, angles.getYaw(AngleUnit.RADIANS));
    }

    /**
     * The SDK's yaw, pitch and roll as a quaternion, {@code {w, x, y, z}}:
     * yaw about Z, then pitch about the new X, then roll about the new Y.
     */
    public static double[] quaternion(YawPitchRollAngles angles) {
        double yaw = angles.getYaw(AngleUnit.RADIANS);
        double pitch = angles.getPitch(AngleUnit.RADIANS);
        double roll = angles.getRoll(AngleUnit.RADIANS);
        double[] z = {Math.cos(yaw / 2), 0, 0, Math.sin(yaw / 2)};        // yaw about Z
        double[] x = {Math.cos(pitch / 2), Math.sin(pitch / 2), 0, 0};    // pitch about X
        double[] y = {Math.cos(roll / 2), 0, Math.sin(roll / 2), 0};      // roll about Y
        return multiply(multiply(z, x), y);
    }

    /** Hamilton product of two {@code {w, x, y, z}} quaternions. */
    private static double[] multiply(double[] a, double[] b) {
        return new double[]{
                a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
                a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
                a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1],
                a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0]};
    }
}
