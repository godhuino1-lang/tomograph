# 预拉依赖清单（需要一次联网）

开发沙箱**没有对外网络**，Maven 本地仓库 `D:\java+python\maven1` 也**不可写**。所以下面这些东西必须在一个**普通终端**（不是通过 DSH 的会话）里预拉一次，之后离线构建才能用到它们。

> 预拉后请回来告诉我结果，我会把对应版本钉进 `pom.xml`。

## 1. ASM 家族补齐到 9.9.1（插桩引擎的前置条件）

**为什么必须做**：实测（见 [ADR 0004](adr/0004-instrumentation-strategy.md)）表明 `asm-commons` 离线最高只有 **9.7**，而 ASM 9.7 **拒绝** Java 25/26 编译出的 class 文件（major 69/70），异常直接从 `ClassReader` 构造函数抛出——那是在宿主 JVM 的类加载路径上。而 `AdviceAdapter` 就在 `asm-commons` 里。

```powershell
mvn dependency:get -Dartifact=org.ow2.asm:asm-commons:9.9.1
mvn dependency:get -Dartifact=org.ow2.asm:asm-util:9.9.1
mvn dependency:get -Dartifact=org.ow2.asm:asm-tree:9.9.1
mvn dependency:get -Dartifact=org.ow2.asm:asm-analysis:9.9.1
```

顺带解决一件事：你的作业 3 需要 **ASMifier**，而 `asm-util` 离线只有 5.0.3 和 8.0。补上 9.9.1 之后 `ASMifier` / `Textifier` / `CheckClassAdapter` 就都能用了。

**验证是否成功**（每个都应输出 `True`）：

```powershell
Test-Path 'D:\java+python\maven1\org\ow2\asm\asm-commons\9.9.1\asm-commons-9.9.1.jar'
Test-Path 'D:\java+python\maven1\org\ow2\asm\asm-util\9.9.1\asm-util-9.9.1.jar'
```

## 2. LangChain4j（v0.1 的「LangChain4j 切点」的前置条件）

**为什么必须做**：`dev/langchain4j` 在本地仓库里**完全不存在**（不是版本旧，是根本没有），而沙箱不能联网。不预拉，v0.1 的切点就只能停在纸面上。

先查当前最新稳定版（我无法联网核对版本号，需要你确认）：

- https://central.sonatype.com/artifact/dev.langchain4j/langchain4j

然后用查到的版本号执行（把 `<VERSION>` 换成实际值）：

```powershell
mvn dependency:get -Dartifact=dev.langchain4j:langchain4j-core:<VERSION>
mvn dependency:get -Dartifact=dev.langchain4j:langchain4j:<VERSION>
```

**验证**：

```powershell
Get-ChildItem 'D:\java+python\maven1\dev\langchain4j' -Recurse -Filter *.jar | Select-Object -ExpandProperty Name
```

## 注意

- **不要在 DSH 会话里跑这些命令**：沙箱会拒绝写入 Maven 仓库目录，报「访问被拒绝」。
- 预拉只做一次。之后 `mvn -B -ntp -o clean package`（带 `-o`）就能离线用上它们。
- 每次新增依赖前，先确认本地已经有**真实的 jar**——本仓库里存在大量「目录在、jar 不在」的空壳（`maven-shade-plugin:3.6.2`、`org.junit.jupiter:*` 都踩过），只看目录名会被骗。
