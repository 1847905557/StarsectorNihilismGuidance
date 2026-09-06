# Rules 系统、命令与内存

本章技术基线：Starsector `0.98a-RC8`。

规则表决定何时命中，Rules 命令决定命中后执行什么，内存决定结果如何保存，Rules 系统按这三步工作。本章沿这条主线依次讲解 `rules.csv` 七列语义与匹配、常用 trigger 场景、rulecmd 命令与 Token，以及 MemoryAPI 与持久化。

## 本章各节

<!-- AUTO:SECTION-LIST -->
