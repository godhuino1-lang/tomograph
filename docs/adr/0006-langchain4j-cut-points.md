# 6. LangChain4j 的第一个切点：切最内层漏斗，且按签名匹配而不是按类名

- 状态：已接受（其中若干条需要拿到 jar 之后复核）
- 日期：2026-10
- 相关：[ADR 0004](0004-instrumentation-strategy.md)（插桩策略与 ASM 边界）、[ADR 0003](0003-zero-dependency-otlp.md)

## 背景

v0.1 要在 LangChain4j 上切第一刀。切点选错的代价很高：**选错了不会报错，只会产出错误的调用树**（重复的 span、漏掉的模型调用），而这正是本项目最不能接受的失败模式。

选切点时有两个直觉答案，**两个都是错的**——本文记录它们为什么错，以及证据来自哪里。

## 核实方法

本地没有 LangChain4j 的 jar（沙箱无网络、离线仓库里没有），但**源码可以直接读**：

```
https://cdn.jsdelivr.net/gh/langchain4j/langchain4j@main/langchain4j-core/src/main/java/dev/langchain4j/model/chat/ChatModel.java
https://cdn.jsdelivr.net/gh/langchain4j/langchain4j@main/langchain4j-core/src/main/java/dev/langchain4j/model/chat/StreamingChatModel.java
```

可 pin 的版本号来自 jsDelivr 的版本 API（`https://data.jsdelivr.com/v1/packages/gh/langchain4j/langchain4j`），
**最新 release 是 `1.21.0`**。

> ⚠️ **诚实标注**：这次读的是 `@main` 分支，**没有 pin 到 SHA**——该仓库的 commit Atom feed 连续两次抓取失败。
> 涉及版本的结论（`@since`）来自源码 javadoc，可复核；但**在拿到 jar、把版本钉死之前，本文的结论都算"待复核"**。

## 事实：真实的调用链（来自源码，不是印象）

### 阻塞式 `ChatModel`

```
chat(String)  ─┐
chat(ChatMessage...) ─┤
chat(List<ChatMessage>) ─┴─► chat(ChatRequest)
                                  └─► chat(ChatRequest, ChatRequestOptions)   ← @since 1.13.0
                                          │  归一化请求、取 listeners、触发 onRequest
                                          ├─► doChat(ChatRequest)             ← ★ 每个实现必须覆写的那一个
                                          │  触发 onResponse / onError
                                          └─► （异步版走 doChatAsync，@since 1.20.0）
```

### 流式 `StreamingChatModel`

```
chat(String, handler) / chat(List, handler) ─► chat(ChatRequest, handler)
                                                   └─► chat(ChatRequest, options, handler)   ← @since 1.13.0
                                                           │  触发 onRequest，包一层 observingHandler
                                                           └─► doChat(ChatRequest, StreamingChatResponseHandler)  ← ★

（另有 1.20.0 起的响应式路径：chat(ChatRequest) → Publisher<ChatModelStreamingEvent> → doChat(ChatRequest)）
```

### 由此得到的三条硬事实

1. **同一个模型调用有多达四个入口**，它们层层委托。最内层是 `doChat*`。
2. **框架自己已经在 `chat(..., options)` 里触发 `ChatModelListener`（onRequest/onResponse/onError）**——也就是说"能看见模型调用"这件事框架已经提供了（代价是要改代码注册 listener）。
3. `ChatLanguageModel.java` 在 `main` 上**返回 404**（同目录的 `ChatModel.java` 正常返回 200），即该接口已被 `ChatModel` 取代。

## 决策

### 决策 1：切最内层的 `doChat`，**不切**外层方法

外层有四个入口、层层转发。如果在 `chat(ChatRequest)` 和 `chat(ChatRequest, ChatRequestOptions)` 上都插桩，
**一次模型调用会产出两个 span**——而且不报错，只会让"调用树"里每次调用都出现两遍。

切 `doChat(ChatRequest)` 则**恰好一个**，因为所有外层路径最终都汇聚到它，而它是抽象 SPI（每个 provider 实现必须覆写）。

> **判据**：切"最内层的唯一收口处"。如果某个方法只是把请求转发给另一个同样会被你插桩的方法，那就是错的切点。

### 决策 2：按 **(方法名 + 描述符)** 匹配，**不按类名清单**

