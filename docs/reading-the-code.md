# 怎么读这个项目的代码（十站路线）

## 先看这个结论

**63 个 Java 文件里，你**现在**就能读懂大部分。** 真正需要"字节码知识"的只有 3 个文件，其余只需要你会写 Java。

所以别从头读到尾——那是最劝退的读法。按下面十站走，每站 5–25 分钟，**每站都告诉你"前置是什么"和"读完应该能回答什么"**。

遇到不认识的词 → 查 [glossary.md](glossary.md)（按"在哪一关会遇到"分组，每条一句白话）。

---

## 第 1 站：一个 span 长什么样

**读**：[`TomographSpan.java`](../tomograph-api/src/main/java/io/github/godhuino1/tomograph/api/TomographSpan.java)（约 150 行）

**前置**：record、builder 模式、不可变对象——**你都会**。

**读完应该能回答**：
- 一个 span 有哪些字段？哪些是必需的，哪些可选？
- 为什么时间戳用 `long` 纳秒而不是 `Instant`？

## 第 2 站：规范是怎么落进代码的

**读**：[`SpanName.java`](../tomograph-semconv/src/main/java/io/github/godhuino1/tomograph/semconv/SpanName.java)、[`GenAiAttributes.java`](../tomograph-semconv/src/main/java/io/github/godhuino1/tomograph/semconv/GenAiAttributes.java)

**前置**：无——**就是两张字符串表**。

**读完应该能回答**：
- 为什么 `retrieval` 的主语是数据源而不是模型？
- `MethodCutPoint` 为什么必须带描述符？（去 `MethodCutPoint.java` 的注释里找，那是一个真实的坑）

## 第 3 站：trace id / span id 的规则

**读**：[`TraceContext.java`](../tomograph-semconv/src/main/java/io/github/godhuino1/tomograph/semconv/TraceContext.java)（约 200 行，含测试）

**前置**：十六进制、位运算、`SecureRandom`——**你都会**。

**读完应该能回答**：
- 为什么 span id 是 16 位十六进制而 trace id 是 32 位？
- 为什么"全 0"是非法 id？
- **我们曾经用 UUID 当 span id，错在哪？**（这是个真实事故，测试里留着）

## 第 4 站：插桩模块的接口（这一站是设计的核心）

**读**：[`TomographModule.java`](../tomograph-api/src/main/java/io/github/godhuino1/tomograph/api/TomographModule.java)、[`MethodCutPoint.java`](../tomograph-api/src/main/java/io/github/godhuino1/tomograph/api/MethodCutPoint.java)

**前置**：接口、`ServiceLoader`（查术语表 D 组）——**注释本身就把设计意图讲完了**。

**读完应该能回答**：
- 为什么这个接口的一个方法返回 `null` 是"别动这个类"？
- 为什么一个模块可以**只声明方法签名**、不声明类名？

## 第 5 站：引擎（第一个"难"的文件）

**读**：[`InstrumentationEngine.java`](../tomograph-core/src/main/java/io/github/godhuino1/tomograph/core/InstrumentationEngine.java)（约 200 行）

**前置**：`Map`/`List`、类加载器的基本概念（术语表 B 组）、**常量池**这个概念（A 组，一句话：class 文件的字典）。

**读完应该能回答**：
- 为什么精确类名路由是"一次哈希查找"，而签名路由不是？
- **两级过滤为什么是"可靠"的而不只是"快"的？**（提示：一个声明了某方法的类，它的方法名必然在常量池里——所以第一级不会漏）
- 一个模块抛异常时，引擎为什么丢弃**整个类**的改写，而不是跳过那个模块？

## 第 6 站：手写的 class 文件扫描器 ← **读完这站，你等于做完了关 1**

**读**：[`DeclaredMethods.java`](../tomograph-core/src/main/java/io/github/godhuino1/tomograph/core/DeclaredMethods.java)（约 160 行）

