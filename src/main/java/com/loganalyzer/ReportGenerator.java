package com.loganalyzer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Збирає статистику за записами та генерує HTML-звіт.
 * Звіт містить:
 *   - Загальну кількість подій за рівнями.
 *   - Графік розподілу помилок по 24 годинах доби (SVG).
 *   - Топ-10 джерел за кількістю помилок.
 */
public class ReportGenerator {

    private long totalCount = 0;
    private final EnumMap<LogLevel, Long> levelCounts = new EnumMap<>(LogLevel.class);
    private final long[] hourlyErrors = new long[24];
    private final Map<String, Long> sourceErrors = new HashMap<>();

    public ReportGenerator() {
        for (LogLevel lv : LogLevel.values()) {
            levelCounts.put(lv, 0L);
        }
    }

    /** Накопичує запис у внутрішню статистику. */
    public void addEntry(LogEntry entry) {
        totalCount++;
        levelCounts.merge(entry.getLevel(), 1L, Long::sum);

        if (entry.hasTimestamp() &&
            (entry.getLevel() == LogLevel.CRITICAL || entry.getLevel() == LogLevel.ERROR)) {

            int hour = entry.getTimestamp().getHour();
            if (hour >= 0 && hour < 24) {
                hourlyErrors[hour]++;
            }
            if (!entry.getSource().isEmpty()) {
                sourceErrors.merge(entry.getSource(), 1L, Long::sum);
            }
        }
    }

    public long getTotalEntries() {
        return totalCount;
    }

    /** Записує HTML-звіт у вказаний файл. */
    public void writeHtml(String outputPath) throws IOException {
        try (Writer out = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(Path.of(outputPath)), StandardCharsets.UTF_8))) {
            writeHtmlContent(out);
        }
    }

    private void writeHtmlContent(Writer out) throws IOException {
        out.write("<!DOCTYPE html>\n<html lang='uk'>\n<head>\n");
        out.write("<meta charset='UTF-8'>\n");
        out.write("<title>LogAnalyzer Report</title>\n");
        out.write("<style>\n");
        out.write("body{font-family:Segoe UI,Arial,sans-serif;max-width:900px;margin:24px auto;"
            + "padding:0 16px;color:#222}\n");
        out.write("h1{border-bottom:2px solid #c0392b;padding-bottom:8px}\n");
        out.write("h2{margin-top:32px;color:#444}\n");
        out.write("table{border-collapse:collapse;width:100%;margin-top:12px}\n");
        out.write("th,td{border:1px solid #ddd;padding:8px 12px;text-align:left}\n");
        out.write("th{background:#f4f4f4}\n");
        out.write(".lvl-Critical{color:#fff;background:#8e1c1c;padding:2px 8px;border-radius:3px}\n");
        out.write(".lvl-Error{color:#fff;background:#c0392b;padding:2px 8px;border-radius:3px}\n");
        out.write(".lvl-Warning{color:#000;background:#f1c40f;padding:2px 8px;border-radius:3px}\n");
        out.write(".lvl-Information{color:#fff;background:#3498db;padding:2px 8px;border-radius:3px}\n");
        out.write(".meta{color:#888;font-size:13px}\n");
        out.write("</style>\n</head>\n<body>\n");

        out.write("<h1>LogAnalyzer — аналітичний звіт</h1>\n");

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        out.write("<p class='meta'>Згенеровано: " + now
            + " &middot; Усього записів: <strong>" + totalCount + "</strong></p>\n");

        // Розподіл за рівнями
        out.write("<h2>Розподіл подій за рівнями важливості</h2>\n<table>\n");
        out.write("<tr><th>Рівень</th><th>Кількість</th><th>Частка</th></tr>\n");
        for (LogLevel lv : LogLevel.values()) {
            long count = levelCounts.get(lv);
            double pct = totalCount > 0 ? 100.0 * count / totalCount : 0.0;
            out.write("<tr><td><span class='lvl-" + lv.displayName() + "'>"
                + lv.displayName() + "</span></td>");
            out.write("<td>" + count + "</td>");
            out.write(String.format(Locale.US, "<td>%.1f%%</td></tr>%n", pct));
        }
        out.write("</table>\n");

        // Графік
        out.write("<h2>Графік розподілу помилок по годинах</h2>\n");
        out.write(renderHourlyChartSvg());
        out.write("\n");

        // Топ джерел
        out.write("<h2>Топ-10 джерел за кількістю помилок</h2>\n");
        if (sourceErrors.isEmpty()) {
            out.write("<p class='meta'>Немає даних про джерела помилок.</p>\n");
        } else {
            List<Map.Entry<String, Long>> sorted = new ArrayList<>(sourceErrors.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

            out.write("<table>\n<tr><th>#</th><th>Source</th><th>Помилок</th></tr>\n");
            int limit = Math.min(10, sorted.size());
            for (int i = 0; i < limit; i++) {
                Map.Entry<String, Long> e = sorted.get(i);
                out.write("<tr><td>" + (i + 1) + "</td>");
                out.write("<td>" + htmlEscape(e.getKey()) + "</td>");
                out.write("<td>" + e.getValue() + "</td></tr>\n");
            }
            out.write("</table>\n");
        }

        out.write("</body>\n</html>\n");
    }

    private String renderHourlyChartSvg() {
        final int width = 720;
        final int height = 280;
        final int padX = 40;
        final int padY = 30;

        long maxVal = 1;
        for (long v : hourlyErrors) {
            if (v > maxVal) maxVal = v;
        }

        StringBuilder svg = new StringBuilder(4096);
        svg.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ")
           .append(width).append(' ').append(height)
           .append("' width='100%' height='").append(height).append("'>");

        // Осі
        svg.append("<line x1='").append(padX).append("' y1='").append(height - padY)
           .append("' x2='").append(width - padX / 2).append("' y2='").append(height - padY)
           .append("' stroke='#888' stroke-width='1'/>");
        svg.append("<line x1='").append(padX).append("' y1='").append(padY)
           .append("' x2='").append(padX).append("' y2='").append(height - padY)
           .append("' stroke='#888' stroke-width='1'/>");

        int chartW = width - padX - padX / 2;
        int chartH = height - padY * 2;
        double barW = chartW / 24.0;

        for (int h = 0; h < 24; h++) {
            double frac = (double) hourlyErrors[h] / maxVal;
            int barH = (int) (frac * chartH);
            int x = padX + (int) (h * barW) + 2;
            int y = (height - padY) - barH;
            int w = (int) barW - 4;
            if (w < 1) w = 1;

            svg.append("<rect x='").append(x).append("' y='").append(y)
               .append("' width='").append(w).append("' height='").append(barH)
               .append("' fill='#c0392b' opacity='0.85'/>");

            if (h % 3 == 0) {
                svg.append("<text x='").append(x + w / 2).append("' y='")
                   .append(height - padY + 16)
                   .append("' font-size='11' text-anchor='middle' fill='#444'>")
                   .append(h).append(":00</text>");
            }
        }

        svg.append("<text x='").append(width / 2).append("' y='18' font-size='14' "
            + "text-anchor='middle' fill='#222' font-weight='bold'>"
            + "Розподіл помилок за годинами доби</text>");
        svg.append("<text x='").append(padX).append("' y='").append(padY - 8)
           .append("' font-size='10' fill='#666'>max: ").append(maxVal).append("</text>");

        svg.append("</svg>");
        return svg.toString();
    }

    private static String htmlEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<':  sb.append("&lt;"); break;
                case '>':  sb.append("&gt;"); break;
                case '&':  sb.append("&amp;"); break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }
}
