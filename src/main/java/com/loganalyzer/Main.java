package com.loganalyzer;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.PatternSyntaxException;


public class Main {

    private static volatile LiveTail tailRef = null;

    public static void main(String[] args) {
        // UTF-8 у консолі — критично для кирилиці на Windows
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        // Перехоплення Ctrl+C для режиму --tail
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (tailRef != null) tailRef.stop();
        }));

        try {
            ArgumentParser.CliOptions opts = ArgumentParser.parse(args);

            if (opts.showHelp || args.length == 0) {
                System.out.print(ArgumentParser.helpText());
                return;
            }

            if (opts.inputFile.isEmpty() && opts.eventChannel.isEmpty()) {
                System.err.println("Error: --input or --evtlog is required.");
                System.err.println("Run with --help to see usage.");
                System.exit(2);
            }

            if (opts.tailMode) {
                if (opts.inputFile.isEmpty()) {
                    System.err.println("Error: --tail requires --input.");
                    System.exit(2);
                }
                runTailMode(opts);
                return;
            }

            if (opts.inputFile.isPresent()) {
                runFromFile(opts);
            } else {
                runFromEventLog(opts);
            }

        } catch (PatternSyntaxException e) {
            System.err.println("Argument error: Invalid regex pattern: " + e.getMessage());
            System.exit(2);
        } catch (IllegalArgumentException e) {
            System.err.println("Argument error: " + e.getMessage());
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Runtime error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static LogFilter buildFilter(ArgumentParser.CliOptions opts) {
        LogFilter filter = new LogFilter();

        for (String s : opts.levelFilters) {
            LogLevel lv = LogLevel.fromString(s);
            if (lv == LogLevel.UNKNOWN) {
                throw new IllegalArgumentException(
                    "Unknown level value: '" + s +
                    "' (allowed: Critical, Error, Warning, Information)");
            }
            filter.allowLevel(lv);
        }

        opts.sinceHours.ifPresent(filter::setRecentHours);
        opts.regexPattern.ifPresent(filter::setRegex);

        return filter;
    }

    private static void printEntry(LogEntry e) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('[').append(e.formatTimestamp()).append("] ");
        sb.append('[').append(e.getLevel().displayName()).append("] ");
        if (!e.getSource().isEmpty()) {
            sb.append('[').append(e.getSource()).append("] ");
        }
        sb.append(e.getMessage());
        System.out.println(sb);
    }

    private static void runFromFile(ArgumentParser.CliOptions opts) throws IOException {
        LogParser parser = new LogParser();
        LogFilter filter = buildFilter(opts);

        long[] stats = new long[]{0, 0}; // parsed, matched

        try (CsvExporter csv = opts.exportPath.isPresent()
                ? new CsvExporter(opts.exportPath.get()) : null) {

            ReportGenerator report = new ReportGenerator();
            boolean needsReport = opts.reportPath.isPresent();
            boolean stdoutMode = opts.exportPath.isEmpty() && opts.reportPath.isEmpty();

            parser.parseFile(opts.inputFile.get(), entry -> {
                stats[0]++;
                if (!filter.matches(entry)) return true;
                stats[1]++;

                try {
                    if (csv != null) csv.write(entry);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
                if (needsReport) report.addEntry(entry);
                if (stdoutMode) printEntry(entry);
                return true;
            });

            if (needsReport) {
                report.writeHtml(opts.reportPath.get());
                System.err.println("Report saved to: " + opts.reportPath.get());
            }
            if (csv != null) {
                System.err.println("CSV exported to: " + opts.exportPath.get()
                    + " (" + csv.rowCount() + " rows)");
            }
        }

        System.err.println("Parsed: " + stats[0] + ", matched: " + stats[1]);
    }

    private static void runFromEventLog(ArgumentParser.CliOptions opts) throws IOException {
        EventLogReader reader = new EventLogReader();
        LogFilter filter = buildFilter(opts);
        long sinceHours = opts.sinceHours.orElse(0);

        long[] stats = new long[]{0, 0};

        try (CsvExporter csv = opts.exportPath.isPresent()
                ? new CsvExporter(opts.exportPath.get()) : null) {

            ReportGenerator report = new ReportGenerator();
            boolean needsReport = opts.reportPath.isPresent();
            boolean stdoutMode = opts.exportPath.isEmpty() && opts.reportPath.isEmpty();

            reader.readChannel(opts.eventChannel.get(), sinceHours, entry -> {
                stats[0]++;
                if (!filter.matches(entry)) return true;
                stats[1]++;

                try {
                    if (csv != null) csv.write(entry);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
                if (needsReport) report.addEntry(entry);
                if (stdoutMode) printEntry(entry);
                return true;
            });

            if (needsReport) {
                report.writeHtml(opts.reportPath.get());
                System.err.println("Report saved to: " + opts.reportPath.get());
            }
            if (csv != null) {
                System.err.println("CSV exported to: " + opts.exportPath.get()
                    + " (" + csv.rowCount() + " rows)");
            }
        }

        System.err.println("Read events: " + stats[0] + ", matched: " + stats[1]);
    }

    private static void runTailMode(ArgumentParser.CliOptions opts) throws IOException {
        LogParser parser = new LogParser();
        LogFilter filter = buildFilter(opts);
        LiveTail tail = new LiveTail(parser, filter);
        tailRef = tail;

        System.err.println("Tailing " + opts.inputFile.get() + " (Ctrl+C to stop)...");

        tail.watch(opts.inputFile.get(), Main::printEntry);

        System.err.println("Tail mode stopped.");
    }
}
