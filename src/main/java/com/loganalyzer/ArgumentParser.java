package com.loganalyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Парсер аргументів командного рядка.
 */
public class ArgumentParser {

    /** Розпарсений набір опцій командного рядка. */
    public static class CliOptions {
        public boolean showHelp = false;
        public Optional<String> inputFile = Optional.empty();
        public Optional<String> eventChannel = Optional.empty();
        public List<String> levelFilters = new ArrayList<>();
        public Optional<Integer> sinceHours = Optional.empty();
        public Optional<String> regexPattern = Optional.empty();
        public boolean tailMode = false;
        public Optional<String> exportPath = Optional.empty();
        public Optional<String> reportPath = Optional.empty();
    }

    /**
     * Розпарсити аргументи.
     * @throws IllegalArgumentException при некоректних опціях.
     */
    public static CliOptions parse(String[] args) {
        CliOptions opts = new CliOptions();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];

            switch (a) {
                case "--help":
                case "-h":
                    opts.showHelp = true;
                    break;
                case "--input":
                    opts.inputFile = Optional.of(requireValue(args, ++i, a));
                    break;
                case "--evtlog":
                    opts.eventChannel = Optional.of(requireValue(args, ++i, a));
                    break;
                case "--level":
                    opts.levelFilters = Arrays.asList(requireValue(args, ++i, a).split(","));
                    opts.levelFilters.removeIf(String::isBlank);
                    break;
                case "--since":
                    String v = requireValue(args, ++i, a);
                    try {
                        int hours = Integer.parseInt(v);
                        if (hours < 0) {
                            throw new IllegalArgumentException(
                                "--since requires a non-negative integer (hours)");
                        }
                        opts.sinceHours = Optional.of(hours);
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException(
                            "--since requires a non-negative integer (hours)");
                    }
                    break;
                case "--regex":
                    opts.regexPattern = Optional.of(requireValue(args, ++i, a));
                    break;
                case "--tail":
                    opts.tailMode = true;
                    break;
                case "--export":
                    opts.exportPath = Optional.of(requireValue(args, ++i, a));
                    break;
                case "--report":
                    opts.reportPath = Optional.of(requireValue(args, ++i, a));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown option: " + a);
            }
        }
        return opts;
    }

    private static String requireValue(String[] args, int idx, String flag) {
        if (idx >= args.length) {
            throw new IllegalArgumentException("Option " + flag + " requires a value");
        }
        return args[idx];
    }

    /** Текст вбудованої довідки. */
    public static String helpText() {
        return "LogAnalyzer — Windows log analysis CLI utility (Java)\n"
            + "\n"
            + "USAGE:\n"
            + "  java -jar LogAnalyzer.jar [OPTIONS]\n"
            + "\n"
            + "INPUT SOURCES (one of --input or --evtlog is required):\n"
            + "  --input <path>          Path to a text log file (.log, .txt)\n"
            + "  --evtlog <channel>      Read from Windows Event Log channel\n"
            + "                          (e.g. System, Application, Security)\n"
            + "                          Uses wevtutil.exe under the hood.\n"
            + "\n"
            + "FILTERS (optional, can be combined):\n"
            + "  --level <list>          Comma-separated severity levels.\n"
            + "                          Values: Critical, Error, Warning, Information.\n"
            + "                          Example: --level Error,Critical\n"
            + "  --since <hours>         Show only entries from the last N hours.\n"
            + "                          Example: --since 24\n"
            + "  --regex <pattern>       Java regex applied to the message field.\n"
            + "                          Example: --regex \"timeout|refused\"\n"
            + "\n"
            + "OUTPUT MODES (optional, can be combined; default is stdout):\n"
            + "  --tail                  Live tail mode: stream new entries as they arrive\n"
            + "                          (only with --input). Press Ctrl+C to stop.\n"
            + "  --export <path>         Write filtered results to a CSV file.\n"
            + "  --report <path>         Generate an HTML analytics report.\n"
            + "\n"
            + "MISC:\n"
            + "  --help, -h              Show this help message and exit.\n"
            + "\n"
            + "EXAMPLES:\n"
            + "  java -jar LogAnalyzer.jar --input app.log --level Error,Critical --since 24\n"
            + "  java -jar LogAnalyzer.jar --evtlog System --since 48 --report system.html\n"
            + "  java -jar LogAnalyzer.jar --input app.log --regex \"timeout\" --export errs.csv\n"
            + "  java -jar LogAnalyzer.jar --input app.log --tail --level Error\n";
    }
}
