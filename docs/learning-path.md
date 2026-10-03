# 学习与推进计划

这份计划把「你自己要写的代码」和「项目要长出来的东西」绑在同一条时间线上。每个阶段都写明**交付物**和**完成标志**——没有可检验的标志，阶段就不算结束。

适配的时间线：研一 2026 秋入学 → 研二下（2028 春）投暑期实习。

## 练习的用法：空白版 + 答案版

每一个练习都有两份：

| | 位置 | 用途 |
|---|---|---|
| **空白版** | `learning/dayNN-*/` | 你写的地方。结构、方法签名、注释都在，方法体是 TODO |
| **答案版** | `learning/answers/dayNN-*/` | 卡住时对照。**先自己想 30 分钟再看** |

建议的顺序，别跳：

1. 读空白版的注释和 TODO——它们本身就在讲该做什么
2. 自己写。允许查 JVMS / ASM 文档 / `javap` 输出
3. 卡住超过 30 分钟 → 看空白版里的提示梯子（`learning/README.md`）
4. 还卡住 → 看答案版，但**看完要合上，自己重写一遍**
5. **用验收脚本证明它过了**，而不是"我看着差不多"

> 直接抄答案版能让你今天通过验收，但会让你在 2028 年面试时答不出"Long 常量为什么占两个槽位"。
> 面试官问的从来不是"你写过吗"，而是"为什么是这样"。

---

## 阶段 0：地基（已完成，2026.10）

**已经能跑的东西**：7 个模块的 Maven 仓库、CI 六格矩阵（JDK 17/21/25 × Linux/Windows）全绿、
OTLP/HTTP 导出器（零第三方依赖，40 个测试）、W3C Trace Context、core 引擎测试、
动态 attach 集成测试（由 CI 强制必须真执行）、语义约定与 OTLP 协议两处规范核对完毕。

**完成标志**：`mvn -o clean verify` 全绿；GitHub 上 CI 徽章为 passing。

---

## 阶段 1：三关（第 1–3 周）— 目标是把字节码从"魔数"变成你能读写的东西

> **阶段 1 的成功标准（唯一一条）**：你能从零写出一个 agent，挂上去打印任意方法的耗时和入参。

### 必读材料（按关划分，只读够用的那部分）

| 关 | 读什么 | 读到什么程度 |
|---|---|---|
| 1 | **JVMS 第 4 章**（Class File Format） | 4.1 的结构表 + 4.4 的常量池表。**建议把常量池所有 tag 和它们的结构抄在一张纸上**——这张纸你这一周会一直用 |
| 2 | `java.lang.instrument` 的 javadoc | 吃透四条硬限制：`premain` 与 `agentmain` 的差别及各自约束；**`retransformClasses` 不能增删方法/字段、不能改签名**；agent jar 的类是怎么被 JVM 找到的（追加到 system class loader 搜索路径）；`transform` 返回 `null` / 原数组 / 新数组各意味着什么 |
| 3 | **ASM User Guide 前 3 章** | 第 2 章（`ClassReader` / `ClassWriter` / `ClassVisitor`）是全部基础；第 3 章讲方法级改写 |
| 并行 | LangChain4j 源码 | 见下面的接口清单 |

### 调试工具家当（插桩排查全靠这四个）

| 工具 | 用途 |
|---|---|
| `javap -c -p Foo.class` | 人肉读字节码。**遇到任何插桩问题，第一反应就是看它**——它是权威，你的猜想不是 |
| `ASMifier` | 把现成的 class 打印成生成它的 ASM 代码。**抄着学最快，但每一行都要理解** |
| `Textifier` | 把类打印成可读的指令列表（比 `javap` 更贴近 ASM 的视角） |
| `CheckClassAdapter` | 校验你生成的字节码是否合法。⚠️ 需要 `asm-util` 9.9.1，见 `docs/prefetch-list.md` |

> **Windows 上的一个坑**：给 `javap` 传 `-J-Dstdout.encoding=UTF-8` 时必须用数组传参（`@('...','...')` 展开），否则 PowerShell 会把参数拆坏，而失败信息看起来跟编码毫无关系。这个坑在本项目里踩过 **5 次**。

### 关 1（第 1–2 周）：class 文件解析器

