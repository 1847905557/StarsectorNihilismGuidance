# LazyLib 工具：数学、几何与 JSON

本节把 LazyLib 3.0.0 的公开工具类按四个分组讲透 (数学与几何、战斗查询、JSON 与文件、通用工具)，并给出一个消费四组工具的完整最小例。战役侧与渲染侧工具不在本节范围。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~45 分钟。*

## 学习目标

完成后，您将能够：

- 说出 LazyLib 3.0.0 工具类的四个分组，以及每个分组的 "无状态" 或 "快照" 性质；
- 用 `MathUtils` / `VectorUtils` 写出距离、范围、角度、最短旋转、向量定长/限长/旋转的最小代码，背出度/弧度分工 (`FastTrig` 用弧度，其余用度) 及违反后果；
- 用 `CombatUtils` / `AIUtils` 做范围查询、可见性查询与拦截点计算，说出 "结果按当前帧快照处理、禁止跨战斗缓存" 与 `@Nullable` 判空要求；
- 用 `JSONUtils.loadCommonJSON` + `CommonDataJSONObject` 读写 `<saves>\common` 下的 JSON，说出 `save()` 的强制语义 (不调用不落盘)；
- 区分 `ModUtils.isClassPresent` 与 `ModUtils.isModEnabled` 的语义分工，用 `LazyLib.MOD_ID` 常量而不是手写 id 做运行时检测；
- 从 `starsector.log` 与 common 文件两个位置复核工具调用是否真的发生，并把 "数学→查询→持久化" 的调用链走通。

## 前置知识

本节假设您已熟悉下列主题：

- 硬依赖声明与运行时检测 (C12-S01)—— 本节是教程工程第一次**真正调用** LazyLib 的 API；`mod_info.json` 的 `dependencies` 必须已声明 `lw_lazylib`。
- 战斗状态读取 (C07-S02)——`getShips()` 含残骸、`isAlive()` 过滤、暂停守卫、实体与列表禁止跨战斗缓存 (本节把同一条纪律延伸到 LazyLib 的查询结果)。
- `Global` 生命周期 (C06-S03)—— 战斗引擎只在战斗运行期 "有效"，查询入口只在战斗内消费。
- 战斗插件模型 (C07-S01)——`addPlugin` 注册与 `advance` 每帧回调 (本节示例插件的骨架)。
- 测试战役 (C03-S01)——`GearIn_test` 是本节示例的验证入口。
- settings 合并与 common 文件语义 (C06-S04)——`<saves>\common\<name>.data` 与 LazyLib `JSONUtils` 的落盘位置是同一个目录，两处对照。

## LazyLib 3.0.0 概览

LazyLib 是硬依赖外部库，所有公开工具类位于包 `org.lazywizard.lazylib` 及其子包 (`campaign` / `combat` / `opengl` / `ui` / `ext`)。3.0.0(2025-03-27 发行) 面向 `0.98a` 与 Java 17。本地发行布局：

```text
Starsector\mods\LazyLib\
├── mod_info.json          # id "lw_lazylib"，gameVersion "0.98a-RC5"，jars 四件
├── lazylib_settings.json  # 库级设置：enableCaching / logLevel / logDeprecated / crashOnDeprecated
├── changelog.txt          # 发行说明
├── javadoc.zip            # 官方 Javadoc（本地发行签名，本节速查表的依据）
├── data\
│   └── console\commands.csv  # 3.0.0 中全部命令以 # 注释，不提供现成控制台命令
└── jars\
    ├── LazyLib.jar            # MOD 依赖应引用的主 jar
    ├── LazyLib-Kotlin.jar     # Kotlin 支持
    └── internal\              # LazyLib-Console.jar、Kotlin-Runtime.jar：
                               # 发行说明明确 internal 不是给 MOD 当依赖加的
```

::: caution 引用 LazyLib 前必须先声明依赖

`mod_info.json` 的 `dependencies` 必须包含 `lw_lazylib`—— 声明与启动器把关体系在前置依赖章已建立；缺声明时用户运行期抛 `NoClassDefFoundError`。本节从 "已声明" 直接进入 "真正调用"。

:::

库元信息入口 `LazyLib` 类：`LazyLib.MOD_ID` 常量就是 `"lw_lazylib"`(与 `mod_info.json` 的 `id` 一致，运行时检测用常量不要手写字符串)；

`getVersion()` 返回 float 版本 (3.0 发行返回 `3.0`)；`getSupportedGameVersion()` 返回本发行声明支持的游戏版本字符串；

`isCachingEnabled()` 读取 `lazylib_settings.json` 的 `enableCaching`(缓存只加速 "当前帧结果不变" 的昂贵查询，不得把缓存结果升级为业务持久状态)。

`getLogLevel()` / `setLogLevel(...)` 控制 LazyLib 各工具类的日志级别，`onDeprecatedMethodUsage()` 由库在检测到弃用方法调用时内部调用 (策略由 `logDeprecated` / `crashOnDeprecated` 决定，仅调试用途)。

::: info 为什么按四分组讲？

四个分组对应四种状态边界：数学与几何是纯静态无状态，战斗查询返回当前帧快照，JSON 与文件接触磁盘，通用工具反映库与 MOD 元信息。工具不会自动持有任何业务状态 —— 需要跨帧保留什么由您的代码显式持有 (数值、id)，不要持有工具返回的实体引用。

:::

## 数学与几何工具

`MathUtils`、`VectorUtils`、`FastTrig`、`EllipseUtils`、`ShapeUtils`、`CollisionUtils`、`CollectionUtils` 都是无持久状态的纯静态工具：

调用方传入 `Vector2f` / `CombatEntityAPI` / `SectorEntityToken` / 角度 / 半径 / 范围，工具返回计算结果，不持有任何跨帧状态。

坐标系与单位由方法签名约定：**`FastTrig` 用弧度，其余全部用度**。

### MathUtils 速查 (`org.lazywizard.lazylib.MathUtils`，度)

| 方法 (代表重载)| 语义 |
|---|---|
| `clamp(float, float, float)` / `clamp(int, int, int)` | 数值钳制到 `[min, max]` |
| `clampAngle(float)` | 角度归一到 `[0, 360)`(如 400° → 40°)|
| `equals(float, float)` | 浮点近等判定 (两个值互相在 99.99999% 内)|
| `getDistance(...)` / `getDistanceSquared(...)` | 距离 / 平方距离 (避免 `sqrt()`)；`Vector2f` 直算，`CombatEntityAPI` 含碰撞半径，`SectorEntityToken` 含交互半径 |
| `isWithinRange(...)` | 范围内判定，重载与 `getDistance` 同构 |
| `getShortestRotation(float, float)` | 两角之间最短旋转的**方向与长度**(单位度；同输入角差 400° 时返回 40° 而不是 400°)|
| `getRandomNumberInRange(float, float)` / `(int, int)` | 随机数；int 版上下限**含端点** |
| `getRandomPointInCircle` / `getRandomPointInCone` / `getRandomPointOnCircumference` / `getRandomPointOnLine` | 均匀随机取点 (圆、扇形、圆周、线段)|
| `getPointOnCircumference(Vector2f, float, float)` / `getPointsAlongCircumference(...)` | 圆周定点 / 等分布多点 (`center` 允许 null，null 时以原点为圆心)|
| `getMidpoint` / `getNearestPointOnLine` / `isPointOnLine` / `isPointWithinCircle` / `getEquidistantPointsInsideCircle` | 几何判定与取点 |
| `getRandom()` | LazyLib 共享的 `Random` 实例 (可用于种子控制或避免自建实例)|
| `FPI` / `FTAU` | float 常量：`3.1415927f` / `6.2831855f`(避免 `Math.PI` 的 double→float 转换)|