`ChatModel` 是**接口**。插桩接口本身没有任何作用，必须命中**实现类**。而实现类的集合是开放的：
`OpenAiChatModel`、`OllamaChatModel`、`AnthropicChatModel`……**以及使用者自己写的实现**和测试替身。

维护一份"已知实现类名清单"有三个问题：随版本漂移、漏掉用户自定义实现、每次上游新增 provider 都要改我们。

**改成按签名匹配**：凡是声明了

```
doChat(Ldev/langchain4j/model/chat/request/ChatRequest;)Ldev/langchain4j/model/chat/response/ChatResponse;
```

的方法，就在它的入口/出口插桩——**不看类名**。

参数类型里带着 `dev/langchain4j/...` 全限定名，所以这个签名本身就是极强的判别式，误伤无关 `doChat` 的概率可以忽略。

**描述符必须是匹配的一部分，不能只匹配方法名**：`StreamingChatModel` 里有一个**同名同参数、只有返回值不同**的 `doChat`：

```
doChat(ChatRequest) → ChatResponse                      （阻塞式，1.x）
doChat(ChatRequest) → java.util.concurrent.Flow$Publisher（响应式，@since 1.20.0）
```

只按方法名匹配会同时命中两者，然后给其中一个插上错误的代码——**直接 VerifyError**。

### 决策 3：v0.1 只切阻塞式 `doChat`，其余三条路径**显式排期**

| 路径 | 切点 | 排期 | 理由 |
|---|---|---|---|
| 阻塞式 | `doChat(ChatRequest)` → `ChatResponse` | **v0.1** | 最常用，且是验证整套机制的最小切片 |
| 流式（handler） | `doChat(ChatRequest, StreamingChatResponseHandler)` | v0.1 之后立即 | 流式应用很常见，漏了等于"看不见真实用户的主要路径" |
| 异步 | `doChatAsync(ChatRequest)` | v0.2 | `@Experimental`，1.20.0 才引入 |
| 响应式 | `doChat(ChatRequest)` → `Publisher` | v0.2 | 同上 |

**明确排期而不是"以后再说"**：漏掉一条路径的表现是"某些应用完全没有数据"，而不是报错——**必须写在纸面上，否则它会以"用户报 bug"的形式回来**。

## 对引擎的影响（这是本 ADR 真正的成本）

现在的 `InstrumentationEngine` 按**精确类名**路由（`TomographModule.targetClassNames()`）。决策 2 需要一种新的路由模式：

> **签名匹配**：类名不在清单里，但类中声明了某个特定签名的方法 → 也交给模块。

代价与约束：

- **性能**：不能对每个加载的类都做一次方法遍历。但有一个便宜的预筛——方法名以 `Utf8` 常量存在于 class 文件的常量池里，**先做一次字节级子串查找**，命中才真正解析。类加载路径上的开销必须保持在这个量级。
- **SPI 要扩**：`TomographModule` 需要一个表达"签名切点"的方式（而不是只有类名集合）。这是一次**破坏性 SPI 变更**，好在现在还没有外部使用者。
- **精确类名路由保留**：对"我们明确知道类名"的目标（例如未来的 Spring AI 适配）它更便宜、更可预测。

## 待复核（拿到 jar 之后必须逐条确认）

1. 把版本**钉死**（优先 `1.21.0`），并把本文所有签名对着**那个版本的 class 文件**核一遍。
2. `ChatLanguageModel` 在 1.x 里是否仍存在（`main` 上已无）。若存在，是否需要单独切点。
3. 具体实现（如 `OpenAiChatModel`）是**自己声明 `doChat`**，还是继承某个抽象基类——这决定签名匹配能否命中。
4. `AiServices` 动态代理这条路径最终是否也走 `doChat`（若是，则自动被覆盖）。
5. 确认没有其它框架类型也叫 `doChat` 且参数类型相同（概率低，但这是误伤的唯一来源）。

## 后果

- v0.1 的第一个切点有了**基于源码的**依据，而不是基于印象——而且排除了两个看起来合理、实际会让调用树出错的选择。
- 引擎要新增签名匹配路由；这是一次真实的能力扩张，也是 v0.1 除 ASM 改写之外的第二块硬骨头。
- **`ChatModelListener` 的存在需要写进 README 的定位叙述**：框架已经能"看见调用"，我们的差异是**不改代码 + agent 语义层**（见 [prior-art.md](../prior-art.md)）。
