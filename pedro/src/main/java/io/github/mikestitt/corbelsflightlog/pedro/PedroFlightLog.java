package io.github.mikestitt.corbelsflightlog.pedro;

import com.pedropathing.follower.Follower;
import com.pedropathing.follower.FollowerLog;
import com.pedropathing.math.Pose;
import com.pedropathing.math.Twist;
import com.pedropathing.math.Vector2D;
import com.pedropathing.math.Velocity;

import io.github.mikestitt.corbelsflightlog.FlightLog;

import java.util.HashMap;
import java.util.Map;

/**
 * Writes Pedro Pathing's state to a {@link FlightLog}, for AdvantageScope.
 *
 * <p>Two things, either or both:
 * <ul>
 *   <li>{@link #record(Follower)} -- the robot's pose, the aim point, the path
 *       being followed, the mode and velocities. Call once a loop.</li>
 *   <li>{@link #record(FollowerLog)} -- Pedro's own debug maps, flattened into
 *       one channel per entry. Wire with
 *       {@code follower.withLogger(pedroLog::record)} and Pedro calls it every
 *       update.</li>
 * </ul>
 *
 * <p>Everything is written only when it changes (see {@link FlightLog}), so a
 * value that holds still costs one record rather than one per loop.
 *
 * <p>Uses only Pedro's public API. Entries a future Pedro adds to its debug
 * maps appear automatically; a type this doesn't recognise is stored as text.
 */
public final class PedroFlightLog {

    /** Pedro works in inches; WPILib structs are metres. */
    private static final double METRES_PER_INCH = 0.0254;

    /** Points sampled along the path segment when it changes. */
    private static final int PATH_SAMPLES = 20;

    private final FlightLog log;
    private final String prefix;
    private final Map<String, Map<String, Keys>> keys = new HashMap<>();
    private Object loggedSegment;

    /** Logs under {@code Pedro/...}. */
    public PedroFlightLog(FlightLog log) {
        this(log, "Pedro");
    }

    public PedroFlightLog(FlightLog log, String prefix) {
        if (log == null) throw new IllegalArgumentException("log is null");
        this.log = log;
        this.prefix = prefix;
    }

    /**
     * The follower's own state: {@code <prefix>/Pose}, {@code /AimPose},
     * {@code /Path}, {@code /Mode} and {@code /vel/*}. Call once a loop, after
     * {@code follower.update()}.
     */
    public void record(Follower follower) {
        if (follower == null) return;
        Pose pose = follower.pose();
        log.pose(prefix + "/Pose", pose.x(), pose.y(), pose.heading());
        log.text(prefix + "/Mode", String.valueOf(follower.mode()));

        boolean aiming = follower.mode() == Follower.Mode.FOLLOW || follower.mode() == Follower.Mode.HOLD;
        if (aiming && follower.closestPose() != null) {
            Pose aim = follower.closestPose();
            log.pose(prefix + "/AimPose", aim.x(), aim.y(), aim.heading());
        }

        Velocity v = follower.velocity();
        log.number(prefix + "/vel/vx_ips", v.vx);
        log.number(prefix + "/vel/vy_ips", v.vy);
        log.number(prefix + "/vel/omega_radps", v.omega);
        // The same speeds as WPILib structs, in metres: AdvantageScope shows
        // ChassisSpeeds and Twist2d as one value each rather than six numbers.
        log.chassisSpeeds(prefix + "/Speeds", v.vx * METRES_PER_INCH, v.vy * METRES_PER_INCH, v.omega);

        Twist t = follower.twist();
        log.number(prefix + "/vel/forward_ips", t.vx);
        log.number(prefix + "/vel/strafe_ips", t.vy);
        log.twist2d(prefix + "/Twist", t.vx * METRES_PER_INCH, t.vy * METRES_PER_INCH, t.omega);
        // Why the guard rather than calling tangentialVelocity() directly:
        // Observed 2026-09-22 against Pedro Pathing 3.0.1, in a
        // desktop JVM simulation (a stand-in drivetrain and localizer, no
        // hardware): with the follower in MANUAL mode, calling
        // follower.tangentialVelocity() raised NullPointerException from
        // Vector2D.dot, and follower.closestTangent() was null at the time.
        // Logging NaN keeps the call out of that state; FlightLog skips
        // non-finite values, so the channel simply has a gap.
        log.number(prefix + "/vel/tangential_ips",
                aiming && follower.closestTangent() != null ? follower.tangentialVelocity() : Double.NaN);

        recordPath(follower);
    }

