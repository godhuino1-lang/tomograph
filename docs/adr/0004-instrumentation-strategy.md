# ADR 0004 — 插桩策略与 ASM 版本边界

- 状态：已接受（其中一项依赖一次联网预拉，见「后果」）
- 日期：2026-10
- 决策者：项目作者

## 背景

插桩引擎需要 ASM。但本地仓库（冻结、且沙箱不能联网）里各 ASM 构件的可用版本**不一致**：

| 构件 | 离线可用版本 | 关键类 |
|---|---|---|
| `asm` | 5.0.3 → **9.9.1** | `ClassReader` / `ClassWriter` / `MethodVisitor` |
| `asm-commons` | 5.0.3 → **9.7** | **`AdviceAdapter`**、`GeneratorAdapter` |
| `asm-tree` | 5.0.3 → **9.7** | `ClassNode` 等 |
| `asm-util` | 只有 **5.0.3 / 8.0** | `ASMifier`、`CheckClassAdapter`、`Textifier` |

这个不一致会致命，因为 ASM 的 `ClassReader` **拒绝**比自己更新的 class 文件。

## 实测（不是凭 release notes 推断）

用同一个源文件分别以 `--release 17 / 21 / 25 / 26` 编译，再用不同 ASM 版本尝试读取（探针见 `D:\github\.poc-agent\src\AsmVersionProbe.java`）：

| ASM | major 61 (Java 17) | major 65 (Java 21) | major 69 (Java 25) | major 70 (Java 26) |
|---|---|---|---|---|
| **9.7** | ✅ 可读 | ✅ 可读 | ❌ `Unsupported class file major version 69` | ❌ `Unsupported class file major version 70` |
| **9.9.1** | ✅ 可读 | ✅ 可读 | ✅ 可读 | ✅ 可读 |

关键细节：异常类型是 `IllegalArgumentException`，**从 `ClassReader` 构造函数抛出**——也就是发生在**宿主 JVM 的类加载过程中**。一个未捕获的异常在这里，等于"我想加观测"变成"我的服务起不来"，这正是本项目最不可接受的失败模式。

## 决策

1. **整个 ASM 家族统一钉在 `9.9.1`**：`asm`、`asm-commons`、`asm-util`、`asm-tree`。
   混用版本会引入一类只有在特定 class 文件版本上才暴露的、极难定位的故障。
2. 这需要**一次联网预拉**（`asm-commons:9.9.1` 与 `asm-util:9.9.1` 不在冻结仓库里）。命令见 [`docs/prefetch-list.md`](../prefetch-list.md)。
3. **把「版本拒绝」当成可容纳的逐类失败，而不是致命错误。** 引擎必须捕获它、记一次日志、返回原始字节码，让这个类照常加载。也就是说：*采不到某个类的数据*是可以接受的，*因为这个类让业务起不来*不可以。
4. 兼容矩阵新增一行：**可插桩的 class 文件版本为 61–70（Java 17–26）**；更新的版本一律跳过并告警。

## 后果

### 正面

- 能插桩 Java 17 到 26 编译的宿主应用，覆盖当前所有 LTS。
- `CheckClassAdapter`（asm-util）可以接进引擎自己的测试：**我们生成的字节码先过校验器，再交给 JVM**。这比等 JVM 抛 `VerifyError` 好得多，而且是这个项目最该有的那道测试。
- 边界是**测出来的数字**而不是印象，因此可以写进 CI 矩阵。

### 负面 / 需要接受的代价

- `asm-commons:9.9.1` 与 `asm-util:9.9.1` 必须先预拉；在那之前引擎只能对着混合版本开发（**未测试**）。
- **每出一个新 JDK，这道墙就会再挡一次**（JDK 27 的 major 71）。兼容矩阵必须跟着重新测量——这是一项**周期性的维护职责**，也正是"兼容矩阵就是护城河"那句话的具体含义。

## 若预拉不可行，备选方案（按优先级）

- **备选 A：`asm-core 9.9.1` + `asm-commons 9.7` 混用。** ASM 在 9.x 内保持二进制兼容，所以**很可能**能用，但**未经测试**。要用它，必须先写一个测试：用这组混合版本改写一个类，定义并执行它，确认通过校验。
- **备选 B：干脆不用 `asm-commons`，自己手写 `MethodVisitor`。** 工作量更大，但彻底消除混合版本风险，而且更贴合本项目"自己拥有字节码工作"的立场（`AdviceAdapter` 主要省的是构造器与 try-finally 的处理）。

**若采用备选 A 或 B，必须新开一份 ADR 记录实测结论**，不能只写在提交信息里。

## 相关

- 环境约束的完整清单见 [ARCHITECTURE.md](../../ARCHITECTURE.md) 的「本机与 CI 环境的硬约束」
- 切点选择（难题 1）属于另一份决策，见 ARCHITECTURE.md
