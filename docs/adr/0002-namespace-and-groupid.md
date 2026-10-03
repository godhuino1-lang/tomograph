# ADR 0002 — 仓库位置与命名空间：为什么 groupId 和包名不一致

- 状态：已接受
- 日期：2026-10
- 决策者：项目作者

## 背景

原计划是新建一个名为 `tomograph` 的 GitHub **组织**，让 `io.github.tomograph` 这个 groupId 名副其实。

**该计划不成立。** 通过 GitHub API 实测（`GET /users/tomograph` 返回 200）：

```
login: tomograph   type: User   created_at: 2018-07-23
name:  Mette Bjerg Lindhøj   company: University of Copenhagen
public_repos: 3    updated_at: 2023-06-23
```

一个 2018 年注册的沉睡个人账号占用了这个名字，与观测领域毫无关系。它不能被申请，也不能被协商。

> 附注（值得留痕）：最初判定"该名字可用"的检查脚本写了 `catch { "FREE" }`。当时 pwsh 在该沙箱中**完全没有对外网络**，于是每一次请求都抛异常、每一次都打印 `FREE`——**"网络不通"和"名字可用"产生了完全相同的输出**。教训：任何"存在性 / 可用性"判断都不得让错误分支产出正面结论。

## 决策

| 项 | 取值 | 原因 |
|---|---|---|
| 仓库位置 | `github.com/Godhuino1-lang/tomograph` | 仓库名只需在 owner 内唯一，个人账号下完全可用，零额外设置 |
| Maven `groupId` | `io.github.godhuino1-lang` | 真实、可验证归属；Maven groupId **允许**连字符 |
| Java 包根 | `io.github.godhuino1.tomograph` | Java 包名段**不允许**连字符，只能去掉 `-lang` |
| GitHub 组织 | 暂不创建 | 组织名变体（如 `tomograph-dev`）会引入连字符，反而让包名更难看 |

## 后果

### 正面

- **归属真实**：将来发布 Maven Central 时，`io.github.godhuino1-lang` 的归属验证会成功（它确实对应作者拥有的账号）。原来的 `io.github.tomograph` 永远不会通过验证——那是一个我们并不拥有的账号。
- 零额外设置，零成本。
- **现在改的成本是 32 个文件、一次机械替换**；若等到 v1.0 再改，就是上百个文件加一次破坏性变更。

### 负面 / 需要接受的代价

- **groupId 与包名前缀不一致**：`io.github.godhuino1-lang` vs `io.github.godhuino1.tomograph`。这是连字符用户名的必然结果，不是疏忽。Maven 不校验二者一致，无功能影响，但确实是观感上的不整齐。
- 若将来想让二者一致，只有两条路：买一个域名（groupId 如 `dev.tomograph`），或注册一个不含连字符的组织名。**groupId 一旦随公开版本发布就不能改**（对使用者是破坏性变更），所以这个决定必须在 v0.1 首次公开发布之前拍板。

## 复现验证方式

```powershell
# 用 web_fetch（本沙箱中唯一能真正联网的通道）而不是 pwsh
# 200 + type:User  => 已被占用；404 => 可用
GET https://api.github.com/users/<name>
```

## 相关的工程教训（本次改名过程中实际踩到）

改名脚本按**扩展名**过滤文件（`.java/.md/.yml/.xml/.txt`），于是漏掉了没有扩展名的
`META-INF/services/...` 描述符——它的内容仍是旧类名。

**而构建依然是成功的**，因为 Maven 构建过程不运行 `ServiceLoader`，断链只在 agent 真正挂载时才暴露。是 agent 的冒烟测试（唯一真的跑 ServiceLoader 的检查）抓住了它。

教训：**"构建通过"不等于"行为完好"。** 对这类跨文件一致性检查，不要按扩展名过滤；并且必须保留一个真正执行运行时路径的验证步骤，而不是只依赖编译。
