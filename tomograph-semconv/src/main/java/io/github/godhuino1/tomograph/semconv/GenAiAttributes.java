package io.github.godhuino1.tomograph.semconv;

/**
 * OpenTelemetry GenAI 语义约定的属性键（attribute keys）。
 *
 * <p><b>已核对</b>：对着 {@link SemconvRevision#REPOSITORY} 里的
 * {@link SemconvRevision#ATTRIBUTE_REGISTRY_FILE} 在 commit {@link SemconvRevision#VERIFIED_COMMIT}
 * （{@link SemconvRevision#VERIFIED_COMMIT_DATE}）处逐条比对。
 * 这个文件的第一版是凭工作经验写的、并标注为"未核对"；后来真去核对，**发现了真错误**——
 * 这正是"记录一个目标版本号"而不是"留一句模糊的待办"的理由。
 *
 * <p>核对纠正了什么：
 * <ul>
 *   <li>{@code gen_ai.token.type} <b>已经不存在</b>，被 {@link #TOKEN_MODALITY} 取代
 *       （后者描述 text/image/audio，而不是 input/output）。继续发旧的键，会产出任何后端都不认识的属性——
 *       而**这个错误是隐形的**，因为没有任何东西会校验属性名。</li>
 *   <li>{@code gen_ai.system} 也已从注册表移除（不是"标记废弃"）。这里保留它，只是为了让老版本
 *       插桩产生的 trace 在读的时候还能被认出来。</li>
 *   <li><b>缓存 token 的拆分整个漏掉了</b>——它是"看起来合理的成本估算"和"错的成本估算"之间的差别，
 *       因为缓存命中的输入 token 计费方式不同。</li>
 *   <li><b>工具调用的参数与结果漏掉了</b>，导致"工具调用及其副作用"这条验收标准无处记录副作用。</li>
 * </ul>
 *
 * <p>注册表比这个类大得多。以下分组**故意还没列**，因为项目里还没有任何东西会发它们：
 * {@code gen_ai.memory.*}、{@code gen_ai.evaluation.*}、{@code gen_ai.retrieval.documents} 的 JSON 结构，
 * 以及需要调用方显式选择加入（opt in）的消息内容属性（{@code gen_ai.input.messages}、
 * {@code gen_ai.output.messages}、{@code gen_ai.system_instructions}）。
 * 把它们在这里点名，是为了让"加一个键"变成**查表**，而不是**发明**。
 *
 * <p>这个模块存在的理由：本项目的定位是这些约定的<b>消费者</b>，绝不是自己字段名的发明者。
 * 把键集中在一处，意味着规范改名时**只需要改一个文件**，而不是在插桩模块里到处 grep。
 */
public final class GenAiAttributes {

    // --- 是谁、在做什么 ---------------------------------------------------------------

    /**
     * AI 系统，例如 {@code openai}、{@code anthropic}。<b>已从注册表移除。</b>
     *
     * @deprecated 由 {@link #PROVIDER_NAME} 取代。保留它只是为了让老版本插桩产生的 trace 还能被读；
     *             **任何新代码都不应该发它**。
     */
    @Deprecated
    public static final String SYSTEM = "gen_ai.system";

    /** 谁运行的模型。注册表枚举了允许的取值，例如 {@code openai}。 */
    public static final String PROVIDER_NAME = "gen_ai.provider.name";

    /**
     * 正在执行的操作。注册表的取值列表很长——{@code chat}、{@code embeddings}、
     * {@code execute_tool}、{@code invoke_agent}、{@code plan}、{@code invoke_workflow}、
     * {@code fetch_response}、{@code *_memory} 系列等等。
     *
     * <p>Tomograph 实际发出去的取值住在 {@link SpanName} 里，因为它们同时是 span 名的前缀，
     * 在这里再写一份只会导致两边慢慢不一致。
     */
    public static final String OPERATION_NAME = "gen_ai.operation.name";

    // --- 请求 -------------------------------------------------------------------------

