package io.github.godhuino1.tomograph.report;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Spans arranged into parent/child trees, grouped by trace id.
 *
 * <h2>Two ways a file is not a clean tree</h2>
 *
 * <p><b>Orphans.</b> A captured payload is frequently partial: an export batch can be cut off by a
 * size limit, a flush interval can land mid-trace, a filter can drop the parent and keep the child.
 * A span whose {@code parentSpanId} names a span that is not in the file is an orphan, and the
 * honest handling is to render it as a root <em>and say so</em>.
 *
 * <p><b>Cycles.</b> Nothing stops a hand-edited or corrupted payload from claiming A's parent is B
 * and B's parent is A. Both spans then have a parent <em>that exists</em>, so neither is an orphan —
 * and a naive implementation ends with no roots at all, which means the renderer walks from an
 * empty list and <b>silently drops every span in the cycle</b>. A report that shows nothing looks
 * like a capture with no data, not like a corrupt file. So reachability is computed explicitly and
 * anything unreachable from a root is promoted to a root and counted.
 *
 * <p>Both cases exist for one reason: this project's worst failure is a partial view presented as
 * a complete one. A half-shown agent run reads as an agent that skipped steps.
 */
public final class SpanTree {

    private final List<CapturedSpan> all;
    private final Map<String, CapturedSpan> byId;
    private final Map<String, List<CapturedSpan>> childrenByParentId;
    private final List<CapturedSpan> roots;
    private final int orphanCount;
    private final int unreachableCount;

    private SpanTree(List<CapturedSpan> all, Map<String, CapturedSpan> byId,
                     Map<String, List<CapturedSpan>> childrenByParentId,
                     List<CapturedSpan> roots, int orphanCount, int unreachableCount) {
        this.all = List.copyOf(all);
        this.byId = Map.copyOf(byId);
        this.childrenByParentId = childrenByParentId;
        this.roots = List.copyOf(roots);
        this.orphanCount = orphanCount;
        this.unreachableCount = unreachableCount;
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
                // Shown at the top level rather than dropped: losing the span entirely would be
                // worse than showing it without its parent, as long as the gap is disclosed.
                roots.add(span);
                orphans++;
                continue;
            }
            children.computeIfAbsent(span.parentSpanId(), key -> new ArrayList<>()).add(span);
        }

        // Reachability pass. Anything not reachable from a root cannot be rendered by walking the
        // tree, so it would vanish from the report without a word - the exact failure this class
        // exists to prevent.
        Set<String> reachable = new HashSet<>();
        Deque<CapturedSpan> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            CapturedSpan span = pending.pop();
            if (!reachable.add(span.spanId())) {
                continue;
            }
            pending.addAll(children.getOrDefault(span.spanId(), List.of()));
        }

        int unreachable = 0;
        for (CapturedSpan span : spans) {
            if (!reachable.contains(span.spanId())) {
                roots.add(span);
                unreachable++;
            }
        }

        return new SpanTree(spans, byId, children, roots, orphans, unreachable);
    }

    /** Every span in the file, in the order it was read. */
    public List<CapturedSpan> all() {
        return all;
    }

    /** Spans with no parent in the file, plus orphans and anything unreachable from a root. */
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

    /** How many spans could not be reached from any root, which means the file contains a cycle. */
    public int unreachableCount() {
        return unreachableCount;
    }

    public boolean isPartial() {
        return orphanCount > 0 || unreachableCount > 0;
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
