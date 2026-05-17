package com.loganalyzer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Експорт записів у CSV-файл (RFC 4180-сумісний).
 * Колонки: Timestamp, Level, Source, Message.
 * Поля з комами, лапками або переносами рядків автоматично екрануються.
 */
public class CsvExporter implements AutoCloseable {

    private final Writer out;
    private long rowCount = 0;

    public CsvExporter(String outputPath) throws IOException {
        try {
            this.out = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(Path.of(outputPath)), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IOException("Cannot open output file for writing: " + outputPath, e);
        }

        // BOM для коректного відкриття у Excel із кирилицею
        out.write('\uFEFF');
        out.write("Timestamp,Level,Source,Message\r\n");
    }

    public void write(LogEntry entry) throws IOException {
        out.write(escapeField(entry.formatTimestamp()));
        out.write(',');
        out.write(escapeField(entry.getLevel().displayName()));
        out.write(',');
        out.write(escapeField(entry.getSource()));
        out.write(',');
        out.write(escapeField(entry.getMessage()));
        out.write("\r\n");
        rowCount++;
    }

    public long rowCount() {
        return rowCount;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    private static String escapeField(String field) {
        if (field == null) return "";
        boolean needsQuotes = false;
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (c == ',' || c == '"' || c == '\n' || c == '\r') {
                needsQuotes = true;
                break;
            }
        }
        if (!needsQuotes) return field;

        StringBuilder sb = new StringBuilder(field.length() + 2);
        sb.append('"');
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (c == '"') sb.append('"');
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }
}
