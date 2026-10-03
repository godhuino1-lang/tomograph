package io.github.godhuino1.tomograph.report;

import io.github.godhuino1.tomograph.semconv.GenAiAttributes;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders a captured trace into one self-contained HTML file.
 *
 * <p>Self-contained means exactly that: no external CSS, no CDN script, no font URL, no image. The
 * file has to open from a bare {@code file://} on a machine with no network, because the situation
 * this exists for is attaching a trace to a bug report and reading it two years later.
 *
 * <h2>Three things this renderer has to defend against</h2>
 *
 * <ol>
 *   <li><b>The data is untrusted.</b> Span names and attribute values come from the observed
 *       application — a prompt, a tool name, a user id. If any of them contains {@code <script>},
 *       the report becomes an injection vector for whoever opens it. Everything interpolated is
 *       escaped, without exception, and a test asserts it.</li>
 *   <li><b>Percentages must use {@link Locale#ROOT}.</b> On a machine with a comma decimal
 *       separator, {@code String.format("%.3f", 0.5)} yields {@code 0,500} — which produces
 *       {@code left:0,500%} and silently collapses the whole timeline. The layout breaks on some
 *       developers' machines and not others, which is the worst way to find a bug.</li>
 *   <li><b>A malformed file can contain a cycle.</b> Nothing stops a hand-edited payload from
 *       saying A's parent is B and B's parent is A. Recursion over that never terminates, so
 *       rendering keeps a set of what it has already emitted and stops when it meets it again.</li>
 * </ol>
 */
public final class HtmlReport {

    /** OTLP span kind numbers. Enum names are forbidden on the wire, so the mapping lives here. */
    private static final String[] KIND_NAMES =
            {"UNSPECIFIED", "INTERNAL", "SERVER", "CLIENT", "PRODUCER", "CONSUMER"};

    private static final String[] STATUS_NAMES = {"UNSET", "OK", "ERROR"};

    /**
     * @param title       shown as the page heading
     * @param sourceName  the file the data came from, so a reader knows what they are looking at
     * @param generatedAt an ISO-8601 instant, or null to omit it (tests pass null for determinism)
     */
    public record Metadata(String title, String sourceName, String generatedAt) {

        public static Metadata of(String title, String sourceName) {
            return new Metadata(title, sourceName, null);
        }
    }

    private HtmlReport() {
    }

    public static String render(SpanTree tree, Metadata metadata) {
        StringBuilder html = new StringBuilder(16 * 1024);
        String title = (metadata.title() == null || metadata.title().isBlank())
                ? "Tomograph report" : metadata.title();

        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
                .append("<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>").append(escape(title)).append("</title>\n")
                .append(style())
                .append("</head>\n<body>\n");

        html.append("<header>\n<h1>").append(escape(title)).append("</h1>\n");
        html.append("<p class=\"meta\">");
        if (metadata.sourceName() != null) {
            html.append("source <code>").append(escape(metadata.sourceName())).append("</code> &middot; ");
        }
        // Which service this is, stated up front: it is the first thing anyone asks of a trace file,
        // and it belongs in the header rather than repeated on every row. The reader caught this
        // being parsed and never displayed, which is why the test asserts on it.
        List<String> services = tree.all().stream()
                .map(CapturedSpan::serviceName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        if (!services.isEmpty()) {
            html.append(services.size() == 1 ? "service " : "services ")
                    .append(escape(String.join(", ", services)))
                    .append(" &middot; ");
        }
        html.append(tree.all().size()).append(" spans &middot; ")
                .append(tree.traceIds().size()).append(" traces &middot; ")
                .append(tree.errorCount()).append(" errors &middot; ")
                .append("wall clock ").append(escape(formatDuration(tree.totalDurationNanos())));
        if (metadata.generatedAt() != null) {
            html.append(" &middot; generated ").append(escape(metadata.generatedAt()));
        }
        html.append("</p>\n");

        if (tree.isPartial()) {
            html.append("<p class=\"warning\"><b>This capture is not a complete trace.</b> ")
                    .append(escape(String.join("; ", tree.partialReasons())))
                    .append(". They are shown at the top level. Do not read this as a complete agent run: steps may be missing.</p>\n");
        }
        html.append("</header>\n<main>\n");

        long origin = originNanos(tree);
        long total = tree.totalDurationNanos();

        for (String traceId : tree.traceIds()) {
            List<CapturedSpan> spansOfTrace = tree.all().stream()
                    .filter(span -> span.traceId().equals(traceId))
                    .toList();
            html.append("<section class=\"trace\">\n<h2>trace <code>")
                    .append(escape(traceId))
                    .append("</code> <span class=\"count\">")
                    .append(spansOfTrace.size()).append(" spans</span></h2>\n<ol class=\"tree\">\n");

            Set<String> emitted = new HashSet<>();
            for (CapturedSpan root : tree.roots()) {
                if (root.traceId().equals(traceId)) {
                    appendSpan(html, tree, root, origin, total, emitted);
                }
            }
            html.append("</ol>\n</section>\n");
        }

        if (tree.all().isEmpty()) {
            html.append("<p class=\"empty\">No spans in this file.</p>\n");
        }

        html.append("</main>\n<footer>Generated by Tomograph &middot; an offline report, ")
                .append("openable without a network or a server.</footer>\n</body>\n</html>\n");
        return html.toString();
    }

    private static void appendSpan(StringBuilder html, SpanTree tree, CapturedSpan span,
                                   long origin, long total, Set<String> emitted) {
        // The cycle guard. A payload that says A's parent is B and B's parent is A would otherwise
        // recurse until the stack ends.
        if (!emitted.add(span.spanId())) {
            html.append("<li class=\"cycle\"><span class=\"name\">")
                    .append(escape(span.name()))
                    .append("</span> <span class=\"badge\">already shown above &mdash; the file contains a cycle</span></li>\n");
            return;
        }

        String kind = (span.kind() >= 0 && span.kind() < KIND_NAMES.length)
                ? KIND_NAMES[span.kind()] : ("KIND_" + span.kind());
        String status = (span.statusCode() >= 0 && span.statusCode() < STATUS_NAMES.length)
                ? STATUS_NAMES[span.statusCode()] : ("CODE_" + span.statusCode());

        html.append("<li>\n<div class=\"row\">\n")
                .append("<span class=\"name\">").append(escape(span.name())).append("</span>\n")
                .append("<span class=\"badge kind\">").append(escape(kind)).append("</span>\n");

        if (span.isError()) {
            html.append("<span class=\"badge error\">ERROR</span>\n");
        } else if (span.statusCode() == 1) {
            html.append("<span class=\"badge ok\">").append(escape(status)).append("</span>\n");
        }

        String model = span.stringAttribute(GenAiAttributes.REQUEST_MODEL);
        if (model != null) {
            html.append("<span class=\"badge model\">").append(escape(model)).append("</span>\n");
        }

        Object inputTokens = span.attributes().get(GenAiAttributes.USAGE_INPUT_TOKENS);
        Object outputTokens = span.attributes().get(GenAiAttributes.USAGE_OUTPUT_TOKENS);
        if (inputTokens != null || outputTokens != null) {
            html.append("<span class=\"badge tokens\">tokens ")
                    .append(escape(String.valueOf(inputTokens == null ? "?" : inputTokens)))
                    .append(" &rarr; ")
                    .append(escape(String.valueOf(outputTokens == null ? "?" : outputTokens)))
                    .append("</span>\n");
        }

        html.append("<span class=\"dur\">").append(escape(formatDuration(span.durationNanos())))
                .append("</span>\n</div>\n");

        // Locale.ROOT: with a comma decimal separator these percentages would emit "left:0,500%",
        // which is not valid CSS and collapses the bar without any error.
        if (total > 0) {
            double left = (span.startEpochNanos() - origin) * 100.0 / total;
            double width = Math.max(span.durationNanos() * 100.0 / total, 0.35);
            html.append("<div class=\"bar\"><span style=\"left:")
                    .append(String.format(Locale.ROOT, "%.3f", clamp(left)))
                    .append("%;width:")
                    .append(String.format(Locale.ROOT, "%.3f", Math.min(width, 100.0 - clamp(left))))
                    .append("%\"></span></div>\n");
        }

        if (!span.attributes().isEmpty()) {
            html.append("<details><summary>attributes (").append(span.attributes().size())
                    .append(")</summary>\n<table>\n");
            for (Map.Entry<String, Object> entry : span.attributes().entrySet()) {
                html.append("<tr><th>").append(escape(entry.getKey())).append("</th><td>")
                        .append(escape(String.valueOf(entry.getValue()))).append("</td></tr>\n");
            }
            html.append("</table>\n</details>\n");
        }

        List<CapturedSpan> children = tree.childrenOf(span.spanId());
        if (!children.isEmpty()) {
            html.append("<ol class=\"tree\">\n");
            for (CapturedSpan child : children) {
                appendSpan(html, tree, child, origin, total, emitted);
            }
            html.append("</ol>\n");
        }

        html.append("</li>\n");
    }

    private static long originNanos(SpanTree tree) {
        return tree.all().stream().mapToLong(CapturedSpan::startEpochNanos).min().orElse(0L);
    }

    private static double clamp(double percentage) {
        return Math.max(0.0, Math.min(percentage, 100.0));
    }

    /** Nanoseconds through seconds, because "1791030141538000000 ns" is unreadable. */
    static String formatDuration(long nanos) {
        if (nanos < 1_000L) {
            return nanos + " ns";
        }
        if (nanos < 1_000_000L) {
            return String.format(Locale.ROOT, "%.2f us", nanos / 1_000.0);
        }
        if (nanos < 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.2f ms", nanos / 1_000_000.0);
        }
        return String.format(Locale.ROOT, "%.2f s", nanos / 1_000_000_000.0);
    }

    /**
     * Escapes text for HTML. Applied to every value taken from the capture, with no exceptions:
     * those values originated in the observed application, which makes them attacker-influenced
     * as far as this file is concerned.
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String style() {
        return """
                <style>
                :root { color-scheme: light dark; }
                body { font: 14px/1.5 ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif;
                       margin: 0 auto; max-width: 1100px; padding: 24px; }
                h1 { font-size: 20px; margin: 0 0 4px; }
                h2 { font-size: 15px; margin: 24px 0 8px; font-weight: 600; }
                .meta, footer { color: #666; font-size: 12px; }
                footer { margin-top: 32px; border-top: 1px solid #ddd; padding-top: 8px; }
                code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
                .warning { background: #fff4e5; border-left: 3px solid #e8a33d; padding: 10px 12px;
                           margin: 12px 0; font-size: 13px; }
                .empty { color: #666; font-style: italic; }
                ol.tree { list-style: none; margin: 0; padding-left: 0; }
                ol.tree ol.tree { padding-left: 18px; border-left: 1px solid #e3e3e3; margin-left: 4px; }
                li { margin: 6px 0; }
                .row { display: flex; flex-wrap: wrap; gap: 8px; align-items: baseline; }
                .name { font-weight: 600; }
                .dur { margin-left: auto; font-variant-numeric: tabular-nums; color: #444; }
                .badge { font-size: 11px; padding: 1px 6px; border-radius: 9px; background: #eee;
                         color: #333; white-space: nowrap; }
                .badge.error { background: #fdecea; color: #b3261e; font-weight: 700; }
                .badge.ok { background: #e7f4ea; color: #1e6b34; }
                .badge.kind { background: #e8eefc; color: #2a4b9b; }
                .badge.model { background: #f0e8fc; color: #5b2a9b; }
                .badge.tokens { background: #eaf6f6; color: #1f6b6b; }
                .bar { position: relative; height: 6px; background: #f1f1f1; border-radius: 3px;
                       margin: 4px 0; }
                .bar span { position: absolute; top: 0; height: 6px; background: #4b7bec;
                            border-radius: 3px; }
                .count { color: #666; font-weight: 400; font-size: 12px; }
                details { margin: 4px 0 0 2px; }
                summary { cursor: pointer; color: #555; font-size: 12px; }
                table { border-collapse: collapse; margin: 6px 0 6px 2px; font-size: 12px; }
                th, td { text-align: left; padding: 2px 10px 2px 0; vertical-align: top;
                         border-bottom: 1px solid #f0f0f0; }
                th { color: #555; font-weight: 500; white-space: nowrap; }
                .cycle { color: #b3261e; }
                @media print { .bar, .badge { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
                </style>
                """;
    }
}
