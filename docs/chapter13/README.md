# 常用依赖库

本章技术基线：Starsector `0.98a-RC8`；前置依赖：LazyLib `3.0.0`、GraphicsLib `1.10.0`、MagicLib `1.5.1`、BoxUtil `1.5.4`。

本章把常用依赖库逐一接入教程工程：先在 `mod_info.json` 中声明硬依赖，让启动器在缺库时直接给出提示；随后依次使用 LazyLib 的数学与 JSON 工具、GraphicsLib 的光源与纹理、MagicLib 的设置与 HUD 及其尾迹系统，以及 BoxUtil 的渲染实体。每个库接入后，都能从日志或画面确认调用确实发生。非强制性依赖 (LunaLib、Nexerelin 等) 的处理在第十四章。

## 本章各节

<!-- AUTO:SECTION-LIST -->
