package com.loganalyzer;

/**
 * Рівень важливості події логу.
 */
public enum LogLevel {
    CRITICAL("Critical"),
    ERROR("Error"),
    WARNING("Warning"),
    INFORMATION("Information"),
    UNKNOWN("Unknown");

    private final String displayName;

    LogLevel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Перетворює рядкове представлення рівня в LogLevel. Регістр не має значення.
     */
    public static LogLevel fromString(String s) {
        if (s == null) return UNKNOWN;
        switch (s.trim().toLowerCase()) {
            case "critical":
            case "crit":
            case "fatal":
                return CRITICAL;
            case "error":
            case "err":
            case "severe":
                return ERROR;
            case "warning":
            case "warn":
                return WARNING;
            case "information":
            case "info":
            case "notice":
                return INFORMATION;
            default:
                return UNKNOWN;
        }
    }
}
