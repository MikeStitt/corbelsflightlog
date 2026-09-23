package io.github.spiresfrc9106.flightlog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Output streams that record what happens to them, or fail on command. */
final class TestStreams {
    private TestStreams() {
    }

    /** Keeps everything written, and counts flush() and close() calls. */
    static final class Recording extends OutputStream {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int flushes;
        int closes;

        @Override
        public void write(int b) {
            bytes.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            bytes.write(b, off, len);
        }

        @Override
        public void flush() {
            flushes++;
        }

        @Override
        public void close() {
            closes++;
        }

        byte[] toByteArray() {
            return bytes.toByteArray();
        }
    }

    /**
     * Accepts {@code bytesBeforeFailure} bytes, then throws on every write --
     * like storage filling up. Can also fail on flush() and/or close().
     */
    static final class Failing extends OutputStream {
        private long remaining;
        boolean failFlush;
        boolean failClose;
        int closes;

        Failing(long bytesBeforeFailure) {
            this.remaining = bytesBeforeFailure;
        }

        @Override
        public void write(int b) throws IOException {
            if (remaining <= 0) throw new IOException("simulated: storage full");
            remaining--;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (remaining < len) {
                remaining = 0;
                throw new IOException("simulated: storage full");
            }
            remaining -= len;
        }

        @Override
        public void flush() throws IOException {
            if (failFlush) throw new IOException("simulated: flush failed");
        }

        @Override
        public void close() throws IOException {
            closes++;
            if (failClose) throw new IOException("simulated: close failed");
        }
    }

    /** A clock tests move by hand, in nanoseconds. */
    static final class FakeClock implements java.util.function.LongSupplier {
        long nanos;

        FakeClock(long startNanos) {
            this.nanos = startNanos;
        }

        @Override
        public long getAsLong() {
            return nanos;
        }

        void advanceMillis(long ms) {
            nanos += ms * 1_000_000L;
        }

        void advanceMicros(long us) {
            nanos += us * 1_000L;
        }
    }
}
