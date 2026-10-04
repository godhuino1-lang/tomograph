package io.github.godhuino1.tomograph.semconv;

import java.util.Objects;
import java.util.Set;

/**
 * GenAI 语义约定里的 span 命名规则。
 *
 * <p><b>已核对</b>：对着 {@link SemconvRevision#REPOSITORY} 里
 * {@link SemconvRevision#SPAN_MODEL_FILE} 与 {@link SemconvRevision#ATTRIBUTE_REGISTRY_FILE}
 * 在 commit {@link SemconvRevision#VERIFIED_COMMIT} 处逐条比对。这个类的第一版只有
 * 十八个操作名里的六个，而且声明了 {@code create_agent} 却没给它提供构造方法。
 *
 * <p>OpenTelemetry 对 span 名字这么严格，是因为**后端就是按名字分组和聚合的**。
 * 名字写错不会报错——它只会让仪表盘上每一行都写着 "chat"、分不出是哪个模型，
 * 或者出现一行 "create_agent" 却无法按 agent 过滤。所以名字在这里**集中构造一次**，
 * 而不是在每个插桩点上各自拼字符串。
 *
 * <h2>一条"差点改错"的记录</h2>
 *
 * <p>回头复核这个类时，冒出过两个怀疑：一是 {@code chat} 可能已经改名叫 {@code inference}
 * （因为 span 类型叫 {@code gen_ai.inference.client}）；二是 javadoc 里那个
 * {@code generate_content} 的例子可能出自旧草案、是我们自己编的。
 * **两个怀疑都是错的**——注册表里 {@code chat} 和 {@code generate_content} 都是正式成员。
 * 当时如果照着任何一个怀疑去"修"，就会把本来正确的代码改坏。
 *
 * <p>结论：**"看起来合理的修正"需要和原始声明同等的证据**。
 *
 * <h2>主语（subject）规则</h2>
 *
 * <p>每个操作后面跟一个主语，而**主语因操作而异**。规范定义了降级规则的地方，
 * 缺主语时只输出裸操作名，而不是 {@code "chat null"}——**名字略欠具体仍然有用，字面上错的没用**。
 *
 * <ul>
 *   <li>{@code chat} / {@code generate_content} / {@code text_completion} / {@code embeddings}
 *       —— 主语是**被请求的模型**</li>
 *   <li>{@code retrieval} —— 主语是**数据源**，不是模型</li>
 *   <li>{@code create_agent} / {@code invoke_agent} / {@code plan} —— 主语是 agent 名字</li>
 *   <li>{@code execute_tool} —— 主语是工具名；厂商特定的细化还会追加更多主语
 *       （skill 名、skill 资源、可执行文件名），本版本尚未构造</li>
 *   <li>{@code invoke_workflow} —— 主语是 workflow 名字</li>
 *   <li>{@code fetch_response} 与 memory 系列 —— <b>没有主语</b>。{@code fetch_response}
 *       是规范明确写了的，因为 response id 基数太高（high cardinality）</li>
 * </ul>
 */
public final class SpanName {

    // --- 推理类 -----------------------------------------------------------------------

    /** 对话补全（chat completion）。 */
    public static final String CHAT = "chat";

    /** 多模态内容生成，例如 Gemini 的 generateContent。 */
    public static final String GENERATE_CONTENT = "generate_content";

    /** 遗留的纯文本补全接口。 */
    public static final String TEXT_COMPLETION = "text_completion";

    // --- 其它客户端操作 ---------------------------------------------------------------

    public static final String EMBEDDINGS = "embeddings";
    public static final String RETRIEVAL = "retrieval";

    /** 按 id 取回一个已经生成过的响应，**不执行推理**。 */
    public static final String FETCH_RESPONSE = "fetch_response";

    // --- agent / 工具 / workflow ------------------------------------------------------

    public static final String CREATE_AGENT = "create_agent";
    public static final String INVOKE_AGENT = "invoke_agent";
    public static final String EXECUTE_TOOL = "execute_tool";
    public static final String INVOKE_WORKFLOW = "invoke_workflow";

    /** agent 的规划（planning）或任务分解阶段。 */
    public static final String PLAN = "plan";

    // --- memory -----------------------------------------------------------------------
    //
    // 这些**都不带主语**：规范直接规定了裸操作名。

