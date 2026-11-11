# 教程验证工程

本工程以 Java 17 与本地 Starsector 安装的 0.98.5a-RC3 API 编译教程完整示例。每个工作单元只拥有 `units/Cxx-Syy/**`，编号使用不带前导零的正整数。

## 构建前置：自行补充 gradle.properties

`tutorial-fixtures/gradle.properties` 是本机配置 (已被 `.gitignore` 忽略，不随仓库分发)。克隆后需自行创建并补充：

```properties
# 你的 Starsector 游戏安装根目录 (含 starsector-core 的目录)，按本机实际路径填写
starsectorRoot=<你的 Starsector 安装根目录>
# 核心游戏与依赖 MOD 分开存放时，可指定现有的 mods 目录
# starsectorModsRoot=<依赖 MOD 所在的 mods 目录>
```

未配置 `starsectorRoot` 时构建会直接报错 (不会回退到任何默认路径)。若本机 JDK 不在 Gradle 自动探测范围内，可同时补充 `org.gradle.java.installations.paths=...`。

`starsectorRoot` 提供当前版本的核心 JAR。依赖库默认从该游戏根下的 `mods/` 读取；设置 `starsectorModsRoot` 时，从明确指定的目录读取相同的依赖库。

```powershell
gradle compileAllFixtures
gradle validateShaders
gradle verifyTutorialFixtures
```

Java 单元按 `build.gradle` 的 `javaUnitIds` 使用独立 source set。shader 单元按 `shaderUnitIds` 在自己的资源目录中保存 `.vert`、`.frag` 和接入代码。执行编译或 shader 校验前会先运行页面与 fixture 契约检查。

```powershell
python -B tools/check_fixtures.py
gradle checkFixtureContracts
```