### VectorUtils 速查 (`org.lazywizard.lazylib.VectorUtils`，度)

| 方法 (代表重载)| 语义 |
|---|---|
| `getFacing(Vector2f)` / `getFacingStrict(Vector2f)` | 向量的朝向角 (度)|
| `getAngle(Vector2f, Vector2f)` / `getAngleStrict(...)` | 两向量之间夹角 (度)|
| `getDirectionalVector(Vector2f, Vector2f)` | 从 source 指向 destination 的归一化向量 |
| `resize(Vector2f, float)` / `clampLength(Vector2f, float)` | 定长 / 限长；带 `dest` 的重载把结果写入 dest |
| `rotate(Vector2f, float)` / `rotateAroundPivot(Vector2f, Vector2f, float)` | 旋转 (可绕任意支点)；带 `dest` 的重载写 dest |
| `getCrossProduct(Vector2f, Vector2f)` / `isZeroVector(Vector2f)` | 叉积 / 零向量判定 |
| `toFloatArray(List<Vector2f>)` / `toVectorList(float[])` | 与 OpenGL `FloatBuffer` 顶点数组互转 (3.0 新增)|

### 规则 1：度/弧度分工必须遵守

`FastTrig.sin/cos/atan/atan2` 的输入与输出是**弧度**；`MathUtils` 与 `VectorUtils` 的 facing、angle、rotate、clamp angle 全部是**度**。两套单位之间没有自动转换。

违反的可观察结果：把 `MathUtils.clampAngle(angle)` 的返回值 (度) 直接喂给 `FastTrig.sin(...)`，或把 `FastTrig` 结果 (弧度) 当度参与 `VectorUtils.rotate(...)`，得到的转向量、圆周取点位置、最短旋转量全部偏移；

角度差 90° 时方向完全错误 (如把 90° 写成 `FastTrig.PI/2` 会得到 51.6° 的效果)。

修正：只在这两个域内分别使用各自单位，需要互转时显式 `Math.toRadians` / `Math.toDegrees`。

### 规则 2：VectorUtils 的原地修改语义必须认清

不带 `dest` 的重载 (`resize` / `clampLength` / `rotate` / `rotateAroundPivot`)**原地修改传入向量并返回同一个对象**，不是纯值函数；带 `dest` 的重载把结果写入 dest。禁止把返回值当纯值对象复用、同时继续依赖传入向量的旧值。

违反的可观察结果：`Vector2f speed = missile.getVelocity(); Vector2f turned = VectorUtils.rotate(speed, 90f);` —— 这行代码把**导弹自己的速度向量**转了 90°，导弹在飞行中突然改向，而不是得到一个新向量；

`VectorUtils.resize(vel, 100f)` 同理把源向量长度改写。

修正：需要保留旧值时传入副本 (`new Vector2f(old)`) 或使用带 `dest` 的重载，且不得把同一 mutable 向量同时当作仍需保留的旧值。

### 几何补充：EllipseUtils / ShapeUtils / CollisionUtils / CollectionUtils

- `EllipseUtils`：`getPointOnEllipse` / `getRandomPointInEllipse` / `getRandomPointOnEllipse` / `isPointWithinEllipse` (椭圆取点、随机点、包含判定，全部按角度参数、允许 null 中心)。
- `ShapeUtils`：`createCircle` / `createEllipse` / `createArc` 返回顶点数组 (3.0 从 `DrawUtils` 拆分而来，只算顶点不绘制)。
- `CollisionUtils`：线段与碰撞边界查询 ——：
  - `getCollisionPoint(lineStart, lineEnd, CombatEntityAPI)` 返回线段与目标碰撞边界相交的点 (**不是命中结算**—— 伤害、护盾、碰撞类处理仍走战斗引擎)。
  - `isPointWithinBounds` / `isPointWithinCollisionCircle` / `getNearestPointOnBounds` / `getCollides` / `isPointOnSegment`。
- `CollectionUtils`：集合工具 ——：
  - `combinedList` / `combinedSet` 合并多集合；`filter(collection, CollectionFilter)` 按过滤器挑元素；`implode(collection, separator)` 拼字符串。
  - `SortEntitiesByDistance` / `SortTokensByDistance` 按距离排序的 `Comparator`(构造参数是基准点)。

成熟实例：`VectorUtils.rotate` + `Vector2f.add` 用于实体局部坐标↔世界坐标互转 (`toRelative` / `toAbsolute`)；

`MathUtils.getShortestRotation(facing, VectorUtils.getAngle(a, b))` 用于最短转向控制；

`CollisionUtils.getCollisionPoint` 用于求射线与舰船边界的命中点。

## 战斗查询工具

`CombatUtils` 与 `AIUtils` 读**当前帧**的战斗引擎状态 (引擎对象表、可见性、弹道)，返回对象仍归 Starsector 战斗引擎管理；`WeaponUtils` / `DefenseUtils` 分别提供武器弧与装甲查询。

### CombatUtils 速查 (`org.lazywizard.lazylib.combat.CombatUtils`)

| 方法 | 语义 |
|---|---|
| `getShipsWithinRange(Vector2f, float)` | 范围内舰船列表 (**排除 Command Shuttle 假舰**)|
| `getMissilesWithinRange` / `getProjectilesWithinRange` | 范围内导弹 / 射弹 (后者不含导弹)|
| `getAsteroidsWithinRange` / `getObjectivesWithinRange` / `getEntitiesWithinRange` | 小行星 / 战术点 / 全部实体 |
| `isVisibleToSide(CombatEntityAPI, int)` | 实体对某侧 (0=玩家 / 1=敌方) 是否可见 |
| `getFleetMember(ShipAPI)` | 由场上舰船取对应 `FleetMemberAPI`(生涯侧身份，对应讲解在生涯侧内容中)|
| `spawnShipOrWingDirectly(String variantId, FleetMemberType, FleetSide, float combatReadiness, Vector2f, float facing)` | 绕过后备直接生成舰船/联队 (写侧，本节只列名)|
| `toScreenCoordinates(Vector2f)` / `toWorldCoordinates(Vector2f)` | 世界/屏幕坐标互转 (UI 侧)|
| `centerViewport(Vector2f)` / `applyForce(CombatEntityAPI, ...)` | 视口居中 / 施加力 (写侧)|

### AIUtils 速查 (`org.lazywizard.lazylib.combat.AIUtils`)

