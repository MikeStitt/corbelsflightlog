package io.github.mikestitt.corbelsflightlog;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Minimal writer for WPILib's WPILOG 1.0 binary log format -- the format
 * AdvantageScope opens.
 *
 * <p>Written from WPILib's published specification,
 * {@code allwpilib/datalog/doc/datalog.adoc}. The format in brief:
 * <ul>
 *   <li>A header: {@code "WPILOG"}, version {@code 0x0100}, and an optional
 *       free-text "extra header".</li>
 *   <li>Then records. Each is a one-byte bitfield giving the byte lengths of
 *       the three fields that follow -- entry ID (1-4 bytes), payload size
 *       (1-4) and timestamp in microseconds (1-8) -- then the payload.
 *       Everything is little-endian, and each field uses as few bytes as
 *       its value needs.</li>
 *   <li>Entry ID 0 is reserved for control records. A Start control record
 *       names an entry and gives its type ({@code "double"},
 *       {@code "string"}, ...) and must come before any data for it.</li>
 * </ul>
 *
 * <p>Not thread-safe: call from the OpMode thread only.
 */
public final class WpiLogWriter implements Closeable {

    private static final int CONTROL_START = 0;
    private static final int CONTROL_FINISH = 1;

    private final OutputStream out;
    private final byte[] scratch = new byte[8];
    private int nextEntryId = 1;

    public WpiLogWriter(OutputStream out, String extraHeader) throws IOException {
        this.out = out;
        out.write(new byte[]{'W', 'P', 'I', 'L', 'O', 'G'});
        writeLittle(0x0100, 2);  // version 1.0 -> bytes 00 01
        byte[] extra = utf8(extraHeader);
        writeLittle(extra.length, 4);
        out.write(extra);
    }

    /** Declares an entry and returns its ID. Must precede any data for it. */
    public int start(String name, String type, String metadata, long timestampUs) throws IOException {
        int id = nextEntryId++;
        byte[] n = utf8(name);
        byte[] t = utf8(type);
        byte[] m = utf8(metadata);
        int payload = 1 + 4 + 4 + n.length + 4 + t.length + 4 + m.length;
        recordHeader(0, payload, timestampUs);
        out.write(CONTROL_START);
        writeLittle(id, 4);
        writeLittle(n.length, 4);
        out.write(n);
        writeLittle(t.length, 4);
        out.write(t);
        writeLittle(m.length, 4);
        out.write(m);
        return id;
    }

    /** Marks an entry as no longer used. */
    public void finish(int id, long timestampUs) throws IOException {
        recordHeader(0, 5, timestampUs);
        out.write(CONTROL_FINISH);
        writeLittle(id, 4);
    }

    public void appendDouble(int id, double value, long timestampUs) throws IOException {
        recordHeader(id, 8, timestampUs);
        writeLittle(Double.doubleToRawLongBits(value), 8);
    }

    public void appendInt64(int id, long value, long timestampUs) throws IOException {
        recordHeader(id, 8, timestampUs);
        writeLittle(value, 8);
    }

    public void appendFloat(int id, float value, long timestampUs) throws IOException {
        recordHeader(id, 4, timestampUs);
        writeLittle(Float.floatToRawIntBits(value) & 0xFFFFFFFFL, 4);
    }

    /** Array types: elements back to back; the reader gets the count from
     *  the payload size. */
    public void appendDoubleArray(int id, double[] values, long timestampUs) throws IOException {
        recordHeader(id, 8 * values.length, timestampUs);
        for (double v : values) writeLittle(Double.doubleToRawLongBits(v), 8);
    }

    public void appendInt64Array(int id, long[] values, long timestampUs) throws IOException {
        recordHeader(id, 8 * values.length, timestampUs);
        for (long v : values) writeLittle(v, 8);
    }

    /** {@code string[]}: a 4-byte count, then each string as a 4-byte length
     *  and its UTF-8 bytes. */
    public void appendStringArray(int id, String[] values, long timestampUs) throws IOException {
        byte[][] utf8 = new byte[values.length][];
        int size = 4;
        for (int i = 0; i < values.length; i++) {
            utf8[i] = utf8(values[i]);
            size += 4 + utf8[i].length;
        }
        recordHeader(id, size, timestampUs);
        writeLittle(values.length, 4);
        for (byte[] b : utf8) {
            writeLittle(b.length, 4);
            out.write(b);
        }
    }

    public void appendBooleanArray(int id, boolean[] values, long timestampUs) throws IOException {
        recordHeader(id, values.length, timestampUs);
        for (boolean v : values) out.write(v ? 1 : 0);
    }

    public void appendBoolean(int id, boolean value, long timestampUs) throws IOException {
        recordHeader(id, 1, timestampUs);
        out.write(value ? 1 : 0);
    }

    public void appendString(int id, String value, long timestampUs) throws IOException {
        byte[] b = utf8(value);
        appendRaw(id, b, b.length, timestampUs);
    }

    /** Writes {@code length} bytes of {@code data} as one record. */
    public void appendRaw(int id, byte[] data, int length, long timestampUs) throws IOException {
        recordHeader(id, length, timestampUs);
        out.write(data, 0, length);
    }

    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    private void recordHeader(int id, int payloadSize, long timestampUs) throws IOException {
        int idLen = byteLength(id & 0xFFFFFFFFL, 4);
        int sizeLen = byteLength(payloadSize & 0xFFFFFFFFL, 4);
        int tsLen = byteLength(timestampUs, 8);
        // bits 0-1: ID length-1, bits 2-3: size length-1, bits 4-6: timestamp length-1
        out.write((idLen - 1) | ((sizeLen - 1) << 2) | ((tsLen - 1) << 4));
        writeLittle(id, idLen);
        writeLittle(payloadSize, sizeLen);
        writeLittle(timestampUs, tsLen);
    }

    /** Fewest bytes (1..max) that hold {@code value} as unsigned. */
    private static int byteLength(long value, int max) {
        int n = 1;
        while (n < max && (value >>> (8 * n)) != 0) {
            n++;
        }
        return n;
    }

    private void writeLittle(long value, int bytes) throws IOException {
        for (int i = 0; i < bytes; i++) {
            scratch[i] = (byte) (value >>> (8 * i));
        }
        out.write(scratch, 0, bytes);
    }

    private static byte[] utf8(String s) {
        return (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
    }
}
