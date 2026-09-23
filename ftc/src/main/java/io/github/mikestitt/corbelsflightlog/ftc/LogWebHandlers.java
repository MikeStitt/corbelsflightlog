package io.github.mikestitt.corbelsflightlog.ftc;

import com.qualcomm.robotcore.util.WebHandlerManager;

import fi.iki.elonen.NanoHTTPD;

import org.firstinspires.ftc.robotcore.internal.webserver.WebHandler;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * Serves the log files from the Robot Controller's own web server, so a laptop
 * on the robot's Wi-Fi can download them from a page instead of needing adb.
 *
 * <p>Two routes, both under the port the Program &amp; Manage page already uses:
 * <ul>
 *   <li>{@code /corbelsflightlog} -- a list, newest first</li>
 *   <li>{@code /corbelsflightlog/download?file=NAME} -- one file</li>
 * </ul>
 */
final class LogWebHandlers {

    static final String INDEX_PATH = "/corbelsflightlog";
    static final String DOWNLOAD_PATH = "/corbelsflightlog/download";

    private LogWebHandlers() {
    }

    static void register(WebHandlerManager manager) {
        if (manager == null) return;
        manager.register(INDEX_PATH, new WebHandler() {
            @Override
            public NanoHTTPD.Response getResponse(NanoHTTPD.IHTTPSession session) {
                return index();
            }
        });
        manager.register(DOWNLOAD_PATH, new WebHandler() {
            @Override
            public NanoHTTPD.Response getResponse(NanoHTTPD.IHTTPSession session) {
                return download(session == null ? null : session.getQueryParameterString());
            }
        });
    }

    /** The listing page. Separate from the handler so it can be tested. */
    static NanoHTTPD.Response index() {
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                NanoHTTPD.MIME_HTML, indexHtml(FtcFlightLog.logDirectory()));
    }

    /** One file, for a {@code ?file=NAME} query. Separate so it can be tested. */
    static NanoHTTPD.Response download(String queryString) {
        String name = requestedName(queryString);
        if (name == null) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
                    NanoHTTPD.MIME_PLAINTEXT, "expected ?file=NAME, where NAME ends in .wpilog");
        }
        File file = new File(FtcFlightLog.logDirectory(), name);
        if (!file.isFile()) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    NanoHTTPD.MIME_PLAINTEXT, "no such log: " + name);
        }
        try {
            NanoHTTPD.Response response = NanoHTTPD.newChunkedResponse(
                    NanoHTTPD.Response.Status.OK, "application/octet-stream",
                    new FileInputStream(file));
            response.addHeader("Content-Disposition", "attachment; filename=\"" + name + "\"");
            return response;
        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    NanoHTTPD.MIME_PLAINTEXT, "could not read " + name + ": " + e);
        }
    }

    /**
     * The file a download query asks for, or null if the query is missing,
     * malformed, or names anything but a plain {@code .wpilog} file in the log
     * folder. Path separators are refused, so no query can walk out of it.
     */
    static String requestedName(String queryString) {
        if (queryString == null) return null;
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0 || !"file".equals(pair.substring(0, eq))) continue;
            String name = pair.substring(eq + 1);
            if (name.isEmpty() || !name.endsWith(".wpilog")) return null;
            if (name.contains("/") || name.contains("\\") || name.contains("..")
                    || name.contains("%") || name.contains(":")) {
                return null;
            }
            return name;
        }
        return null;
    }

    /** The listing page: every log in the folder, newest first. */
    static String indexHtml(File folder) {
        File[] files = folder == null ? null : folder.listFiles((d, n) -> n.endsWith(".wpilog"));
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\">")
                .append("<title>corbelsflightlog</title>")
                .append("<style>body{font-family:system-ui,sans-serif;margin:2rem;}")
                .append("td{padding:.2rem .8rem .2rem 0;}</style></head><body>")
                .append("<h1>Logs</h1><p>")
                .append(escape(String.valueOf(folder)))
                .append("</p>");
        if (files == null || files.length == 0) {
            sb.append("<p>No logs yet. Run an OpMode.</p>");
        } else {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            SimpleDateFormat when = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            sb.append("<table>");
            long total = 0;
            for (File f : files) {
                total += f.length();
                sb.append("<tr><td><a href=\"").append(DOWNLOAD_PATH).append("?file=")
                        .append(escape(f.getName())).append("\" download>")
                        .append(escape(f.getName())).append("</a></td><td>")
                        .append(kb(f.length())).append("</td><td>")
                        .append(when.format(new Date(f.lastModified())))
                        .append("</td></tr>");
            }
            sb.append("</table><p>").append(files.length).append(" files, ")
                    .append(kb(total)).append("</p>");
        }
        return sb.append("</body></html>").toString();
    }

    static String kb(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
