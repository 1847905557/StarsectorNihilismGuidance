# 建立 Gradle 工程

到目前为止，MOD 里的 Java 代码都由游戏在启动时自己编译。这免去了安装开发环境的麻烦，代价是只能用一部分 Java 语法，写错了要进游戏才知道，编辑器也无从提示游戏 API 有哪些方法。

一个真正的 Java 工程能解决这些问题。工程搭得规整，此后每一章都会省事。

---

_本节预期阅读时间：\~10 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- Java 的类、接口、继承、集合与泛型；
- [C1-S1] 的 MOD 目录与 `mod_info.json`；
- 能够在终端中进入目录并执行命令。

## 准备 JDK 17 与 IDE

游戏自带的 Java 运行时是 17，所以工程的编译目标也是 17。安装一份 JDK 17，在终端确认：

```text
java -version
javac -version
```

两条命令都应报告 17。本机装了多个 JDK 时，终端、IDE 和 Gradle 可能各自用不同的一份，**以三者实际报告的版本为准，不以安装了什么为准。**

编者推荐 `IntelliJ IDEA`，社区版足够。它直接识别 Gradle 工程，导入后自动按 `build.gradle` 配置源码目录与依赖，不需要在 IDE 里另外设置 classpath。Gradle 本身也需要单独安装，在终端执行 `gradle --version` 能看到版本号即可。

::: tip 游戏自带的编译器

游戏用 Janino 编译 `data/` 下的 `.java` 文件，它不支持 `<>` 钻石语法、lambda 表达式等较新的写法。前几章的代码都避开了这些写法，所以能在两种方式下编译。从第七章起，示例会使用完整的 Java 17 语法，它们只能通过本章的工程编译。

:::

## 规划工程目录

**工程根目录就是 MOD 根目录。** 构建文件、源码和产物都放在 `GearIndustry/` 里，与 `data/`、`graphics/` 并列：

```text
GearIndustry/
├── build.gradle        构建配置
├── settings.gradle     工程名
├── gradle.properties   本机游戏路径
├── src/                Java 源码，下一节迁入
├── jars/               构建产物，游戏从这里加载
├── data/               游戏数据，原样保留
├── graphics/
└── mod_info.json
```

工程与 MOD 共用一个目录，因为构建产物要放进 MOD 才能被游戏读取。工程放在别处，每次构建后都要把 JAR 复制过来，忘了复制，游戏就在用旧代码。共用目录的代价是发布时要挑出 `build/`、`src/` 与几个构建文件，清单见 [C6-S2]。

## 填写本机游戏路径

在 `gradle.properties` 中写入游戏根目录，也就是包含 `starsector-core` 的那一层：

```properties
starsectorRoot=C:/Games/Starsector
```

请换成您的实际路径，Windows 下同样用 `/`。

**本机路径单独放进 `gradle.properties`，构建脚本里不写死任何路径。** 别人拿到这份工程，只需要改这一行；把路径写进 `build.gradle`，每个人都得改构建脚本，改完再提交，又会覆盖下一个人的路径。

## 编写构建脚本

在 `settings.gradle` 中写入工程名：

```groovy
rootProject.name = "GearIndustry"
```

在 `build.gradle` 中写入完整配置：

```groovy
plugins {
    id "java"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

def gameRoot = file(providers.gradleProperty("starsectorRoot").get())

sourceSets {
    main {
        java {
            srcDirs = ["src"]
        }
        resources {
            srcDirs = []
        }
    }
}

dependencies {
    compileOnly fileTree(dir: "${gameRoot}/starsector-core", include: ["*.jar"])
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.named("jar") {
    archiveFileName = "GearIndustry.jar"
    destinationDirectory = file("jars")
}
```

各段的职责如下：

| 段落                  | 职责                                                            |
| --------------------- | --------------------------------------------------------------- |
| `plugins`             | 启用 Java 插件，提供 `compileJava`、`jar` 等任务                |
| `java.toolchain`      | 指定用 JDK 17 编译                                              |
| `gameRoot`            | 从 `gradle.properties` 读取游戏路径                             |
| `sourceSets`          | 源码在 `src/`；资源目录置空，因为游戏数据不经过构建             |
| `dependencies`        | 游戏核心 JAR 只参与编译                                         |
| `JavaCompile`         | 源码按 UTF-8 读取，产物目标为 Java 17                           |
| `jar`                 | 产物输出到 `jars/GearIndustry.jar`                              |

**游戏核心 JAR 用 `compileOnly`，不打进产物。** 编译时需要知道 `ShipAPI` 的方法签名，运行时这些类由游戏提供。改用 `implementation` 并配合合并依赖的打包方式，游戏类会被复制进 MOD 的 JAR，与游戏本体的同名类冲突。

**toolchain 选择编译器，`release` 选择产物版本，两者都写 17。** 只写 toolchain，换一台装着更高版本 JDK 的电脑，产物照样是 17；只写 `release`，Gradle 会用它找到的任意 JDK。产物版本高于游戏的运行时，构建能成功，游戏启动时才报 `UnsupportedClassVersionError`。

`encoding` 固定为 UTF-8，是因为源码里有中文注释与字符串。不写这一行，Windows 下的编译器按系统默认的 GBK 读取，中文注释会报错，字符串则会变成乱码进入游戏。

Gradle 找不到 JDK 17 时，在 `gradle.properties` 中补一行 JDK 的安装目录：

```properties
org.gradle.java.installations.paths=C:/Program Files/Java/jdk-17
```

### 进 IDE 核对

保存三个文件，在 MOD 根目录执行：

```text
gradle compileJava
```

此时 `src/` 还是空的，任务报告 `NO-SOURCE` 并以 `BUILD SUCCESSFUL` 结束，这是正常的。

然后用 IntelliJ IDEA 打开 MOD 根目录，等待它导入 Gradle 工程：

- 项目视图中，`External Libraries` 下出现 `starfarer.api.jar` 等游戏 JAR；
- 新建一个临时类，输入 `Global.getSettings()`，IDE 能补全并跳转到 `SettingsAPI`。

核对完把临时类删掉。

::: tip 阅读 API 源码

在 IDEA 中跳转到游戏类时，默认看到的是反编译结果。给 `starfarer.api.jar` 附加 `starsector-core/starfarer.api.zip` 作为源码，就能直接看到真实参数名与 `impl/` 下的原版实现。

看原版实现怎么写。方法上面的注释一定程度上可以参考，但不总是准确。编者也无法解释到底哪里出了问题，因为特例、特例的特例、特例的特例的特例多到这里写不下。

:::

## 常见问题

- **执行 `gradle` 时报找不到命令**：Gradle 没有安装，或安装目录不在 PATH 中。
    - **解决方案**：配置 PATH 后重新打开终端，执行 `gradle --version` 确认。

- **报 `Cannot query the value of Gradle property 'starsectorRoot'`**：`gradle.properties` 中没有这一项，或命令不是在 MOD 根目录执行的。
    - **解决方案**：补上 `starsectorRoot`，回到 MOD 根目录重新执行。

- **报找不到 Java 17 工具链**：Gradle 没有自动发现 JDK 17。
    - **解决方案**：在 `gradle.properties` 中填写 `org.gradle.java.installations.paths`。

- **IDE 里游戏类全部标红**：`starsectorRoot` 不是包含 `starsector-core` 的那一层，或 IDE 没有重新导入工程。
    - **解决方案**：核对路径，在 IDEA 的 Gradle 面板中点击重新加载。

## 建议参考项

- `starsector-core/*.jar`：编译依赖。
- `starsector-core/starfarer.api.zip`：原版 API 与 `impl` 实现源码。