**前置**：关 1 的那 6 个词（魔数 / 常量池 / major version / access_flags / 槽位 / 描述符）。

**读完应该能回答**：
- 它怎么跳过字段、直接走到方法表？
- **Long/Double 占两个槽位**这条规则在代码里是哪一行？（和你作业里踩的是同一个坑）
- 它遇到不认识的常量池 tag 时为什么返回 `false` 而不是抛异常？

> **这一站和你作业 1 是同一件事的两个方向**：作业是"把内容打印出来"，这里是"只回答一个问题"。
> 你做完作业 1，回来看这一站会觉得"这不就是我刚写的东西吗"。

## 第 7 站：启动引导与纪律

**读**：[`AgentBootstrap.java`](../tomograph-core/src/main/java/io/github/godhuino1/tomograph/core/AgentBootstrap.java)（约 200 行）

**前置**：`premain` / `agentmain`（关 2 会学）。

**读完应该能回答**：
- 为什么每个可能失败的地方都包在 `catch (Throwable)` 里？
- 为什么模块的类加载器要从"当前线程的上下文加载器"取？

## 第 8 站：OTLP 编码的五个陷阱

**读**：[`OtlpPayloadBuilder.java`](../tomograph-exporter-otlp/src/main/java/io/github/godhuino1/tomograph/exporter/otlp/OtlpPayloadBuilder.java)（类注释就列了五个）

**前置**：JSON 基础——**你都会**。

**读完应该能回答**：
- 为什么 `traceId` 用十六进制而不是 base64？（proto3 标准 JSON 里 `bytes` 是 base64，OTLP 特意例外）
- 为什么 64 位整数要写成**字符串**？（JS 的 number 精度）
- enum 为什么只能用整数？（proto3 允许名字，**OTLP 明令禁止**）

## 第 9 站：真实的切点（关 3 之后再来，收获最大）

**读**：[`LangChain4jCutPoints.java`](../tomograph-instrumentation-langchain4j/src/main/java/io/github/godhuino1/tomograph/instrumentation/langchain4j/LangChain4jCutPoints.java)、[`LangChain4jModule.java`](../tomograph-instrumentation-langchain4j/src/main/java/io/github/godhuino1/tomograph/instrumentation/langchain4j/LangChain4jModule.java)、[`LangChain4jProbe.java`](../tomograph-instrumentation-langchain4j/src/main/java/io/github/godhuino1/tomograph/instrumentation/langchain4j/LangChain4jProbe.java)

**前置**：ASM 的 `ClassVisitor`/`MethodVisitor`（关 3）+ 栈的基本概念。

**读完应该能回答**：
- 注入的字节码里为什么**一个框架类型都不出现**？
- `DUP` 那一条是干什么的？（提示：响应既要被上报、又要被返回）
- 为什么 response 是探针的**第一个**参数，尽管它是第一个被压栈的？

## 第 10 站：另一个方向的工程（可选，轻松）

**读**：[`HtmlReport.java`](../tomograph-report-html/src/main/java/io/github/godhuino1/tomograph/report/HtmlReport.java)、[`SpanTree.java`](../tomograph-report-html/src/main/java/io/github/godhuino1/tomograph/report/SpanTree.java)

**前置**：无。

**读完应该能回答**：
- 为什么所有插入 HTML 的值都要转义？（数据来自被观测的应用，是不可信输入）
- **环状数据**（A 的父是 B、B 的父是 A）会怎么毁掉一个天真的实现？

---

## 读不动的时候

1. **别硬读**。停在那一站，把不懂的那一行原样发给我，我讲那一行。
2. **跳过去**。第 5、6、9 站看不懂很正常（它们要么需要关 1 的知识，要么需要关 3 的）。**先把能读的读完**，你会发现自己其实懂不少。
3. **写下来**。任何"这里为什么这样"的问题记在纸上；答案往往就在同一份文件的注释里——这个项目的注释写得比正文多，是故意的。
