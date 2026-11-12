# Agent Intro

项目入口索引。先读这里，再按任务读取 `.zcode/` 中的现行约束。

## 读取顺序

1. `AGENTS.md`
2. `.zcode/workflow.md`
3. `.zcode/overview.md`
4. 按 `.zcode/workflow.md` 读取任务涉及的 guideline 和 `.zcode/terminology.md`。

## 绝对性原则

- 在得到用户直接要求前，禁止修改任何文件。
- 修改后，如需同步相关文档，必须询问用户授权。
- 意外出现的修改必须视为用户内容并完整保留，禁止回退。
- `.zcode/` 目录内含各类文档，`.zcode/plans/` 目录下的各子目录是允许按任务自行创建的临时工作区。
- 用户要求可能违反文档或规范时，必须先询问确认；授权不明确时必须拒绝。

## 编码规范

- 所有文本文件都必须以 UTF-8 无 BOM 编码读取和写入。
- `Get-Content` 必须带 `-Encoding utf8` 参数。