    public static final String SEARCH_MEMORY = "search_memory";
    public static final String CREATE_MEMORY = "create_memory";
    public static final String UPDATE_MEMORY = "update_memory";
    public static final String UPSERT_MEMORY = "upsert_memory";
    public static final String DELETE_MEMORY = "delete_memory";
    public static final String CREATE_MEMORY_STORE = "create_memory_store";
    public static final String DELETE_MEMORY_STORE = "delete_memory_store";

    private static final Set<String> MEMORY_OPERATIONS = Set.of(
            SEARCH_MEMORY, CREATE_MEMORY, UPDATE_MEMORY, UPSERT_MEMORY,
            DELETE_MEMORY, CREATE_MEMORY_STORE, DELETE_MEMORY_STORE);

    private SpanName() {
    }

    /** 一次对话补全往返。{@code model} 在框架还没解析出来时可以是 null。 */
    public static String chat(String model) {
        return withSubject(CHAT, model);
    }

    /** 一次多模态生成往返。 */
    public static String generateContent(String model) {
        return withSubject(GENERATE_CONTENT, model);
    }

    /** 一次遗留文本补全往返。 */
    public static String textCompletion(String model) {
        return withSubject(TEXT_COMPLETION, model);
    }

    /** 一次嵌入计算。 */
    public static String embeddings(String model) {
        return withSubject(EMBEDDINGS, model);
    }

    /**
     * 一次文档 / 向量检索步骤。
     *
     * <p>这是**唯一一个主语是数据源**、而不是模型或工具的名字——规范对检索 span 就是这么定义的。
     */
    public static String retrieval(String dataSourceId) {
        return withSubject(RETRIEVAL, dataSourceId);
    }

    /**
     * 按 id 取回一个已存储的响应：不执行推理，也不消耗 token。
     *
     * <p><b>故意不接受任何参数</b>：response id 基数太高，规范明确把它排除在 span 名字之外。
     */
    public static String fetchResponse() {
        return FETCH_RESPONSE;
    }

    /** 创建一个远端 agent 资源。 */
    public static String createAgent(String agentName) {
        return withSubject(CREATE_AGENT, agentName);
    }

    /** 一次端到端的 agent 运行。 */
    public static String invokeAgent(String agentName) {
        return withSubject(INVOKE_AGENT, agentName);
    }

    /** 一次工具 / 函数调用。 */
    public static String executeTool(String toolName) {
        return withSubject(EXECUTE_TOOL, toolName);
    }

    /** 一次 workflow 执行：协调多个 agent 或多次 GenAI 调用。 */
    public static String invokeWorkflow(String workflowName) {
        return withSubject(INVOKE_WORKFLOW, workflowName);
    }

    /** agent 的规划阶段。 */
    public static String plan(String agentName) {
        return withSubject(PLAN, agentName);
    }

    /**
     * 一次 memory 操作。
     *
     * <p>memory 的 span 名不带主语，所以这里**校验调用方传进来的确实是七种 memory 操作之一**，
     * 然后原样返回。另一种写法是接受任意字符串——那会让一个拼写错误产出一个"看起来很合理"的
     * span 名，而没有任何后端能正确地按它分组。
     *
     * @throws IllegalArgumentException 如果 {@code operation} 不是 GenAI 的 memory 操作
     */
    public static String memory(String operation) {
        Objects.requireNonNull(operation, "operation");
        if (!MEMORY_OPERATIONS.contains(operation)) {
            throw new IllegalArgumentException("not a GenAI memory operation: " + operation);
        }
        return operation;
    }

    /** 这个名字是否可能出自上面某个构造方法。 */
    public static boolean isKnownOperation(String operation) {
        return MEMORY_OPERATIONS.contains(operation)
                || ALL_OPERATIONS.contains(operation);
    }

    private static final Set<String> ALL_OPERATIONS = Set.of(
            CHAT, GENERATE_CONTENT, TEXT_COMPLETION, EMBEDDINGS, RETRIEVAL, FETCH_RESPONSE,
            CREATE_AGENT, INVOKE_AGENT, EXECUTE_TOOL, INVOKE_WORKFLOW, PLAN);

    /**
     * 把操作名和主语拼起来——**全项目唯一拼 span 名字的地方**。
     *
     * <p>主语为空时返回裸操作名，这是规范要求的降级行为；末尾还会 {@code trim()}
     * 掉多余的空白。
     */
    private static String withSubject(String operation, String subject) {
        Objects.requireNonNull(operation, "operation");
        if (subject == null || subject.isBlank()) {
            return operation;
        }
        return operation + " " + subject.trim();
    }
}
