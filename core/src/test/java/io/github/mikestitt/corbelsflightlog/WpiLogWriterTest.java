package io.github.mikestitt.corbelsflightlog;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import edu.wpi.first.util.datalog.DataLogRecord;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Byte-level tests of {@link WpiLogWriter}.
 *
 * <p>Two kinds of check. Where WPILib's format specification
 * ({@code allwpilib/datalog/doc/datalog.adoc}) gives exact bytes, the test
 * compares exact bytes. Everything else is written and then read back with
 * WPILib's own reader (vendored under src/test), so a pass means WPILib agrees
 * with the file, not merely that our writer agrees with itself.
 *
 * <p>tools/logcheck goes further and compares against WPILib's native writer.
 */
public class WpiLogWriterTest {

    // ------------------------------------------------------------ helpers

    private static final int HEADER_LEN = 12;   // "WPILOG" + version + empty extra header

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) b[i] = (byte) values[i];
        return b;
    }

    private static byte[] slice(byte[] b, int from, int to) {
        return Arrays.copyOfRange(b, from, to);
    }

    /** Writer over an in-memory buffer, with an empty extra header. */
    private static final class Mem {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final WpiLogWriter w;

        Mem() throws IOException {
            w = new WpiLogWriter(out, "");
        }

        int size() {
            return out.size();
        }

        byte[] bytes() {
            return out.toByteArray();
        }

        RecordedLog read() {
            return RecordedLog.of(out.toByteArray());
        }
    }

    // ------------------------------------------------------------ header

    @Test
    public void headerMatchesTheSpecExampleByteForByte() throws IOException {
        Mem m = new Mem();
        // Spec: "The entire header for a version 1.0 file with no extra header
        // string will be 57 50 49 4c 4f 47 00 01 00 00 00 00."
        assertArrayEquals(bytes(0x57, 0x50, 0x49, 0x4c, 0x4f, 0x47, 0x00, 0x01, 0, 0, 0, 0), m.bytes());
    }

    @Test
    public void nullExtraHeaderIsWrittenAsEmpty() throws IOException {
        ByteArrayOutputStream a = new ByteArrayOutputStream();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        new WpiLogWriter(a, null);
        new WpiLogWriter(b, "");
        assertArrayEquals(b.toByteArray(), a.toByteArray());
    }

    @Test
    public void extraHeaderIsUtf8AndWpilibReadsIt() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String header = "Corbels FlightLog é 🤖";
        new WpiLogWriter(out, header);
        RecordedLog log = RecordedLog.of(out.toByteArray());
        assertEquals(0x0100, log.reader.getVersion());
        assertEquals(header, log.reader.getExtraHeader());
        int utf8Len = header.getBytes(StandardCharsets.UTF_8).length;
        assertEquals(HEADER_LEN + utf8Len, out.size());
    }

    // ------------------------------------------------------------ spec examples

    @Test
    public void startRecordMatchesTheSpecExample() throws IOException {
        Mem m = new Mem();
        m.w.start("test", "int64", "", 1_000_000);
        // The spec's example is 32 bytes. (Its printed type bytes contain a
        // typo -- "69 6e 74 66 64" -- but its stated length, 5, is "int64".)
        assertArrayEquals(bytes(
                0x20, 0x00, 0x1a, 0x40, 0x42, 0x0f,
                0x00,
                0x01, 0x00, 0x00, 0x00,
                0x04, 0x00, 0x00, 0x00, 't', 'e', 's', 't',
                0x05, 0x00, 0x00, 0x00, 'i', 'n', 't', '6', '4',
                0x00, 0x00, 0x00, 0x00), slice(m.bytes(), HEADER_LEN, m.size()));
    }

    @Test
    public void int64RecordMatchesTheSpecExample() throws IOException {
        Mem m = new Mem();
        int id = m.w.start("test", "int64", "", 1_000_000);
        int before = m.size();
        m.w.appendInt64(id, 3, 1_000_000);
        assertArrayEquals(bytes(0x20, 0x01, 0x08, 0x40, 0x42, 0x0f, 3, 0, 0, 0, 0, 0, 0, 0),
                slice(m.bytes(), before, m.size()));
    }

    @Test
    public void finishRecordMatchesTheSpecExample() throws IOException {
        Mem m = new Mem();
        int id = m.w.start("test", "int64", "", 1_000_000);
        int before = m.size();
        m.w.finish(id, 1_000_000);
        assertArrayEquals(bytes(0x20, 0x00, 0x05, 0x40, 0x42, 0x0f, 0x01, 0x01, 0, 0, 0),
                slice(m.bytes(), before, m.size()));
    }

    // ------------------------------------------------------------ start / finish

    @Test
    public void entryIdsAreSequentialFromOne() throws IOException {
        Mem m = new Mem();
        assertEquals(1, m.w.start("/a", "double", "", 1));
        assertEquals(2, m.w.start("/b", "double", "", 1));
        assertEquals(3, m.w.start("/c", "double", "", 1));
    }

    @Test
    public void startRecordFieldsReadBackIncludingUtf8AndMetadata() throws IOException {
        Mem m = new Mem();
        m.w.start("/moteur/vitesse é", "double", "{\"source\":\"test\"}", 42);
        RecordedLog.Entry e = m.read().entry("/moteur/vitesse é");
        assertEquals("double", e.type);
        assertEquals("{\"source\":\"test\"}", e.metadata);
        assertEquals(42, e.startTimestamp);
        assertEquals(1, e.id);
    }

    @Test
    public void nullStartStringsAreWrittenAsEmpty() throws IOException {
        Mem m = new Mem();
        m.w.start(null, null, null, 1);
        RecordedLog.Entry e = m.read().entry("");
        assertEquals("", e.type);
        assertEquals("", e.metadata);
    }

    @Test
    public void finishRecordReadsBackAsAFinish() throws IOException {
        Mem m = new Mem();
        int id = m.w.start("/a", "double", "", 1);
        m.w.finish(id, 99);
        RecordedLog log = m.read();
        DataLogRecord last = log.all.get(log.all.size() - 1);
        assertTrue(last.isFinish());
        assertEquals(id, last.getFinishEntry());
        assertEquals(Long.valueOf(99), log.entry("/a").finishTimestamp);
    }

    // ------------------------------------------------------------ scalar types

    @Test
    public void doublesRoundTripBitForBit() throws IOException {
        double[] values = {0.0, -0.0, 1.5, -2.25, Double.MIN_VALUE, Double.MAX_VALUE,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN, Math.PI};
        Mem m = new Mem();
        int id = m.w.start("/d", "double", "", 0);
        for (int i = 0; i < values.length; i++) m.w.appendDouble(id, values[i], i + 1);
        RecordedLog.Entry e = m.read().entry("/d");
        assertEquals(values.length, e.records.size());
        for (int i = 0; i < values.length; i++) {
            assertEquals("value " + i, Double.doubleToRawLongBits(values[i]),
                    Double.doubleToRawLongBits(e.records.get(i).getDouble()));
            assertEquals(i + 1, e.records.get(i).getTimestamp());
        }
    }

    @Test
    public void floatsAreFourBytesAndRoundTripBitForBit() throws IOException {
        float[] values = {0.1f, -0.0f, Float.MAX_VALUE, Float.MIN_VALUE, Float.NaN,
                Float.NEGATIVE_INFINITY};
        Mem m = new Mem();
        int id = m.w.start("/f", "float", "", 0);
        int before = m.size();
        m.w.appendFloat(id, 1.0f, 1);
        assertEquals("header byte + id + size + 1-byte ts + 4-byte payload", 1 + 1 + 1 + 1 + 4,
                m.size() - before);
        for (float v : values) m.w.appendFloat(id, v, 2);
        RecordedLog.Entry e = m.read().entry("/f");
        for (int i = 0; i < values.length; i++) {
            DataLogRecord r = e.records.get(i + 1);
            assertEquals(4, r.getSize());
            assertEquals(Float.floatToRawIntBits(values[i]), Float.floatToRawIntBits(r.getFloat()));
        }
    }

    @Test
    public void int64sRoundTripIncludingExtremes() throws IOException {
        long[] values = {0, 1, -1, 3, Long.MIN_VALUE, Long.MAX_VALUE, 0x0123456789ABCDEFL};
        Mem m = new Mem();
        int id = m.w.start("/i", "int64", "", 0);
        for (long v : values) m.w.appendInt64(id, v, 5);
        RecordedLog.Entry e = m.read().entry("/i");
        for (int i = 0; i < values.length; i++) assertEquals(values[i], e.records.get(i).getInteger());
    }

    @Test
    public void booleansAreOneByteZeroOrOne() throws IOException {
        Mem m = new Mem();
        int id = m.w.start("/b", "boolean", "", 0);
        m.w.appendBoolean(id, true, 1);
        m.w.appendBoolean(id, false, 2);
        RecordedLog.Entry e = m.read().entry("/b");
        assertTrue(e.records.get(0).getBoolean());
        assertFalse(e.records.get(1).getBoolean());
        assertArrayEquals(new byte[]{1}, e.records.get(0).getRaw());
        assertArrayEquals(new byte[]{0}, e.records.get(1).getRaw());
    }

    @Test
    public void stringsAreUtf8AndNullIsEmpty() throws IOException {
        Mem m = new Mem();
        int id = m.w.start("/s", "string", "", 0);
        m.w.appendString(id, "héllo 🤖", 1);
        m.w.appendString(id, "", 2);
        m.w.appendString(id, null, 3);
        RecordedLog log = m.read();
        assertEquals(Arrays.asList("héllo 🤖", "", ""), log.strings("/s"));
        assertEquals(0, log.entry("/s").records.get(2).getSize());
    }

    @Test
    public void rawWritesExactlyTheRequestedLength() throws IOException {
        Mem m = new Mem();
        int id = m.w.start("/r", "raw", "", 0);
        m.w.appendRaw(id, new byte[]{1, 2, 3, 4, 5}, 3, 1);
        m.w.appendRaw(id, new byte[]{9}, 0, 2);
        RecordedLog.Entry e = m.read().entry("/r");
        assertArrayEquals(new byte[]{1, 2, 3}, e.records.get(0).getRaw());
        assertEquals(0, e.records.get(1).getSize());
    }

    // ------------------------------------------------------------ array types

    @Test
    public void doubleArraysRoundTripIncludingEmpty() throws IOException {
        Mem m = new Mem();
        int id = m.w.start("/da", "double[]", "", 0);
        m.w.appendDoubleArray(id, new double[]{1.0, -0.0, Double.NaN, 2.5}, 1);
        m.w.appendDoubleArray(id, new double[0], 2);
        RecordedLog.Entry e = m.read().entry("/da");
        double[] got = e.records.get(0).getDoubleArray();
        assertEquals(4, got.length);
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(got[1]));
        assertTrue(Double.isNaN(got[2]));
        assertEquals(0, e.records.get(1).getDoubleArray().length);
    }

    @Test
    public void int64ArraysRoundTripIncludingEmpty() throws IOException {
        Mem m = new Mem();
        int id = m.w.start("/ia", "int64[]", "", 0);
        m.w.appendInt64Array(id, new long[]{Long.MIN_VALUE, 0, Long.MAX_VALUE}, 1);
        m.w.appendInt64Array(id, new long[0], 2);
        RecordedLog.Entry e = m.read().entry("/ia");
        assertArrayEquals(new long[]{Long.MIN_VALUE, 0, Long.MAX_VALUE}, e.records.get(0).getIntegerArray());
        assertEquals(0, e.records.get(1).getIntegerArray().length);
    }

    @Test
    public void booleanArraysRoundTripIncludingEmpty() throws IOException {
        Mem m = new Mem();
        int id = m.w.start("/ba", "boolean[]", "", 0);
        m.w.appendBooleanArray(id, new boolean[]{true, false, true}, 1);
        m.w.appendBooleanArray(id, new boolean[0], 2);
        RecordedLog.Entry e = m.read().entry("/ba");
        assertArrayEquals(new boolean[]{true, false, true}, e.records.get(0).getBooleanArray());
        assertEquals(0, e.records.get(1).getBooleanArray().length);
    }

    // ------------------------------------------------------------ header field widths

    /** Bits 4-6 of a record's first byte: timestamp length - 1. */
    private static int timestampWidth(byte bitfield) {
        return ((bitfield >> 4) & 0x7) + 1;
    }

    private static int sizeWidth(byte bitfield) {
        return ((bitfield >> 2) & 0x3) + 1;
    }

    private static int idWidth(byte bitfield) {
        return (bitfield & 0x3) + 1;
    }

    @Test
    public void timestampFieldUsesTheFewestBytesAtEveryBoundary() throws IOException {
        long[] ts = {0L, 0xFFL, 0x100L, 0xFFFFL, 0x1_0000L, 0xFF_FFFFL, 0x100_0000L,
                0xFFFF_FFFFL, 0x1_0000_0000L, 0xFF_FFFF_FFFFL, 0x100_0000_0000L,
                0xFFFF_FFFF_FFFFL, 0x1_0000_0000_0000L, 0xFF_FFFF_FFFF_FFFFL,
                0x100_0000_0000_0000L, Long.MAX_VALUE, -1L, Long.MIN_VALUE};
        int[] width = {1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 8, 8};
        Mem m = new Mem();
        int id = m.w.start("/b", "boolean", "", 0);
        for (int i = 0; i < ts.length; i++) {
            int before = m.size();
            m.w.appendBoolean(id, true, ts[i]);
            byte bitfield = m.bytes()[before];
            assertEquals("timestamp " + Long.toHexString(ts[i]), width[i], timestampWidth(bitfield));
            assertEquals("record length", 1 + 1 + 1 + width[i] + 1, m.size() - before);
            assertEquals("spare bit is zero", 0, bitfield & 0x80);
        }
        RecordedLog.Entry e = m.read().entry("/b");
        for (int i = 0; i < ts.length; i++) assertEquals(ts[i], e.records.get(i).getTimestamp());
    }

    @Test
    public void payloadSizeFieldUsesTheFewestBytesAtEveryBoundary() throws IOException {
        int[] sizes = {0, 0xFF, 0x100, 0xFFFF, 0x1_0000, 0xFF_FFFF, 0x100_0000};
        int[] width = {1, 1, 2, 2, 3, 3, 4};
        byte[] payload = new byte[0x100_0000];
        payload[0x100_0000 - 1] = 7;
        for (int i = 0; i < sizes.length; i++) {
            Mem m = new Mem();
            int id = m.w.start("/r", "raw", "", 0);
            int before = m.size();
            m.w.appendRaw(id, payload, sizes[i], 1);
            byte bitfield = m.bytes()[before];
            assertEquals("payload " + sizes[i], width[i], sizeWidth(bitfield));
            RecordedLog.Entry e = m.read().entry("/r");
            assertEquals(sizes[i], e.records.get(0).getSize());
        }
    }

    @Test
    public void entryIdFieldUsesTheFewestBytesAtEveryBoundary() throws Exception {
        int[] ids = {1, 0xFF, 0x100, 0xFFFF, 0x1_0000, 0xFF_FFFF, 0x100_0000, Integer.MAX_VALUE};
        int[] width = {1, 1, 2, 2, 3, 3, 4, 4};
        Field next = WpiLogWriter.class.getDeclaredField("nextEntryId");
        next.setAccessible(true);
        for (int i = 0; i < ids.length; i++) {
            Mem m = new Mem();
            next.setInt(m.w, ids[i]);          // skip ahead rather than start millions of entries
            int id = m.w.start("/e", "boolean", "", 0);
            assertEquals(ids[i], id);
            int before = m.size();
            m.w.appendBoolean(id, true, 1);
            assertEquals("id " + Integer.toHexString(ids[i]), width[i], idWidth(m.bytes()[before]));
            assertEquals(ids[i], m.read().entry("/e").records.get(0).getEntry());
        }
    }

    // ------------------------------------------------------------ stream behaviour

    @Test
    public void flushAndCloseReachTheStream() throws IOException {
        TestStreams.Recording out = new TestStreams.Recording();
        WpiLogWriter w = new WpiLogWriter(out, "");
        w.flush();
        w.flush();
        w.close();
        assertEquals(2, out.flushes);
        assertEquals(1, out.closes);
    }

    @Test
    public void writeFailuresPropagate() throws IOException {
        try {
            new WpiLogWriter(new TestStreams.Failing(3), "");
            fail("header write should have failed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("storage full"));
        }
        WpiLogWriter w = new WpiLogWriter(new TestStreams.Failing(HEADER_LEN), "");
        try {
            w.start("/a", "double", "", 1);
            fail("start should have failed");
        } catch (IOException expected) {
            // expected
        }
    }

    @Test
    public void everyAppendPropagatesAWriteFailure() throws IOException {
        String[] kinds = {"double", "float", "int64", "boolean", "string", "raw",
                "double[]", "int64[]", "boolean[]", "finish"};
        for (String kind : kinds) {
            TestStreams.Failing out = new TestStreams.Failing(1_000);
            WpiLogWriter w = new WpiLogWriter(out, "");
            int id = w.start("/x", kind, "", 1);
            out.write(new byte[1_000 - HEADER_LEN - 27 - kind.length()]);  // fill to capacity
            try {
                switch (kind) {
                    case "double": w.appendDouble(id, 1, 1); break;
                    case "float": w.appendFloat(id, 1, 1); break;
                    case "int64": w.appendInt64(id, 1, 1); break;
                    case "boolean": w.appendBoolean(id, true, 1); break;
                    case "string": w.appendString(id, "x", 1); break;
                    case "raw": w.appendRaw(id, new byte[]{1}, 1, 1); break;
                    case "double[]": w.appendDoubleArray(id, new double[]{1}, 1); break;
                    case "int64[]": w.appendInt64Array(id, new long[]{1}, 1); break;
                    case "boolean[]": w.appendBooleanArray(id, new boolean[]{true}, 1); break;
                    default: w.finish(id, 1); break;
                }
                fail(kind + " should have failed");
            } catch (IOException expected) {
                // expected
            }
        }
    }
}
