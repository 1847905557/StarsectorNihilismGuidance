# 迁移源码并构建 JAR

迁移就是搬家：源码从游戏自己编译的位置搬进工程，游戏改用工程打出来的 JAR。数据文件里登记的每一个类名，搬完之后都还得找得到原来那个类，游戏内的运行表现不该因为这次搬家发生任何变化。

---

_本节预期阅读时间：\~10 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C6-S1] 的 Gradle 工程；
- [C2-S1] 到 [C4-S1] 中写过的 Java 类，以及它们在数据文件中的登记位置；
- Java 包名与目录的对应关系。

## 迁移源码

样本 MOD 目前有八个 Java 文件，分别来自 C2 到 C4：

```text
data/scripts/gearin/GearInModPlugin.java
data/scripts/gearin/campaign/GearInSystemGenerator.java
data/scripts/gearin/combat/GearInDurinOnHitEffect.java
data/scripts/gearin/combat/GearInUriBeamEffect.java
data/scripts/gearin/hullmods/GearInStableInjectorHullMod.java
data/scripts/gearin/shipsystems/GearInAssaultFeedSystem.java
data/scripts/gearin/shipsystems/GearInResistFieldSystem.java
data/missions/GearIn_test/MissionDefinition.java
```

把它们连同目录一起移进 `src/`，每个文件的路径前面多出一层 `src/`：

```text
src/data/scripts/gearin/GearInModPlugin.java
src/data/scripts/gearin/campaign/GearInSystemGenerator.java
src/data/scripts/gearin/combat/GearInDurinOnHitEffect.java
src/data/scripts/gearin/combat/GearInUriBeamEffect.java
src/data/scripts/gearin/hullmods/GearInStableInjectorHullMod.java
src/data/scripts/gearin/shipsystems/GearInAssaultFeedSystem.java
src/data/scripts/gearin/shipsystems/GearInResistFieldSystem.java
src/data/missions/GearIn_test/MissionDefinition.java
```

**包名从源码根目录算起，`src/` 不属于包名。** `src/data/scripts/gearin/` 下的类，包名仍是 `data.scripts.gearin`，文件中的 `package` 一行不需要改。因此 `.system` 的 `statsScript`、`hull_mods.csv` 的 `script`、`mod_info.json` 的 `modPlugin` 也都不需要改。

**是移动，不是复制。** `data/` 下留着一份同名的 `.java`，游戏启动时会用 Janino 再编译一遍，与 JAR 里的同名类冲突。迁完之后，`data/` 下不应再有任何 `.java` 文件。

编者知道上面这一段看上去有点 不是/而是，但显然这里是没有更好的表达方式了。

战役只迁走 `MissionDefinition.java`。同一目录下的 `descriptor.json`、`mission_text.txt` 与 `icon.jpg` 是游戏按战役 ID 直接读取的数据，留在 `data/missions/GearIn_test/`。**`src/` 只放 Java 源码，游戏数据一律留在原处。**

### 包名与目录的约定

迁移后的包结构沿用前几章，从这里开始作为工程约定固定下来：

| 包                                | 内容                         |
| --------------------------------- | ---------------------------- |
| `data.scripts.gearin`             | MOD 入口                     |
| `data.scripts.gearin.campaign`    | 生涯：星系生成、生涯插件     |
| `data.scripts.gearin.combat`      | 战斗：武器特效、战斗插件     |
| `data.scripts.gearin.hullmods`    | 船插                         |
| `data.scripts.gearin.shipsystems` | 战术系统                     |
| `data.missions.<战役 ID>`         | 战役定义，包名由游戏规定     |

前四类包名是本教程的约定，游戏按完整类名查找，包怎么分不影响加载。这里按游戏系统分包，因为原版 API 本身就是这样分的：生涯类型在 `campaign` 下，战斗类型在 `combat` 下。一个类该放哪里，看它主要和哪一侧的 API 打交道。

战役是例外。游戏按 `data.missions.<战役 ID>.MissionDefinition` 这个固定名字查找战役定义，包名不能自定。

## 构建 JAR

在 MOD 根目录执行：

```text
gradle jar
```

`jar` 任务会先执行 `compileJava`。以 `BUILD SUCCESSFUL` 结束后，`jars/` 下出现 `GearIndustry.jar`。用 JDK 自带的 `jar` 工具查看它的内容：

```text
jar tf jars/GearIndustry.jar
```

