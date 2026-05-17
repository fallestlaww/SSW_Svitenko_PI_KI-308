package com.loganalyzer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Структура одного запису логу. Усі поля опціональні: парсер заповнює лише ті,
 * які вдалося розпізнати у вхідному рядку.
 */
public class LogEntry {

    private static final DateTimeFormatter TS_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LocalDateTime timestamp;        // null, якщо не вдалося розпарсити
    private LogLevel level = LogLevel.UNKNOWN;
    private String source = "";
    private String message = "";
    private String rawLine = "";            // Оригінальний рядок (для діагностики)

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public boolean hasTimestamp() { return timestamp != null; }

    public LogLevel getLevel() { return level; }
    public void setLevel(LogLevel level) { this.level = level; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source == null ? "" : source; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message == null ? "" : message; }

    public String getRawLine() { return rawLine; }
    public void setRawLine(String rawLine) { this.rawLine = rawLine == null ? "" : rawLine; }

    /** Форматує timestamp у вигляді "YYYY-MM-DD HH:MM:SS". */
    public String formatTimestamp() {
        return timestamp == null ? "" : timestamp.format(TS_FORMAT);
    }
}
