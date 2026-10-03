# ARCHITECTURE

## 数据流

```
                        宿主应用 JVM
  ┌──────────────────────────────────────────────────────────────┐
  │  Spring Boot / 任意 Java 应用                                 │
  │     └─ LangChain4j / Spring AI                                │
  │            │                                                  │
  │            │ ① 类加载时被拦截（字节码插桩，业务代码零改动）      │
  │            ▼                                                  │
  │  ┌────────────────────────┐                                   │
  │  │  ClassFileTransformer  │  ← tomograph-core                 │
  │  │  InstrumentationEngine │                                   │
  │  └───────────┬────────────┘                                   │
  │              │ ② 按类名分发给匹配的插桩模块                     │
  │              ▼                                                │
  │  ┌────────────────────────┐                                   │
  │  │  TomographModule (SPI) │  ← tomograph-instrumentation-*    │
  │  │  langchain4j / springai│                                   │
  │  └───────────┬────────────┘                                   │
  │              │ ③ 采集到语义化的 span（模型/工具/检索/token/成本）│
  │              ▼                                                │
  │  ┌────────────────────────┐                                   │
  │  │  SpanSink (SPI)        │                                   │
  │  └───────┬────────┬───────┘                                   │
  └──────────┼────────┼──────────────────────────────────────────┘
             │        │
     ④ OTLP  │        │ ④ 本地录制文件（录制/重放内核）
             ▼        ▼
   Jaeger / Grafana   自包含单文件 HTML 报告
   Tempo / Langfuse   （面试/离线分享用）
```

## 模块划分

**当前骨架已存在的模块**（只建有真实内容的模块，不建空壳）：

| 模块 | 职责 |
|---|---|
| `tomograph-api` | 纯 SPI 与数据模型，**零第三方依赖**。宿主和插桩模块都只依赖它。 |
| `tomograph-core` | 插桩引擎：类名分发、上下文采集、agent 启动引导。 |
| `tomograph-javaagent` | 打包成 `-javaagent` 可用的 fat jar（shade + manifest）。 |
| `tomograph-semconv` | OpenTelemetry GenAI 语义约定的**唯一**落点：属性键、span 命名规则、`tomograph.*` 自有键。零编译期依赖，只为了让规范升级是"改一个文件"而不是"全项目 grep"。 |
| `tomograph-exporter-otlp` | 零依赖的 OTLP/HTTP JSON 导出：只用 JDK 的 `HttpClient`，不引 OpenTelemetry SDK、protobuf、Jackson 或任何第三方 HTTP 客户端。见 [ADR 0003](docs/adr/0003-zero-dependency-otlp.md)。 |
| `tomograph-examples/tomograph-example-fakeagent` | 一个刻意写得很笨的假 Agent，用来验证管线和写集成测试。 |
| `tomograph-report-html` | 离线报告：把一份 OTLP/JSON 读回成 span 树，渲染成**一个自包含 HTML 文件**（内联 CSS、无脚本、无外链、无字体）。**这个模块可以用 Jackson**——ADR 0003 的零依赖规矩针对的是"注入别人 JVM 的 agent"，不针对在开发者机器上读文件的离线工具。见 [ADR 0005](docs/adr/0005-offline-report-input.md)。 |

**规划中的模块**（在对应里程碑有真实内容时创建）：

| 模块 | 创建时机 | 职责 |
|---|---|---|
| `tomograph-instrumentation-langchain4j` | v0.1 | LangChain4j 切点（ASM 字节码改写） |
| `tomograph-instrumentation-springai` | v0.2 | Spring AI 切点 |
| `tomograph-replay` | v1.0 | 录制 / 确定性重放内核 |

## SPI 设计

插桩模块通过 `java.util.ServiceLoader` 发现，因此第三方可以**不改 Tomograph 源码**就新增对某个框架的支持——这是 3 年后生态能长大的前提。

```
tomograph-api/src/main/java/io/github/godhuino1/tomograph/api/TomographModule.java
META-INF/services/io.github.godhuino1.tomograph.api.TomographModule   ← 模块自己声明
```