    public static final String REQUEST_MODEL = "gen_ai.request.model";
    public static final String REQUEST_MAX_TOKENS = "gen_ai.request.max_tokens";
    public static final String REQUEST_TEMPERATURE = "gen_ai.request.temperature";
    public static final String REQUEST_TOP_P = "gen_ai.request.top_p";

    /**
     * Top-K 采样。
     *
     * <p>注册表**明确警告**：OpenAI 的 {@code top_logprobs} 是另一回事，绝不能填在这里。
     */
    public static final String REQUEST_TOP_K = "gen_ai.request.top_k";

    public static final String REQUEST_FREQUENCY_PENALTY = "gen_ai.request.frequency_penalty";
    public static final String REQUEST_PRESENCE_PENALTY = "gen_ai.request.presence_penalty";
    public static final String REQUEST_STOP_SEQUENCES = "gen_ai.request.stop_sequences";
    public static final String REQUEST_CHOICE_COUNT = "gen_ai.request.choice.count";
    public static final String REQUEST_SEED = "gen_ai.request.seed";
    public static final String REQUEST_ENCODING_FORMATS = "gen_ai.request.encoding_formats";

    /** 这次请求是否以流式（streaming）模式发出。 */
    public static final String REQUEST_STREAM = "gen_ai.request.stream";

    /** 推理 / 思考强度，例如 {@code low}、{@code medium}、{@code high}。 */
    public static final String REQUEST_REASONING_LEVEL = "gen_ai.request.reasoning.level";

    // --- 响应 -------------------------------------------------------------------------

    public static final String RESPONSE_ID = "gen_ai.response.id";
    public static final String RESPONSE_MODEL = "gen_ai.response.model";
    public static final String RESPONSE_FINISH_REASONS = "gen_ai.response.finish_reasons";

    /**
     * 可能是后台任务的响应的生命周期状态：{@code queued}、{@code in_progress}、
     * {@code completed}、{@code incomplete}、{@code failed}、{@code cancelled}。
     *
     * <p>和 finish reasons 不是一回事：后者描述"开始生成之后为什么停下来"。
     */
    public static final String RESPONSE_STATUS = "gen_ai.response.status";

    /** 从发出流式请求到收到第一个 chunk 的秒数。 */
    public static final String RESPONSE_TIME_TO_FIRST_CHUNK = "gen_ai.response.time_to_first_chunk";

    // --- 用量 -------------------------------------------------------------------------
    //
    // 注册表还定义了按模态（modality）细分的变体，它们是这些总量的子集，命名规则是
    // gen_ai.usage.<modality>.<input|output>_tokens 与
    // gen_ai.usage.<modality>.cache_read.input_tokens，其中 modality ∈ text | image | audio。

    public static final String USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens";
    public static final String USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";

    /** 由服务商管理的缓存提供的输入 token。**已经计入输入总量**。 */
    public static final String USAGE_CACHE_READ_INPUT_TOKENS = "gen_ai.usage.cache_read.input_tokens";

    /** 写入服务商管理的缓存的输入 token。**已经计入输入总量**。 */
    public static final String USAGE_CACHE_WRITE_INPUT_TOKENS = "gen_ai.usage.cache_write.input_tokens";

    /** 花在推理上的输出 token。**已经计入输出总量**。 */
    public static final String USAGE_REASONING_OUTPUT_TOKENS = "gen_ai.usage.reasoning.output_tokens";

    /**
     * 正在被计数的模态：{@code text}、{@code image}、{@code audio}、{@code unknown}。
     *
     * <p>这就是取代 {@code gen_ai.token.type} 的东西。旧键描述"这个计数属于哪个桶"；
     * 注册表现在改用**属性名本身**表达这件事（上面那些按模态细分的键），
     * 而用这一个表达"模态"这个维度。
     */
    public static final String TOKEN_MODALITY = "gen_ai.token.modality";

    // --- 工具 -------------------------------------------------------------------------

