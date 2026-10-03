package io.github.godhuino1.tomograph.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spans arranged into parent/child trees, grouped by trace id.
 *
 * <h2>Orphans are the interesting case</h2>
 *
 * <p>A captured payload is frequently <b>partial</b>: an export batch can be cut off by a size
 * limit, a flush interval can land mid-trace, and a filter can drop the parent while keeping the
 * child. A span whose {@code parentSpanId} names a span that is not in the file is an
 * <b>orphan</b>, and the honest handling is to render it as a root <em>and say so</em> — not to
 * pretend the file is a complete trace.
 *
 * <p>Presenting a partial recording as though it were complete is the specific failure this project
 * has to avoid: a half-shown agent run reads as an agent that skipped steps. So orphans are
 * counted and exposed via {@link #orphanCount()}, and the report can label them.
 */
public final class SpanTree {

    private final List<CapturedSpan> all;
    private final Map<String, CapturedSpan> byId;
    private final Map<String, List<CapturedSpan>> childrenByParentId;
    private final List<CapturedSpan> roots;
    private final int orphanCount;

    private SpanTree(List<CapturedSpan> all, Map<String, CapturedSpan> byId,
                     Map<String, List<CapturedSpan>> childrenByParentId,
                     List<CapturedSpan> roots, int orphanCount) {
        this.all = List.copyOf(all);
        this.byId = Map.copyOf(byId);
        this.childrenByParentId = childrenByParentId;
        this.roots = List.copyOf(roots);
        this.orphanCount = orphanCount;
    }

    public static SpanTree of(List<CapturedSpan> spans) {
        Map<String, CapturedSpan> byId = new HashMap<>();
        for (CapturedSpan span : spans) {
            byId.put(span.spanId(), span);
        }

        Map<String, List<CapturedSpan>> children = new LinkedHashMap<>();
        List<CapturedSpan> roots = new ArrayList<>();
        int orphans = 0;

        for (CapturedSpan span : spans) {
            if (span.isRoot()) {
                roots.add(span);
                continue;
            }
            if (!byId.containsKey(span.parentSpanId())) {
                // Rendered at the top level rather than dropped: losing the span entirely would be
                // worse than showing it without its parent, as long as the gap is disclosed.
                roots.add(span);
                orphans++;
                continue;
            }
            children.computeIfAbsent(span.parentSpanId(), key -> new ArrayList<>()).add(span);
        }

        return new SpanTree(spans, byId, children, roots, orphans);
    }

    /** Every span in the file, in the order it was read. */
    public List<CapturedSpan> all() {
        return all;
    }

    /** Spans with no parent in the file — true roots plus orphans. */
    public List<CapturedSpan> roots() {
        return roots;
    }

    public List<CapturedSpan> childrenOf(String spanId) {
        return Collections.unmodifiableList(
                childrenByParentId.getOrDefault(spanId, List.of()));
    }

    public Optional<CapturedSpan> byId(String spanId) {
        return Optional.ofNullable(byId.get(spanId));
    }

    /** How many rendered roots are orphans rather than genuine roots. */
    public int orphanCount() {
        return orphanCount;
    }

    public boolean isPartial() {
        return orphanCount > 0;
    }

    /** Distinct trace ids present, in first-seen order. */
    public List<String> traceIds() {
        List<String> ids = new ArrayList<>();
        for (CapturedSpan span : all) {
            if (!ids.contains(span.traceId())) {
                ids.add(span.traceId());
            }
        }
        return ids;
    }

    /** Total wall-clock time spanned by the whole file, which is what a timeline is scaled against. */
    public long totalDurationNanos() {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (CapturedSpan span : all) {
            min = Math.min(min, span.startEpochNanos());
            max = Math.max(max, span.endEpochNanos());
        }
        return (min > max) ? 0L : max - min;
    }

    public long errorCount() {
        return all.stream().filter(CapturedSpan::isError).count();
    }
}
