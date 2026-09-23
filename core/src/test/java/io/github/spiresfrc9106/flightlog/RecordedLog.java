package io.github.spiresfrc9106.flightlog;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import edu.wpi.first.util.datalog.DataLogReader;
import edu.wpi.first.util.datalog.DataLogRecord;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A .wpilog file as WPILib's own reader sees it: every entry, in start order,
 * with its data records. Tests assert against this rather than against our
 * own understanding of the format.
 */
public final class RecordedLog {

    /** One entry: what its Start record declared, and the data written to it. */
    public static final class Entry {
        public final int id;
        public final String name;
        public final String type;
        public final String metadata;
        public final long startTimestamp;
        public final List<DataLogRecord> records = new ArrayList<>();
        public Long finishTimestamp;

        Entry(int id, String name, String type, String metadata, long startTimestamp) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.metadata = metadata;
            this.startTimestamp = startTimestamp;
        }

        public DataLogRecord last() {
            assertTrue(name + " has no records", !records.isEmpty());
            return records.get(records.size() - 1);
        }
    }

    public final DataLogReader reader;
    public final Map<String, Entry> entries = new LinkedHashMap<>();
    public final List<DataLogRecord> all = new ArrayList<>();

    private RecordedLog(DataLogReader reader) {
        this.reader = reader;
        assertTrue("WPILib's reader rejects the file", reader.isValid());
        Map<Integer, Entry> byId = new HashMap<>();
        // NOT a for-each loop. WPILib's DataLogIterator.hasNext() (v2026.2.2,
        // and still on WPILib's main branch in September 2026) only reports a
        // next record if 16 or more bytes remain, but a record can be as small
        // as 5 bytes -- so for-each silently drops the last records of a log
        // whenever they total under 16 bytes. forEachRemaining() uses the
        // correct end test (position < size) and sees every record.
        reader.iterator().forEachRemaining(all::add);
        for (DataLogRecord r : all) {
            if (r.isStart()) {
                DataLogRecord.StartRecordData s = r.getStartData();
                Entry e = new Entry(s.entry, s.name, s.type, s.metadata, r.getTimestamp());
                byId.put(s.entry, e);
                entries.put(s.name, e);
            } else if (r.isFinish()) {
                Entry e = byId.get(r.getFinishEntry());
                assertNotNull("Finish for unknown entry " + r.getFinishEntry(), e);
                e.finishTimestamp = r.getTimestamp();
            } else if (!r.isControl()) {
                Entry e = byId.get(r.getEntry());
                assertNotNull("data for an entry that was never started: " + r.getEntry(), e);
                e.records.add(r);
            }
        }
    }

    public static RecordedLog of(byte[] bytes) {
        return new RecordedLog(new DataLogReader(ByteBuffer.wrap(bytes)));
    }

    public static RecordedLog of(File file) throws IOException {
        return new RecordedLog(new DataLogReader(file.getAbsolutePath()));
    }

    public Entry entry(String name) {
        Entry e = entries.get(name);
        assertNotNull("no entry named " + name + "; have " + entries.keySet(), e);
        return e;
    }

    public boolean has(String name) {
        return entries.containsKey(name);
    }

    public int count(String name) {
        return has(name) ? entries.get(name).records.size() : 0;
    }

    public List<String> strings(String name) {
        List<String> out = new ArrayList<>();
        for (DataLogRecord r : entry(name).records) out.add(r.getString());
        return out;
    }

    public List<Double> doubles(String name) {
        List<Double> out = new ArrayList<>();
        for (DataLogRecord r : entry(name).records) out.add(r.getDouble());
        return out;
    }

    public List<Long> timestamps(String name) {
        List<Long> out = new ArrayList<>();
        for (DataLogRecord r : entry(name).records) out.add(r.getTimestamp());
        return out;
    }

    /** A Pose2d struct record as {x, y, rotation} -- three little-endian doubles. */
    public static double[] pose(DataLogRecord r) {
        double[] d = r.getDoubleArray();   // same bytes: struct Pose2d is three doubles
        return d;
    }
}