    /**
     * The path segment being followed, as a Pose2d[] AdvantageScope draws as a
     * trajectory -- written when the segment changes, so a whole path costs one
     * record. An empty array clears it when the path ends.
     *
     * <p>Why this reads currentSegment() and not the more obvious currentPath():
     * Observed 2026-09-22 by Mike Stitt, against Pedro Pathing 3.0.1, in a
     * desktop JVM simulation (a stand-in drivetrain and localizer, no
     * hardware): after the last segment of a path completed there was exactly
     * one update in which follower.currentPath() returned a path while
     * follower.currentSegment() returned null, and calling follower.poseAt()
     * during that update raised NullPointerException from
     * Follower.currentCurve(). With the drawing forced into that update it
     * reproduced in 5 runs out of 5. currentSegment() is also null when no path
     * is being followed, which is the same answer this method needs, so it is
     * the value tested here.
     */
    private void recordPath(Follower follower) {
        Object segment = follower.currentSegment();
        if (segment == loggedSegment) return;
        loggedSegment = segment;
        if (segment == null) {
            log.poses(prefix + "/Path", new double[0]);
            return;
        }
        double[] xyh = new double[(PATH_SAMPLES + 1) * 3];
        for (int i = 0; i <= PATH_SAMPLES; i++) {
            Pose p = follower.poseAt((double) i / PATH_SAMPLES);
            xyh[3 * i] = p.x();
            xyh[3 * i + 1] = p.y();
            xyh[3 * i + 2] = p.heading();
        }
        log.poses(prefix + "/Path", xyh);
    }

    /**
     * Pedro's own debug data, one channel per entry, named
     * {@code <prefix>/<map>/<entry>}. Wire with
     * {@code follower.withLogger(pedroLog::record)}.
     *
     * <p>Each entry holds whatever Pedro reported on the most recent update, so
     * read them alongside {@code <prefix>/Mode}.
     */
    public void record(FollowerLog followerLog) {
        if (followerLog == null) return;
        flatten("follow", followerLog.followState());
        flatten("algorithm", followerLog.algorithm());
        flatten("localizer", followerLog.localizer());
        flatten("drivetrain", followerLog.drivetrain());
    }

    /** One line of text for a dashboard, built from the maps directly so that a
     *  null map is rendered rather than dereferenced. */
    public static String describe(FollowerLog l) {
        // Why we build this rather than calling FollowerLog.toString():
        // Reading the Pedro Pathing 3.0.1 source on 2026-09-23, we
        // found that toString() calls toString() on each of its maps without a
        // null check, and a team's own Drivetrain or Localizer supplies those
        // maps from its debug() method. Building the
        // text here means a null map prints as "null" instead.
        if (l == null) return "";
        return "follow=" + l.followState() + " algorithm=" + l.algorithm()
                + " localizer=" + l.localizer() + " drivetrain=" + l.drivetrain();
    }

    private void flatten(String map, Map<String, Object> entries) {
        if (entries == null) return;
        for (Map.Entry<String, Object> e : entries.entrySet()) {
            Object v = e.getValue();
            if (v == null) continue;
            Keys k = keys(map, e.getKey());
            if (v instanceof Double || v instanceof Float) {
                log.number(k.base, ((Number) v).doubleValue());
            } else if (v instanceof Number) {                 // Integer, Long, Short, Byte
                log.integer(k.base, ((Number) v).longValue());
            } else if (v instanceof Boolean) {
                log.bool(k.base, (Boolean) v);
            } else if (v instanceof Pose) {
                Pose p = (Pose) v;
                log.pose(k.base, p.x(), p.y(), p.heading());
            } else if (v instanceof Vector2D) {
                Vector2D vec = (Vector2D) v;
                log.number(k.x, vec.x());
                log.number(k.y, vec.y());
            } else if (v instanceof Velocity) {
                Velocity vel = (Velocity) v;
                three(k, vel.vx, vel.vy, vel.omega);
            } else if (v instanceof Twist) {
                Twist t = (Twist) v;
                three(k, t.vx, t.vy, t.omega);
            } else {
                log.text(k.base, v instanceof Enum ? ((Enum<?>) v).name() : String.valueOf(v));
            }
        }
    }

    private void three(Keys k, double vx, double vy, double omega) {
        log.number(k.vx, vx);
        log.number(k.vy, vy);
        log.number(k.omega, omega);
    }

    /** Channel names, built once per entry so the loop allocates none. */
    private static final class Keys {
        final String base;
        final String x;
        final String y;
        final String vx;
        final String vy;
        final String omega;

        Keys(String base) {
            this.base = base;
            x = base + "/x";
            y = base + "/y";
            vx = base + "/vx";
            vy = base + "/vy";
            omega = base + "/omega";
        }
    }

    private Keys keys(String map, String entry) {
        Map<String, Keys> byEntry = keys.get(map);
        if (byEntry == null) {
            byEntry = new HashMap<>();
            keys.put(map, byEntry);
        }
        Keys k = byEntry.get(entry);
        if (k == null) {
            k = new Keys(prefix + "/" + map + "/" + entry);
            byEntry.put(entry, k);
        }
        return k;
    }
}