    public static final String TOOL_NAME = "gen_ai.tool.name";
    public static final String TOOL_CALL_ID = "gen_ai.tool.call.id";
    public static final String TOOL_TYPE = "gen_ai.tool.type";
    public static final String TOOL_DESCRIPTION = "gen_ai.tool.description";

    /** 传给工具调用的参数。规范标注它**可能包含敏感信息**。 */
    public static final String TOOL_CALL_ARGUMENTS = "gen_ai.tool.call.arguments";

    /** 工具返回了什么。规范同样标注**可能包含敏感信息**。 */
    public static final String TOOL_CALL_RESULT = "gen_ai.tool.call.result";

    /** 提供给模型的工具定义。同样被标注为可能敏感。 */
    public static final String TOOL_DEFINITIONS = "gen_ai.tool.definitions";

    // --- agent ------------------------------------------------------------------------

    /**
     * 托管的 agent 资源的稳定标识（由服务商分配）——**不是内存里的实例 id**。
     * 注册表明确不建议用后者，因为它是临时的。
     */
    public static final String AGENT_ID = "gen_ai.agent.id";

    public static final String AGENT_NAME = "gen_ai.agent.name";
    public static final String AGENT_DESCRIPTION = "gen_ai.agent.description";
    public static final String AGENT_VERSION = "gen_ai.agent.version";

    /** 本进程里的顶层 agent；注册表把它作为一个实体（entity）来定义。 */
    public static final String MAIN_AGENT_ID = "gen_ai.main_agent.id";
    public static final String MAIN_AGENT_NAME = "gen_ai.main_agent.name";
    public static final String MAIN_AGENT_DESCRIPTION = "gen_ai.main_agent.description";

    // --- 会话与数据 -------------------------------------------------------------------

    public static final String CONVERSATION_ID = "gen_ai.conversation.id";

    /** **只在确知做过上下文压缩时设为 true；永远不要设为 false。** */
    public static final String CONVERSATION_COMPACTED = "gen_ai.conversation.compacted";

    public static final String DATA_SOURCE_ID = "gen_ai.data_source.id";

    // --- 检索与嵌入 -------------------------------------------------------------------

    public static final String RETRIEVAL_QUERY_TEXT = "gen_ai.retrieval.query.text";
    public static final String RETRIEVAL_DOCUMENTS = "gen_ai.retrieval.documents";
    public static final String RETRIEVAL_TOP_K = "gen_ai.retrieval.top_k";
    public static final String EMBEDDINGS_DIMENSION_COUNT = "gen_ai.embeddings.dimension.count";

    // --- 输出类型 ---------------------------------------------------------------------

    /** 请求要求的输出媒体类型：{@code text}、{@code json}、{@code image}、{@code speech}。 */
    public static final String OUTPUT_TYPE = "gen_ai.output.type";

    // --- workflow / prompt / skill 词表 ------------------------------------------------
    // 还没有发出去过。列在这里，是为了让这套词汇待在一个地方，
    // 而不是在第一个需要它的插桩点上被重新发明一遍。

    /** 由应用提供的、低基数的 workflow 名。**不能是类型名。** */
    public static final String WORKFLOW_NAME = "gen_ai.workflow.name";

    public static final String PROMPT_NAME = "gen_ai.prompt.name";
    public static final String PROMPT_VERSION = "gen_ai.prompt.version";

    public static final String SKILL_NAME = "gen_ai.skill.name";
    public static final String SKILL_DESCRIPTION = "gen_ai.skill.description";
    public static final String SKILL_SOURCE_URI = "gen_ai.skill.source.uri";

    // --- 通用约定：GenAI span 也总要带的几个键 -----------------------------------------
    //
    // 它们不在 GenAI 注册表里：它们是"通用语义约定"，而 GenAI 的 span 同样要带，
    // 所以放在这里，而不是单独开一个类。

    /** 低基数的错误分类。**只出现在失败的 span 上。** */
    public static final String ERROR_TYPE = "error.type";

    public static final String SERVER_ADDRESS = "server.address";
    public static final String SERVER_PORT = "server.port";

    private GenAiAttributes() {
    }
}
