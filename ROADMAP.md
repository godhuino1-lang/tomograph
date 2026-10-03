# ROADMAP

时间线按「研一 2026 秋入学 → 研二下 2028 春投实习」推算。2027 年暑假是唯一能整块投入的窗口，**开源首发必须卡在那里**，这样到 2028 年 3 月投递时项目已有 8 个月公开历史。

## 里程碑

| 时间 | 版本 | 目标 | 完成标准（Definition of Done） | 状态 |
|---|---|---|---|---|
| 2026.10–12 | — | 补课 + PoC | 能自己写出「不改业务代码打印任意方法耗时入参」的 agent | 🟡 进行中 |
| 2027.01–03 | `v0.1` | LangChain4j 首个切点 + OTLP | 挂上 agent 能看到完整的模型调用树并导入 Jaeger | ⬜ |
| 2027.04–06 | `v0.2` | 工具/检索/成本切点 + 离线报告 | 单文件 HTML 报告可离线打开；CI 矩阵 JDK 17/21/25 全绿 | ⬜ |
| **2027.07–08** | **`v0.3` 开源首发** | Spring AI 适配 + JMH 基准 | GitHub 公开仓库 + `v0.1.0` release + 英文 README + 开销数字进 README | ⬜ |
| 2027.09–12 | `v1.0` | 稳定 API + 录制重放内核 | 能把一次真实运行确定性重放并 diff | ⬜ |
| 2028.01–03 | — | 冲实习 | 简历 2 条量化 bullet + 30 分钟深挖问答准备完毕 | ⬜ |
| 2028.03–06 | — | 投实习 | 此时项目已 1.5 年持续提交、有 release、有真实用户 | ⬜ |
| 2028.07+ | — | 实习 / 秋招 | 继续长：MCP 工具治理、成本与配额治理 | ⬜ |

## v0.1 验收标准（可量化 = 简历上的数字来源）

- [ ] 业务代码 **0 改动**，只加 `-javaagent` 参数即生效
- [ ] 采集 **≥5 类语义切点**：Agent 轮次 / LLM 调用 / 工具调用（含副作用）/ 检索 / 嵌入
- [ ] token 用量与成本**与 provider 返回值逐条对齐**（不是估算）
- [ ] **P99 插桩开销 < 2%**（JMH 实测，可复现）
- [ ] **JDK 17 / 21 / 25 × Linux / Windows CI 全绿**
- [ ] 单文件离线 HTML 报告可打开

## 目标简历 bullet（预先写下来，作为北极星）

> 基于 `java.lang.instrument` + 自研 ASM 插桩，构建 JVM 上 LLM Agent 应用的零侵入可观测框架，覆盖 LangChain4j / Spring AI 双框架 5 类语义切点；P99 插桩开销 <2%（JMH 实测），支持 JDK 17–25 兼容矩阵。

> 设计并实现 Agent 运行「录制–重放」内核，支持确定性回放、轨迹 diff 与故障注入，将线上 Agent 故障定位从人工翻日志变为可复现的本地回放。

## 待办：仓库正式化（开源首发前必做）

- [ ] 通过 GitHub 模板添加 `LICENSE`（Apache-2.0）
- [ ] 补英文 `README.md`，中文移到 `README.zh-CN.md`
- [ ] 加 CI / 版本 / 覆盖率徽章
- [ ] `v0.1.0` tag + GitHub Release，附 `tomograph-agent.jar`
- [ ] 写一篇「为什么 Java 生态需要一个 Agent 观测层」的发布文章

## 已知技术债（明确记账，不假装不存在）

| 债 | 何时还 |
|---|---|
| `SpanCollector` 用 ThreadLocal，虚拟线程/异步下会失效 | v1.0 前必须解决（难题 2） |
| agent 自身类加载未做严格隔离 | v0.2 |
| JUnit 5 与 surefire 已接入并可离线运行；仍缺覆盖率门禁与 CI 测试报告 | v0.1 |
| Maven Wrapper 未引入，依赖本机 Maven 3.9+ | v0.1 |
| ~~`GenAiAttributes` 属性名未核对~~ **已核对并修正**：对着 `semantic-conventions-genai` 的 `model/gen-ai/registry.yaml`（commit `e07f4eb`）逐条比对。修正了 `gen_ai.token.type`（规范已删除，现为 `gen_ai.token.modality`）、标明 `gen_ai.system` 已从注册表移除；补上了缓存 token 拆分、工具调用参数与结果、检索、模态、plan/invoke_workflow 等漏掉的键。核对目标与结论记录在 `SemconvRevision` | 已还清 |
| ~~OTLP/JSON 的编码细节未核对~~ **已核对**：对着 OTLP proto **v1.11.1**（commit `b3f7558`）的 `docs/specification.md` 逐条验证，**五个假设全部确认正确**（十六进制 id 而非 base64；int64 与时间戳写成字符串；enum 必须用整数且名字 "MUST NOT be used"；`intValue` 加引号；`status.code` 0/1/2）。另记录两处刻意偏离规范 SHOULD 的地方：不重试 429/502/503/504；不用 gzip。核对目标记录在 `OtlpSpecRevision` | 已还清 |
| ~~动态 attach（agentmain）路径未验证~~ **已由 CI 覆盖**：`DynamicAttachTest` 真起 JVM → attach → 加载 agent → 断言目标 JVM 日志；另有一个 CI 步骤强制它在 Linux 上**必须执行**（被跳过即构建失败） | 已还清 |
