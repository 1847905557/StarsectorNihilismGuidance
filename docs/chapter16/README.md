# Rules 系统、命令与内存

本章技术基线：Starsector `0.98a-RC8`。

规则表先把数据装入 Rules 引擎，对话再用选项、分数和 fire 方法组织结果；原版生涯场景提供实际 trigger，自定义命令扩展脚本能力，Memory 保存运行状态。本章沿这条链路依次讲解 `rules.csv` 的结构与加载、Rules 对话编排、原版场景接入、rulecmd 命令，以及 `MemoryAPI` 与持久化。

## 本章各节

<!-- AUTO:SECTION-LIST -->