`InstrumentationEngine` 在构造时把 `targetClassNames()` 反转成 `类名 → 模块列表` 的索引，避免每次类加载都线性扫描所有模块——**这是热路径，不能有 O(n) 查找**。

## 三个真正的技术难题

数据"能采到"不难。难的是下面三件事，它们也是这个项目全部的技术含量所在。

### 难题 1：切点选在哪

切接口还是切实现？切父类还是切具体方法？

- 切得太"抽象"（比如只切 `ChatModel` 接口）：采不到框架内部的关键状态（实际用了哪个模型、重试了几次）
- 切得太"具体"（切某个实现类的私有方法）：框架一升级就崩

**应对**：切点表是显式的、可配置的、并且**每个受支持版本都有集成测试钉住**。兼容矩阵不是文档，是 CI。

### 难题 2：上下文怎么传播

Agent 是多轮的、可能异步、可能流式、可能在虚拟线程里并发。怎么知道"这次 LLM 调用属于哪一次 Agent 运行、哪一个工具调用"？

**`ThreadLocal` 在这里会失效**，而且失效得很安静：

- 虚拟线程被 unmount/remount 后，线程局部状态可能与调用栈不再对应
- `@Async` / `CompletableFuture` 把工作交到另一个线程，语义上下文丢失
- Reactor 的操作符边界会换线程
- 线程池把同一个线程复用给两次完全不同的 Agent 运行

见 `tomograph-core` 里的 `SpanCollector`——那个类目前是 ThreadLocal 实现，但文件头明确记录了它的已知失效场景。**正确解法是在插桩点显式捕获上下文快照并作为参数/属性传递，而不是依赖环境线程状态。**

这个"显式载体"现在有了具体形态：[`TraceContext`](tomograph-semconv/src/main/java/io/github/godhuino1/tomograph/semconv/TraceContext.java)——一个不可变值对象，同时承载 W3C 的 trace id、span id 与采样标志，并能渲染成 `traceparent` 头用于跨进程传播。它刻意**不是**环境状态：插桩代码必须把它当作参数往下传。`SpanCollector` 暂时保留，仅为让最早的骨架能跑通，终将被移除。

**必须在 v1.0 之前解决，不是可以糊过去的细节。**

### 难题 3：怎么做到"确定性"

重放要一字不差，就要接管所有非确定性来源：时间、随机数、并发完成顺序、网络、外部工具副作用。

**应对**：录制模式下把所有外部交互（模型请求/响应、工具入参/出参）写成有序的 cassette；重放模式下在**字节码层**拦截真实调用并返回录制结果，让宿主应用完全感知不到自己没联网。

## 本机与 CI 环境的硬约束（实测记录）