- **空白版**：`learning/day01-classdump/ClassDump.java`
- **答案版**：`learning/answers/day01-classdump/ClassDump.java`
- **要做**：读 class 文件头（magic / minor / major / constant_pool_count / this_class），再把整个常量池 dump 出来
- **完成标志**：`.\check-day01.ps1` 对**全部 7 个** class 文件输出 PASS（素材里含枚举、接口、内部类、匿名类）
- **必须能答**：`constant_pool_count` 为什么比实际项数大 1？哪类常量占两个槽位、为什么？

### 关 2（第 3–4 周）：把 javaagent 管线打通（**本关不改写字节码**）

- **空白版**：`learning/day03-agent/Agent.java`（素材 `Target.java` / `manifest.txt` 共用，不要改）
- **答案版**：`learning/answers/day03-agent/Agent.java`
- **要做**：`premain` 与 `agentmain` 两个入口、注册一个**只观察**的 `ClassFileTransformer`、对**已经加载过**的类强制 `retransformClasses`、以及把一切包进 `catch (Throwable)`
- **完成标志**：`.\check-day02.ps1` 输出 `ALL CHECKS PASS`，具体检查四件事——
  挂 agent 与不挂 agent **应用输出逐字节相同**；agent 报告看到了 `Target` 加载；agent 打出了 `[RETRANSFORM]`；没有任何异常
- **必须能答**：agent 的类由哪个 ClassLoader 加载？为什么这会成为问题？`transform` 返回 `null` 和返回原数组有什么区别？为什么 retransform 不能在 `premain` 里立刻做？

> **为什么本关不要求"打印耗时与入参"**：那需要真的往方法体里插指令，也就是字节码改写——那是关 3。
> 把顺序拆开的好处是：关 2 结束时你已经有一条**能跑通的完整管线**，关 3 只需专注在字节码本身。
> 一开始就让两者混在一起，是最容易两头都卡住的做法。

### 关 3（第 5–7 周）：手写 ASM 方法插桩

- **空白版 / 答案版**：`learning/day05-asm/` 与 `learning/answers/day05-asm/`
- **要做**：用 `ClassReader` + `ClassWriter` + 自写 `MethodVisitor` 往方法里插指令，实现**耗时与入参**上报——也就是关 2 当初那句验收的真正归属
- **完成标志**：`.\check-day03.ps1` 输出 `ALL CHECKS PASS`——五个方法各自的耗时与参数都正确，且宿主应用输出逐字节不变；并能用 `javap -c -p` 看到你插入的指令
- **可选进阶**：`CheckClassAdapter` 校验你生成的字节码。它属于 `asm-util`，而本地仓库目前只有 5.0.3 / 8.0——**补上 9.9.1 之后再执行**（见 `docs/prefetch-list.md`）。把这个写成必过项是错的：当前环境根本做不到
- **必须能答**：为什么 `retransformClasses` 不能给已加载的类加字段？`COMPUTE_FRAMES` 干了什么？为什么本关**不用** `AdviceAdapter`？

### 并行（第 1–3 周，约 4 小时）：LLM 应用速成

这块不占主线，但**必须在阶段 1 内补上**，否则阶段 3 你不知道该在哪里切。

**概念**（能讲清就行，不用深入）：token 是什么、为什么它是计费单位；流式（streaming）与非流式响应的区别；function / tool calling 的请求-响应结构；embedding 与向量检索（RAG）的基本流程；ReAct 循环（思考 → 调工具 → 观察 → 再思考）。

**动手**：直接调一次 OpenAI 兼容的 chat completions API（`curl` 或 `HttpClient` 都行，**刻意不用框架**），把请求体和响应体的 JSON 原样打印出来看一遍。

**读框架**：在 IDE 里打开 LangChain4j 源码，找到这几个接口并画出继承关系：

| 接口 | 为什么关心它 |
|---|---|
| `ChatModel` / `ChatLanguageModel` | 一次模型调用的必经之路，最可能的第一个切点 |
| `ToolSpecification` / 工具执行入口 | 工具调用的"副作用"就发生在这里 |
| `EmbeddingStore` / `ContentRetriever` | 检索与嵌入，对应规范里的 `embeddings` / `retrieval` 两类切点 |
| `AiServices` | ⚠️ **它是动态代理生成的实现**——这意味着你**没法继承它来插桩**，只能从字节码层面切。这一个认识直接决定了项目的技术路线 |

