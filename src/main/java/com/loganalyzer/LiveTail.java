package com.loganalyzer;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Моніторинг файлу в реальному часі (аналог `tail -f`).
 * Зчитує існуючий вміст файлу від поточної позиції EOF, потім блокує потік,
 * періодично перевіряючи, чи з'явилися нові дані.
 */
public class LiveTail {

    private final LogParser parser;
    private final LogFilter filter;
    private final AtomicBoolean stopFlag = new AtomicBoolean(false);

    public LiveTail(LogParser parser, LogFilter filter) {
        this.parser = parser;
        this.filter = filter;
    }

    /** Сигнал зупинки (потокобезпечно). */
    public void stop() {
        stopFlag.set(true);
    }

    /**
     * Запускає режим моніторингу. Блокує потік до встановлення stop().
     *
     * @param filePath        Шлях до файлу, який треба відстежувати.
     * @param onEntry         Колбек для нових записів.
     * @param pollIntervalMs  Період опитування файлу (за замовчуванням 250 мс).
     */
    public void watch(String filePath, Consumer<LogEntry> onEntry, int pollIntervalMs)
            throws IOException {

        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IOException("Cannot open file for tailing: " + filePath);
        }

        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            // Стартуємо з кінця файлу — як `tail -f`
            long pos = raf.length();
            raf.seek(pos);

            StringBuilder buffer = new StringBuilder(1024);
            byte[] readBuf = new byte[8192];

            while (!stopFlag.get()) {
                long currentLength = raf.length();
                if (currentLength < pos) {
                    // Файл скоротився (logrotate) — починаємо з початку
                    pos = 0;
                    raf.seek(0);
                }

                if (currentLength > pos) {
                    raf.seek(pos);
                    int read = raf.read(readBuf);
                    if (read > 0) {
                        String chunk = new String(readBuf, 0, read, StandardCharsets.UTF_8);
                        for (int i = 0; i < chunk.length(); i++) {
                            char c = chunk.charAt(i);
                            if (c == '\n') {
                                // Прибираємо CR, якщо було CRLF
                                int len = buffer.length();
                                if (len > 0 && buffer.charAt(len - 1) == '\r') {
                                    buffer.setLength(len - 1);
                                }
                                if (buffer.length() > 0) {
                                    LogEntry entry = parser.parseLine(buffer.toString());
                                    if (filter.matches(entry)) {
                                        onEntry.accept(entry);
                                    }
                                }
                                buffer.setLength(0);
                            } else {
                                buffer.append(c);
                            }
                        }
                        pos = raf.getFilePointer();
                    }
                } else {
                    try {
                        Thread.sleep(pollIntervalMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    public void watch(String filePath, Consumer<LogEntry> onEntry) throws IOException {
        watch(filePath, onEntry, 250);
    }
}
