package io.github.mikestitt.corbelsflightlog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * The folder has a size budget: at open, oldest logs go until the total fits.
 * Without it the Control Hub fills up and logging stops for good.
 */
public class DiskBudgetTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File savedDirectory;
    private long savedCap;

    @Before
    public void setUp() {
        savedDirectory = FlightLog.directory;
        savedCap = FlightLog.maxDirectoryBytes;
        FlightLog.directory = tmp.getRoot();
    }

    @After
    public void tearDown() {
        FlightLog.directory = savedDirectory;
        FlightLog.maxDirectoryBytes = savedCap;
    }

    /** A .wpilog of the given size, with the given age in minutes. */
    private File oldLog(String name, int bytes, int minutesOld) throws IOException {
        File f = new File(tmp.getRoot(), name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(new byte[bytes]);
        }
        assertTrue(f.setLastModified(System.currentTimeMillis() - minutesOld * 60_000L));
        return f;
    }

    private static String[] names(File folder) {
        String[] names = folder.list((d, n) -> n.endsWith(".wpilog"));
        Arrays.sort(names);
        return names;
    }

    @Test
    public void theDefaultBudgetIsTwoGibibytes() {
        assertEquals(2L * 1024 * 1024 * 1024, savedCap);
    }

    @Test
    public void nothingIsDeletedWhileTheFolderFitsTheBudget() throws IOException {
        oldLog("a.wpilog", 1000, 30);
        oldLog("b.wpilog", 1000, 20);
        FlightLog.maxDirectoryBytes = 10_000;
        FlightLog.open("Run").close();
        assertEquals(3, names(tmp.getRoot()).length);
    }

    @Test
    public void theOldestGoFirstUntilTheTotalFits() throws IOException {
        oldLog("oldest.wpilog", 1000, 90);
        oldLog("middle.wpilog", 1000, 60);
        oldLog("newest.wpilog", 1000, 30);
        FlightLog.maxDirectoryBytes = 2_500;          // room for two, plus the new one
        FlightLog.open("Run").close();
        String[] left = names(tmp.getRoot());
        assertFalse("oldest deleted", Arrays.asList(left).contains("oldest.wpilog"));
        assertTrue("newest kept", Arrays.asList(left).contains("newest.wpilog"));
        assertTrue("middle kept", Arrays.asList(left).contains("middle.wpilog"));
    }

    @Test
    public void everythingOlderGoesWhenTheBudgetIsTiny() throws IOException {
        oldLog("a.wpilog", 5000, 90);
        oldLog("b.wpilog", 5000, 60);
        oldLog("c.wpilog", 5000, 30);
        FlightLog.maxDirectoryBytes = 1;
        FlightLog.open("Run").close();
        assertEquals("only the run just opened remains", 1, names(tmp.getRoot()).length);
    }

    @Test
    public void filesThatAreNotLogsAreLeftAlone() throws IOException {
        oldLog("old.wpilog", 5000, 90);
        File keep = new File(tmp.getRoot(), "notes.txt");
        try (FileOutputStream out = new FileOutputStream(keep)) {
            out.write(new byte[5000]);
        }
        FlightLog.maxDirectoryBytes = 1;
        FlightLog.open("Run").close();
        assertTrue("a non-log file is not ours to delete", keep.isFile());
        assertEquals(1, names(tmp.getRoot()).length);
    }

    @Test
    public void anEmptyFolderIsFine() {
        FlightLog.maxDirectoryBytes = 1;
        FlightLog log = FlightLog.open("First");
        assertTrue(log.status(), log.isRecording());
        log.close();
    }
}