| 方法 | 语义 |
|---|---|
| `getNearestEnemy(CombatEntityAPI)` / `getNearestAlly` / `getNearestShip` / `getNearestMissile` / `getNearestEnemyMissile` / `getNearestObjective` | 最近的可见敌/友/舰/导弹/目标点 |
| `getEnemiesOnMap` / `getAlliesOnMap` / `getEnemyMissilesOnMap` | 场上全部可见敌/友/敌导弹 |
| `getNearbyEnemies` / `getNearbyAllies` / `getNearbyEnemyMissiles` | 范围内列表 |
| `getBestInterceptPoint(Vector2f point, float speed, Vector2f targetLoc, Vector2f targetVel)` | 以给定速度拦截移动目标的最佳瞄准点 |
| `canUseSystemThisFrame(ShipAPI)` | 舰船战术系统本帧是否可激活 |

- `WeaponUtils`(`aimTowardsPoint` / `getTimeToAim` / `isWithinArc` / `getEnemiesInArc` 等) 是只读查询。
- `DefenseUtils`(`getDefenseAtPoint` / `getArmorLevel` / `getArmorDamage` / `getMostDamagedArmorCell` / `hasHullDamage` / `hasArmorDamage` / 常量 `NOT_IN_GRID`) 是只读查询。

开火决策与伤害结算仍归武器状态与战斗引擎。

::: warning 直接拿查询结果数当 "存活数" 会偏

`CombatUtils.getShipsWithinRange(...)` 的结果是引擎对象表的当前帧快照，可能含残骸 (LazyLib 只保证排除 Command Shuttle)。需要 "存活" 语义时按 `isAlive()` 过滤 (建议级，示例的 `alive` 计数写法)；需要含残骸时明确表达意图。

:::

### 规则 3：查询结果是当前帧快照，禁止跨战斗缓存

`AIUtils` 与 `CombatUtils` 的返回对象按**当前帧**引擎状态计算；实体死亡、脱离范围或 owner 变化后，之前拿到的引用不再表达有效状态。

把 `getNearestEnemy(...)` / `getShipsWithinRange(...)` 的结果存进字段跨帧、尤其**跨战斗**复用，与战斗状态内容的缓存禁令同一条纪律：战斗结束后引擎销毁重建，旧实体判定恒失效。

违反的可观察结果：第二场战斗起，缓存的引用 "数据还在但全部判定失效"(`isAlive()` / 距离全部按新引擎/新位置计算或返回 false)，且**没有任何报错**；跨帧持有也常在实体被击毁后读到陈旧状态。

修正：每次需要时重新查询，只把查询结果用于当帧决策；跨帧业务状态由调用方显式持有 (数值、id 等)，不持有实体引用。

### 规则 4：@Nullable 返回值必须判空

`AIUtils.getNearestEnemy(...)` 与 `getBestInterceptPoint(...)` 在 javadoc 上标注 `@Nullable`：没有可见敌人 / 目标无法拦截时返回 `null`。

违反的可观察结果：敌方全灭或视野内无目标时，`getNearestEnemy(...)` 返回 null，下一行调用其方法直接抛 NPE，日志出现 `ERROR` 与堆栈，本帧流程中止。

修正：使用前判空 (示例的 `if (enemy == null) { ...; return; }` 写法)，并让空目标分支输出日志而不是静默。

## JSON 与文件工具

`JSONUtils` 提供 common JSON 读写与杂项转换，`IOUtils` 提供受限类加载器下的文件读取。

### JSONUtils 速查 (`org.lazywizard.lazylib.JSONUtils`)

| 方法 | 语义 |
|---|---|
| `loadCommonJSON(String filename)` | 从 `<saves>\common` 加载 JSON；文件不存在则返回空包装 (不写盘)|
| `loadCommonJSON(String filename, String defaultJSONPath)` | 文件不存在时用 `defaultJSONPath`(MOD 文件夹内的 JSON) 填充并**立即保存** |
| `clear(JSONObject)` | 清空一个 JSONObject 的全部条目 |
| `toColor(JSONArray)` | 把 ≥3 个 int 的 JSONArray 转为 `Color`(配合 settings 合并处的文件加载方法读颜色配置)|
| `CommonDataJSONObject` | `extends JSONObject implements AutoCloseable`：构造器与 `loadCommonJSON` 的返回类型，`save()` 落盘 |

`CommonDataJSONObject` 的 javadoc 原文：`save() Saves this JSONObject's contents to common data. You MUST call this if you want your data to persist!` —— 这是官方文档里的**必须**。

`save()` 把内容写到 `saves/common`("persist between saves (and presumably survive Starsector updates)")，读取侧每次 `loadCommonJSON` 重新加载磁盘内容。

`close()` 只是 `AutoCloseable` 接口实现 (javadoc 原文 "You will never need to call this")，try-with-resources 里调用即可，真正的写盘动作是 `save()`。

### 规则 5：save() 必须显式调用

写完 `CommonDataJSONObject` 后**必须**调用 `save()`，否则改动只存在于内存对象，`close()` 不会自动写盘。

违反的可观察结果：战斗内 `put(...)` 后没有 `save()`，本场战斗读到的还是旧值；退出游戏/重进后新增键完全消失，`<saves>\common` 下文件内容不变 —— 表现为 "计数器归零 / 设置丢失"，且没有任何报错。

修正：写操作末尾调用 `save()`；读取失败与写盘失败都要 catch(`IOException` / `JSONException`)，不要让异常中断战斗循环。

`defaultJSONPath` 的语义边界：它只用于**首次创建**时填充默认值 (2.4b 起：若默认文件存在且目标文件不存在，立即创建并保存)。

**禁止**把 `loadCommonJSON(filename, defaultJSONPath)` 读到的内容当作用户显式配置 —— 文件一旦创建，后续读取都以磁盘内容为准，默认值不再参与。

需要 "用户配置 + 默认值" 分层时，读取后自行判断键是否存在 (示例的 `has(key)` 写法)，不要依赖默认文件覆盖。

::: warning 默认值不是 "用户配置" 的备用值

`defaultJSONPath` 只在**文件不存在**的首次创建时填充并立即保存；文件一旦存在，后续读取都以磁盘内容为准。把默认内容当用户配置反复写回，会覆盖用户对 common 文件的修改 (表现为 "用户改了配置，下次进游戏被默认值覆盖")。

:::

对照前置章节：游戏侧 `Global.getSettings().writeJSONToCommon(...)` 与 LazyLib 的 `JSONUtils` 写的是同一个 `<saves>\common` 目录，两条路径选一条即可；

LazyLib 路径的优势是 `CommonDataJSONObject` 本身就是一个 `JSONObject`(原生 `put` / `getInt` / `has`)，无需手动序列化。

`IOUtils.readAllBytes(String)` 是 `Files.readAllBytes` 的移植版，能在游戏的类加载器限制下按路径读完整字节数组 (MOD 文件夹内文件)；读取时用 `/` 路径分隔 (沿用 settings 合并处建立的 `validateFileName` 规则)。

