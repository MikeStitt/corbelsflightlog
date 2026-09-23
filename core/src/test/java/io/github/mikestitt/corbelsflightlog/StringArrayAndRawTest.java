package io.github.mikestitt.corbelsflightlog;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/** The two WPILOG primitives added last: string[] and raw. */
public class StringArrayAndRawTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File saved;

    @Before
    public void setUp() {
        saved = FlightLog.directory;
        FlightLog.directory = tmp.getRoot();
    }

    @After
    public void tearDown() {
        FlightLog.directory = saved;
    }

    private RecordedLog record(Body body) throws IOException {
        FlightLog log = FlightLog.open("Types");
        body.run(log);
        log.close();
        return RecordedLog.of(tmp.getRoot().listFiles((d, n) -> n.endsWith(".wpilog"))[0]);
    }

    private interface Body {
        void run(FlightLog log);
    }

    @Test
    public void stringArraysRoundTripIncludingUtf8AndEmpty() throws IOException {
        RecordedLog log = record(l -> {
            l.recordOutput("s", new String[]{"one", "héllo 🤖", ""});
            l.recordOutput("s", new String[]{"one", "héllo 🤖", ""});   // unchanged
            l.recordOutput("s", new String[0]);
        });
        RecordedLog.Entry e = log.entry("/s");
        assertEquals("string[]", e.type);
        assertEquals("written only when changed", 2, e.records.size());
        assertArrayEquals(new String[]{"one", "héllo 🤖", ""}, e.records.get(0).getStringArray());
        assertEquals(0, e.records.get(1).getStringArray().length);
    }

    @Test
    public void rawBytesRoundTripAndAreCopied() throws IOException {
        byte[] bytes = {1, 2, 3, -1};
        RecordedLog log = record(l -> {
            l.recordOutput("r", bytes);
            l.recordOutput("r", bytes.clone());     // same content
            bytes[0] = 9;
            l.recordOutput("r", bytes);             // caller mutated its array
            l.recordOutput("r", new byte[0]);
        });
        RecordedLog.Entry e = log.entry("/r");
        assertEquals("raw", e.type);
        assertEquals(3, e.records.size());
        assertArrayEquals(new byte[]{1, 2, 3, -1}, e.records.get(0).getRaw());
        assertArrayEquals(new byte[]{9, 2, 3, -1}, e.records.get(1).getRaw());
        assertEquals(0, e.records.get(2).getSize());
    }

    @Test
    public void nullsAndTypeConflictsBehaveLikeEveryOtherType() throws IOException {
        RecordedLog log = record(l -> {
            l.recordOutput("s", (String[]) null);
            l.recordOutput("r", (byte[]) null);
            l.recordOutput("k", new String[]{"a"});
            l.recordOutput("k", new byte[]{1});     // different type: refused
        });
        assertEquals(false, log.has("/s"));
        assertEquals(false, log.has("/r"));
        assertEquals("string[]", log.entry("/k").type);
        assertEquals(1, log.count("/k"));
        assertEquals(1, log.count("/Events"));
    }

    @Test
    public void aDisabledLogAcceptsBoth() {
        FlightLog log = FlightLog.disabled("test");
        log.recordOutput("s", new String[]{"a"});
        log.recordOutput("r", new byte[]{1});
        assertEquals(0, tmp.getRoot().listFiles().length);
    }
}
