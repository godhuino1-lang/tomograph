# 第一周任务清单（2026.10）

目标：**打通「不改业务代码就能观测 JVM 内部」这条链路**，并补上 LLM 应用的基本概念。

> 判定本周成功的唯一标准：你能从零写出一个 agent，挂上去打印任意方法的耗时和入参。
> 仓库里 `.poc-agent/`（在 `D:\github\` 下，不在本仓库内）就是这条路线的参考实现。

---

## Day 1–2：字节码基础（这是唯一的地基，别跳过）

**读**：JVMS（Java Virtual Machine Specification）第 4 章 Class File Format。

**做**：手写一个 **class 文件解析器**（约 30–50 行就够），要求：

- 读 `magic`（必须是 `0xCAFEBABE`）、`minor_version`、`major_version`
- 遍历常量池，把 `CONSTANT_Utf8` 的内容打出来
- 打印这个 class 的 `this_class` 名字

**为什么值得花两天**：做完之后，"字节码"对你来说就从一堆魔数变成了一个**你能亲手读写的文件格式**。后面所有插桩工作都建立在这个直觉上。

**验收**：`java -cp . ClassDump out/app/Main.class` 能打印出你自己的主类名和常量池内容。

## Day 3–4：`java.lang.instrument` + 第一个 agent

**读**：`java.lang.instrument` 的 javadoc，重点吃透这些硬限制：

- `premain`（启动时挂载）vs `agentmain`（运行时 attach）的区别与各自约束
- **`retransformClasses` 不能增删方法/字段，不能改方法签名**——这是 JVM 的硬限制
- agent jar 是怎么被 JVM 找到类的（system class loader 搜索路径追加）
- `ClassFileTransformer` 返回 `null` / 返回原数组 / 返回新数组分别意味着什么

**做**：写出第一个 agent，运行方式：

```bash
java -javaagent:my-agent.jar -cp out app.Main
```

**验收**：不改 `app.Main` 一个字符，agent 输出该方法被调用时的耗时。

## Day 5–7：ASM 入门

**读**：ASM 官方 User Guide 的前 3 章。

**关键工具（必须熟练，这是插桩调试的全部家当）**：

| 工具 | 用途 |
|---|---|
| `javap -c -p Foo.class` | 人肉读字节码。**遇到任何插桩问题，第一反应就是看它。** |
| `ASMifier` | 把现成的 class 打印成生成它的 ASM 代码——抄着学最快 |
| `CheckClassAdapter` | 校验你生成的字节码是否合法 |
| `Textifier` | 把类打印成可读的指令列表 |

**做**：把 Day 3–4 的 agent 用**手写 ASM** 重做一遍。用 `AdviceAdapter` 在方法进入/退出时插桩。

**验收**：仍然是零侵入，但现在是你自己生成的字节码；且用 `javap -c` 能看到插入的指令。

---

## 并行任务：LLM 应用速成（约 4 小时，本周内完成）

这块不占主线，但必须在本周补上，否则 v0.1 时你不知道该在哪里切。

1. **概念**：token 是什么、为什么它是计费单位；流式（streaming）响应与非流式的区别；function / tool calling 的请求-响应结构；embedding 与向量检索（RAG）的基本流程；ReAct 循环（思考 → 调工具 → 观察 → 再思考）
2. **动手**：直接调一次 OpenAI 兼容的 chat completions API（用 `curl` 或 `HttpClient` 都行，**刻意不用框架**），把请求体和响应体的 JSON 原样打印出来看一遍
3. **读框架**：在 IDE 里打开 LangChain4j 源码，找到这几个接口，画出它们的继承关系：
   - `ChatModel` / `ChatLanguageModel`
   - `ToolSpecification` / 工具执行入口
   - `EmbeddingStore` / `ContentRetriever`
   - `AiServices`（注意它是**动态代理**生成的实现——这决定了你没法直接继承它来插桩）

**验收**：你能回答"如果要在 LangChain4j 里切一刀采集模型调用，我会切哪个方法，为什么"。

---

## 本周结束时应该能回答的问题

1. `retransformClasses` 为什么不能给一个已加载的类加字段？
2. 你的 agent 类由哪个 class loader 加载？为什么这会成为一个问题？
3. `ClassFileTransformer.transform()` 返回 `null` 和不返回 `null` 有什么区别？
4. LangChain4j 里，一次 LLM 调用的"必经之路"是哪个方法？
5. 为什么 `ThreadLocal` 在虚拟线程和 `@Async` 场景下会丢上下文？

答不上第 5 个不要紧（那是难题 2，见 `ARCHITECTURE.md`），但前 4 个必须能答。