| 事实 | 证据 | 影响 |
|---|---|---|
| `-javaagent`（premain）在 JDK 26 可用 | 探针实测：premain OK，transform 被调用，retransform 成功 | 主路径无障碍 |
| JDK 26 **未**默许收紧动态加载 agent | `java -XX:+PrintFlagsFinal -version` → `EnableDynamicAgentLoading = true {default}` | 策略层面无风险 |
| **DSH 沙箱内无法使用 attach 机制** | `jcmd -l` 列不出 JVM、`jcmd <pid> VM.version` 报 `IOException: 拒绝访问`，堆栈落在 `VirtualMachineImpl.openProcess`；目标 JVM stderr 无任何警告 | `jcmd`/`jstack`/`jmap`/动态 attach 在开发沙箱内不可用；attach 的真实可用性需在沙箱外或 Linux CI 验证 |
| 宿主框架要求 Java 17+ | LangChain4j / Spring AI 的最低 JDK 版本 | 兼容矩阵定为 **17 / 21 / 25**，不做 8/11 |
| **沙箱内 Maven 无法访问中央仓库** | `Invoke-WebRequest https://repo1.maven.org/...` → `基础连接已经关闭`；Maven 解析未缓存插件时失败 | 本地构建**必须加 `-o`（离线）**，且所有插件/依赖版本必须已存在于本地缓存 |
| **Maven 本地仓库在工作区外且只读** | 实际仓库 = `D:\java+python\maven1`（由 Maven 安装目录 `conf/settings.xml` 指定）；写入测试报「访问被拒绝」 | 不能新增任何未缓存的依赖；需要新依赖时必须在能联网的环境（如 CI）拉取后再用 |
| 已缓存的 shade 版本只有 3.2.4 / 3.4.1 / 3.5.0 / **3.6.0** / 3.6.1 | 逐目录检查 jar 是否存在；**3.6.2 只有空目录没有 jar** | `plugin.shade.version` 钉 **3.6.0**。改版本前先确认 jar 真的存在，别只看目录名 |
| Windows 上 Java 输出中文会乱码 | 不传编码时输出 `Tomograph �ܿ�����ε�����`；传 `-Dstdout.encoding=UTF-8` 后正常 | 本机跑示例加 `-Dstdout.encoding=UTF-8`；Linux CI 默认 UTF-8 无此问题 |
| **pwsh 在本沙箱完全没有对外网络** | 所有 `Invoke-RestMethod` / `Invoke-WebRequest` 一律抛异常，包括对 `api.github.com` | 任何"存在性 / 可用性"判断**不得用 `catch` 兜底成正面结论**。曾经因此把"网络不通"当成"github 用户名可用"，直接导致一次错误的技术决策。需要联网核实一律走 `web_fetch`，且必须能区分 404 与连接失败 |
| **离线可用库清单是项目的一条硬边界** | `dev/langchain4j` 与 `org/springframework/ai` 完全不存在；`io/opentelemetry/*` 目录存在但 0 个 jar | 依赖必须事先确认 jar 真的在本地（看版本子目录，别只看 artifact 目录）；未缓存的依赖只能在联网环境预拉 |
| **ASM 9.7 拒绝 Java 25/26 的 class 文件（major 69/70）** | 实测：同一源文件以 `--release 17/21/25/26` 编译后用不同 ASM 读取。9.7 对 69/70 抛 `IllegalArgumentException: Unsupported class file major version`（来自 `ClassReader` 构造函数，即宿主类加载路径上）；9.9.1 四者全部可读 | 插桩引擎的 ASM 家族必须统一钉 **9.9.1**（需一次预拉，见 [docs/prefetch-list.md](docs/prefetch-list.md)）；可插桩的 class 版本边界 = **61–70**。详见 [ADR 0004](docs/adr/0004-instrumentation-strategy.md) |
| 本地仓库同类构件版本不齐 | `asm` 到 9.9.1，但 `asm-commons` 只到 9.7、`asm-util` 只到 8.0 | 任何"多构件配套使用"的库，都要逐个构件确认最高可用版本，不能只看其中一个 |
| **Windows PowerShell 5.1 会把无 BOM 的 UTF-8 文件按 ANSI 读** | `learning/check-links.ps1` 的第一版写了几行中文提示，运行时中文变成乱码（`涓 Heng`），其中一处**直接让解析器报 `UnexpectedToken`**，脚本完全跑不起来 | 为这个环境写的 `.ps1` **一律只用 ASCII**（三个验收脚本和链接检查器都遵守这条）。需要中文输出时让 **Java** 程序去打印（配 `-Dstdout.encoding=UTF-8`），不要在 PowerShell 字符串里写中文 |

### 本地开发命令（本机沙箱约束下）

```bash
mvn -B -ntp -o clean package     # -o 是必需的，不是可选优化
```

CI（GitHub Actions）上**不要**加 `-o`，那里需要联网拉取依赖。


## 工程纪律

1. **插桩代码绝不把异常抛给宿主。** `InstrumentationEngine` 逐模块 `catch (Throwable)`，出错只记日志、返回原字节码。
2. **不改宿主类的签名。** `retransformClasses` 不允许增删方法/字段或改签名，这是 JVM 的硬限制，不是风格问题。
3. **agent 自己的类不能污染业务类加载器。** 当前骨架依赖 `-javaagent` 的 system class loader 追加机制；真正的隔离方案在 v0.2 落地。
