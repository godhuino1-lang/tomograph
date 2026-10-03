# ADR 0003 — OTLP 导出走零依赖自研，而不是引入 OpenTelemetry SDK

- 状态：已接受
- 日期：2026-10
- 决策者：项目作者

## 背景

最初批准的技术选型是：「遥测只依赖 `opentelemetry-api`，OTLP 编码自研」。

**这个前提不成立。** 实测本地仓库：

```
io/opentelemetry/ 存在，但 -Recurse 找到的 jar 数量 = 0
```

`opentelemetry-api`、`opentelemetry-context`、各种 exporter 目录都在，但**一个 jar 都没有**——和 `maven-shade-plugin:3.6.2`、`org.junit.jupiter:*` 是同一个陷阱：**目录存在不等于 jar 存在**。而开发沙箱没有网络，装不进来。

同时 SCOPE.md 已经定下两条硬约束：内核保持小体积；**插桩代码绝不把异常抛给宿主应用**。

## 决策

用 **OTLP/HTTP 的 JSON 编码** 自行实现导出，**只用 JDK**：

- HTTP：`java.net.http.HttpClient`（Java 11 起自带）
- 序列化：手写 JSON 字符串转义（`OtlpJson`）
- 不引入 OpenTelemetry SDK/API、protobuf、Jackson、任何第三方 HTTP 客户端

## 理由

1. **OTLP/HTTP 本来就有 JSON 编码，且是规范的一部分。** 加上 JDK 自带 HTTP 客户端，这个需求用**零第三方代码**即可完整覆盖。引 SDK 不是需求，只是习惯。
2. **agent 是注入到别人应用里的。** 一个 shaded 进 agent jar 的 Jackson 或 protobuf，会和宿主自带的版本打架——而"引入观测工具导致业务启动失败"是本项目最不可接受的失败模式。零依赖 = 零冲突。
3. **少一条版本追赶链。** OpenTelemetry SDK 版本迭代很快，跟它意味着长期维护成本；而我们真正需要的那一小部分（`gen_ai.*` 的键名）已经在 `tomograph-semconv` 里自己掌握了。
4. 符合 SCOPE.md 的「内核保持小体积、不依赖任何云厂商 SDK」。

## 后果

### 正面

- agent jar 目前 24 KB，加上导出器也只增长约 15 KB 量级。对比：一个 shaded OTel SDK + Jackson 通常是数 MB。
- **永远不会与宿主产生依赖冲突。**
- 我们掌握了协议细节，不再被上游 SDK 的抽象层遮蔽。

### 负面 / 必须付出的代价

- **编码正确性由我们自己负责。** proto3 的 JSON 映射有几个反直觉规则，写错的后果是**静默失败**——span 被后端直接丢弃，或者时间戳显示成 1970 年，而不是报错。已在 `OtlpPayloadBuilder` 的类注释里逐条列出：
  - `int64` 必须写成 JSON **字符串**
  - `traceId` / `spanId` 用 **十六进制**，不是 base64（proto3 里其他 `bytes` 字段才是 base64）
  - `intValue` 同理要加引号
  - span kind 是**数字**（1=INTERNAL，3=CLIENT）
  - `status.code` 是 0/1/2，UNSET 是 0，必须显式输出
- **这些规则没有对规范逐条核对过**：`opentelemetry.io` 的表格是前端渲染的（文本抓取只能拿到导航），`raw.githubusercontent.com` 被 DNS 屏蔽。所以用**黄金测试**（`OtlpPayloadBuilderTest` 里那份完整 payload 等值断言）锁死了当前实现，并在 ROADMAP 记成待办。修正时会是可见的 diff。

### 有意不做的事

- **不做重试，不做持久化重试队列。** 二者都会在"宿主本身已经出问题"的场景下造成内存无界增长。后端跟不上实时流量是后端的问题，应该表现为**可观测的丢 span 计数**，而不是被一个 OOM 掩盖。
- **不调用 `HttpClient.close()`。** 它 Java 21 才出现，而本模块的编译目标是 Java 17。

## 何时推翻这个决策

如果出现以下情况，重新评估（可能需要引入 protobuf 传输）：

1. 目标后端只接受 OTLP/gRPC，不接受 OTLP/HTTP
2. 吞吐高到 JSON 编码成为瓶颈（JSON 比 protobuf 大约 2-3 倍体积）
3. 我们需要 OTel 生态里某个仅以 SDK 形式提供的能力（例如复杂的采样器）