输出中应有八个 class，路径与包名一一对应：

```text
data/scripts/gearin/GearInModPlugin.class
data/scripts/gearin/campaign/GearInSystemGenerator.class
data/scripts/gearin/combat/GearInDurinOnHitEffect.class
data/scripts/gearin/combat/GearInUriBeamEffect.class
data/scripts/gearin/hullmods/GearInStableInjectorHullMod.class
data/scripts/gearin/shipsystems/GearInAssaultFeedSystem.class
data/scripts/gearin/shipsystems/GearInResistFieldSystem.class
data/missions/GearIn_test/MissionDefinition.class
```

**JAR 里只有本工程的类。** 出现了 `com/fs/starfarer/` 开头的条目，说明游戏 JAR 被打了进来，回头检查 [C6-S1] 的依赖配置。

## 声明 JAR

打开 `mod_info.json`，加入 `jars`：

```json
	"jars":["jars/GearIndustry.jar"],
	"modPlugin":"data.scripts.gearin.GearInModPlugin"
```

路径相对于 MOD 根目录，与 `build.gradle` 中 `jar` 任务的输出位置一致。**构建输出的位置和 `jars` 声明的位置必须是同一个文件**，改了其中一处，另一处要一起改。

[C1-S1] 提醒过不要预先填写 `jars`：指向不存在的文件，游戏会在启动时报错。现在文件已经存在，可以填了。

### 进游戏核对

启动游戏，确认迁移没有改变任何行为：

- 进入测试战役，齿轮工业的舰船全部在列；
- 部署工匠，突击填弹器与抵抗力场照常生效，都林命中照常出现火花；
- 创建新生涯，Nipher 照常生成。

然后确认游戏用的确实是 JAR 里的代码。打开 `src/` 中的 `GearInAssaultFeedSystem.java`，把 `SPEED_BONUS` 从 `50f` 改为 `150f`，执行 `gradle jar`，重启游戏，在测试战役中激活突击填弹器。速度的提升应当明显大于之前。核对完把数值改回去，再构建一次。

## 此后的修改流程

从这里开始，改 Java 代码的流程是：

1. 在 `src/` 中修改源码；
2. 执行 `gradle jar`；
3. 重启游戏。

**改了源码不构建，游戏用的仍是上一次的 JAR。** 改了没效果时，先看 `jars/GearIndustry.jar` 的修改时间是不是刚才。数据文件不受影响，改完照旧只需要重启。

发布 MOD 时，玩家只需要 JAR 和数据。以下内容属于开发用，不随 MOD 发布：

- `src/`、`build/`、`.gradle/`、`.idea/`；
- `build.gradle`、`settings.gradle`、`gradle.properties`。

开源发布另当别论，那时 `src/` 与构建文件正是别人需要的，只排除 `build/`、`.gradle/`、`.idea/` 与写着本机路径的 `gradle.properties`。

## 常见问题

- **启动时报 `duplicate class` 或同名类冲突**：`data/` 下还留着已迁移的 `.java` 文件。
    - **解决方案**：删除 `data/` 下的全部 `.java` 文件，重启。

- **启动时报找不到 `jars/GearIndustry.jar`**：`jars` 声明的路径与实际文件不一致，或还没有构建。
    - **解决方案**：执行 `gradle jar`，核对 `jars` 的路径，保存后重启。

- **启动时报 `Error loading [data.scripts.gearin.GearInModPlugin]`**：JAR 中缺少这个类，或包名与目录不一致。
    - **解决方案**：用 `jar tf` 查看 JAR 内容，核对源码的 `package` 一行与所在目录。

- **启动时报 `UnsupportedClassVersionError`**：产物的目标版本高于 Java 17。
    - **解决方案**：确认 `options.release = 17`，重新构建。

- **战役列表里还有测试战役，但描述和图标没了**：`descriptor.json`、`mission_text.txt` 或 `icon.jpg` 被一起移进了 `src/`。
    - **解决方案**：把它们移回 `data/missions/GearIn_test/`。

- **改了代码，游戏里没有变化**：没有重新构建，或游戏启用的是另一份 MOD 目录。
    - **解决方案**：执行 `gradle jar`，确认 JAR 的修改时间，并确认游戏启用的就是这个工程所在的 MOD 目录。

## 建议参考项

- `mod_info.json`：`jars` 与 `modPlugin`。
- `jar tf <文件>`：查看 JAR 内容。
