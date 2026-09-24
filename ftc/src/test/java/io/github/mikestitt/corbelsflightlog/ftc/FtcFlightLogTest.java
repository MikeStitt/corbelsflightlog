package io.github.mikestitt.corbelsflightlog.ftc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;

import fi.iki.elonen.NanoHTTPD;

import io.github.mikestitt.corbelsflightlog.FlightLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

/**
 * The robot-side behaviour that doesn't need a robot: where files go when
 * storage is awkward, that logs close when an OpMode ends, and that the
 * download page can't be talked into serving something else.
 *
 * <p>Nothing here touches AppUtil or WebHandlerManager: on a real SDK those
 * can't be constructed or reassigned, so the library keeps the decisions in
 * plain functions that can be.
 */
public class FtcFlightLogTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File savedDirectory;

    /** A stand-in OpMode; only its class name is used. */
    public static class SampleOpMode extends OpMode {
        @Override public void init() { }
        @Override public void loop() { }
    }

    @Before
    public void setUp() throws IOException {
        savedDirectory = FlightLog.directory;
        FtcFlightLog.useDirectory(tmp.newFolder("logs-under-test"));
    }

    @After
    public void tearDown() {
        FtcFlightLog.closeCurrent();
        FtcFlightLog.useDirectory(null);
        FlightLog.directory = savedDirectory;
    }

    // ------------------------------------------------------------ storage

    @Test
    public void logsGoInALogsFolderInsideTheRobotControllersOwnFolder() throws IOException {
        File first = tmp.newFolder("FIRST");
        assertEquals(new File(first, FtcFlightLog.FOLDER_NAME),
                FtcFlightLog.chooseDirectory(first, null));
        assertTrue("created if missing",
                new File(first, FtcFlightLog.FOLDER_NAME).isDirectory());
    }

    @Test
    public void theFolderIsNamedAfterTheLibraryNotSomethingGeneric() {
        // Its size is then a straight measure of what this library is using.
        assertEquals("corbelsflightlog", FtcFlightLog.FOLDER_NAME);
    }

    @Test
    public void anUnusableFirstFolderFallsBackToTheAppsOwnStorage() throws IOException {
        File notAFolder = tmp.newFile("not-a-folder");     // a Control Hub with no usable FIRST
        File appFiles = tmp.newFolder("app-files");
        assertEquals(new File(appFiles, FtcFlightLog.FOLDER_NAME),
                FtcFlightLog.chooseDirectory(notAFolder, appFiles));
    }

    @Test
    public void withNoStorageAtAllItStillReturnsSomewhereAndNeverThrows() throws IOException {
        assertNotNull(FtcFlightLog.chooseDirectory(null, null));
        assertNotNull(FtcFlightLog.chooseDirectory(tmp.newFile("blocked"), null));
        assertNotNull(FtcFlightLog.logDirectory());
    }

    @Test
    public void anOverrideWinsWhenItIsUsable() throws IOException {
        File somewhereElse = tmp.newFolder("usb-stick");
        FtcFlightLog.useDirectory(somewhereElse);
        assertEquals(somewhereElse, FtcFlightLog.logDirectory());
    }

    @Test
    public void anUnusableOverrideIsIgnoredRatherThanBreakingLogging() throws IOException {
        FtcFlightLog.useDirectory(tmp.newFile("override-is-a-file"));
        assertNotNull(FtcFlightLog.logDirectory());
        assertFalse(FtcFlightLog.logDirectory().getPath().endsWith("override-is-a-file"));
    }

    @Test
    public void usableSaysNoToNullsAndFilesAndYesToFoldersItCanCreate() throws IOException {
        assertFalse(FtcFlightLog.usable(null));
        assertFalse(FtcFlightLog.usable(tmp.newFile("a-file")));
        assertTrue(FtcFlightLog.usable(new File(tmp.getRoot(), "made-on-demand")));
    }

    @Test
    public void openPutsTheFileInThatFolderNamedAfterTheOpMode() {
        FlightLog log = FtcFlightLog.open(new SampleOpMode());
        assertTrue(log.status(), log.isRecording());
        assertTrue(log.status(), log.status().startsWith("SampleOpMode-"));
        File[] files = FtcFlightLog.logDirectory().listFiles((d, n) -> n.endsWith(".wpilog"));
        assertEquals(1, files.length);
    }

    @Test
    public void aNullOpModeIsAccepted() {
        FlightLog log = FtcFlightLog.open((OpMode) null);
        assertTrue(log.status(), log.status().startsWith("OpMode-"));
    }

    // ------------------------------------------------------------ lifecycle

    @Test
    public void theLogClosesWhenTheOpModeStopsEvenIfNobodyCallsClose() {
        FlightLog log = FtcFlightLog.open(new SampleOpMode());
        assertTrue(log.isRecording());
        FtcFlightLog.listener().onOpModePostStop(new SampleOpMode());
        assertFalse("stopping the OpMode closed it", log.isRecording());
    }

    @Test
    public void aLogLeftOpenIsClosedBeforeTheNextOpModeInitialises() {
        FlightLog log = FtcFlightLog.open(new SampleOpMode());
        FtcFlightLog.listener().onOpModePreInit(new SampleOpMode());
        assertFalse(log.isRecording());
    }

    @Test
    public void theStartHookDoesNothingToAnOpenLog() {
        FlightLog log = FtcFlightLog.open("Running");
        FtcFlightLog.listener().onOpModePreStart(new SampleOpMode());
        assertTrue("start must not close the log", log.isRecording());
    }

    @Test
    public void aNullOpModeInACallbackIsFine() {
        FlightLog log = FtcFlightLog.open("NullCallback");
        FtcFlightLog.listener().onOpModePostStop(null);
        assertFalse(log.isRecording());
    }

    @Test
    public void openingAgainClosesThePreviousLog() {
        FlightLog first = FtcFlightLog.open("First");
        FlightLog second = FtcFlightLog.open("Second");
        assertFalse("the first was closed", first.isRecording());
        assertTrue(second.isRecording());
    }

    @Test
    public void closingTwiceAndClosingWithNothingOpenAreBothFine() {
        FtcFlightLog.closeCurrent();
        FlightLog log = FtcFlightLog.open("Once");
        FtcFlightLog.closeCurrent();
        FtcFlightLog.closeCurrent();
        assertFalse(log.isRecording());
    }

    @Test
    public void closingYourselfStillWorksAndTheHookDoesNotMind() {
        FlightLog log = FtcFlightLog.open("Manual");
        log.close();
        FtcFlightLog.listener().onOpModePostStop(new SampleOpMode());
        assertFalse(log.isRecording());
    }

    // ------------------------------------------------------------ web routes

    @Test
    public void registeringWithNoManagerIsIgnoredRatherThanThrowing() {
        LogWebHandlers.register(null);
    }

    @Test
    public void theListingShowsEveryLogNewestFirst() throws Exception {
        FtcFlightLog.open("Older").close();
        Thread.sleep(1100);                       // file names carry whole seconds
        FtcFlightLog.open("Newer").close();
        String html = LogWebHandlers.indexHtml(FtcFlightLog.logDirectory());
        assertTrue(html, html.contains("Older-"));
        assertTrue(html, html.contains("Newer-"));
        assertTrue("newest first", html.indexOf("Newer-") < html.indexOf("Older-"));
        assertTrue("links to the download route", html.contains("/corbelsflightlog/download?file="));
    }

    @Test
    public void anEmptyFolderSaysSoRatherThanFailing() throws IOException {
        assertTrue(LogWebHandlers.indexHtml(tmp.newFolder("empty")).contains("No logs yet"));
    }

    @Test
    public void aMissingFolderIsHandled() {
        assertTrue(LogWebHandlers.indexHtml(new File(tmp.getRoot(), "never-created"))
                .contains("No logs yet"));
    }

    @Test
    public void theIndexResponseIsHtml() {
        NanoHTTPD.Response response = LogWebHandlers.index();
        assertEquals(NanoHTTPD.Response.Status.OK, response.getStatus());
        assertEquals(NanoHTTPD.MIME_HTML, response.getMimeType());
    }

    @Test
    public void theDownloadQueryAcceptsOnlyAPlainLogNameInTheLogFolder() {
        assertEquals("Auto-20260923-101500.wpilog",
                LogWebHandlers.requestedName("file=Auto-20260923-101500.wpilog"));
        // Anything that could leave the folder, or isn't a log, is refused.
        for (String bad : new String[]{
                null, "", "file=", "name=x.wpilog", "file=notes.txt",
                "file=../secret.wpilog", "file=../../etc/passwd",
                "file=sub/dir.wpilog", "file=sub\\dir.wpilog",
                "file=%2e%2e%2fsecret.wpilog", "file=/etc/shadow.wpilog",
                "file=C:\\logs\\x.wpilog"}) {
            assertNull("should refuse: " + bad, LogWebHandlers.requestedName(bad));
        }
    }

    @Test
    public void downloadingAFileThatIsNotThereIsANotFoundNotACrash() {
        assertEquals(NanoHTTPD.Response.Status.NOT_FOUND,
                LogWebHandlers.download("file=missing.wpilog").getStatus());
    }

    @Test
    public void aBadQueryIsABadRequest() {
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST,
                LogWebHandlers.download("file=../escape.wpilog").getStatus());
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST,
                LogWebHandlers.download(null).getStatus());
    }

    @Test
    public void downloadingARealFileOffersItAsAnAttachment() {
        FlightLog log = FtcFlightLog.open("Served");
        String name = log.status();
        log.close();
        NanoHTTPD.Response response = LogWebHandlers.download("file=" + name);
        assertEquals(NanoHTTPD.Response.Status.OK, response.getStatus());
        assertNotNull(response.getData());
        assertEquals("attachment; filename=\"" + name + "\"",
                response.getHeader("Content-Disposition"));
    }

    @Test
    public void sizesAreReadable() {
        assertEquals("512 B", LogWebHandlers.kb(512));
        assertEquals("2 KB", LogWebHandlers.kb(2048));
        assertEquals("1.5 MB", LogWebHandlers.kb(1024 * 1024 * 3 / 2));
    }

    @Test
    public void namesWithHtmlCharactersAreEscaped() {
        assertEquals("&lt;b&gt;&amp;&quot;", LogWebHandlers.escape("<b>&\""));
    }
}
