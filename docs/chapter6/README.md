# Java 工程与全局服务

本章技术基线：Starsector `0.98a-RC8`。

本章把 Java 代码从 "游戏启动时即时编译源码" 切换到 "先编译成 JAR 再交给游戏加载"，建立 Java 17 + Gradle 工具链并补全 ModPlugin 生命周期回调。 随后借助 Global 静态访问器与 Settings、Logger、SoundPlayer 服务组织全局初始化，为生涯与战斗代码提供统一入口。

## 本章各节

<!-- AUTO:SECTION-LIST -->
