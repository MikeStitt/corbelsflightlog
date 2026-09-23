package io.github.spiresfrc9106.flightlog.pedro;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.algorithm.ForesightConfig;
import com.pedropathing.api.PoseFactory;
import com.pedropathing.controllers.Controller;
import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.drivetrain.Drivetrain;
import com.pedropathing.follower.Follower;
import com.pedropathing.localization.Localizer;
import com.pedropathing.localization.MotionState;
import com.pedropathing.math.Matrix;
import com.pedropathing.math.Pose;
import com.pedropathing.math.Twist;
import com.pedropathing.math.Vector2D;

import java.util.HashMap;
import java.util.Map;

/**
 * A real Pedro {@link Follower} driving a simulated robot, for tests.
 *
 * <p>The drivetrain keeps Pedro's commanded powers; the localizer turns them
 * into motion (power x max speed, with a short lag standing in for inertia)
 * and integrates a pose. Crude physics, but enough for the follower to finish
 * a path the way it does on the robot.
 */
final class SimRobot {

    static final double MAX_FORWARD_IPS = 64.4;
    static final double MAX_STRAFE_IPS = 43.6;
    static final double MAX_TURN_RADPS = 4.0;
    static final double LAG_S = 0.15;

    final SimDrive drive = new SimDrive();
    final SimLocalizer localizer = new SimLocalizer(drive);
    final Follower follower = new Follower(localizer, drive, new Foresight(config()));

    static final PoseFactory POSES = PoseFactory.degrees();

    /**
     * Foresight settings for the simulation. A copy of values tuned on the
     * robot in September 2026; retuning the robot doesn't require changing
     * these -- they only need to let the follower finish a path.
     */
    static ForesightConfig config() {
        return new ForesightConfig(c -> {
            Controller forwardP = Controller.proportional(0.368944499316663);
            Controller forwardS = Controller.proportional(0.13631513411892088);
            Controller strafeP = Controller.proportional(0.28643896916380074);
            Controller strafeS = Controller.proportional(0.10583154531580646);
            c.forwardTranslational.set(Controller.piecewise(forwardS).put(2.5, forwardP));
            c.strafeTranslational.set(Controller.piecewise(strafeS).put(2.5, strafeP));
            c.coast.set(Controller.proportionalFeedforward(0.016695978563625216));
            c.brake.set(Controller.proportionalFeedforward(0.014191581779081433));
            c.headingFeedback.set(Controller.proportional(4.443284774829611));
            c.headingBrakeCoefficients.set(Vector2D.cartesian(0.20442551239984175, -0.004974765042140602));
            c.linearBrakeCoefficients.set(Matrix.diag(0.04993877265618792, 0.08334033043867983));
            c.quadraticBrakeCoefficients.set(Matrix.diag(0.0011536551239685066, 6.430819946483606E-4));
            c.maxAchievableForwardVelocity.set(MAX_FORWARD_IPS);
            c.maxAchievableStrafeVelocity.set(MAX_STRAFE_IPS);
            c.naturalForwardDeceleration.set(46.50217822427653);
            c.naturalStrafeDeceleration.set(62.02176070971554);
        });
    }

    static final class SimDrive implements Drivetrain {
        volatile DrivePowers last = DrivePowers.zero();

        @Override
        public void drive(DrivePowers powers, boolean manual) {
            last = powers;
        }

        @Override
        public double maxScaling(DrivePowers a, DrivePowers b) {
            return 1;
        }

        @Override
        public void stop() {
            last = DrivePowers.zero();
        }

        @Override
        public void stop(boolean brake) {
            stop();
        }

        @Override
        public double interpolateVelocity(double xRatio, double yRatio, double t) {
            return MAX_FORWARD_IPS;
        }

        @Override
        public Map<String, Object> debug() {
            return new HashMap<>();
        }
    }

    static final class SimLocalizer implements Localizer {
        private final SimDrive drive;
        private double x;
        private double y;
        private double heading;
        private double vx;
        private double vy;
        private double omega;
        private long lastNs;
        private MotionState state = MotionState.zero();

        SimLocalizer(SimDrive drive) {
            this.drive = drive;
        }

        @Override
        public void setPose(Pose p) {
            x = p.x();
            y = p.y();
            heading = p.heading();
            state = state.withPose(p);
        }

        @Override
        public MotionState state() {
            return state;
        }

        @Override
        public void reset() {
            x = y = heading = vx = vy = omega = 0;
            state = MotionState.zero();
        }

        @Override
        public void update() {
            long now = System.nanoTime();
            double dt = lastNs == 0 ? 0 : (now - lastNs) / 1e9;
            lastNs = now;
            double k = Math.min(1, dt / LAG_S);
            vx += (drive.last.forward() * MAX_FORWARD_IPS - vx) * k;
            vy += (drive.last.strafe() * MAX_STRAFE_IPS - vy) * k;
            omega += (drive.last.turn() * MAX_TURN_RADPS - omega) * k;
            x += (vx * Math.cos(heading) - vy * Math.sin(heading)) * dt;
            y += (vx * Math.sin(heading) + vy * Math.cos(heading)) * dt;
            heading += omega * dt;
            state = MotionState.ofTwist(POSES.of(x, y, Math.toDegrees(heading)), new Twist(vx, vy, omega));
        }
    }

}
