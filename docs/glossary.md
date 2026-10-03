# 术语表与资料清单

## 怎么用这份表

**你不需要现在懂全部。** 按「在哪一关会遇到」分组，只读当前那一组，其余的等用到再看。

每条给三样：**一句白话** + **在哪用到** + **想深入看什么**。

> **看不懂时唯一的正确做法：问。**
> 「这个词什么意思」永远不丢人，**猜着往下走才丢人**——本项目已经因为"没核实的假设"栽过好几次（见 ROADMAP 的技术债表）。
> 你问我，我换一种说法重讲；如果换了说法还不懂，那就是我的问题，不是你的。

---

## A 组：class 文件与字节码（关 1 会用到）

| 词 | 一句白话 | 深入看 |
|---|---|---|
| **字节码** bytecode | Java 源码编译后变成的另一种"程序"，是给 JVM 看的指令序列 | 《深入理解Java虚拟机》第 6 章 |
| **class 文件** | 一个 `.class` = 一个类的完整描述，二进制格式 | JVMS 第 4 章 `jvms-4.html` |
| **魔数** magic | 文件头 4 字节 `0xCAFEBABE`，JVM 用它确认"这确实是 class 文件" | JVMS 4.1 |
| **常量池** constant pool | class 文件的**字典**：所有字符串、数字、类和成员的引用都在这；文件其他部分用**下标**引用它 | JVMS 4.4 |
| **两个槽位的常量** | `Long`/`Double` 在常量池里**占两个位置**，第二个不可用。忘记跳一格 → 后面全部错位（关 1 的经典翻车点） | JVMS 4.4.5 |
| **major / minor version** | class 文件的格式版本。**major 61 = Java 17**，不是"Java 版本号"本身 | JVMS 4.1 |
| **内部名** internal name | class 文件里类名用**斜杠**（`com/foo/Bar`），不是点 | JVMS 4.2.1 |
| **描述符** descriptor | 用类型代码描述签名，如 `(ILjava/lang/String;)J` = 「收 int 和 String，返回 long」 | JVMS 4.3 |
| **access_flags** | 修饰符的位掩码（public/static/final…）。**class 文件里它排在 `this_class` 前面**，漏读会让解析整体错位 | JVMS 4.1 |
| **modified UTF-8** | class 文件里字符串的编码，和标准 UTF-8 有两处差别（`\u0000` 占两字节；增补字符用代理对） | JVMS 4.4.7 |
| **`javap`** | JDK 自带的反汇编器。**你最重要的工具**：遇到任何字节码问题，第一反应就是看它 | `javap -v -p Foo.class` |
| **JVMS** | Java 虚拟机规范。class 文件格式在**第 4 章** | [Oracle 规范索引](https://docs.oracle.com/javase/specs/) → 选一个版本 → JVMS |

## B 组：javaagent 与类加载（关 2 会用到）

| 词 | 一句白话 | 深入看 |
|---|---|---|
| **插桩** instrumentation | 不改源码，就往程序里加监测或改写它的行为 | [OTel 中文：零代码插桩](https://opentelemetry.io/zh/docs/concepts/instrumentation/zero-code/) |
| **agent**（javaagent） | 一个 jar，用 `-javaagent:xxx.jar` 参数让 JVM 加载它 | [Baeldung: Guide to Java Instrumentation](https://www.baeldung.com/java-instrumentation)（[中文镜像](https://baeldung.cn/java-instrumentation)） |
| **`premain`** | agent 的入口方法，**JVM 启动时**被调用 | 同上 |
| **`agentmain`** | agent 的入口方法，**JVM 已经跑起来之后**被挂载时调用 | 同上 |
| **attach**（动态挂载） | 把 agent 挂到一个**已在运行**的 JVM 上 | 同上 |
| **`Instrumentation`** | JVM 交给 agent 的"操作台"：注册 transformer、重新转换类、查询已加载类 | `java.lang.instrument` javadoc |
| **`ClassFileTransformer`** | 你的钩子。JVM 每加载一个类就把字节码交给它，你返回新字节码或 `null`（`null` = 别动它） | 同上 |
| **`retransformClasses`** | 对**已经加载过**的类重新跑一遍 transformer | [芋道源码：不重启 JVM 替换已加载的类](https://cloud.tencent.com.cn/developer/article/1458085) |
| **它的硬限制** | 不能增删方法/字段、不能改签名。原因：方法区里的元数据已经存在，改不了 | 关 2 的「必须能答」 |
| **ClassLoader** | 负责把 class 文件变成内存里的类。**agent 的类由 system loader 加载**，所以 agent jar 必须自带它的依赖 | 下面那条 |
| **为什么 agent jar 要"胖"** | 放到 system loader 搜索路径上的**只有 agent jar 自己**。少一个依赖 → `NoClassDefFoundError` → `FATAL ERROR`，整个进程起不来 | ADR 0003 / 关 3 的打包陷阱 |
| **JVMTI** | JVM 的 native 级调试接口。`java.lang.instrument` 是它的 Java 包装；Arthas 等工具底层就是它 | [谈谈 Arthas 背后的原理](https://cloud.tencent.com.cn/developer/article/1950546) |

## C 组：ASM 与字节码改写（关 3 会用到）

| 词 | 一句白话 | 深入看 |
|---|---|---|
| **ASM** | 一个 Java 库，用来读、写、改字节码。**和汇编语言无关** | [ASM 官网](https://asm.ow2.io/) |
| **`ClassReader`** | 把 class 字节读成"事件流"（依次通知每个字段、方法、指令） | ASM User Guide 第 2 章 |
| **`ClassWriter`** | 把事件流写回成字节 | 同上 |
| **`ClassVisitor` / `MethodVisitor`** | 访问者模式。你只覆写关心的回调，其余原样放行 | 同上 |
| **`AdviceAdapter`** | ASM 的便利基类，替你处理"进入/退出方法时插代码"的琐事（构造器、try-finally） | ASM User Guide 第 3 章。**本练习故意不用它** |
| **栈帧 / `StackMapTable`** | JVM 校验字节码用的表：每个跳转点上、栈和局部变量分别是什么类型 | JVMS 4.10 |
| **`COMPUTE_MAXS` / `COMPUTE_FRAMES`** | 让 ASM 帮你算 `max_stack`/`max_locals`；后者连栈帧一起重算，代价是要能加载应用类 | ASM javadoc `ClassWriter` |
| **局部变量槽位** | 实例方法的槽位 0 是 `this`，参数从 1 开始；静态方法从 0 开始；`long`/`double` 占**两个**槽位 | JVMS 2.6 |
| **装箱** boxing | `int` → `Integer` 才能放进 `Object[]` | 关 3 的 TODO 5 |
| **`VerifyError`** | JVM 校验你生成的字节码不通过时抛的错 | 关 3 的「可选进阶」 |
| **`LDC` / `INVOKESTATIC` / `ANEWARRAY` / `AASTORE`** | 关 3 会用到的几条 JVM 指令（压常量 / 调静态方法 / 建对象数组 / 往数组存引用） | JVMS 6.5 的指令表 |

## D 组：可观测性（全程都会碰到）

| 词 | 一句白话 | 深入看 |
|---|---|---|
| **可观测性** observability | 通过系统对外输出的数据，推断它内部发生了什么 | [OTel 中文：可观测性入门](https://opentelemetry.io/zh/docs/concepts/observability-primer/) |
| **trace（链路）/ span（跨度）** | 一次完整请求的过程 = 一个 trace；其中一个步骤 = 一个 span | [OTel 中文：链路](https://opentelemetry.io/zh/docs/concepts/signals/traces/) |
| **traceId / spanId** | 32 位 / 16 位十六进制字符串。**spanId 不是 UUID**（我们曾经在这上面栽过：UUID 去掉横线是 32 位，当 spanId 用是错的） | W3C Trace Context |
| **parentSpanId** | 指向上层 span，让 span 组成一棵树 | 同上 |
| **OTLP** | OpenTelemetry 的数据传输协议（我们用的是 HTTP + JSON） | ADR 0003、`docs/prior-art.md` |
| **语义约定** semantic conventions | **字段命名规范**（`gen_ai.request.model` 之类）。本项目是规范的**消费者**，不自己发明字段名 | `tomograph-semconv`、`SemconvRevision` |
| **resource / scope / service.name** | span 的归属信息：哪个服务、哪个库产生的 | OTLP 规范 |
| **exporter（导出器）** | 把采集到的数据发出去的组件 | `tomograph-exporter-otlp` |
| **Jaeger / Grafana Tempo / Langfuse** | 后端：把 span 画成调用树给人看的地方 | 各自官网 |
| **W3C Trace Context / `traceparent`** | 跨进程传递 traceId 的标准 HTTP 头 | `TraceContext` 类 |
| **SPI / `ServiceLoader`** | JDK 自带的"插件发现"机制。我们用它**在运行时**找到插桩模块，所以 core 不认识 LangChain4j | `TomographModule` / `SpanSinkProvider` |

## E 组：LLM 应用（并行任务，约 4 小时）

| 词 | 一句白话 | 深入看 |
|---|---|---|
| **token** | 模型处理文本的最小单位，也是**计费单位** | [LangChain4j 官方文档](https://docs.langchain4j.dev/) |
| **prompt / completion** | 输入 / 输出 | 同上 |
| **流式** streaming | 边生成边返回（逐 token 推），而不是等全部生成完 | 同上 |
| **工具调用** tool calling | 模型不直接回答，而是说"请调用这个函数，参数是…"；**副作用发生在你这边** | 同上 |
| **ReAct 循环** | 思考 → 调工具 → 观察 → 再思考 | 同上 |
| **embedding** | 把文本变成向量（一串数字），用于相似度计算 | 同上 |
| **RAG / 检索增强** | 先检索相关文档，再连同问题一起交给模型 | 同上 |
| **`ChatModel` / `ChatLanguageModel`** | LangChain4j 里"一次模型调用"的接口。**最可能的第一个切点** | 同上 |
| **`AiServices`** | LangChain4j 用**动态代理**生成的实现——**所以不能靠继承来插桩**。这一条直接决定了本项目的技术路线 | 同上 |
| **切点** cut point | 我们决定"在哪里切一刀"的那个方法。**难题 1** | `ARCHITECTURE.md` |

## F 组：工程与构建（随时会遇到）

| 词 | 一句白话 | 深入看 |
|---|---|---|
| **Maven 多模块 / reactor** | 一个父 `pom.xml` 管多个子模块，一次构建全部（本项目 8 个模块） | Maven 官方文档 |
| **`pom.xml`** | Maven 的构建描述文件 | 同上 |
| **shade**（maven-shade-plugin） | 把依赖一起打进同一个 jar（fat jar）。**agent 必须这样**（见 B 组） | `tomograph-javaagent/pom.xml` |
| **manifest / `Premain-Class`** | jar 里的元数据文件。JVM 靠它找到 agent 的入口。写错报错和"manifest 缺失"长得一样 | 关 2 的 `manifest.txt` |
| **CI / 矩阵构建** | 每次提交自动在多种环境（JDK×系统）上跑测试 | `.github/workflows/ci.yml` |
| **JMH** | Java 微基准测试框架。用来测"插桩开销到底是多少" | 阶段 3 |
| **ADR** | 架构决策记录：记录**为什么这样选、为什么不那样选**。**每一份 ADR 都是一道面试题** | `docs/adr/` |

---

## 资料清单（按阶段，链接都已核实）

### 现在就要用的（关 1）

1. **《深入理解Java虚拟机：JVM高级特性与最佳实践》（周志明）第 6 章「类文件结构」** —— 中文世界里讲这个最好的材料，先把这一章读完再动手
   - 有人做了[阅读笔记](https://github.com/hidult/understanding-the-jvm)，可以对照
2. **[JVMS（Java 虚拟机规范）第 4 章](https://docs.oracle.com/javase/specs/)** —— 权威，**当字典查，不要通读**
3. `javap -v -p` —— 最权威的答案永远在它手里，包括你写的解析器对不对

### 关 2 会用到

4. **[Baeldung: Guide to Java Instrumentation](https://www.baeldung.com/java-instrumentation)**（有[中文镜像](https://baeldung.cn/java-instrumentation)）—— `premain`/`agentmain`/`transformer` 的最快入门
5. **[芋道源码：不重启 JVM，替换掉已经加载的类](https://cloud.tencent.com.cn/developer/article/1458085)** —— 把 `retransform` 讲透了，中文里最好的一篇
6. **[谈谈阿里 Arthas 背后的原理](https://cloud.tencent.com.cn/developer/article/1950546)** —— 看看真实世界的工具怎么用这套 API；读完你会发现我们的技术路线和 Arthas 是同一族

### 关 3 会用到

7. **[ASM User Guide（官方 PDF）](https://asm.ow2.io/asm4-guide.pdf)** —— **只读前 3 章**。第 2 章讲读写，第 3 章讲方法级改写
8. **[ASM javadoc](https://asm.ow2.io/javadoc/overview-summary.html)** —— 查 `MethodVisitor` 有哪些回调
9. **`ASMifier`** —— 拿一个现成的 class，让它打印出"生成这个 class 的 ASM 代码"。**抄着学最快，但每一行都要理解**

### 可观测性概念（任何时候）

10. **OpenTelemetry 中文文档**：
    - [链路（Trace）](https://opentelemetry.io/zh/docs/concepts/signals/traces/) —— trace/span 是什么
    - [零代码插桩](https://opentelemetry.io/zh/docs/concepts/instrumentation/zero-code/) —— **我们这类方案的官方叫法**
    - [术语表](https://opentelemetry.io/zh/docs/concepts/glossary/) —— 官方中文术语
    - [Java 部分](https://opentelemetry.io/zh/docs/languages/java/) —— 官方 Java agent 的做法
11. **[官方 OTel Java agent 的受支持库清单](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/supported-libraries.md)** —— 在里面搜 `GenAI`，就能看到我们的生态位（见 `docs/prior-art.md`）

### LLM 应用（并行任务）

12. **[LangChain4j 官方文档](https://docs.langchain4j.dev/)** —— 重点看 `ChatModel`、tool calling、`EmbeddingStore`

### 想看看"别人怎么做的"（可选，但很有用）

- **Arthas** —— 阿里开源的 JVM 诊断工具，靠 javaagent + 字节码增强
- **JaCoCo** —— 代码覆盖率工具，底层也是插桩（**你看，插桩不只用于可观测性**）
- **Byteman / BTrace** —— 专门做"往运行中的方法里注入代码"的工具
- **[P4suta/walaru](https://github.com/P4suta/walaru)** —— 一个 0 star 的预发布项目，做 JVM 测试的确定性重放。它的 `docs/replay.md` 是 v1.0 最该读的先例

### 本项目自带的教材

- **三关的答案版**（`learning/answers/`）—— **注释就是教材**，写的是"为什么这样做、不这样做会怎样"
- **三个验收脚本**（`learning/check-day0*.ps1`）—— 它们会把你的错误指出来，然后你去看答案版对应的地方
- **`.poc-agent/src/`** —— 我当初的探针和诊断工具（含那个定位 `access_flags` 漏读的偏移走查器）
- **`docs/adr/`** —— 五份决策记录，含真实翻车过程
