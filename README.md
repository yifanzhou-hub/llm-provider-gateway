# LLM Provider Gateway

应用开发者用**一条统一命令**调用不同模型服务：网关按配置选 Provider、把统一 `messages` 转成各家协议、再把结果和错误归一化成同一个结构。

> 课程项目，0.1.0。

- 仓库：https://github.com/yifanzhou-hub/llm-provider-gateway （public）
- CI：GitHub Actions，`mvn -B verify`，[run 45665508973（成功）](https://github.com/yifanzhou-hub/llm-provider-gateway/actions/runs/45665508973)
- 设计文档：[docs/DESIGN.docx](docs/DESIGN.docx)

## 用户、场景与范围

**谁**：写小工具、脚本或内部服务，需要接不止一家模型服务，又不想为每家 SDK 写一套适配的应用开发者。

**什么情况**：脚本里要发一条对话请求，换一家 Provider 就得改代码、改超时、改错误处理。

**做什么**：用同一条命令提交 `messages`，由网关完成校验、选路、超时控制、协议转换与错误归一化，返回统一结果。

```text
输入                    核心                     Provider               结果
messages + 可选参数  →  校验 → 选路 → 超时  →  mock（内置）        →  text / finishReason
--provider <id>         能力校验 → 归一化        completion（扩展）      usage / providerId
--json                                          → 本地模拟上游          latencyMs / requestId
                                                           成功 exit 0；失败 exit 3–7
```

**基础能力**：内置确定性 Mock Provider，不联网、可断言（同输入两次结果一致）。

**独立扩展**：`completion` 协议适配器——把统一 `messages` 折叠成单段 prompt，调用本地模拟的 `POST /v1/completions`，再把 `choices[0].text`/`finish_reason`/`usage` 映射回统一响应。它只在 `gateway-spi` 上编译，按 `plugins/*.jar` 目录接入，核心源码不改。

**本次不做什么**：HTTP 入口、流式（SSE）、多轮工具调用、插件热加载、多版本并行、真实模型 API 接入、计费、多租户、集群限流、复杂路由、管理后台、Docker/云部署。

## 快速开始

**环境**：JDK 17+、Maven 3.9+。CI 使用 temurin 17，本机实测 JDK 17 与 25 均可构建。

```powershell
# 在工程根执行
mvn -B -DskipTests package     # 编译 8 个模块
mvn -B verify                  # 单元 + 集成 + Checkstyle
mvn -B -pl gateway-core test -Dtest=ConfigLoaderTest   # 只跑配置加载
```


## 核心与扩展

| 模块 | 职责 | 依赖 |
| --- | --- | --- |
| `gateway-spi` | 统一数据模型、`LlmProvider` 接口、错误码、异常 | 无 |
| `gateway-core` | 配置、插件加载、注册表、调度、超时、归一化、重试 | spi |
| `gateway-cli` | 入口、参数、渲染、退出码 | core, spi |
| `gateway-provider-mock` | 内置 Mock（确定性回复 + 故障注入） | spi |
| `gateway-provider-completion` | 独立扩展：第二协议适配器 | spi（**禁止依赖 core**） |
| `gateway-provider-contract-test` | 契约测试基类 | spi, JUnit 5 |
| `mock-upstream-server` | 本地模拟上游（四模式） | JDK HttpServer |

**硬约束**：`gateway-provider-*` 的 pom 不出现 `gateway-core`；`gateway-spi` 不依赖任何实现模块。CI 与 Review 都查这条。

**扩展如何接入**：

1. 独立构建：`mvn -B -pl gateway-provider-completion -am -DskipTests package`
2. 放入插件目录：`Copy-Item gateway-provider-completion\target\gateway-provider-completion-0.1.0.jar plugins\`
3. 启动本地模拟上游：`java -jar lib\mock-upstream-server-0.1.0-all.jar --port 8099`
4. 重启网关 → `providers` 从 1 行变 2 行 → `chat --provider completion` 可用

**是否需要重新打包**：不需要。放入 JAR 后重启即生效，允许重启，不要求热加载。

**核心源码未改的核验方法**：

```powershell
git diff --stat base-w0..<接入提交> -- gateway-core/src/main gateway-spi/src/main
# 输出必须为空
```

## 测试与检查

```powershell
mvn -B verify
```

| 层次 | 位置 | 验证什么 |
| --- | --- | --- |
| 单元 | `gateway-core` / provider / cli | 配置校验、选路、映射、退出码 |
| 契约 | `gateway-provider-contract-test` | 任何 Provider 都必须满足的语义（info、统一响应、失败、超时预算） |
| 集成 | completion / cli（Failsafe） | 真起 HttpServer 的跨模块调用与失败路径 |
| 端到端 | cli 子进程 | 演示命令的文本、JSON 与退出码 |

**断言纪律**：不写 `assertNotNull` 或「返回非空」；断言字段值、错误码、退出码、条数；随机/时间字段只断言格式。

**故意错误实验**：把 `ConfigLoader` 里 `timeoutMs <= 0` 的判断改成 `< 0`，`ConfigLoaderTest.nonPositiveTimeout_isRejected` 立刻变红；`git checkout` 恢复后重跑，全部通过。复现命令：

```powershell
mvn -B -pl gateway-core -am test -Dtest=ConfigLoaderTest -Dsurefire.failIfNoSpecifiedTests=false
```

映射层还有一条同类实验：把 `finish_reason` 的映射改成 `"done"`，契约测试应立刻变红；改回 `"stop"` 后变绿。

**基础检查**：Checkstyle（4 空格 / 行宽 120 / 无通配 import / core+spi 禁 `System.out` / 类名=文件名 / 禁空 catch / 末尾换行 / 无 tab）。

**CI**：每次 push 与 PR 都跑 `mvn -B verify`，见仓库 Actions 页。

## 协作与贡献

- 分支：`feat/gw-<Ixx>-<短名>`、`fix/...`、`docs/...`；**main 禁止直接 push**（分支保护：必须 PR + 1 个 approval + `build` 检查通过）。
- PR：标题 `[Ixx] 一句话`；描述写三行——改了什么 / 怎么自测（贴命令与输出）/ 是否动契约。
- Review：至少 1 人，必须指出一处具体问题，禁止 LGTM；作者逐条回复「已改（SHA）/ 不改（理由）」。
- 合并：审查通过且 CI 绿后由作者合并。
- 已有记录：PR [#22](https://github.com/yifanzhou-hub/llm-provider-gateway/pull/22)（I02 配置加载），描述里贴了自测命令与结果；Issue 21 条按任务卡片建立。
- 平台不可用时：保留分支与提交，在 README 记录「作者 / 审查人 / 被审 Commit / 具体意见 / 作者处理 / 更新后 Commit / 结论」。

## 成员贡献与 AI 核验

> 提交前填写；未使用 AI 的写「未使用」。

| 姓名/学号 | 本人实质改动及 Commit/PR | 审查他人的改动及结果 | 关键决策与 AI 核验 |
| --- | --- | --- | --- |
| 待填 |  |  | 建议 → 是否采用 → 如何验证 → 结论 |
| 待填 |  |  |  |

## 许可与已知限制

- 项目许可证：**MIT**（2 人小组、7 天周期，Apache-2.0 的专利与 NOTICE 义务对本项目无增益）。全文见 [LICENSE](LICENSE)。
- 关键依赖及义务：picocli、Jackson、AssertJ（Apache-2.0，保留版权与许可声明）；logback（EPL-1.0 / LGPL-2.1，需保留声明，未修改源码）；JUnit（EPL-2.0）；Checkstyle（LGPL-2.1，仅构建期使用）。汇总见 `THIRD-PARTY-NOTICES.md`。
- 已知限制：
  - 不接真实模型 API，`completion` 只对本地模拟上游验证；
  - 不支持流式、热加载、多版本并行；
  - 超时是「Provider 自律 + 核心线程池硬兜底」，被取消的 Provider 线程可能短暂继续运行；
  - 不保证跨主版本的插件二进制兼容（`spiVersion` 主版本不同会被跳过并 WARN）。
