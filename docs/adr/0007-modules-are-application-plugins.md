# 7. 插桩模块是"应用 classpath 上的插件"，不是 agent jar 的内容

- 状态：已接受
- 日期：2026-10
- 相关：[ADR 0003](0003-zero-dependency-otlp.md)（agent 零依赖）、[ADR 0004](0004-instrumentation-strategy.md)（ASM 版本钉死）、[ADR 0006](0006-langchain4j-cut-points.md)（第一个切点）

## 背景

v0.1 要写第一个真正改写字节码的模块（LangChain4j）。它需要 ASM。

但 [ADR 0003](0003-zero-dependency-otlp.md) 规定 **agent 零第三方依赖**，而且这条规定现在是**机器检查的**——
`AgentJarContentsTest` 会拆开 `tomograph-agent.jar`，要求里面除了 `io/github/godhuino1/tomograph/` 之下的类以外什么都不许有。

于是在动手之前必须先回答：**ASM 放在哪里？** 这是一个真实存在的矛盾，不是假想。

## 决策

**模块以独立 jar 的形式，放在应用（宿主）的 classpath 上，并自带自己的依赖。**

```
宿主应用的 classpath：
  ├── 应用自己的 jar / classes
  ├── tomograph-instrumentation-langchain4j.jar   ← 插桩模块（自带 ASM）
  ├── asm-9.9.1.jar                               ← 模块的依赖
  └── （通过 -javaagent 注入的）tomograph-agent.jar ← 零第三方依赖
```

agent 通过 `ServiceLoader` 在**运行时**发现模块（`META-INF/services/...TomographModule`）——
这正是 SPI 从第一天起的设计意图：第三方加一个新框架支持，**不需要碰 Tomograph 的源码**。

## 为什么不把 ASM shade 进 agent jar

这是最"方便"的做法，也是**明确拒绝**的做法：

| 问题 | 说明 |
|---|---|
| **和宿主的 ASM 冲突** | 宿主应用自己可能就用 ASM——JaCoCo、CGLIB、ByteBuddy、Hibernate、各种探针。把一个 shaded ASM 塞进去，等于在别人的类加载器里放一份可能不兼容的副本。这正是 ADR 0003 存在的理由 |
| **体积由所有人买单** | 每个开启插桩的应用都要多背 ASM，哪怕它根本不用 LangChain4j |
| **发布节奏被绑死** | 想升级模块就得重新发布 agent；反之亦然 |

## 为什么不把模块 jar 塞进 agent jar

同样的冲突问题，外加：一个只关心 Spring AI 的用户，会连 LangChain4j 模块一起背。

## 后果（连同代价一起写下来）

- **agent jar 保持小、干净、无冲突** ✔ 而且这条规矩有测试守着（`AgentJarContentsTest`）：它断言 agent jar 里**没有任何第三方类**，于是"模块把 ASM 带进 agent"这条路走不通——**纪律由机器执行，不由记忆执行**。
- **模块可以用任何库**（ASM、Jackson……），只要声明在自己的 pom 里。
- ⚠️ **诚实的代价：零代码改动 ≠ 零部署改动。** 使用者的 classpath 上要多两个 jar。这是真实的摩擦，不该被"零侵入"这个词掩盖掉。缓解方式是把依赖声明的负担放在模块的 README 里，并保持模块数量少而稳。
  > 需要记住：README 里"业务代码一个字不改"这句话**仍然是准确的**——它说的是源码，不是部署。
- **模块必须自己检查类名**，不能依赖引擎的路由。理由不是洁癖：依赖别人的路由意味着引擎一改（或出现第二个调用方），模块就会去改写任何交给它的类。这条已经写进示例模块的代码里。
- **模块先改写、后上报**：改写抛异常时引擎会丢弃整次改写，此时不该已经留下一条"成功"的 span。

## 复核方式

```powershell
# agent jar 里不能有第三方类（这条必须是绿的）
mvn -o -pl tomograph-examples/tomograph-example-fakeagent test -Dtest=AgentJarContentsTest

# 模块确实改写了，且 JVM 真的加载并执行了改写后的字节
mvn -o test -Dtest=ModuleRewriteTest
```

第二条测试做的事值得单独说：它向模块要改写后的字节 → **用自定义 ClassLoader 定义它** → 实例化 → 调用方法 → 断言**注入的调用执行了、且原方法返回值没变**。
前者证明改写生效，后者证明改写没有弄坏宿主——**一个改坏结果的插桩模块比什么都不做更糟**。
