# 接入常用依赖库

接入第三方前置库时，能过编译并不代表能进游戏运行：Gradle 找到外部 JAR 只保证编得出包，玩家启动器里漏勾一项依赖，游戏启动时照样会因缺类而报错。

先把 LazyLib、GraphicsLib 和 MagicLib 写进清单与编译路径，再确认各自的初始化时机 —— 后面所有外库功能都要从这套入口往外接。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C1-S1] 的 MOD 目录与 `mod_info.json`；
- [C6-S1] 的 Gradle 工程、`compileOnly` 和 JAR 输出；
- [C7-S1] 的 `BaseModPlugin` 与 `onApplicationLoad()`。

## 声明启动器依赖

在 GearIn 的 `mod_info.json` 中加入 `dependencies`，启动器按依赖 MOD 的 `id` 检索对应条目：

```json
"dependencies": [
  {"id":"lw_lazylib","name":"LazyLib"},
  {"id":"shaderLib","name":"GraphicsLib"},
  {"id":"MagicLib","name":"MagicLib"}
]
```

`id` 决定启动器去哪个 manifest 匹配目标 MOD，`name` 只管在缺少依赖的提示弹窗里显示给玩家看的名称。玩家勾选 GearIn 时，启动器会联动启用这三项；只要其中一项缺失或未安装，GearIn 就会在列表里被锁死无法启用。

### 谨慎填写 version 字段

依赖项还可以附带 `version`，例如：

```json
{"id":"lw_lazylib","name":"LazyLib","version":"3.0.0"}
```

启动器会把版本号拆成 major、minor 和 patch 逐级比对。major 不同会直接阻断勾选，minor 或 patch 不同则按对应兼容级别提示，但仍允许选中。

::: warning version 比的是精确段位而非最低兼容下限

`"version":"3.0.0"` 表达的是与 `3.0.0` 做版本段比较，高版本同样可能触发不匹配提示或阻断。哪怕只是多了 0.0.1，您的勾选界面也会出现一抹令人不快的刺目黄色。

由于这很显然根本不是人能设计出来的，编者强烈建议您总是省略 `version`，只靠稳定 ID 确认依赖存在。

:::

## 建立编译 classpath

打开 [C6-S1] 的 `build.gradle`，沿用 `gradle.properties` 中的 `starsectorRoot`，从游戏根目录定位 `mods` 下的依赖目录：

```groovy
def gameRoot = file(providers.gradleProperty("starsectorRoot").get())
def modsDir = file("${gameRoot}/mods")

def dependencyJarDirs = [
    "${modsDir}/LazyLib/jars",
    "${modsDir}/LazyLib/jars/internal",
    "${modsDir}/GraphicsLib/jars",
    "${modsDir}/MagicLib/jars",
]
```

LazyLib 除了主 `jars` 目录之外，还把 Kotlin 运行时与控制台相关包放在 `jars/internal`；GraphicsLib 与 MagicLib 则只需引入各自的 `jars` 目录。路径数组定好后，一起交给 `compileOnly`：

```groovy
dependencies {
    compileOnly fileTree(dir: "${gameRoot}/starsector-core", include: ["*.jar"])
    dependencyJarDirs.each { dir ->
        compileOnly fileTree(dir: dir, include: ["*.jar"])
    }
}
```

第一条 `compileOnly` 保留原版核心 JAR，循环把依赖库的 JAR 一起放进编译期 classpath。顺手补一个查看任务，构建排错时会很直观：

```groovy
tasks.register("printCompileClasspath") {
    doLast {
        sourceSets.main.compileClasspath.files.each { println it }
    }
}
```

执行 `gradle printCompileClasspath`，就能直接看到编译器实际拿到的完整 JAR 列表。

`compileOnly` 只在编译期生效，打包 `GearIndustry.jar` 时绝不能把外库类一起打进去 —— 运行时由各前置库自己的 MOD 目录加载。另外，从 `starsectorRoot` 推导 `modsDir` 可以让工程放到任意独立目录编译；如果偷懒写 `rootDir.parentFile`，工程一旦挪出 `Starsector/mods/GearIndustry` 就会当场失灵。依赖数组里的目录名，按本机实际安装的目录名填写即可。

## 编译配置与运行时初始化的边界

接通这两步之后，代码里就可以正常导入并调用 `org.lazywizard.lazylib.MathUtils`。**编译通过与依赖 MOD 已在游戏里加载，是各自独立的两层配置：**

| 层次           | 配置入口                     | 直接结果                               |
| -------------- | ---------------------------- | -------------------------------------- |
| 启动器依赖     | `mod_info.json.dependencies` | 查找并联动启用前置 MOD                 |
| 编译 classpath | Gradle `compileOnly`         | 编译器能够解析依赖库类型               |
| 运行时初始化   | 依赖库入口与使用方入口       | 建立库的服务、shader 注册表或数据加载 |

`compileOnly` 管不着游戏启动器，`dependencies` 也替不了 Gradle 找 JAR，两份清单各守一边。

### 三个库的运行入口分工

启动器校验过依赖之后，游戏会按各自的 `mod_info.json` 先后拉起前置库本身的 JAR 和入口插件；GearIn 只需在自己的时机里调用公开入口或挂载数据文件：

