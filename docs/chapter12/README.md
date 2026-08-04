# 文件读取与写入

本章技术基线：Starsector `0.98a-RC8`；前置知识：Java 工具链、`Global` 门面、MOD 目录结构和 JSON/CSV 基础。

本章围绕两类文件工作：从 MOD 资源空间读取文本、JSON 与 CSV，从存档 `common` 读取并保存跨存档数据。最后用 `strings.json` 和一个取值封装完成本地化机制，把静态文本、动态替换和可观察验证接在同一条流程上。

## 本章各节

<!-- AUTO:SECTION-LIST -->
