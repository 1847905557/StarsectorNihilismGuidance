# 配置 Gradle 工程

本节配置 `settings.gradle` 和 `build.gradle`，让 Gradle 从 `src/` 编译 Java 17 源码，并把游戏核心 JAR 作为编译期依赖。

*本节预期阅读时间：约 10 分钟。*

*本节预期操作时间：约 15 分钟。*

## 学习目标

完成后，您将能够：

- 配置工程名、源码目录和 Java toolchain；
- 声明 `compileOnly` 核心依赖；
- 设置 UTF-8、`--release 17` 和 JAR 输出目录。

## 前置知识

- [C6-S1] 的 Java 17 与编译依赖；
- [C6-S2] 的 `src/` 源码布局；
- Gradle 基本语法。

## 设置工程名

`settings.gradle` 只声明 MOD 工程名：

```groovy
rootProject.name = "gear_in"
```

## 配置源码和依赖

`sourceSets.main.java.srcDirs = ["src"]` 让 Gradle 读取 [C6-S2] 迁移后的文件。`compileOnly fileTree(...)` 扫描 `starsector-core/*.jar`，这些依赖只参与编译。

```groovy
plugins {
    id "java"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

def gameRoot = file("Starsector")

sourceSets {
    main {
        java {
            srcDirs = ["src"]
        }
    }
}

dependencies {
    compileOnly fileTree(dir: "${gameRoot}/starsector-core", include: ["*.jar"])
}
```

## 固定编译目标

在所有 JavaCompile 任务上设置 UTF-8 和 `options.release = 17`。toolchain 选择 JDK 17，`release` 固定 class 文件目标；两项都必须保留。

```groovy
tasks.withType(JavaCompile).configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}
```

## 设置 JAR 输出

`jar` 任务把编译结果命名为 `GearIndustry.jar` 并输出到 MOD 的 `jars/`。下一节会把同一路径写入 `mod_info.json`。

```groovy
tasks.named("jar") {
    archiveFileName = "GearIndustry.jar"
    destinationDirectory = file("jars")
}
```

## 验证方案

前置数据：[C6-S2] 的源码迁移已完成，本节的 `build.gradle` 片段已写入工程。

1. 检查 `settings.gradle` 的工程名；
2. 检查 `srcDirs`、toolchain、`compileOnly` 和 `options.release`；
3. 检查 JAR 输出目录和文件名；
4. 进入 [C6-S4] 执行构建和 manifest 声明。

预期结果：

- Gradle 从 `src/` 读取源码；
- 工程使用 Java 17 编译；
- JAR 输出位置为 `jars/GearIndustry.jar`。

## 故障分析

- **没有发现 Java 文件**：`srcDirs` 仍指向旧目录。
    - **解决方案**：改为 `["src"]`。

- **编译目标高于 17**：缺少 `options.release = 17`。
    - **解决方案**：补齐并重新构建。

- **依赖被打包**：使用了 `implementation`。
    - **解决方案**：改为 `compileOnly`。

## 总结

Gradle 通过 `srcDirs` 找到迁移后的源码，通过 Java 17 toolchain 和 `--release 17` 固定字节码目标，并把 JAR 写入 `jars/`。

## 建议参考项

- `Starsector/starsector-core/*.jar`：`compileOnly` 声明的原版核心编译依赖。
