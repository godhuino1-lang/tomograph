package io.github.godhuino1.tomograph.api;

import java.util.Map;
import java.util.Objects;

/**
 * 一次 agent 运行中被采集到的一个工作单元。
 *
 * <p>字段名在规范有定义时遵循 OpenTelemetry GenAI 语义约定（semantic conventions）；
 * 属于 Tomograph 自己的东西一律加 {@code tomograph.} 前缀（见 {@code tomograph-semconv} 模块）。
 *
 * <p>不可变（immutable），可以安全地交给任意线程。
 */
public record TomographSpan(
        String traceId,
        String spanId,
        String parentSpanId,
        Kind kind,
        String name,
        long startEpochNanos,
        long durationNanos,
        Map<String, Object> attributes,
        Status status,
        String statusMessage) {

    /** Tomograph 承诺能采到的五类语义切点（v0.1 的验收标准之一）。 */
    public enum Kind {
        /** 一次端到端的 agent 运行：一个用户请求流过整个循环。 */
        AGENT_RUN,
        /** 一次模型调用（请求 + 响应）。 */
        LLM_CALL,
        /** 一次工具/函数调用，包含它的副作用。 */
        TOOL_CALL,
        /** 一次检索步骤（向量检索、关键词检索、文档查找）。 */
        RETRIEVAL,
        /** 一次嵌入（embedding）计算。 */
        EMBEDDING,
        /** Tomograph 测量的其它东西：排队、序列化、重试。 */
        INTERNAL
    }

    public enum Status {
        UNSET,
        OK,
        ERROR
    }

    /**
     * 紧凑构造器（compact constructor）：record 的字段在这里做校验和归一化。
     *
     * <p>注意最后两行——把 {@code null} 收敛成默认值，而不是留着。这样"没采到"和"采到了空"
     * 在数据结构层面就是同一件事，下游不用到处判空。
     */
    public TomographSpan {
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(spanId, "spanId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        attributes = (attributes == null) ? Map.of() : Map.copyOf(attributes);
        status = (status == null) ? Status.UNSET : status;
    }

    public static Builder builder(String traceId, String spanId, Kind kind, String name) {
        return new Builder(traceId, spanId, kind, name);
    }

    /** 结束时间 = 开始时间 + 时长。存的是这两个，而不是开始和结束两个时间戳。 */
    public long endEpochNanos() {
        return startEpochNanos + durationNanos;
    }

    /**
     * 从墙上时钟（wall clock）读出的 epoch 纳秒。
     *
     * <p><b>故意不用 {@link System#nanoTime()}。</b> 那是一个单调时钟（monotonic clock），
     * 它的起点是任意的（通常是系统启动时刻）；而 OTLP 的 {@code startTimeUnixNano} 意思是
     * "距离 Unix 纪元（1970-01-01）的纳秒数"。用错的结果是**每一个导出的 span 都被打上 1970 年**——
     * 这正是本项目第一次端到端跑通时发生的事，而且当时所有单元测试都是绿的。
     *
     * <p>精度只到毫秒：JDK 无法直接读出 epoch 纳秒，**编造多余的数字比承认这个限制更糟**。
     * 亚毫秒精度对"时长"才有意义，所以时长用 {@code System.nanoTime()} 的差值来算——
     * 见 {@link #durationNanos()}。
     */
    public static long epochNanosNow() {
        return System.currentTimeMillis() * 1_000_000L;
    }

    /**
     * 一个小 builder：被插桩的调用点要填 6–12 个属性，没有 builder 的话代码会没法读。
     *
     * <p>既然 record 不可变，为什么还需要 builder？因为不可变只解决"造好之后不能改"，
     * 不解决"造的时候要填十几个字段"。两者是不同的问题。
     */
    public static final class Builder {

        private final String traceId;
        private final String spanId;
        private final Kind kind;
        private final String name;
        private String parentSpanId;
        /** 默认现在就取一次时间：调用方不显式给开始时间时，span 从构造这一刻开始算。 */
        private long startEpochNanos = epochNanosNow();
        private long durationNanos;
        /** LinkedHashMap 而不是 Map.copyOf：插入顺序要保留，否则同一份数据生成的报告没法 diff。 */
        private final Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        private Status status = Status.UNSET;
        private String statusMessage;

        private Builder(String traceId, String spanId, Kind kind, String name) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.kind = kind;
            this.name = name;
        }

        public Builder parent(String parentSpanId) {
            this.parentSpanId = parentSpanId;
            return this;
        }

        public Builder start(long epochNanos) {
            this.startEpochNanos = epochNanos;
            return this;
        }

        public Builder duration(long nanos) {
            this.durationNanos = nanos;
            return this;
        }

        public Builder attribute(String key, Object value) {
            if (key != null && value != null) {
                attributes.put(key, value);
            }
            return this;
        }

        /** {@code null} 值会被丢掉：OTLP 里没有"null 属性"，**键不存在就表示"没采到"**。 */
        public Builder attributes(Map<String, Object> values) {
            if (values != null) {
                values.forEach(this::attribute);
            }
            return this;
        }

        public Builder status(Status status, String message) {
            this.status = status;
            this.statusMessage = message;
            return this;
        }

        /** 把异常直接翻成 ERROR 状态——这是插桩代码里最常用的一句。 */
        public Builder error(Throwable t) {
            if (t != null) {
                this.status = Status.ERROR;
                this.statusMessage = t.getClass().getName() + ": " + t.getMessage();
            }
            return this;
        }

        public TomographSpan build() {
            return new TomographSpan(traceId, spanId, parentSpanId, kind, name,
                    startEpochNanos, durationNanos, attributes, status, statusMessage);
        }
    }
}
