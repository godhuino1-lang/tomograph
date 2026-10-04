# 读代码的顺序（平铺清单，从第 1 个开始）

**规则**：从第 1 个往下读，**不要跳**。每个文件 5–25 分钟。
标 ⚠️ 的四个需要"字节码基础"，**读不懂就直接跳过**，回头再来。

> **如果你只有 30 分钟**：只读第 1、2、3 个。它们最简单，也最能把这个项目在干什么说清楚。

| # | 文件 | 行数 | 需要什么 | 大约 |
|---|---|---|---|---|
| **1** | `tomograph-api/…/api/TomographSpan.java` | 152 | 会写 Java 就行 | 15 min |
| **2** | `tomograph-api/…/api/MethodCutPoint.java` | 38 | 同上 | 5 min |
| **3** | `tomograph-api/…/api/TomographModule.java` | 62 | 同上 | 10 min |
| **4** | `tomograph-semconv/…/semconv/SpanName.java` | 175 | 同上（就是字符串表） | 15 min |
| **5** | `tomograph-semconv/…/semconv/GenAiAttributes.java` | 175 | 同上 | 15 min |
| **6** | `tomograph-semconv/…/semconv/SemconvRevision.java` | 41 | 同上 | 3 min |
| **7** | `tomograph-semconv/…/semconv/TomographAttributes.java` | 70 | 同上 | 3 min |
| **8** | `tomograph-semconv/…/semconv/TraceContext.java` | 200 | 十六进制、位运算 | 20 min |
| **9** ⚠️ | `tomograph-core/…/core/ByteScan.java` | 65 | **常量池是什么** | 10 min |
| **10** ⚠️ | `tomograph-core/…/core/DeclaredMethods.java` | 137 | 同上 ← **读完它等于做完了关 1** | 25 min |
| **11** ⚠️ | `tomograph-core/…/core/InstrumentationEngine.java` | 211 | 同上 + 类加载器 | 25 min |
| **12** | `tomograph-core/…/core/AgentBootstrap.java` | 195 | 知道 `premain` 是什么 | 20 min |
| **13** | `tomograph-exporter-otlp/…/OtlpPayloadBuilder.java` | 187 | JSON 基础 | 20 min |
| **14** | `tomograph-report-html/…/report/SpanTree.java` | 182 | 会写 Java 就行 | 15 min |
| **15** | `tomograph-report-html/…/report/HtmlReport.java` | 300 | 同上 | 20 min |
| **16** ⚠️ | `tomograph-instrumentation-langchain4j/…/LangChain4jCutPoints.java` | 59 | **ASM（关 3 之后）** | 5 min |
| **17** ⚠️ | `tomograph-instrumentation-langchain4j/…/LangChain4jModule.java` | 183 | 同上 | 25 min |
| **18** ⚠️ | `tomograph-instrumentation-langchain4j/…/LangChain4jProbe.java` | 275 | 同上 | 25 min |

**完整路径**（仓库内相对路径，一行一个，可以直接复制）：

```
tomograph-api/src/main/java/io/github/godhuino1/tomograph/api/TomographSpan.java
tomograph-api/src/main/java/io/github/godhuino1/tomograph/api/MethodCutPoint.java
tomograph-api/src/main/java/io/github/godhuino1/tomograph/api/TomographModule.java
tomograph-semconv/src/main/java/io/github/godhuino1/tomograph/semconv/SpanName.java
tomograph-semconv/src/main/java/io/github/godhuino1/tomograph/semconv/GenAiAttributes.java
tomograph-semconv/src/main/java/io/github/godhuino1/tomograph/semconv/SemconvRevision.java
tomograph-semconv/src/main/java/io/github/godhuino1/tomograph/semconv/TomographAttributes.java
tomograph-semconv/src/main/java/io/github/godhuino1/tomograph/semconv/TraceContext.java
tomograph-core/src/main/java/io/github/godhuino1/tomograph/core/ByteScan.java
tomograph-core/src/main/java/io/github/godhuino1/tomograph/core/DeclaredMethods.java
tomograph-core/src/main/java/io/github/godhuino1/tomograph/core/InstrumentationEngine.java
tomograph-core/src/main/java/io/github/godhuino1/tomograph/core/AgentBootstrap.java
tomograph-exporter-otlp/src/main/java/io/github/godhuino1/tomograph/exporter/otlp/OtlpPayloadBuilder.java
tomograph-report-html/src/main/java/io/github/godhuino1/tomograph/report/SpanTree.java
tomograph-report-html/src/main/java/io/github/godhuino1/tomograph/report/HtmlReport.java
tomograph-instrumentation-langchain4j/src/main/java/io/github/godhuino1/tomograph/instrumentation/langchain4j/LangChain4jCutPoints.java
tomograph-instrumentation-langchain4j/src/main/java/io/github/godhuino1/tomograph/instrumentation/langchain4j/LangChain4jModule.java
tomograph-instrumentation-langchain4j/src/main/java/io/github/godhuino1/tomograph/instrumentation/langchain4j/LangChain4jProbe.java
```

