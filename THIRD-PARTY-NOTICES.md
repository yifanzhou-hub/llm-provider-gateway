# 第三方依赖与许可声明

本项目以 MIT 许可发布（见 `LICENSE`）。以下依赖未做任何修改，按其原始许可分发；本文件保留其来源与义务说明。

| 依赖 | 版本 | 许可证 | 用途 | 来源 |
| --- | --- | --- | --- | --- |
| picocli | 4.6.2 | Apache-2.0 | CLI 子命令与退出码 | https://github.com/remkop/picocli |
| Jackson（databind / core / annotations / dataformat-yaml） | 2.17.1 | Apache-2.0 | JSON/YAML 解析与序列化 | https://github.com/FasterXML/jackson |
| SnakeYAML | 2.x（Jackson 传递依赖） | Apache-2.0 | YAML 底层解析 | https://bitbucket.org/snakeyaml/snakeyaml |
| SLF4J API | 2.0.9 | MIT | 日志门面 | https://www.slf4j.org/ |
| Logback（classic / core） | 1.4.5 | EPL-1.0 或 LGPL-2.1 | 日志实现，输出到 stderr | https://logback.qos.ch/ |
| JUnit Jupiter | 5.10.2 | EPL-2.0 | 单元测试（不随发行物分发） | https://junit.org/junit5/ |
| AssertJ | 3.23.1 | Apache-2.0 | 断言（不随发行物分发） | https://assertj.github.io/doc/ |
| Checkstyle | 10.x（构建期） | LGPL-2.1 | 静态检查（仅构建期使用，不随发行物分发） | https://checkstyle.org/ |

## 义务说明

- **Apache-2.0**：保留版权声明、许可文本与 NOTICE（若有）；修改过的文件需标注。本项目未修改上述依赖。
- **MIT（SLF4J）**：保留版权与许可声明。
- **EPL / LGPL（Logback）**：以未修改的二进制形式随发行物分发，保留其许可声明；如需替换为其他实现，不修改本项目源码即可完成。
- **EPL-2.0 / LGPL-2.1（JUnit、Checkstyle）**：仅测试与构建期使用，不进入交付的发行物。

发行 ZIP 中随 `lib/` 一并分发上述运行时依赖，许可证全文见各依赖制品内的 `META-INF` 或项目主页。
