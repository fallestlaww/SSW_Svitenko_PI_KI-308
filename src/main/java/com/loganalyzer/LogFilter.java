package com.loganalyzer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Багатокритеріальний фільтр записів логу. Усі критерії опціональні.
 * Запис проходить фільтр лише якщо задовольняє ВСІ задані критерії (логічне AND).
 */
public class LogFilter {

    private final Set<LogLevel> allowedLevels = EnumSet.noneOf(LogLevel.class);
    private LocalDateTime sinceTime;
    private Pattern regex;

    /** Додає рівень до списку дозволених. Якщо список порожній — пропускає всі рівні. */
    public void allowLevel(LogLevel level) {
        allowedLevels.add(level);
    }

    /** Встановлює часовий поріг: пропускає лише записи з timestamp ≥ since. */
    public void setTimeWindow(LocalDateTime since) {
        this.sinceTime = since;
    }

    /** Встановлює часове вікно "за останні N годин". */
    public void setRecentHours(long hours) {
        this.sinceTime = LocalDateTime.now().minus(Duration.ofHours(hours));
    }

    /** Регулярний вираз для повідомлення. */
    public void setRegex(String pattern) throws PatternSyntaxException {
        this.regex = Pattern.compile(pattern);
    }

    /** Перевіряє, чи запис проходить через фільтр. */
    public boolean matches(LogEntry entry) {
        if (!allowedLevels.isEmpty() && !allowedLevels.contains(entry.getLevel())) {
            return false;
        }

        if (sinceTime != null) {
            // Якщо у запису немає часу — пропускаємо лише коли часовий фільтр не заданий
            if (!entry.hasTimestamp() || entry.getTimestamp().isBefore(sinceTime)) {
                return false;
            }
        }

        if (regex != null) {
            if (!regex.matcher(entry.getMessage()).find()) {
                return false;
            }
        }

        return true;
    }

    public boolean hasAnyCriteria() {
        return !allowedLevels.isEmpty() || sinceTime != null || regex != null;
    }
}
