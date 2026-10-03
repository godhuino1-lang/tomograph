# Tomograph

[![CI](https://github.com/godhuino1-lang/tomograph/actions/workflows/ci.yml/badge.svg)](https://github.com/godhuino1-lang/tomograph/actions/workflows/ci.yml)

> 给 JVM 上的 AI Agent 做**断层扫描**：不改一行业务代码，看清 Agent 内部发生了什么；并把一次真实运行**原样重放**出来。

医学上做 CT 不用开刀，就能重建你身体内部的三维结构。Tomograph 做的是同一件事，只不过扫描对象是**运行中的 JVM 里的 AI Agent**。

## 为什么需要它

今天给一个 Java 写的 LLM Agent 排查问题，你面对的是这样的日志：

```
INFO  calling model...
INFO  tool result received
ERROR request failed
```

你看不到：喂给模型的完整 prompt 是什么、RAG 命中了哪几段文档、Agent 中间调了哪些工具、每个工具返回了什么、这一步烧了多少 token 多少钱、失败的那次工具调用是不是被上层 `catch` 吞掉了。

更糟的是**你回不到那一刻**：模型每次回答都不一样，本地重跑复现不了线上问题。Agent 的调试因此长期停留在"玄学"阶段。

### 为什么这个空白还在

说"业界缺失"没有说服力，**说清别人为什么没做**才有——因为前者事后可以被任何新项目推翻，后者是一个能被检验的解释：

1. **官方 OpenTelemetry Java agent 覆盖的是"厂商 SDK"，不是"框架层"。** 它的受支持库清单里提到 GenAI 的只有两项：`AWS SDK` 与 `OpenAI Java SDK 1.1+`。**LangChain4j、Spring AI 都不在其中。**
2. **原因在插桩策略**：OTel 打的是厂商 SDK，而 LangChain4j 有自己的 HTTP 客户端、**不经过 `openai-java`**——所以官方那个零侵入 agent 看不见 LangChain4j 的模型调用。
3. **JVM 上现有的 LLM 可观测方案都要改代码**：`instrument()` 调用、`@Agent` 注解、注册 `listeners(...)`、或主动调 SDK。**没有一个只要一个 `-javaagent` 参数。**
4. **但"能采到 span"本身不是差异。** LangChain4j 自带 `ChatModelListener`，openinference 也已经覆盖 Java。**真正的差异在语义层**：打在 SDK 层的 agent 能给你 token 数与模型名，给不了"跑了几轮、每轮调了哪个工具、工具返回了什么、检索命中了什么"。

所以本项目的价值主张是**精确的**：*在框架层零侵入地还原 Agent 的语义结构*，而不是"又一个能导出 OTLP 的工具"。

> 完整对照（各项目 star 数、侵入方式、我们面临的四条风险）见 **[docs/prior-art.md](docs/prior-art.md)**。
> **最大的风险写在明面上**：OTel 随时可以给 LangChain4j 加一个 instrumentation 模块，这不受我们控制——所以价值必须建立在语义层的深度与重放能力上，而不是"我们支持 LangChain4j"这一条事实。

## 它做什么

**能力一：零侵入观测。** 应用启动时多加一个参数：

```bash
java -javaagent:tomograph-agent.jar -jar your-agent-app.jar
```

业务代码一个字不改，就能拿到完整的 Agent 调用树——模型调用、token 与成本、工具调用与副作用、检索命中、异常，全部按 OpenTelemetry GenAI 语义约定导出，接 Jaeger / Grafana Tempo / Langfuse 任何一个后端都行。

**能力二：确定性重放。** 把一次真实运行的所有外部交互录制到本地，之后可以离线、免费、无副作用地原样重演，并对两次运行做 diff。Agent 的调试从"玄学复现"变成**可回归的单元测试**。

## 快速开始

前置：JDK 17+（开发用 26 也可以）、Maven 3.9+。

```bash
git clone https://github.com/Godhuino1-lang/tomograph.git
cd tomograph
mvn -B -ntp clean package
```

跑一遍带 agent 的示例（示例是一个刻意写得很笨的假 Agent）。

注意 classpath 上有**两样东西**：应用自己的类，**以及插桩模块自己的依赖**——模块是应用 classpath 上的插件，不是 agent jar 的内容（见 [ADR 0007](docs/adr/0007-modules-are-application-plugins.md)）。
少放一个，agent 会启动失败并**明确告诉你**是哪个模块加载不了，而宿主应用照常运行、不受影响：

```bash
# 示例插桩模块改写字节码，所以它需要 ASM。版本钉在 9.9.1（见 ADR 0004），
# 路径按你本机的 Maven 仓库调整（本项目的在 ARCHITECTURE.md 里记着）。
ASM=~/.m2/repository/org/ow2/asm/asm/9.9.1/asm-9.9.1.jar

java -javaagent:tomograph-javaagent/target/tomograph-agent.jar \
     -cp "tomograph-examples/tomograph-example-fakeagent/target/classes:$ASM" \
     io.github.godhuino1.tomograph.examples.fakeagent.Main
```

你应该看到 agent 在 `stderr` 上打印启动横幅、报告它**改写**了示例类，随后出现：

```
[agent] module example-fakeagent rewrote .../FakeChatModel (1784 -> 1879 bytes, loader=app)
[example-probe] entered chat
```

**最后那行来自被插入的字节码**：这个仓库里没有任何 Java 源码调用 `ExampleProbe`，所以它出现只可能是"字节码被改写 → 被 JVM 加载 → 被执行"——**这就是 `premain → transformer → 模块 → 改写后的字节码真的在跑` 这条完整链路的证据**。

### 本地开发注意事项

当前开发机所在的环境有两条硬约束（详见 [ARCHITECTURE.md](ARCHITECTURE.md#本机与-ci-环境的硬约束实测记录)）：

1. **构建必须加 `-o`（离线）**：该沙箱无法访问 Maven 中央仓库，且 Maven 本地仓库位于工作区外、不可写。所以：

   ```bash
   mvn -B -ntp -o clean package
   ```

   CI 上则**不要**加 `-o`。

2. **在 Windows 上跑示例时加编码参数**，否则 Java 输出的中文会乱码：

   ```bash
   java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -javaagent:... ...
   ```

   想看到更详细的插桩日志，加 `-Dtomograph.debug=true`。


## 项目状态

`0.1.0-SNAPSHOT` — **pre-alpha，骨架阶段**。当前已打通的是插桩管线和 SPI；真正的字节码改写（ASM）与 LangChain4j 切点在 v0.1 落地。看 [ROADMAP.md](ROADMAP.md) 了解时间表。

## 设计边界（这个项目**不**做什么）

维护周期按 3 年设计，边界比功能更重要：

1. **不做 Agent 框架。** 永不与 LangChain4j / Spring AI 竞争——只做它们的观测面。框架越强，Tomograph 越有用。
2. **不做 UI 平台，不做后端服务。** 数据全部走标准 OTLP，可视化交给现成后端；只保留一个**自包含单文件 HTML 报告**用于离线分享和面试演示。
3. **不依赖任何云厂商 SDK。** 内核保持小体积、零外部服务依赖。

完整定义见 [SCOPE.md](SCOPE.md)。

## 文档

- [SCOPE.md](SCOPE.md) — 项目宪法：边界、3 年演化原则
- [ARCHITECTURE.md](ARCHITECTURE.md) — 模块划分、数据流、三个真正的技术难题
- [ROADMAP.md](ROADMAP.md) — 里程碑与时间表
- [docs/learning-path.md](docs/learning-path.md) — 分阶段学习与推进计划：每阶段的交付物、完成标志与必读材料
- [docs/glossary.md](docs/glossary.md) — **术语表 + 资料清单**：每个词一句白话、在哪一关会遇到、该看什么材料
- [docs/reading-the-code.md](docs/reading-the-code.md) — **怎么读这个项目**：十站路线，每站写明前置知识与"读完应该能回答什么"（七站不需要字节码知识）
- [docs/prior-art.md](docs/prior-art.md) — 同类项目对照与定位：谁已经做了什么、我们的差异点在哪、风险是什么

## 许可

Apache License 2.0。**LICENSE 文件尚未添加**——请用 GitHub 的模板一键生成，不要手抄：许可证是一份法律文件，凭记忆重建它存在写错条款的风险。