**完成标志**：能回答"要在 LangChain4j 里切一刀采集模型调用，我切哪个方法，为什么"。

**顺手想一想（通向阶段 5）**：为什么 `ThreadLocal` 在虚拟线程和 `@Async` 场景下会丢上下文？现在答不上不要紧——那是 `ARCHITECTURE.md` 的难题 2，也是 v1.0 录制重放要解决的核心。

---

## 阶段 2：v0.1 内核（第 4–8 周）— 引擎先在自己可控的目标上跑通

**前置**：联网终端执行 `docs/prefetch-list.md` 里的预拉（ASM 家族补齐到 9.9.1、LangChain4j）。

- **交付物**：`tomograph-instrumentation-fakeagent`（切点表 + ASM 改写）、`InstrumentationEngine` 接入真实改写、JMH 基准骨架
- **要做的关键技术决策**：切接口还是切实现；`ClassLoader` 隔离方案；插桩开销如何测量
- **完成标志**：挂上 agent 后，假 Agent 的方法调用被**真实改写**并产出 span；改写后的字节码通过 `CheckClassAdapter` 校验；异常路径（改写失败）不破坏宿主
- **风险**：`AdviceAdapter` 在构造器与 try-finally 上容易翻车——这是本题最容易卡住的地方

---

## 阶段 3：v0.1 完成（第 9–16 周）— 真实框架 + 兼容矩阵

- **交付物**：`tomograph-instrumentation-langchain4j`、JDK 17/21/25 全矩阵集成测试、JMH 开销报告、`v0.1.0` release
- **完成标志**（这些数字就是简历上的数字来源）：
  - 宿主应用零改动，只加 `-javaagent`
  - 采集 ≥5 类语义切点：Agent 轮次 / LLM 调用 / 工具调用（含副作用）/ 检索 / 嵌入
  - token 与成本与 provider 返回值逐条对齐
  - **P99 插桩开销 < 2%**（JMH 可复现）
  - CI 全绿，且 LangChain4j 每个受支持版本都有集成测试钉住
- **风险**：LangChain4j 升级导致切点失效——所以兼容矩阵必须进 CI，而不是写在文档里

---

## 阶段 4：开源首发（2027 暑假，唯一能整块投入的窗口）

- **交付物**：`tomograph-instrumentation-springai`、英文 README、单文件离线 HTML 报告、发布文章、`v0.3` release
- **完成标志**：GitHub 公开可访问、有人 star 或提 issue、能 5 分钟跑出一个可看的调用树
- **为什么必须卡在这里**：到 2028 年 3 月投实习时，项目就有 8 个月公开历史——「持续维护」这个信号比代码本身更值钱

---

## 阶段 5：v1.0 录制与重放（研二上，2027.09–12）

- **交付物**：cassette 格式、录制模式、确定性重放内核、轨迹 diff、故障注入
- **完成标志**：把一次真实运行录下来，离线重放得到**逐字节相同**的 span 序列；改一版 prompt 后能 diff 出差异
- **技术难点**：接管所有非确定性来源（时间、随机数、并发完成顺序、网络、工具副作用）；以及 ARCHITECTURE 里的「难题 2」——`ThreadLocal` 上下文在虚拟线程/异步下失效，必须换成显式携带的 `TraceContext`

---

## 阶段 6：投实习（研二下，2028.01–06）

- **交付物**：简历 2 条量化 bullet、30 分钟深挖问答的准备稿、项目 README 的"为什么"叙事
- **完成标志**：能对着任何一条 bullet 往下讲三层为什么，且每一层都有证据（测试、基准、CI 记录、ADR）
- **准备方式**：把 `docs/adr/` 里每一份 ADR 当成一道面试题来复习——它们记录的正是"为什么不那样做"

---

## 贯穿全程的三条纪律

1. **写下来的才算知道。** 每个"暂时不确定"都要变成 ROADMAP 里的一行债，或一份 ADR。这个项目已经证明过：不写下来的假设会在最关键的时候错。
2. **验证你的验证手段。** 一个只会说"通过"的检查脚本比没有更危险。项目里已经因此抓到三次错（`catch` 兜底成"可用"、少看一层目录、按扩展名过滤漏文件）。
3. **构建绿灯不等于行为完好。** 每个功能都要有一个"真的跑一遍"的验证（冒烟测试、端到端跑一次、CI 断言），而不只是编译通过。