## 通用工具

`ModUtils` / `StringUtils` / `CollectionUtils` / `LazyLib` 提供 MOD 检测、文本与库元信息。

### ModUtils 速查 (`org.lazywizard.lazylib.ModUtils`)

| 方法 | 语义 |
|---|---|
| `isModEnabled(String modId)` | 该 MOD id 是否在启用列表 (逐字比较)|
| `isClassPresent(String classCanonicalName)` | 类是否存在于已加载 MOD 的 classpath(`Class.forName` 式存在性检查，不初始化)|
| `getEnabledModIds()` | 全部已启用 MOD 的 id 列表 |
| `getOverrides()` | 各 MOD 在 `mod_info.json` 里显式 override 的原版文件列表 |
| `loadClassesIfClassIsPresent(...)` | 目标类存在时批量加载一组类 (可选初始化)，用于按 MOD 存在性做可选分支 |

### 规则 6：isClassPresent 与 isModEnabled 语义分工必须分清

`isClassPresent(...)` 表达 **classpath 存在性**，`isModEnabled(...)` 表达 **MOD 启用状态**：前者用于 "这个类能不能调"(API 可用性)，后者用于 "这个 MOD 开着没有"(功能开关)。

违反的可观察结果：用 `isClassPresent("org.lazywizard.lazylib.combat.CombatUtils")` 判断 "LazyLib 已启用" ——classpath 上的类可能由**其他已启用 MOD 间接携带**

(某个 MOD 把 LazyLib 类打进自己的 jar)，此时 `isClassPresent` 为 true 而 `isModEnabled("lw_lazylib")` 为 false，功能分支错误开启；

反过来用 `isModEnabled` 判断 "类可用" 也会在类缺失时误判。

修正：API 可用性用 `isClassPresent`，启用状态用 `isModEnabled`，需要两者同时成立时显式 AND。

硬依赖的声明与启动器把关仍按前置依赖章建立的 `ModManagerAPI` 体系 (本节的 `ModUtils` 是 LazyLib 提供的便捷入口，语义分工一致)。

`StringUtils`：`wrapString(String, int)` 按固定行宽换行、`indent(String, String)` 逐行缩进。`LazyLib` 元信息与设置见 "概览" 节。

`data\console\commands.csv` 在 3.0.0 中全部以 `#` 注释 (LazyLib 自带的控制台测试命令未启用)，不要把 "LazyLib 提供控制台命令" 当作可用现状。

## 文件与 API 链路

