# 预拉依赖清单（需要一次联网）

开发沙箱**没有对外网络**，Maven 本地仓库 `D:\java+python\maven1` **在沙箱里不可写**——但**在你的普通终端里是可写的**，你上一次运行已经证明了这一点：它成功下载了 `maven-dependency-plugin:3.7.0`。所以下面这些东西必须在一个**普通终端**（不是通过 DSH 的会话）里拉一次。

> ## ⚠️ 先读这个：PowerShell 会吃掉 `-D` 参数（已实证）
>
> 同一个命令的两种写法，Maven 实际收到的东西不一样：
>
> | 写法 | Maven 收到的 | 结果 |
> |---|---|---|
> | `mvn dependency:get -Dartifact=dev.langchain4j:langchain4j-core:1.21.0` | 一个**裸坐标** `dev.langchain4j:...` | ✗ 它把这个记号当成「插件前缀:目标」去解析 → `Plugin not found in any plugin repository: .langchain4j:langchain4j-core` |
> | `mvn dependency:get "-Dartifact=dev.langchain4j:langchain4j-core:1.21.0"` | 完整的 `-Dartifact=...` | ✔ 正常按 artifact 解析 |
>
> **结论：`-D` 开头的参数一律整体加引号。**
> 本项目已经在这件事上栽过 **6 次**（前 5 次都在我这边的脚本里：`git commit -m` 多行、`javap -J-D…`、`java -Dstdout.encoding…`）。
>
> **更稳的思路：能不用 `-D` 就不用。** 见下面第 0 节。

---

## 0. 最简单的路：**不要用 `dependency:get`**

`langchain4j-core` 已经作为依赖声明在 `tomograph-integration-tests/pom.xml` 里了。所以**让 Maven 自己去下载**就行——**没有任何 `-D` 参数可以被打乱**：

```powershell
cd D:\github\tomograph
mvn -B -ntp -Pwith-langchain4j verify
```

这一条命令会：下载 `dev.langchain4j:langchain4j-core:1.21.0` → 编译集成测试 → **真的运行它** → 直接告诉你 v0.1 到底过没过。

（这正是 `.github/workflows/langchain4j-integration.yml` 里 CI 跑的那一条，所以如果你先推送，CI 也会替你做。）

## 1. LangChain4j（只想单独预拉时）

**版本已核实**：`1.21.0`——2026-10 从 jsDelivr 的版本 API 读到的最新 release，不再需要你人工确认。

```powershell
mvn dependency:get "-Dartifact=dev.langchain4j:langchain4j-core:1.21.0"
```

`dev.langchain4j:langchain4j`（聚合 artifact）**现在不需要**：集成测试只需要 `core`，它自己不带任何 provider。

**验证**：

```powershell
Test-Path 'D:\java+python\maven1\dev\langchain4j\langchain4j-core\1.21.0\langchain4j-core-1.21.0.jar'
```

## 2. ASM 家族（**目前不挡任何事**，可以往后放）

原先这一节被我写成"插桩引擎的前置条件"——**那是错的**，现在改正：agent 和 LangChain4j 模块都**只用 `asm-core`**（本地一直有 9.9.1），而

- `AdviceAdapter`（在 `asm-commons`）：ADR 0006 里**明确不用**；
- `CheckClassAdapter`（在 `asm-util`）：只是关 3 的**可选进阶**。

所以这条只在两种情况需要：你想在关 3 里用 `CheckClassAdapter` 校验自己生成的字节码，或者将来决定改用 `AdviceAdapter`。

```powershell
mvn dependency:get "-Dartifact=org.ow2.asm:asm-commons:9.9.1"
mvn dependency:get "-Dartifact=org.ow2.asm:asm-util:9.9.1"
mvn dependency:get "-Dartifact=org.ow2.asm:asm-tree:9.9.1"
```

**验证**：

```powershell
Test-Path 'D:\java+python\maven1\org\ow2\asm\asm-util\9.9.1\asm-util-9.9.1.jar'
```

补上 `asm-util` 之后，`ASMifier` / `Textifier` / `CheckClassAdapter` 就都能用了——它们是你关 3 的调试家当（见 [glossary.md](glossary.md) 的「调试工具家当」）。

## 注意

- **不要在 DSH 会话里跑这些命令**：沙箱会拒绝写入 Maven 仓库目录，报「访问被拒绝」。
- 预拉只做一次。之后 `mvn -B -ntp -o clean package`（带 `-o`）就能离线用上它们。
- 每次新增依赖前，先确认本地已经有**真实的 jar**——本仓库里存在大量「目录在、jar 不在」的空壳（`maven-shade-plugin:3.6.2`、`org.junit.jupiter:*` 都踩过），只看目录名会被骗。
