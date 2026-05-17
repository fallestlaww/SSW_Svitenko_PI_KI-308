package com.loganalyzer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Читач системних подій Windows через утиліту wevtutil.exe,
 * яка є вбудованою у Windows 10/11 та обгорткою над Event Log API.
 *
 * Доступні стандартні канали: "System", "Application", "Security", "Setup",
 * або довільний шлях до каналу.
 *
 * Використання wevtutil.exe замість JNI/JNA дозволяє уникнути зовнішніх
 * залежностей та робить код повністю portable Java.
 */
public class EventLogReader {

    @FunctionalInterface
    public interface EntryCallback extends Predicate<LogEntry> {}

    private static final Pattern EVENT_ID_PATTERN =
        Pattern.compile("<EventID[^>]*>(\\d+)</EventID>");
    private static final Pattern LEVEL_PATTERN =
        Pattern.compile("<Level>(\\d+)</Level>");
    private static final Pattern PROVIDER_PATTERN =
        Pattern.compile("<Provider\\s+Name=['\"]([^'\"]+)['\"]");
    private static final Pattern TIME_PATTERN =
        Pattern.compile("<TimeCreated\\s+SystemTime=['\"]([^'\"]+)['\"]");

    /**
     * Зчитує події з зазначеного каналу.
     *
     * @param channelName Назва каналу ("System", "Application" тощо).
     * @param sinceHours  Зчитувати лише події не давніші, ніж за вказану кількість годин
     *                    (0 = без обмеження за часом).
     * @param onEntry     Колбек на кожну подію.
     * @return Кількість зчитаних подій.
     */
    public long readChannel(String channelName, long sinceHours, EntryCallback onEntry)
            throws IOException {

        if (!isWindows()) {
            throw new IOException(
                "Windows Event Log is only available on Windows. " +
                "Use --input <file> on non-Windows platforms.");
        }

        // Будуємо команду wevtutil.exe
        List<String> cmd = new ArrayList<>();
        cmd.add("wevtutil.exe");
        cmd.add("qe");                          // query-events
        cmd.add(channelName);
        cmd.add("/f:xml");                      // формат XML
        cmd.add("/rd:true");                    // reverse direction (новіші першими)
        cmd.add("/c:1000");                     // ліміт у 1000 подій за виклик

        if (sinceHours > 0) {
            long ms = sinceHours * 60L * 60L * 1000L;
            cmd.add("/q:*[System[TimeCreated[timediff(@SystemTime) <= " + ms + "]]]");
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IOException(
                "Cannot execute wevtutil.exe. Ensure it is in PATH (standard on Windows).", e);
        }

        long count = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder eventBuf = new StringBuilder(2048);
            String line;
            while ((line = reader.readLine()) != null) {
                eventBuf.append(line).append('\n');

                // wevtutil виводить кожну подію одним блоком, але без чітких роздільників.
                // Орієнтуємось на закриваючий тег </Event>.
                if (line.contains("</Event>")) {
                    LogEntry entry = parseEventXml(eventBuf.toString());
                    count++;
                    eventBuf.setLength(0);
                    if (!onEntry.test(entry)) break;
                }
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0 && count == 0) {
                // Читаємо stderr для діагностики
                String errMsg;
                try (BufferedReader er = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String ln;
                    while ((ln = er.readLine()) != null) sb.append(ln).append('\n');
                    errMsg = sb.toString().trim();
                }
                throw new IOException("wevtutil.exe exited with code " + exitCode +
                    (errMsg.isEmpty() ? "" : ": " + errMsg));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }

        return count;
    }

    private LogEntry parseEventXml(String xml) {
        LogEntry entry = new LogEntry();
        entry.setRawLine(xml);

        // Level
        Matcher m = LEVEL_PATTERN.matcher(xml);
        if (m.find()) {
            try {
                entry.setLevel(mapEvtLevel(Integer.parseInt(m.group(1))));
            } catch (NumberFormatException ignored) {}
        }

        // Provider Name
        m = PROVIDER_PATTERN.matcher(xml);
        if (m.find()) {
            entry.setSource(m.group(1));
        }

        // Timestamp
        m = TIME_PATTERN.matcher(xml);
        if (m.find()) {
            String ts = m.group(1);
            // Формат: 2024-05-10T14:23:01.1234567Z
            try {
                // Прибираємо мікросекунди та Z
                int dotPos = ts.indexOf('.');
                String trimmed = dotPos > 0 ? ts.substring(0, dotPos) : ts;
                if (trimmed.endsWith("Z")) trimmed = trimmed.substring(0, trimmed.length() - 1);
                entry.setTimestamp(LocalDateTime.parse(trimmed,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
            } catch (Exception ignored) {}
        }

        // Message: спочатку шукаємо <Message>, потім fallback на EventID
        int msgStart = xml.indexOf("<Message>");
        if (msgStart >= 0) {
            int msgEnd = xml.indexOf("</Message>", msgStart);
            if (msgEnd > msgStart) {
                entry.setMessage(xml.substring(msgStart + 9, msgEnd).trim());
            }
        }
        if (entry.getMessage().isEmpty()) {
            m = EVENT_ID_PATTERN.matcher(xml);
            if (m.find()) {
                entry.setMessage("EventID=" + m.group(1));
            }
        }

        return entry;
    }

    private static LogLevel mapEvtLevel(int level) {
        // Windows Event Log levels:
        //   1 = Critical, 2 = Error, 3 = Warning, 4 = Information, 5 = Verbose
        switch (level) {
            case 1: return LogLevel.CRITICAL;
            case 2: return LogLevel.ERROR;
            case 3: return LogLevel.WARNING;
            case 4: return LogLevel.INFORMATION;
            default: return LogLevel.UNKNOWN;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().startsWith("windows");
    }
}
