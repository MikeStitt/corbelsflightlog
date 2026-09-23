"""WPILib parity tests for the Corbels WPILOG logging.

Compares the files our Java writer produced (TeamCode/build/logcheck/, written
by ParityFilesTest.java) against WPILib's own native C++ writer and WPILib's
own struct and geometry code, all via robotpy 2026.2.2.

Run from the repository root, after the Java tests:

    ./gradlew :core:test
    python -m unittest discover -s tools/logcheck -v

Set LOGCHECK_DIR to read our files from somewhere else.
"""

import math
import os
import tempfile
import unittest

import wpimath.geometry as geom
import wpiutil
import wpiutil.log as wlog
import wpiutil.wpistruct as wstruct

HERE = os.path.dirname(os.path.abspath(__file__))
SCENARIO = os.path.join(HERE, "scenario.tsv")
REPO = os.path.dirname(os.path.dirname(HERE))
OURS = os.environ.get("LOGCHECK_DIR", os.path.join(REPO, "core", "build", "logcheck"))

QUARTER_TURNS = 4
FIELD_CENTER_IN = 72.0
EPS = 1e-12


def rows():
    """The scenario's non-comment lines, split on tabs."""
    with open(SCENARIO, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line and not line.startswith("#"):
                yield line.split("\t")


def field(row, i):
    """Field i, or "" if the line ends before it -- so editors that strip
    trailing whitespace can't change or break the scenario."""
    return row[i] if i < len(row) else ""


def items(field):
    return [] if field == "" else field.split(",")


def parse_bool(s):
    return s == "true"


def write_with_wpilib(path):
    """Writes the scenario with WPILib's native writer. Returns record count."""
    header = next((field(r, 1) for r in rows() if r[0] == "header"), "")
    log = wpiutil.DataLogWriter(path, header)
    ids, types, records = {}, {}, 0
    for r in rows():
        kind = r[0]
        if kind == "start":
            ids[r[2]] = log.start(r[2], r[3], field(r, 4), int(r[1]))
            types[r[2]] = r[3]
        elif kind == "data":
            i, t, ts, v = ids[r[2]], types[r[2]], int(r[1]), field(r, 3)
            if t == "double":
                log.appendDouble(i, float(v), ts)
            elif t == "float":
                log.appendFloat(i, float(v), ts)
            elif t == "int64":
                log.appendInteger(i, int(v), ts)
            elif t == "boolean":
                log.appendBoolean(i, parse_bool(v), ts)
            elif t == "string":
                log.appendString(i, v, ts)
            elif t == "raw":
                log.appendRaw(i, bytes.fromhex(v), ts)
            elif t == "double[]":
                log.appendDoubleArray(i, [float(x) for x in items(v)], ts)
            elif t == "int64[]":
                log.appendIntegerArray(i, [int(x) for x in items(v)], ts)
            elif t == "boolean[]":
                log.appendBooleanArray(i, [parse_bool(x) for x in items(v)], ts)
            else:
                raise ValueError(f"scenario uses unknown type {t}")
        elif kind == "finish":
            log.finish(ids[r[2]], int(r[1]))
        else:
            continue
        records += 1
    log.flush()
    log.stop()
    del log
    return records


def decode(path):
    """Every record, as WPILib's reader sees it, in a comparable form."""
    reader = wlog.DataLogReader(path)
    names, out = {}, []
    for rec in reader:
        if rec.isStart():
            d = rec.getStartData()
            names[d.entry] = d.name
            out.append(("start", rec.getTimestamp(), d.name, d.type, d.metadata))
        elif rec.isFinish():
            out.append(("finish", rec.getTimestamp(), names[rec.getFinishEntry()]))
        elif rec.isControl():
            out.append(("control", rec.getTimestamp()))
        else:
            out.append(("data", rec.getTimestamp(), names[rec.getEntry()], bytes(rec.getRaw()).hex()))
    return reader, out


def our_pose_file(q):
    d = os.path.join(OURS, f"poses-q{q}")
    files = [f for f in os.listdir(d) if f.endswith(".wpilog")] if os.path.isdir(d) else []
    if len(files) != 1:
        raise unittest.SkipTest(f"expected one pose file in {d}; run the Java tests first")
    return os.path.join(d, files[0])


def scenario_poses():
    return [(float(r[1]), float(r[2]), float(r[3])) for r in rows() if r[0] == "pose"]


def expected_pose(x_in, y_in, heading_deg, q):
    """Pedro pose -> AdvantageScope's FTC field frame, via WPILib's geometry."""
    turn = geom.Rotation2d.fromDegrees(90.0 * q)
    t = geom.Translation2d((x_in - FIELD_CENTER_IN) * 0.0254,
                           (y_in - FIELD_CENTER_IN) * 0.0254).rotateBy(turn)
    r = geom.Rotation2d.fromDegrees(heading_deg).rotateBy(turn)
    return t, r


class WriterParity(unittest.TestCase):
    """Our WpiLogWriter vs WPILib's native DataLogWriter."""

    @classmethod
    def setUpClass(cls):
        cls.ours = os.path.join(OURS, "writer.wpilog")
        if not os.path.isfile(cls.ours):
            raise unittest.SkipTest(f"{cls.ours} not found; run the Java tests first")
        cls.tmp = tempfile.TemporaryDirectory()
        cls.wpilib = os.path.join(cls.tmp.name, "wpilib.wpilog")
        cls.records = write_with_wpilib(cls.wpilib)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_files_are_byte_for_byte_identical(self):
        with open(self.ours, "rb") as a, open(self.wpilib, "rb") as b:
            ours, theirs = a.read(), b.read()
        if ours != theirs:
            # Show the first differing RECORD, which is far easier to act on.
            _, ro = decode(self.ours)
            _, rw = decode(self.wpilib)
            for i, (x, y) in enumerate(zip(ro, rw)):
                self.assertEqual(x, y, f"first differing record is #{i}")
            self.assertEqual(len(ro), len(rw), "record counts differ")
            self.fail(f"bytes differ but records match (sizes {len(ours)} vs {len(theirs)})")

    def test_wpilib_reads_every_record(self):
        reader, recs = decode(self.ours)
        self.assertTrue(reader.isValid())
        self.assertEqual(0x0100, reader.getVersion())
        header = next((field(r, 1) for r in rows() if r[0] == "header"), "")
        self.assertEqual(header, reader.getExtraHeader())
        self.assertEqual(self.records, len(recs))


class StructParity(unittest.TestCase):
    """Our Pose2d structs vs WPILib's struct schema, encoder and geometry."""

    def wpilib_schemas(self):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "schema.wpilog")
            log = wpiutil.DataLogWriter(p, "")
            log.addStructSchema(geom.Pose2d, 1)
            log.flush()
            log.stop()
            del log
            _, recs = decode(p)
        return schema_view(recs)

    def test_schema_entries_match_wpilib_exactly(self):
        theirs = self.wpilib_schemas()
        self.assertEqual(3, len(theirs))
        for q in range(QUARTER_TURNS):
            _, recs = decode(our_pose_file(q))
            self.assertEqual(theirs, schema_view(recs), f"quarter turns {q}")

    def test_poses_decode_with_wpilib_to_the_expected_field_pose(self):
        poses = scenario_poses()
        for q in range(QUARTER_TURNS):
            _, recs = decode(our_pose_file(q))
            types = {r[2]: r[3] for r in recs if r[0] == "start"}
            data = {r[2]: bytes.fromhex(r[3]) for r in recs if r[0] == "data"}
            for i, (x, y, h) in enumerate(poses):
                name = f"/Pose/{i}"
                with self.subTest(q=q, pose=i):
                    self.assertEqual("struct:Pose2d", types[name])
                    got = wstruct.unpack(geom.Pose2d, data[name])
                    t, r = expected_pose(x, y, h, q)
                    self.assertAlmostEqual(t.x, got.x, delta=EPS)
                    self.assertAlmostEqual(t.y, got.y, delta=EPS)
                    # Same angle (compare direction, not raw radians)...
                    self.assertAlmostEqual(r.cos(), got.rotation().cos(), delta=EPS)
                    self.assertAlmostEqual(r.sin(), got.rotation().sin(), delta=EPS)
                    # ...and ours keeps headings in [0, 2 pi), like Pedro.
                    self.assertGreaterEqual(got.rotation().radians(), 0.0)
                    self.assertLess(got.rotation().radians(), 2 * math.pi)

    def test_pose_bytes_reencode_identically_with_wpilib(self):
        for q in range(QUARTER_TURNS):
            _, recs = decode(our_pose_file(q))
            for r in recs:
                if r[0] == "data" and r[2].startswith("/Pose/"):
                    b = bytes.fromhex(r[3])
                    self.assertEqual(b, wstruct.pack(wstruct.unpack(geom.Pose2d, b)), r[2])

    def test_pose_array_matches_the_single_poses(self):
        poses = scenario_poses()
        for q in range(QUARTER_TURNS):
            _, recs = decode(our_pose_file(q))
            types = {r[2]: r[3] for r in recs if r[0] == "start"}
            data = {r[2]: bytes.fromhex(r[3]) for r in recs if r[0] == "data"}
            self.assertEqual("struct:Pose2d[]", types["/Path"])
            arr = wstruct.unpackArray(geom.Pose2d, data["/Path"])
            self.assertEqual(len(poses), len(arr))
            for i, p in enumerate(arr):
                self.assertEqual(data[f"/Pose/{i}"], wstruct.pack(p), f"q={q} pose {i}")


def schema_view(recs):
    """(name, type, schema text) for each struct schema entry, in order.
    Timestamps are left out: WPILib's writer stamps schemas with "now"."""
    starts = {r[2]: r[3] for r in recs if r[0] == "start"}
    return [(r[2], starts[r[2]], bytes.fromhex(r[3]).decode("utf-8"))
            for r in recs if r[0] == "data" and r[2].startswith("/.schema/")]


if __name__ == "__main__":
    unittest.main()