```powershell
$root = 'D:\github\tomograph'
$tail = 'src\main\java\io\github\godhuino1\tomograph'
$files = @(
  "$root\tomograph-api\$tail\api\TomographSpan.java",
  "$root\tomograph-api\$tail\api\MethodCutPoint.java",
  "$root\tomograph-api\$tail\api\TomographModule.java",
  "$root\tomograph-semconv\$tail\semconv\SpanName.java",
  "$root\tomograph-semconv\$tail\semconv\GenAiAttributes.java",
  "$root\tomograph-semconv\$tail\semconv\SemconvRevision.java",
  "$root\tomograph-semconv\$tail\semconv\TomographAttributes.java",
  "$root\tomograph-semconv\$tail\semconv\TraceContext.java",
  "$root\tomograph-core\$tail\core\ByteScan.java",
  "$root\tomograph-core\$tail\core\DeclaredMethods.java",
  "$root\tomograph-core\$tail\core\InstrumentationEngine.java",
  "$root\tomograph-core\$tail\core\AgentBootstrap.java",
  "$root\tomograph-exporter-otlp\$tail\exporter\otlp\OtlpPayloadBuilder.java",
  "$root\tomograph-report-html\$tail\report\SpanTree.java",
  "$root\tomograph-report-html\$tail\report\HtmlReport.java",
  "$root\tomograph-instrumentation-langchain4j\$tail\instrumentation\langchain4j\LangChain4jCutPoints.java",
  "$root\tomograph-instrumentation-langchain4j\$tail\instrumentation\langchain4j\LangChain4jModule.java",
  "$root\tomograph-instrumentation-langchain4j\$tail\instrumentation\langchain4j\LangChain4jProbe.java"
)
code @files
```

---

# 每份文件看什么

## 1. `TomographSpan.java` —— 一个 span 长什么样

- **最上面那 10 个字段**：一个 span 的身份证（谁、属于哪次追踪、何时开始、多久、带了什么、成功还是失败）
- **为什么不可变的 record 还需要 builder**（注释里写了）
- **`epochNanosNow()`**：这是我们真实的坑——曾经用 `System.nanoTime()`，把所有 span 打成 **1970 年**，而当时所有单元测试都是绿的

**读完回答**：为什么 `attributes` 是 `Map<String, Object>`，而不是给每个字段单独定义一个？

## 2. `MethodCutPoint.java` —— 为什么"只匹配方法名"会出事

**读完回答**：为什么这个类型**必须**带描述符，而不能只带方法名？

## 3. `TomographModule.java` —— 模块的接口（设计核心）

**读完回答**：为什么"返回 `null`"表示"别动这个类"？为什么一个模块可以**只声明方法签名、不声明类名**？

## 4. `SpanName.java` —— 规范怎么落进代码

- 十八个操作名，每个的**主语规则**都不同（模型 / 数据源 / agent 名 / workflow 名 / 没有主语）
- 类注释里有一整段"**差点改错**"的记录：我怀疑 `chat` 改名了、怀疑 `generate_content` 是编的，**两个怀疑都是错的**

**读完回答**：`retrieval` 的主语为什么是数据源，而不是模型？

## 5. `GenAiAttributes.java` —— 属性键表 + 一次真实纠错

类注释记录了核对的全过程：**`gen_ai.token.type` 已经不存在了**，而继续发它会产出任何后端都不认识的属性——**而且这个错误从一开始就是隐形的**，因为没有任何东西会校验属性名。

**读完回答**：为什么"未核对的假设"要写成一个**可核对的目标版本号**，而不是一句"待办"？

## 6. `TraceContext.java` —— id 的规则

**读完回答**：为什么 span id 是 16 位十六进制、trace id 是 32 位？为什么全 0 是非法？**我们曾经用 UUID 当 span id，错在哪？**

## 7 ⚠️ `ByteScan.java` —— 最小的一次"读字节"

**读完回答**：为什么"在 class 字节里搜一个方法名"这件事**不可能漏**？（提示：方法名必然在常量池里）

## 8 ⚠️ `DeclaredMethods.java` —— 手写的 class 文件扫描器

**读完回答**：
- 它怎么跳过字段、直接走到方法表？
- **Long/Double 占两个槽位**这条规则在代码里是哪一行？（和你作业 1 是同一个坑）
- 遇到不认识的常量池 tag，它为什么返回 `false` 而不是抛异常？

> **这一份和你作业 1 是同一件事的两个方向**：作业是"把内容打印出来"，它是"只回答一个问题"。

## 9 ⚠️ `InstrumentationEngine.java` —— 引擎

**读完回答**：
- 为什么类名路由是"一次哈希查找"，而签名路由不是？
- **两级过滤为什么是"可靠"的，而不只是"快"的？**
- 一个模块抛异常时，为什么丢弃**整个类**的改写，而不是只跳过那个模块？

## 10. `AgentBootstrap.java` —— 启动纪律

**读完回答**：为什么每个可能失败的地方都包在 `catch (Throwable)` 里？

## 11. `OtlpPayloadBuilder.java` —— proto3 JSON 的五个陷阱

类注释就把五个都列了。**读完回答**：为什么 64 位整数要写成**字符串**？为什么 enum 只能用整数（proto3 允许名字，**OTLP 明令禁止**）？

## 12. `SpanTree.java` —— 环形数据会怎么毁掉一个天真的实现

**读完回答**：A 的父是 B、B 的父是 A，会发生什么？

## 13. `HtmlReport.java` —— 不可信输入

**读完回答**：为什么所有插进 HTML 的值都要转义？为什么百分比格式化必须指定 `Locale.ROOT`？

## 14–16 ⚠️ LangChain4j 三件（**关 3 之后再来**）

**读完回答**：
- 切点为什么选**最内层的那个漏斗方法**（`doChat`），而不是任何一个外层方法？
- 注入的字节码里为什么**一个框架类型都不出现**？
- `DUP` 那一条是干什么的？为什么 response 是探针的**第一个**参数？

---

# 读不动的时候

1. **别硬读**。停在那一行，**把那一行原样发给我**，我只讲那一行。
2. **跳过 ⚠️**。第 7、8、9 个需要关 1 的知识，第 14–16 个需要关 3 的。**先把不需要的读完**，你会发现自己懂不少。
3. **先写作业也行**。作业 1 做完再回来读第 8 个，会突然变得很简单。
