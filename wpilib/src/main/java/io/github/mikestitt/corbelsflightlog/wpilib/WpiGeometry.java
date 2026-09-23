package io.github.mikestitt.corbelsflightlog.wpilib;

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

/**
 * Logs WPILib's own geometry types, for teams that already hold them.
 *
 * <p>Nothing is converted here: these values are already in the units and the
 * layout a WPILOG struct carries, so each one is written straight through.
 * Teams whose poses come from Pedro or the FTC SDK want those modules instead,
 * which convert.
 */
public final class WpiGeometry {

    private WpiGeometry() {
    }

    public static void recordOutput(FlightLog log, String key, Translation2d value) {
        if (log == null || value == null) return;
        log.translation2d(key, value.getX(), value.getY());
    }

    public static void recordOutput(FlightLog log, String key, Rotation2d value) {
        if (log == null || value == null) return;
        log.rotation2d(key, value.getRadians());
    }

    public static void recordOutput(FlightLog log, String key, Pose2d value) {
        if (log == null || value == null) return;
        log.pose2d(key, value.getX(), value.getY(), value.getRotation().getRadians());
    }

    public static void recordOutput(FlightLog log, String key, Twist2d value) {
        if (log == null || value == null) return;
        log.twist2d(key, value.dx, value.dy, value.dtheta);
    }

    public static void recordOutput(FlightLog log, String key, ChassisSpeeds value) {
        if (log == null || value == null) return;
        log.chassisSpeeds(key, value.vxMetersPerSecond, value.vyMetersPerSecond,
                value.omegaRadiansPerSecond);
    }

    public static void recordOutput(FlightLog log, String key, MecanumDriveWheelSpeeds value) {
        if (log == null || value == null) return;
        log.mecanumWheelSpeeds(key, value.frontLeftMetersPerSecond, value.frontRightMetersPerSecond,
                value.rearLeftMetersPerSecond, value.rearRightMetersPerSecond);
    }

    public static void recordOutput(FlightLog log, String key, Translation3d value) {
        if (log == null || value == null) return;
        log.translation3d(key, value.getX(), value.getY(), value.getZ());
    }

    public static void recordOutput(FlightLog log, String key, Quaternion value) {
        if (log == null || value == null) return;
        log.quaternion(key, value.getW(), value.getX(), value.getY(), value.getZ());
    }

    public static void recordOutput(FlightLog log, String key, Rotation3d value) {
        if (log == null || value == null) return;
        Quaternion q = value.getQuaternion();
        log.rotation3d(key, q.getW(), q.getX(), q.getY(), q.getZ());
    }

    public static void recordOutput(FlightLog log, String key, Pose3d value) {
        if (log == null || value == null) return;
        Translation3d t = value.getTranslation();
        Quaternion q = value.getRotation().getQuaternion();
        log.pose3d(key, t.getX(), t.getY(), t.getZ(), q.getW(), q.getX(), q.getY(), q.getZ());
    }
}