| 环节 | 文件或 API | 使用方 / 行为 |
|---|---|---|
| 依赖声明 | `mod_info.json` 的 `dependencies`(`lw_lazylib`)| 启动器 `ModManagerAPI` 强制检查 (C12-S01)；LazyLib 侧 id 常量 `LazyLib.MOD_ID` |
| 库加载 | `Starsector\mods\LazyLib\jars\LazyLib.jar`(+ `LazyLib-Kotlin.jar`；`jars\internal\` 不用于 MOD 依赖)| 脚本类路径；`ModUtils.isClassPresent` 反映 classpath 存在性 |
| 数学 / 几何 | `MathUtils` / `VectorUtils` / `FastTrig` / `EllipseUtils` / `ShapeUtils` / `CollisionUtils` / `CollectionUtils` | 纯静态无状态；度/弧度分工 (规则 1)、原地修改语义 (规则 2)|
| 战斗查询 | `CombatUtils` / `AIUtils` / `WeaponUtils` / `DefenseUtils` | 当前帧引擎状态快照 (规则 3)；`@Nullable` 判空 (规则 4)|
| JSON / 文件 | `JSONUtils` / `JSONUtils.CommonDataJSONObject` / `IOUtils` | `<saves>\common` 下 JSON 读写；`save()` 落盘 (规则 5)|
| MOD 检测 | `ModUtils` / `LazyLib` | classpath 存在性 vs 启用状态 (规则 6)；版本 / 缓存 / 日志设置 |
| 库配置 | `Starsector\mods\LazyLib\lazylib_settings.json` | `enableCaching` / `logLevel` / `logDeprecated` / `crashOnDeprecated` 四键 |
| 可观察结果 | `Starsector\starsector-core\starsector.log`；`<saves>\common\GearIn_lazy_demo` 文件 | 本节示例的 `GearIn lazy ...` 日志行与 common JSON 内容 |

## 完整最小例

在既有累计工程布局上追加一个薄入口类战斗插件与四个工具族用法片段类 (不新增任何游戏资源)，五个类同在 `data.scripts.gearin.lib` 包内协作：

- `GearInLazyPlugin`：薄入口类战斗插件 —— 只做接入与节奏控制 (暂停守卫 + 报告周期)，按序调用四个用法片段，不含任何工具调用细节；
- `GearInLazyMathUsage`：数学与几何工具族片段 ——`MathUtils` / `VectorUtils` 的距离、范围、角度、最短旋转、向量定长与圆周取点 (`AIUtils.getNearestEnemy` 仅用于取演示目标)；
- `GearInLazyCombatQuery`：战斗查询工具族片段 ——`CombatUtils` / `AIUtils` 的范围列表 (`isAlive()` 过滤)、可见性统计与拦截点；
- `GearInLazyJsonStore`：JSON 与文件工具族片段 ——`JSONUtils.loadCommonJSON` + `CommonDataJSONObject` 读写 `<saves>\common\GearIn_lazy_demo`，写后必须 `save()`；
- `GearInLazyRuntimeProbe`：通用工具族片段 ——`LazyLib.MOD_ID` 常量 + `ModUtils` 运行时检测。

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json                      # 已声明 dependencies: [lw_lazylib]（前置依赖章）
├── data\
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin            # 前置章节所有
│       ├── GearInGlobalAccess         # 前置章节所有
│       ├── combat\
│       │   └── GearInCombatStatePlugin   # 前置章节所有
│       └── lib\                       # 本节新增五个类
│           ├── GearInLazyPlugin            # 薄载体战斗插件（接线与节奏）
│           ├── GearInLazyMathUsage         # 数学与几何工具族片段
│           ├── GearInLazyCombatQuery       # 战斗查询工具族片段
│           ├── GearInLazyJsonStore         # JSON 与文件工具族片段
│           └── GearInLazyRuntimeProbe      # 通用工具族片段
└── jars\
```

### GearInLazyPlugin：薄入口类战斗插件

```java
package data.scripts.gearin.lib;

import java.util.List;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 薄载体：把四个工具族用法片段接成完整最小例的战斗插件。
 *
 * 由既有战斗插件在 init() 中 addPlugin 注册（C07-S01 模型），只在本场
 * 战斗内有效；每个工具族一个用法片段类（GearInLazyMathUsage /
 * GearInLazyCombatQuery / GearInLazyJsonStore / GearInLazyRuntimeProbe），
 * 本类只做接线与节奏控制（暂停守卫 + 报告周期），不含工具调用细节。
 */
public class GearInLazyPlugin extends BaseEveryFrameCombatPlugin {

    private CombatEngineAPI engine;
    private final IntervalUtil reportInterval = new IntervalUtil(2f, 2f);
    private int battleCount;
    private int reportCount;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        battleCount = GearInLazyJsonStore.readInt("battles");
        battleCount++;
        GearInLazyJsonStore.writeInt("battles", battleCount);
        GearInLazyRuntimeProbe.logInit(battleCount);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine.isPaused()) {
            return; // 暂停守卫（C07-S01）：暂停时 advance 仍会被调用
        }
        reportInterval.advance(amount);
        if (reportInterval.intervalElapsed()) {
            reportCount++;
            GearInLazyMathUsage.report(engine);
            GearInLazyCombatQuery.report(engine, reportCount);
            GearInLazyJsonStore.writeInt("reports", reportCount);
        }
    }
}
```

接入关系：`init` 里先经 `GearInLazyJsonStore.readInt("battles")` 读计数、自增后 `writeInt` 写回，再经 `GearInLazyRuntimeProbe.logInit(battleCount)` 输出检测行；

`advance` 里报告周期到点时依次调用 `GearInLazyMathUsage.report(engine)`、`GearInLazyCombatQuery.report(engine, reportCount)`。

`reports` 计数经 `GearInLazyJsonStore.writeInt` 落盘。

入口类只维护引擎引用、周期计时器与两个计数，不直接调用任何 LazyLib 工具。

- **暂停守卫**：`advance` 开头 `if (engine.isPaused()) return;`，暂停期间报告停摆 —— 沿用前置章节建立的暂停守卫纪律。
- **日志前缀**：`GearIn lazy init:` / `GearIn lazy math:` / `GearIn lazy combat:` / `GearIn lazy json:` 四条前缀由四个类各自输出，逐字保留，作为预期结果与故障分析的核对信号。

### GearInLazyMathUsage：数学与几何工具族片段

```java
package data.scripts.gearin.lib;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * 数学与几何工具族的用法片段：MathUtils / VectorUtils 的最小消费面。
 *
 * 无持久状态的纯静态工具，角度一律使用度（degrees）；AIUtils 只用于
 * 获取演示目标（最近可见敌舰），查询结果按当前帧快照使用（C07-S02 同规）。
 */
public final class GearInLazyMathUsage {

    private static final Logger LOG = Global.getLogger(GearInLazyMathUsage.class);

    private GearInLazyMathUsage() {
        // 工具类：不实例化
    }

    /** 距离、范围、角度、最短旋转、向量定长与圆周取点各输出一行日志。 */
    public static void report(CombatEngineAPI engine) {
        ShipAPI player = engine.getPlayerShip();
        if (player == null) {
            return; // 防御性判空：getPlayerShip() 战斗内实际永不返回 null（C07-S02）
        }
        Vector2f playerLoc = player.getLocation();
        ShipAPI enemy = AIUtils.getNearestEnemy(player);
        if (enemy == null) {
            LOG.info("GearIn lazy math: no enemy in sight");
            return;
        }
        Vector2f enemyLoc = enemy.getLocation();
        float angleTo = VectorUtils.getAngle(playerLoc, enemyLoc);

        // 原地修改语义：getDirectionalVector 返回新向量；resize 原地改写并返回同一引用
        Vector2f dirToEnemy = VectorUtils.getDirectionalVector(playerLoc, enemyLoc);
        Vector2f spot = VectorUtils.resize(dirToEnemy, 100f); // dirToEnemy 被改写为长度 100
        Vector2f.add(spot, playerLoc, spot);

        LOG.info("GearIn lazy math: dist=" + MathUtils.getDistance(player, enemy)
                + " inRange1200=" + MathUtils.isWithinRange(player, enemy, 1200f)
                + " angleTo=" + angleTo
                + " clampAngle=" + MathUtils.clampAngle(angleTo + 360f)
                + " facing=" + VectorUtils.getFacing(player.getVelocity())
                + " shortest=" + MathUtils.getShortestRotation(VectorUtils.getFacing(player.getVelocity()), angleTo)
                + " spot=(" + spot.getX() + ", " + spot.getY() + ")"
                + " circumference=(" + MathUtils.getPointOnCircumference(playerLoc, 100f, angleTo).getX() + ", "
                + MathUtils.getPointOnCircumference(playerLoc, 100f, angleTo).getY() + ")"
                + " random=" + MathUtils.getRandomNumberInRange(1, 6));
    }
}
```

要点：

- `MathUtils.getDistance` / `isWithinRange` 做距离与范围判定 (含碰撞半径)；`VectorUtils.getAngle` / `getFacing` / `MathUtils.getShortestRotation` 算朝向与最短旋转 (全部度)。
- `clampAngle(angleTo + 360f)` 演示角度归一 (结果应等于 `angleTo`)。
- `resize` 演示原地修改语义 (注释标明 `dirToEnemy` 被改写为长度 100)；`getPointOnCircumference` 演示圆周定点。
- 目标获取借用 `AIUtils.getNearestEnemy`(`@Nullable`，判空后输出 `no enemy in sight`)；战斗查询族的完整消费面在 `GearInLazyCombatQuery`。

### GearInLazyCombatQuery：战斗查询工具族片段

```java
package data.scripts.gearin.lib;

import java.util.List;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * 战斗查询工具族的用法片段：CombatUtils / AIUtils 的最小消费面。
 *
 * 查询结果是当前帧引擎状态快照，实体仍归战斗引擎管理，结果禁止跨战斗
 * 缓存（C07-S02 同规）；@Nullable 返回值（getNearestEnemy /
 * getBestInterceptPoint）使用前必须判空。
 */
public final class GearInLazyCombatQuery {

    private static final Logger LOG = Global.getLogger(GearInLazyCombatQuery.class);

    private GearInLazyCombatQuery() {
        // 工具类：不实例化
    }

    /** 范围列表、可见性统计与拦截点各输出一行日志。 */
    public static void report(CombatEngineAPI engine, int reportCount) {
        ShipAPI player = engine.getPlayerShip();
        if (player == null) {
            return;
        }
        List<ShipAPI> ships = CombatUtils.getShipsWithinRange(player.getLocation(), 2000f);
        int alive = 0;
        int visible = 0;
        for (ShipAPI ship : ships) {
            if (ship.isAlive()) {
                alive++; // 快照可能含残骸，按 C07-S02 的 isAlive() 纪律过滤
            }
            if (CombatUtils.isVisibleToSide(ship, player.getOwner())) {
                visible++;
            }
        }
        ShipAPI enemy = AIUtils.getNearestEnemy(player);
        String intercept = "none";
        if (enemy != null) {
            Vector2f point = AIUtils.getBestInterceptPoint(player.getLocation(), 400f,
                    enemy.getLocation(), enemy.getVelocity());
            intercept = point == null ? "none" : "(" + point.getX() + ", " + point.getY() + ")";
        }
        LOG.info("GearIn lazy combat: inRange2000=" + ships.size()
                + " alive=" + alive + " visible=" + visible
                + " intercept=" + intercept
                + " reports=" + reportCount);
    }
}
```

要点：

- `CombatUtils.getShipsWithinRange` 返回 2000 范围列表；按战斗状态内容的纪律用 `isAlive()` 过滤后计数 (快照可能含残骸)，再按 `CombatUtils.isVisibleToSide(ship, player.getOwner())` 统计可见数。
- `AIUtils.getNearestEnemy` 判空后，`getBestInterceptPoint` 以 400 速度求拦截点，`point == null` 时输出 `intercept=none` —— 两次判空同一条纪律 (规则 4)。

### GearInLazyJsonStore：JSON 与文件工具族片段

```java
package data.scripts.gearin.lib;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject;

import com.fs.starfarer.api.Global;

/**
 * JSON 与文件工具族的用法片段：JSONUtils / CommonDataJSONObject 的最小消费面。
 *
 * 读写 <saves>/common 下的 JSON：只有调用 CommonDataJSONObject.save()
 * 才会写盘（不调用 save() 的改动不会落盘）；读取与写盘失败都要 catch，
 * 不让异常中断战斗循环。
 */
public final class GearInLazyJsonStore {

    private static final Logger LOG = Global.getLogger(GearInLazyJsonStore.class);

    /** 公共数据文件名：JSONUtils 在 <saves>/common 下读写该 JSON。 */
    private static final String COMMON_FILE = "GearIn_lazy_demo";

    private GearInLazyJsonStore() {
        // 工具类：不实例化
    }

    /** 读公共 JSON 中的 int 键；文件或键不存在时返回 0。 */
    public static int readInt(String key) {
        try (CommonDataJSONObject jsonObject = JSONUtils.loadCommonJSON(COMMON_FILE)) {
            if (jsonObject.has(key)) {
                return jsonObject.getInt(key);
            }
        } catch (Exception e) {
            LOG.warn("GearIn lazy json: failed to read key " + key, e);
        }
        return 0;
    }

    /** 写公共 JSON 的 int 键并立即 save()：不调用 save() 的改动不会落盘。 */
    public static void writeInt(String key, int value) {
        try (CommonDataJSONObject jsonObject = JSONUtils.loadCommonJSON(COMMON_FILE)) {
            jsonObject.put(key, value);
            jsonObject.save();
        } catch (Exception e) {
            LOG.warn("GearIn lazy json: failed to write key " + key, e);
        }
    }
}
```

要点：

- 读侧 `has(key)` 判键、`getInt` 取值，文件或键不存在时返回 0；写侧 `put` 后**必须** `save()`(规则 5)，`close()` 不会自动写盘。
- 读取与写盘失败都 catch(`Exception`) 并输出 `GearIn lazy json:` 警告行，不让异常中断战斗循环。
- try-with-resources 用法：`CommonDataJSONObject` 实现 `AutoCloseable`，`close()` 只是接口实现，真正的写盘动作是 `save()`。

### GearInLazyRuntimeProbe：通用工具族片段

```java
package data.scripts.gearin.lib;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.LazyLib;
import org.lazywizard.lazylib.ModUtils;

import com.fs.starfarer.api.Global;

/**
 * 通用工具族的用法片段：LazyLib / ModUtils 的最小消费面。
 *
 * 以库自身常量 LazyLib.MOD_ID 做启用检测（不手写 id 字符串）；
 * isClassPresent 表达 classpath 存在性、isModEnabled 表达启用状态，
 * 两条信息分别回答"LazyLib 开着没"与"这个类能调没"。
 */
public final class GearInLazyRuntimeProbe {

    private static final Logger LOG = Global.getLogger(GearInLazyRuntimeProbe.class);

    private GearInLazyRuntimeProbe() {
        // 工具类：不实例化
    }

    /** 输出库版本、支持的游戏版本与运行时检测结果一行日志。 */
    public static void logInit(int battleCount) {
        LOG.info("GearIn lazy init: version=" + LazyLib.getVersion()
                + " supported=" + LazyLib.getSupportedGameVersion()
                + " modEnabled=" + ModUtils.isModEnabled(LazyLib.MOD_ID)
                + " classPresent=" + ModUtils.isClassPresent("org.lazywizard.lazylib.combat.CombatUtils")
                + " battleCount=" + battleCount);
    }
}
```

要点：

- `LazyLib.getVersion()` / `getSupportedGameVersion()` 打印库版本与支持的游戏版本；`ModUtils.isModEnabled(LazyLib.MOD_ID)` 用库常量做启用检测 (不手写 `"lw_lazylib"`)。
- `ModUtils.isClassPresent("org.lazywizard.lazylib.combat.CombatUtils")` 单独表达 "类在不在 classpath" —— 两条信息分别回答 "LazyLib 开着没" 与 "这个类能调没"(规则 6)。

### 注册片段 (工程片段 —— 本节不改动既有战斗插件所有者文件)

```java
    // 既有战斗插件（前置章节的所有者类）的 init() 内新增一行
    @Override
    public void init(CombatEngineAPI engine) {
        engine.addPlugin(new GearInLazyPlugin());
        // ... 原有逻辑
    }
```

### 构建

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/lib/` 下五个类 (可用 `jar tf` 核对)：
- `GearInLazyPlugin` / `GearInLazyMathUsage` / `GearInLazyCombatQuery` / `GearInLazyJsonStore` / `GearInLazyRuntimeProbe`

本节五个类的源码与 fixture 逐字一致 (fixture 目录 `tutorial-fixtures\units\C12-S02\src\main\java\data\scripts\gearin\lib\`)，fixture 编译验收命令见 "预期结果"。

### 验证方案 (基于本例)

- 前置数据：既有 MOD 布局 (C06-S01)；`dependencies` 已声明 `lw_lazylib`(C12-S01)；既有战斗插件接入本节注册片段 (C07-S01 模型)；`GearIn_test` 测试战役 (C03-S01)。
- 操作 1(fixture 编译)：在仓库根目录执行 `gradle -p tutorial-fixtures compileC12s02Java --no-daemon`，确认 `BUILD SUCCESSFUL`(退出码 0)。
- 操作 2(游戏构建)：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `data/scripts/gearin/lib/` 下五个类条目。
- 操作 3(初始化)：进入 `GearIn_test` 一场战斗，`starsector.log` 核对 `GearIn lazy init: ...` 行内容。
  - `version=3.0`；`supported` 与 LazyLib `mod_info.json` 的 `gameVersion` 一致。
  - `modEnabled=true`、`classPresent=true`、`battleCount=1`。
- 操作 4(周期报告)：战斗 (未暂停) 中每 2 秒出现 `GearIn lazy math:` 与 `GearIn lazy combat:` 各一行；按空格暂停约 10 秒，暂停期间没有报告输出，恢复后继续。
- 操作 5(空目标)：敌方全灭后 `math` 行输出 `GearIn lazy math: no enemy in sight`、`combat` 行 `intercept=none`，无 `ERROR`。
- 操作 6(持久化)：退出战役再进入一场战斗，init 行 `battleCount` 递增 (1 → 2)；`<saves>\common\` 下出现以 `GearIn_lazy_demo` 开头的文件，内容含 `battles` 与 `reports` 键。
- 操作 7(故障注入·save)：注释 `GearInLazyJsonStore.writeInt` 里的 `jsonObject.save()`，重启游戏进入战斗，`battleCount` 从 1 重新开始；改回后确认恢复递增。
- 操作 8(故障注入·判空)：删除 `GearInLazyMathUsage.report` 里的 `if (enemy == null)` 分支，清空敌方后下一个报告周期日志出现 NPE 堆栈；改回后确认恢复。
- 通过信号：操作 1–6 全部符合；操作 7/8 的注入与恢复都符合预期。
- 清理：取消勾选测试 MOD；删除 `GearIn_lazy_demo` 测试文件 (如需)；确认没有对共享文件做过修改。

::: tip 只看本节日志行

`starsector.log` 混有大量其他日志，核对时可只过滤前缀：`grep 'GearIn lazy' Starsector\starsector-core\starsector.log`。

:::

## 预期结果

1. **fixture 编译**：在仓库根目录执行 `gradle -p tutorial-fixtures compileC12s02Java --no-daemon`，以 `BUILD SUCCESSFUL` 结束 (退出码 0)。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/lib/` 下五个类的条目。
   - 类列表：`GearInLazyPlugin` / `GearInLazyMathUsage` / `GearInLazyCombatQuery` / `GearInLazyJsonStore` / `GearInLazyRuntimeProbe`。
3. **初始化日志**：首次进入 `GearIn_test` 测试战役一场战斗，`starsector.log` 出现一行。
```text
GearIn lazy init: version=3.0 supported=0.98a-RC5 modEnabled=true classPresent=true battleCount=1
```
`version` 是 `LazyLib.getVersion()` 的 float 输出；`supported` 应与 `Starsector\mods\LazyLib\mod_info.json` 的 `gameVersion`(`0.98a-RC5`) 一致；`battleCount` 首次为 1。
4. **周期报告**：战斗 (未暂停) 中每 2 秒出现 `GearIn lazy math:` 与 `GearIn lazy combat:` 各一行；`math` 行的 `dist` 随敌舰距离变化、`clampAngle` 与 `angleTo` 数值一致 (角度归一到 `[0,360)`)、`shortest` 的绝对值不超过 180(最短旋转)、`spot` 与玩家距离约 100(`resize` 定长 + 平移)；`combat` 行的 `inRange2000` 覆盖 2000 范围内的舰船 (含残骸时 `alive` 小于 `inRange2000`)。
5. **暂停语义**：按空格暂停约 10 秒，暂停期间**没有**报告输出；恢复后报告继续。
6. **空目标分支**：敌方全灭后，`math` 行变为 `GearIn lazy math: no enemy in sight`，无 `ERROR`；`combat` 行的 `intercept=none`。
7. **common JSON 持久化**：退出战役再进入一场战斗，init 行 `battleCount` 递增 (1 → 2)；`<saves>\common\` 下出现以 `GearIn_lazy_demo` 开头的文件，内容含 `battles` 键，战斗内 `reports` 键随报告周期增长。
8. **变更验证 (单位分工)**：把示例的 `MathUtils.clampAngle(angleTo + 360f)` 换成 `FastTrig.sin(angleTo)` 输出 —— 数值不再等于 `angleTo` (度被当弧度用)；改回后恢复。
9. **变更验证 (save)**：把 `GearInLazyJsonStore.writeInt` 里的 `jsonObject.save()` 注释掉，重启游戏进入战斗 —— `battleCount` 从 1 重新开始 (上次的 1 没有落盘)；改回后恢复。

静态自查：Java 源码与 fixture 逐字一致 (包括 import 顺序与注释)；

工作单元编号没有进入任何包名、类名、数据键或 common 文件名 (包 `data.scripts.gearin.lib`、类 `GearInLazyPlugin` / `GearInLazyMathUsage` / `GearInLazyCombatQuery` / `GearInLazyJsonStore` / `GearInLazyRuntimeProbe`、文件名

`GearIn_lazy_demo`、键 `battles` / `reports`)；

源码 UTF-8 无 BOM、CRLF 换行。

## 实例驱动故障分析

1. **角度全部偏了，转向、取点位置不对**。
  - 触发：把 `FastTrig` 的弧度输入与 `MathUtils` / `VectorUtils` 的度混用 (如 `VectorUtils.rotate(vec, FastTrig.cos(x))` 或 `FastTrig.sin(angleInDegrees)`)。
  - 可观察：转向量、圆周取点、最短旋转全部偏移；差值恰为弧度/度的换算比例 (90° 写成弧度会差约 38.4°)。
  - 原因：两个域没有自动转换 (规则 1)。
  - 修正：各自域内用各自单位，互转显式 `Math.toRadians` / `Math.toDegrees`。
2. **common JSON 改了但重进游戏又 "归零"**。
  - 触发：`put(...)` 后忘了 `save()`。
  - 可观察：本场战斗内读到的还是旧值；退出重进后键值丢失，文件内容不变，无任何报错。
  - 原因：`CommonDataJSONObject.save()` 是唯一写盘入口 (规则 5，javadoc "You MUST call this if you want your data to persist!")。
  - 修正：写操作末尾调用 `save()`；把 "读-改-写" 封装成示例的 `GearInLazyJsonStore.writeInt` 单一入口。
3. **敌方全灭后日志出现 NPE 堆栈**。
  - 触发：`AIUtils.getNearestEnemy(player)` 的返回值直接 `.getLocation()`，没有判空。
  - 可观察：敌方全部被击毁后的下一个报告周期抛 NPE，`ERROR` + 堆栈，本帧流程中止。
  - 原因：`getNearestEnemy` / `getBestInterceptPoint` 标注 `@Nullable`(规则 4)。
  - 修正：判空并输出 `no enemy in sight` / `intercept=none` 日志。
4. **第二场战斗起查询全部 "失效" 却无报错**。
  - 触发：把 `CombatUtils.getShipsWithinRange(...)` / `AIUtils.getNearestEnemy(...)` 的结果存进字段，跨战斗复用。
  - 可观察：第二场战斗里缓存的实体引用判定恒 false / 位置陈旧，逻辑静默失效 —— 与战斗状态内容的实体缓存禁令同一条纪律。
  - 原因：查询结果是当前帧快照，实体归战斗引擎管理 (规则 3)；战斗结束引擎销毁重建。
  - 修正：每帧重新查询，不缓存实体引用。
5. **导弹/舰船方向被 "算出来的向量" 改掉了**。
  - 触发：`Vector2f vel = ship.getVelocity(); VectorUtils.rotate(vel, 90f);` 想 "算出转向后的新方向"。
  - 可观察：`ship.getVelocity()` 本身被旋转 ——`getVelocity()` 返回的是引擎内部的同一个向量对象，实体飞行方向突变。
  - 原因：`VectorUtils` 不带 `dest` 的重载原地修改并返回同一引用 (规则 2)。
  - 修正：先 `new Vector2f(old)` 再变换，或使用带 `dest` 的重载。
6. **`isClassPresent(...)` 为 true 但 LazyLib 没勾选，功能分支错误开启**。
  - 触发：用 `ModUtils.isClassPresent("org.lazywizard.lazylib.combat.CombatUtils")` 当 "LazyLib 已启用" 判据。
  - 可观察：某个其他已启用 MOD 把 LazyLib 类打进了自己的 jar(间接携带)，classpath 上存在类但 `isModEnabled("lw_lazylib")` 为 false，分支按 "可用" 执行后调用缺失的功能。
  - 原因：`isClassPresent` 表达 classpath 存在性、`isModEnabled` 表达启用状态 (规则 6)。
  - 修正：API 可用性用 `isClassPresent`、启用状态用 `isModEnabled`，需要同时成立时显式 AND。
7. **用户改了 common 配置，下次进游戏却被 "默认值" 覆盖**。
  - 触发：`JSONUtils.loadCommonJSON("GearIn_settings", "data/config/gear_defaults.json")` 每次读取后把默认文件的内容整体写回。
  - 可观察：用户对 `GearIn_settings` 的修改丢失，文件内容回到默认值。
  - 原因：`defaultJSONPath` 只在**文件不存在**的首次创建时填充并立即保存 (2.4b 起)，一旦文件存在就以磁盘为准；把默认值当用户配置反复写回属于误用。
  - 修正：默认值只用于初始化；读取后按 `has(key)` 判断键是否存在 (示例写法)。
8. **范围查询统计含残骸，计数 "不对"**。
  - 触发：`CombatUtils.getShipsWithinRange(...).size()` 直接当 "存活舰船数"。
  - 可观察：击毁敌舰后计数不减或延迟减少。
  - 原因：查询结果是引擎对象表的当前帧快照 (引擎对象表含残骸与待移除舰；LazyLib 只保证排除 Command Shuttle)。修正 (建议)：需要 "存活" 语义时按 `isAlive()` 过滤 (示例的 `alive` 计数写法)；需要含残骸时明确表达意图。

## 总结

- **四个分组**：
  - 数学/几何 (`MathUtils` / `VectorUtils` / `FastTrig` / `EllipseUtils` / `ShapeUtils` / `CollisionUtils` / `CollectionUtils`)—— 纯静态无状态。
  - 战斗查询 (`CombatUtils` / `AIUtils` / `WeaponUtils` / `DefenseUtils`)—— 当前帧快照。
  - JSON/文件 (`JSONUtils` / `IOUtils`)——`<saves>\common` 读写。
  - 通用 (`ModUtils` / `StringUtils` / `LazyLib`)——MOD 检测、文本与库元信息。
- **六条规则**：单位分工 (`FastTrig` 弧度、其余度)；`VectorUtils` 原地修改语义；战斗查询结果禁止跨战斗缓存；`@Nullable` 判空；`CommonDataJSONObject.save()` 必须显式调用；`isClassPresent` 与 `isModEnabled` 语义分工。每条都有对应的可观察失败结果 (角度偏移、向量被改写、NPE、静默失效、数据归零、分支误开)。
- **复用既有纪律**：暂停守卫、`isAlive()` 过滤、禁止跨战斗缓存与战斗状态内容、战斗插件模型章节完全同源；依赖声明与运行时检测复用前置依赖章；common 文件位置与 settings 合并内容对照。
- **最简流程**：薄入口类 + 四个用法片段一次覆盖四组工具 —— 入口类 `init` 经 `GearInLazyRuntimeProbe` 用 `LazyLib.MOD_ID` + `ModUtils` 检测、经 `GearInLazyJsonStore` 读写 `battles` 计数；报告周期经 `GearInLazyMathUsage` 算距离角度与取点、经 `GearInLazyCombatQuery` 查范围与拦截点、经 `GearInLazyJsonStore` 写 `reports` 计数；`starsector.log` 与 `<saves>\common` 两个可观察位置验证调用真的发生。

战役侧工具 (`campaign` 包) 与渲染侧工具 (`opengl` / `ui` 包) 不在本节范围，由后续内容接续；教程工程对 LazyLib 的依赖声明已在前置依赖章建立，本节开始真正消费它的 API。

## 建议参考项

- `org.lazywizard.lazylib.MathUtils`：数学工具 —— 钳制、距离、范围、最短旋转、随机取点 (度)。
- `org.lazywizard.lazylib.VectorUtils`：向量工具 —— 朝向、夹角、定向、定长/限长/旋转 (原地修改语义，度)。
- `org.lazywizard.lazylib.FastTrig`：三角工具 ——`sin` / `cos` / `atan` / `atan2`(弧度)。
- `org.lazywizard.lazylib.EllipseUtils` / `ShapeUtils` / `CollisionUtils` / `CollectionUtils`：椭圆几何、顶点生成、碰撞查询与集合工具。
- `org.lazywizard.lazylib.combat.CombatUtils`：战斗范围与可见性查询 (当前帧快照)。
- `org.lazywizard.lazylib.combat.AIUtils`：最近目标与拦截点计算 (`@Nullable` 判空)。
- `org.lazywizard.lazylib.combat.WeaponUtils` / `DefenseUtils`：武器弧与装甲只读查询。
- `org.lazywizard.lazylib.JSONUtils`：common JSON 读写；`CommonDataJSONObject.save()` 落盘。
- `org.lazywizard.lazylib.ModUtils`：MOD 启用状态与 classpath 存在性检测。
- `org.lazywizard.lazylib.LazyLib`：库元信息 (`MOD_ID` / `getVersion()` / `getSupportedGameVersion()`) 与日志设置。
- `org.lazywizard.lazylib.IOUtils` / `StringUtils`：受限类加载器下的文件读取与文本换行、缩进。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`getPlayerShip()` / `isPaused()` / `addPlugin(...)`。
- `com.fs.starfarer.api.combat.ShipAPI`：`isAlive()` / `getLocation()` / `getVelocity()` / `getOwner()`。
- `com.fs.starfarer.api.util.IntervalUtil`：周期计时器 (`advance` + `intervalElapsed`)。
- `Starsector\mods\LazyLib\javadoc.zip`：LazyLib 3.0.0 官方 Javadoc(本节速查表的依据)。
- `Starsector\mods\LazyLib\mod_info.json`：`id` `lw_lazylib`、`gameVersion` `0.98a-RC5`。
- `Starsector\mods\LazyLib\lazylib_settings.json`：`enableCaching` / `logLevel` / `logDeprecated` / `crashOnDeprecated` 四键。
- `Starsector\saves\common\`：common JSON 落盘目录 (`GearIn_lazy_demo` 文件所在)。
- `Starsector\starsector-core\starsector.log`：游戏日志，本节日志行的可观察位置。
- `mod_info.json`(本 MOD)：`dependencies` 声明 `lw_lazylib`。
- `tutorial-fixtures\build.gradle`：教程验证工程构建脚本 (`compileC12s02Java` 任务)。
