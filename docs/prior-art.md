# 同类项目与定位（prior art）

> **数据快照：2026-10-03**。star 数是快照，会变。复核方法见文末——**这份文档要能重新核对，而不是靠记忆**。

## 一句话结论

**可观测性半边很拥挤，但没有人在 JVM 上做「零侵入 + 框架层 + agent 语义」的组合；重放半边几乎无人，但已有商业方向与我们重叠的早期项目。**

## 对照表

| 项目 | Stars | 侵入方式 | 覆盖哪一半 | 对我们的意义 |
|---|---|---|---|---|
| [openinference](https://github.com/Arize-ai/openinference)（Arize） | 1.2k | Java 侧是**库**：`LangChain4jInstrumentor.instrument()` 调用；或 ByteBuddy agent 插桩**被注解的方法**（`@Agent`/`@LLM`/`@Tool`/`@Chain`） | 可观测 | **最直接对手**，但都要改代码。用自家 OpenInference 语义约定，不是 OTel GenAI |
| [openllmetry](https://github.com/traceloop/openllmetry)（Traceloop） | 7.5k | Python / JS 自动插桩 | 可观测 | 生态最大，**但 JVM 不是它的主场** |
| [langfuse-java](https://github.com/langfuse/langfuse-java) | 74 | Java SDK，在代码里主动上报 | 可观测 | 需要改代码；是"后端 + SDK"，不是 agent |
| LangChain4j 自带 `ChatModelListener` | （框架内置） | 注册 listener | 可观测 | **必须知道的既有能力**：它让"采 LLM 调用"这件事有了一条不用 agent 的路。我们的差异不在"能采到"，而在**不用改代码 + 覆盖 agent 语义** |
| [opentelemetry-java-instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation)（官方） | 大 | **零侵入 javaagent** | 可观测 | ⚠️ **关键**：它的 GenAI 覆盖只有 `AWS SDK` 与 `OpenAI Java SDK` 1.1+，**没有 LangChain4j / Spring AI** |
| [jamjet-runtime-java](https://github.com/jamjet-labs/jamjet-runtime-java) | 0 | 注解 `@DurableAgent` + `@Checkpoint` + 把方法体包进 `replayOrExecute(...)`；可观测性走 `listeners(...)` | **重放** + 可观测 | **方向与我们重叠最严重的项目**。README 写 "Zero code changes"，但示例本身在改代码——它指的是零**基础设施**改动 |
| [walaru](https://github.com/P4suta/walaru) | 0 | Gradle 插件 + JVM agent + 零依赖 API | **重放** | 做的是**通用 Java/Kotlin 测试**的确定性重放，不是 LLM agent。但它的重放保证与安全契约是最值得读的先例 |
| [tracelm-java-agent](https://github.com/unnivm/tracelm-java-agent) | 0 | （自称 "non-intrusive LLM observation tool in Java"） | 可观测 | 名字几乎和我们一样，但**没人用**。说明这个描述本身不构成壁垒 |

## 三条硬事实（决定我们的差异化站不站得住）

### 1. 官方 OTel agent 覆盖"厂商 SDK"，不覆盖"框架层"

它的受支持列表里提到 GenAI 的只有两行：`AWS SDK` 与 `OpenAI Java SDK`。**LangChain4j、Spring AI 都不在其中**（LangChain4j 仓库里还有一个 open 的 issue 在要 OTel GenAI 接入文档）。

原因不难理解：OTel 的插桩策略是**打厂商 SDK**。而 LangChain4j 有自己的 HTTP 客户端，不走 `openai-java`，所以**官方 agent 看不见 LangChain4j 的模型调用**。

> 这意味着：如果你的应用"直接用 OpenAI Java SDK"，官方 agent 已经免费给你 GenAI span；**一旦你用了 LangChain4j 或 Spring AI，就没有任何零侵入方案**。

### 2. JVM 上所有 LLM 可观测方案都要求改代码

`instrument()` 调用、`@Agent` 注解、`listeners(...)` 注册、SDK 主动上报——**没有一个只要 `-javaagent` 参数**。
**"零侵入"在 JVM 上是真差异，不是营销话术。**

### 3. 但"能采到"不是差异，"采到什么"才是

官方 agent 打在 SDK 层，能给你 token 数、模型名、HTTP 状态。**它给不了 agent 语义**：调用了几轮、每轮调了哪个工具、工具参数与副作用、检索命中了什么、agent 的规划阶段。

**我们的价值主张应该精确表述为**：*在框架层零侵入地还原 agent 的语义结构*，而不是"又一个能导出 OTLP 的工具"。

## 风险（必须写在明面上）

1. **OTel 随时可能给 LangChain4j 加一个 instrumentation 模块。** 他们的模式很成熟：一个模块、一个受支持版本矩阵。这是最大的一条风险，且不受我们控制。
   → 对策：把价值建立在**语义层的深度**（切点覆盖 + 重放），而不是"我们支持 LangChain4j"这一条事实。
2. **openinference 已经在 Java 上有 ByteBuddy 注解方案**，背后是 Arize（有商业动力）。
   → 它要改代码这点短期内不会变（注解是它的设计核心），但值得持续观察。
3. **jamjet 的商业方向覆盖"重放 + LangChain4j"**。虽然 0 star 且要求改代码，但它是公司而不是个人项目。
   → 对策：我们的重放要做到**零侵入**，这是它结构上做不到的（它依赖注解来知道"哪里是可重放的一步"）。
4. **不要去做另一个 Langfuse / Phoenix。** 那是后端与 UI 的战场，有充足资金和先发者。我们的边界见 `SCOPE.md`。

## 每个项目里具体值得学的东西

| 来源 | 具体学什么 |
|---|---|
| openinference-java | 语义约定的代码组织方式；隐私控制的 API 设计（`hideInputMessages` / `@ExcludeFromSpan`）——**这类"默认不采敏感内容"的开关我们会需要** |
| walaru `docs/replay.md` | **重放"何时算 verified"的定义**（只有匹配的全新 JVM 才返回 `verified: true`）；"partial recording" 的概念（网络/子进程/JNI/文件 IO/不确定的调度都会让一次录制不完整，**绝不把不完整呈现为精确成功**）。这正是 v1.0 要设计的核心 |
| walaru 安全契约 | 捕获时不调用用户 getter/`toString()`；对深度、字符串、集合、响应做上界；疑似密钥一律脱敏 |
| jamjet | 注解式插桩的** API 手感**（两个注解就上手）；以及"崩溃恢复其实是重放的副产品"这个论证角度 |
| openllmetry | 一个 7.5k star 项目的 instrumentor 分层与版本矩阵组织方式 |
| 官方 OTel agent | 它的 instrumentation 模块结构与 `InstrumentationModule` SPI——**是"如何被广泛采用"的教科书**，也是我们切点声明方式的参照 |

## 怎么重新核对这份文档

三个非 API 的取数方式（本项目已验证可用）：

```powershell
# 1. star 数：shields.io 的 JSON 端点，不消耗 GitHub API 配额
#    https://img.shields.io/github/stars/<owner>/<repo>.json   ->  {"value":"1.2k"}

# 2. 活跃度与最新提交 SHA：Atom feed（非 API）
#    https://github.com/<owner>/<repo>/commits/main.atom

# 3. 仓库文件内容：jsDelivr，pin 到 SHA 最可靠（分支名通常也能用）
#    https://cdn.jsdelivr.net/gh/<owner>/<repo>@<sha>/README.md
```

**核对官方 OTel 覆盖范围的权威文件**：`open-telemetry/opentelemetry-java-instrumentation` 的 `docs/supported-libraries.md`——在全文里搜 `GenAI`，看有哪些库被覆盖。**这是判断我们生态位是否还存在的唯一权威依据，应当定期重跑。**