- **LazyLib**：运行环境由 `org.lazywizard.lazylib.LazyLib` 自行建立，使用方编译通过即可在战斗或生涯代码里直接调 `MathUtils`、`AIUtils` 等工具类；
- **MagicLib**：由 `org.magiclib.Magic_modPlugin` 接管自身初始化和约定的赏金/尾迹路径，使用方直接调 API 或把数据文件放进规定路径即可；
- **GraphicsLib**：`org.dark.shaders.ShaderModPlugin` 在加载阶段会调用 `ShaderLib.init()`。`ShaderLib.init()` 开头带有已初始化守卫，重复调用会直接返回；在自己的 `onApplicationLoad()` 里显式调一次 `ShaderLib.init()`，再检查硬件支持与加载光效/贴图 CSV，就能避免受模组插件加载顺序影响。

## 选择渲染依赖

::: tip GraphicsLib 与 BoxUtil 的职责

GraphicsLib 负责全屏 shader、动态光源以及 material / normal 贴图管线；若需要创建自定义渲染实体、批量提交 Sprite 贴图或自管渲染队列，可结合 [C20-S6] 的 BoxUtil 选型。

:::

## 完整的配置样本

将三项依赖补进 [C1-S1] 的 `mod_info.json`：

```json
{
  "id":"GearIndustry",
  "name":"Gear Industry",
  "version":"1.0.0",
  "gameVersion":"0.98.5a-RC3",
  "dependencies":[
    {"id":"lw_lazylib","name":"LazyLib"},
    {"id":"shaderLib","name":"GraphicsLib"},
    {"id":"MagicLib","name":"MagicLib"}
  ],
  "jars":["jars/GearIndustry.jar"],
  "modPlugin":"data.scripts.gearin.GearInModPlugin"
}
```

`build.gradle` 沿用 [C6-S1] 的 Java 17 工具链与产物配置，把依赖库目录并入 `dependencies`：

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
def modsDir = file("${gameRoot}/mods")

def dependencyJarDirs = [
    "${modsDir}/LazyLib/jars",
    "${modsDir}/LazyLib/jars/internal",
    "${modsDir}/GraphicsLib/jars",
    "${modsDir}/MagicLib/jars",
]

sourceSets {
    main {
        java {
            srcDirs = ["src"]
        }
    }
}

dependencies {
    compileOnly fileTree(dir: "${gameRoot}/starsector-core", include: ["*.jar"])
    dependencyJarDirs.each { dir ->
        compileOnly fileTree(dir: dir, include: ["*.jar"])
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.named("jar") {
    archiveFileName = "GearIndustry.jar"
    destinationDirectory = file("jars")
}

tasks.register("printCompileClasspath") {
    doLast {
        sourceSets.main.compileClasspath.files.each { println it }
    }
}
```

### 进游戏核对

先按上述配置写好 `mod_info.json` 与 `build.gradle` (前提是本机 `Starsector/mods/` 下已安装 LazyLib、GraphicsLib 与 MagicLib)：

- 执行 `gradle printCompileClasspath`，输出列表同时包含核心 API 与三库对应目录的 JAR；
- 执行 `gradle jar`，再用 `jar tf jars/GearIndustry.jar` 检查压缩包，里面只有 GearIn 自己的 `.class`，没有混入三方库包体；
- 打开游戏启动器并勾选 GearIn，三项依赖同步联动启用；故意取消勾选其中任意一项后再勾 GearIn，会看到对应的缺库提示；
- 恢复三项依赖启动进入主菜单，查看 `starsector.log` 确认 GearIn 与三库全部正常加载。

## 常见问题

- **启动器找不到依赖**：`mod_info.json` 中声明的 `id` 与对应库 manifest 不一致 (注意 LazyLib 是 `lw_lazylib`，GraphicsLib 是 `shaderLib`)，或对应 MOD 未放进 `Starsector/mods/`。
    - **解决方案**：按三库 `mod_info.json` 恢复精确 ID 与目录位置。

- **启动器阻止选择带版本的依赖**：填写的 `version` 与本机已安装版本发生 major 不匹配。
    - **解决方案**：确认接口兼容后修正具体版本号，或像本节一样省略 `version` 字段。

- **Gradle 报错找不到外部库类或目录**：`starsectorRoot` 未指向真实的 Starsector 安装根目录，或本机 MOD 文件夹名与 `dependencyJarDirs` 不符。
    - **解决方案**：跑一次 `gradle printCompileClasspath` 对照输出路径修正。

- **编译通过但进游戏抛出 `NoClassDefFoundError`**：Gradle 已加入 JAR，但 `mod_info.json` 漏掉了运行时 `dependencies`。
    - **解决方案**：补全 `dependencies` 中的对应 ID 并在启动器勾选启用。

- **`GearIndustry.jar` 体积暴涨并打进了外库类**：打包配置错误地把依赖 JAR 解包合并进了输出。
    - **解决方案**：保留 `compileOnly`，删掉额外合并依赖的配置后重新执行 `gradle jar`，用 `jar tf` 复查产物。

- **GraphicsLib 类可调用，但光效或贴图没有生效**：只接通了包依赖，尚未在 `onApplicationLoad()` 调用 `ShaderLib.init()` 或登记光效/贴图 CSV。
    - **解决方案**：按 [C18-S5] 至 [C18-S7] 补齐初始化顺序与 CSV 读取。

## 建议参考项

- `mod_info.json`：`dependencies` 的 ID、名称与可选版本；
- `Gradle compileOnly`：只进入编译 classpath 的依赖声明；
- `Starsector/mods/LazyLib/mod_info.json`：`lw_lazylib` ID 与 `jars`、`jars/internal` 目录；
- `Starsector/mods/GraphicsLib/mod_info.json`：`shaderLib` ID 与 `ShaderModPlugin` / `ShaderLib` 入口；
- `Starsector/mods/MagicLib/mod_info.json`：`MagicLib` ID 与运行时入口。
