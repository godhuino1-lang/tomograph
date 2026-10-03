package io.github.godhuino1.tomograph.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Command line entry point: a captured OTLP/JSON file in, one self-contained HTML file out.
 *
 * <pre>
 * java -jar tomograph-report-html.jar capture.json report.html [--title "checkout agent"]
 * </pre>
 *
 * <p>Deliberately a file-to-file tool. It never contacts a backend, never needs the agent, and
 * never needs the application that produced the capture — which is what makes it usable on a
 * colleague's machine, or on a trace attached to an issue two years from now.
 *
 * <p>Exit codes: 0 written, 2 bad usage, 1 anything else. Nothing is written when the input cannot
 * be read, so a failed run never leaves a truncated report behind for someone to trust.
 */
public final class ReportMain {

    private ReportMain() {
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: tomograph-report <capture.json> <report.html> [--title <text>]");
            System.exit(2);
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String title = null;
        for (int i = 2; i < args.length - 1; i++) {
            if ("--title".equals(args[i])) {
                title = args[i + 1];
            }
        }

        if (!Files.isReadable(input)) {
            System.err.println("cannot read: " + input);
            System.exit(1);
        }

        try {
            List<CapturedSpan> spans = OtlpJsonReader.read(input);
            SpanTree tree = SpanTree.of(spans);

            String report = HtmlReport.render(tree, new HtmlReport.Metadata(
                    (title == null) ? "Tomograph report" : title,
                    input.getFileName().toString(),
                    Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()));

            Files.writeString(output, report, StandardCharsets.UTF_8);

            System.out.println("wrote " + output + " (" + report.length() + " chars)");
            System.out.println("  " + spans.size() + " spans, "
                    + tree.traceIds().size() + " traces, "
                    + tree.errorCount() + " errors");
            if (tree.isPartial()) {
                // Said out loud on the console too, not only inside the report: a partial capture
                // that is only disclosed in a file nobody opened yet is not disclosed.
                System.out.println("  WARNING: " + tree.orphanCount()
                        + " span(s) reference a parent that is not in this file - the capture is incomplete");
            }
        } catch (IOException | RuntimeException e) {
            // RuntimeException included on purpose: a malformed payload must produce a message, not
            // a stack trace, because the person running this is usually not the person who wrote it.
            System.err.println("could not read " + input + ": " + e);
            System.exit(1);
        }
    }
}
