package com.loganalyzer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер текстових лог-файлів формату [Timestamp] [Level] [Source] Message.
 * Працює потоково: викликає колбек на кожному успішно розпарсеному записі,
 * що дозволяє обробляти файли довільного розміру без завантаження їх у пам'ять.
 */
public class LogParser {

    /**
     * Колбек, що викликається на кожному розпарсеному запису.
     * Повертає false → парсинг переривається.
     */
    @FunctionalInterface
    public interface EntryCallback extends Predicate<LogEntry> {}

    // Шаблон 1: [2024-05-10 14:23:01] [ERROR] [AuthService] Message
    private static final Pattern PATTERN_BRACKETED = Pattern.compile(
        "\\[\\s*([0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}:[0-9]{2}" +
        "(?:\\.[0-9]+)?(?:Z|[+\\-][0-9:]+)?)\\s*\\]\\s*" +
        "\\[\\s*([A-Za-z]+)\\s*\\]\\s*" +
        "(?:\\[\\s*([^\\]]*)\\s*\\]\\s*)?" +
        "(.*)"
    );

    // Шаблон 2: 2024-05-10 14:23:01 ERROR AuthService: Message
    private static final Pattern PATTERN_PLAIN = Pattern.compile(
        "([0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}:[0-9]{2}" +
        "(?:\\.[0-9]+)?(?:Z|[+\\-][0-9:]+)?)\\s+" +
        "([A-Za-z]+)\\s+" +
        "([A-Za-z0-9_.\\-]+):\\s*" +
        "(.*)"
    );

    private static final DateTimeFormatter[] TS_FORMATS = new DateTimeFormatter[]{
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
    };

    private static LocalDateTime parseTimestamp(String s) {
        // Прибираємо хвостовий Z або часові пояси для спрощеного парсингу
        String cleaned = s;
        if (cleaned.endsWith("Z")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        // Видаляємо +XX:XX / -XX:XX в кінці
        int plus = Math.max(cleaned.lastIndexOf('+'), cleaned.lastIndexOf('-'));
        if (plus > 10) {
            cleaned = cleaned.substring(0, plus);
        }

        for (DateTimeFormatter fmt : TS_FORMATS) {
            try {
                return LocalDateTime.parse(cleaned, fmt);
            } catch (Exception ignored) { /* try next */ }
        }
        return null;
    }

    /**
     * Розбирає один рядок. Якщо рядок не відповідає шаблону —
     * повертає LogEntry з заповненим лише полем rawLine та level = UNKNOWN.
     */
    public LogEntry parseLine(String line) {
        LogEntry entry = new LogEntry();
        entry.setRawLine(line);

        Matcher m = PATTERN_BRACKETED.matcher(line);
        if (m.matches()) {
            entry.setTimestamp(parseTimestamp(m.group(1)));
            entry.setLevel(LogLevel.fromString(m.group(2)));
            entry.setSource(m.group(3) == null ? "" : m.group(3));
            entry.setMessage(m.group(4));
            return entry;
        }

        m = PATTERN_PLAIN.matcher(line);
        if (m.matches()) {
            entry.setTimestamp(parseTimestamp(m.group(1)));
            entry.setLevel(LogLevel.fromString(m.group(2)));
            entry.setSource(m.group(3));
            entry.setMessage(m.group(4));
            return entry;
        }

        // Не вдалося — повертаємо запис із сирим вмістом і Unknown level
        entry.setMessage(line);
        return entry;
    }

    /**
     * Розбирає файл за вказаним шляхом.
     *
     * @return Кількість успішно розпарсених записів.
     * @throws IOException при помилці відкриття/читання файлу.
     */
    public long parseFile(String filePath, EntryCallback onEntry) throws IOException {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IOException("Cannot open file: " + filePath + " (file does not exist)");
        }
        if (!Files.isReadable(path)) {
            throw new IOException("Cannot open file: " + filePath + " (access denied)");
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parseStream(reader, onEntry);
        } catch (java.nio.charset.MalformedInputException e) {
            // Файл не в UTF-8 — пробуємо системне кодування (на Windows це часто CP1251 / CP866)
            try (BufferedReader reader = Files.newBufferedReader(path, java.nio.charset.Charset.defaultCharset())) {
                return parseStream(reader, onEntry);
            }
        }
    }

    /**
     * Розбирає вхідний потік. Корисно для тестів та stdin.
     */
    public long parseStream(Reader input, EntryCallback onEntry) throws IOException {
        BufferedReader br = (input instanceof BufferedReader)
            ? (BufferedReader) input
            : new BufferedReader(input, 65536); // 64 KB буфер

        long count = 0;
        String line;
        while ((line = br.readLine()) != null) {
            if (line.isEmpty()) continue;
            LogEntry entry = parseLine(line);
            count++;
            if (!onEntry.test(entry)) break;
        }
        return count;
    }
}
