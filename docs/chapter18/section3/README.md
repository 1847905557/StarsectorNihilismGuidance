# 使用 LazyLib 查询战场

战斗逻辑通常先从一片空间中取得候选，再按敌我关系、战争迷雾和距离选出真正需要处理的目标。本节使用 LazyLib 组织这条查询链，并用战斗粒子直接标出每一步得到的对象。

*本节预期阅读时间：\~30 分钟。*

*本节预期操作时间：\~35 分钟。*

## 学习目标

完成后，您将能够：

- 区分 `CombatUtils` 的空间查询与 `AIUtils` 的敌我关系查询；
- 查询指定范围内的舰船、导弹、弹丸、小行星和综合实体；
- 按实体边缘距离排序候选，并选择可见的敌方目标；
- 根据目标速度计算拦截点，正确处理无解结果；
- 判断舰船系统的当前可用性，并识别右键系统的独立边界；
- 根据粒子颜色验证战场扫描器返回的对象。

## 前置知识

- [C7-S1] 理解 Java 类、方法、字段与入口插件；
- [C8-S2] 理解战斗引擎、舰船、导弹和弹丸对象；
- [C8-S3] 理解每帧战斗插件、暂停判断和 `settings.json` 注册；
- [C18-S2] 理解坐标、距离、向量与碰撞点；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 区分空间查询与关系查询

战斗目标选择通常有两个起点。已知坐标和半径时，`CombatUtils` 从该片空间取回指定类型的实体；已知一艘舰船、导弹或其他战斗实体时，`AIUtils` 根据该实体的 `owner` 判断敌我关系，并在敌方查询中应用战争迷雾。两组方法可以搜索同一片战场，但回答的问题不同。

例如，爆炸效果需要影响半径内的一切舰船，应从 `CombatUtils.getShipsWithinRange(...)` 开始；舰船系统 AI 只关心自己看得见的敌舰，则直接使用 `AIUtils.getNearbyEnemies(...)`。先确定空间候选，再决定是否需要敌我和可见性筛选，可以避免把两层规则混在一次循环中。

本页的扫描器以玩家舰船为中心，同时展示两层结果。蓝色、橙色、黄色和灰色粒子分别标记空间查询取得的最近舰船、导弹、弹丸和小行星；红色粒子标记 `AIUtils` 选出的可见敌舰；紫色粒子标记以 800 su/s 追向该敌舰时的拦截点。

## 使用 CombatUtils 取得战场实体

### 按实体类型查询范围

范围查询接收世界坐标 `location` 和半径 `range`，返回当前战斗中落在圆形区域内的对象。下面的局部代码可以放入每帧战斗插件、舰船系统效果或武器效果的回调中：

```java
Vector2f center = ship.getLocation();
List<ShipAPI> ships = CombatUtils.getShipsWithinRange(center, 1600f);
List<MissileAPI> missiles = CombatUtils.getMissilesWithinRange(center, 1600f);
List<DamagingProjectileAPI> projectiles =
        CombatUtils.getProjectilesWithinRange(center, 1600f);
List<CombatEntityAPI> asteroids =
        CombatUtils.getAsteroidsWithinRange(center, 1600f);
```

`getShipsWithinRange(...)` 返回舰船并排除 shuttle pod。`getMissilesWithinRange(...)` 只返回导弹，`getProjectilesWithinRange(...)` 则返回导弹以外的 `DamagingProjectileAPI`，两份列表不会把同一枚导弹重复计算。小行星使用更宽泛的 `CombatEntityAPI`，因为它不属于舰船或弹丸接口。

这四个方法只检查实体类型和空间位置。列表中可以同时出现友军、敌军和中立对象，也可以出现当前观察方尚未发现的敌方实体。范围伤害、冲击波和区域视觉通常需要这种原始候选；战术 AI 还要继续检查 `owner` 或交给 `AIUtils` 筛选。

### 查询目标点和综合实体

`getObjectivesWithinRange(...)` 返回范围内的战斗目标点，适合判断导航浮标、传感器阵列等目标是否靠近当前单位。`getEntitiesWithinRange(...)` 把舰船、导弹、弹丸和小行星合并为 `CombatEntityAPI` 列表，适合射线、传送落点和统一排斥区域。

综合列表省去了多次查询，但调用方仍要用 `instanceof` 或碰撞类别决定后续行为。只处理单一类型时，使用对应的专用方法可以让循环中的对象契约更明确。

### 判断另一方能否看见实体

`isVisibleToSide(entity, side)` 以战斗阵营编号检查战争迷雾。`side` 通常来自观察者的 `getOwner()`；友方和中立实体始终可见，敌方实体则取决于当前一方的视野。

`CombatUtils` 的范围方法没有自动调用这项检查。需要从空间候选中建立战术目标时，可以在循环中使用 `CombatUtils.isVisibleToSide(candidate, ship.getOwner())`。`AIUtils` 的敌方舰船和敌方导弹方法已经应用同一可见性规则，对它们的结果再次检查不会改变集合。

### 取得舰队成员和处理特殊入口

战斗舰船由 `ShipAPI` 表示，生涯舰队中的持久成员由 `FleetMemberAPI` 表示。`getFleetMember(ship)` 会寻找与战斗舰船对应的舰队成员；模拟战、临时生成物或特殊战斗对象没有对应成员时返回 `null`。需要读取舰船设计、船员或持久舰队数据时，先确认返回对象存在。

`spawnShipOrWingDirectly(...)` 按 variant 或 wing ID、成员类型、战斗方、战备值、位置和朝向直接生成舰船或战机联队。它属于战斗生成入口，调用结果会立即改变当前战场，适合任务战或脚本化增援。

`toWorldCoordinates(...)` 把屏幕坐标转换为世界坐标，`toScreenCoordinates(...)` 执行相反转换，`centerViewport(...)` 则把战斗视口中心移动到指定世界坐标。它们服务于输入、HUD 和镜头效果，不参与实体筛选。

## 使用 AIUtils 筛选战斗目标

### 取得最近对象和局部集合

`AIUtils` 从传入的 `CombatEntityAPI` 读取位置和所属方。下面的局部代码同时展示最近对象与 1600 su 内的关系集合：

```java
ShipAPI nearestEnemy = AIUtils.getNearestEnemy(ship);
ShipAPI nearestAlly = AIUtils.getNearestAlly(ship);
ShipAPI nearestVisibleShip = AIUtils.getNearestShip(ship);
MissileAPI nearestVisibleMissile = AIUtils.getNearestMissile(ship);
MissileAPI nearestEnemyMissile = AIUtils.getNearestEnemyMissile(ship);
BattleObjectiveAPI nearestObjective = AIUtils.getNearestObjective(ship);

List<ShipAPI> nearbyEnemies = AIUtils.getNearbyEnemies(ship, 1600f);
List<ShipAPI> nearbyAllies = AIUtils.getNearbyAllies(ship, 1600f);
List<MissileAPI> nearbyEnemyMissiles =
        AIUtils.getNearbyEnemyMissiles(ship, 1600f);
```

`getNearestEnemy(...)` 和 `getNearestEnemyMissile(...)` 同时限定敌对关系与可见性。`getNearestAlly(...)` 只选择友方舰船；`getNearestShip(...)` 与 `getNearestMissile(...)` 接受任意可见阵营，适合碰撞规避或寻找最近对象。`getNearestObjective(...)` 查找最近的战斗目标点。

最近对象方法在没有候选时返回 `null`。局部集合方法返回列表，调用方可以继续按舰体级别、碰撞类别、相位状态、伤害载荷或自定义分数筛选。

### 对比局部列表和全图列表

`getEnemiesOnMap(...)`、`getAlliesOnMap(...)` 与 `getEnemyMissilesOnMap(...)` 查询整张战场。敌方版本继续遵守战争迷雾，友方版本返回当前仍在地图上的全部盟友。编队协调、全局目标分配和低频战术评估适合使用这些集合。

全图查询的工作量随战场实体数量增长。只处理舰船附近威胁时，`getNearbyEnemies(...)`、`getNearbyAllies(...)` 与 `getNearbyEnemyMissiles(...)` 已经表达了明确半径，也能避免每 0.20 秒遍历整张战场。

## 排序并选择最终目标

查询方法返回列表，不承诺列表已经按距离排列。`CollectionUtils.SortEntitiesByDistance` 接收排序中心；第二个构造参数设为 `true` 时，比较的是中心距离减去实体碰撞半径，也就是实体边缘离观察点的距离。

扫描器复制列表后再排序，保留调用方收到的原始集合：

```java
private static <T extends CombatEntityAPI> T nearest(List<T> entities, Vector2f origin) {
    if (entities.isEmpty()) {
        return null;
    }
    List<T> sorted = new ArrayList<T>(entities);
    sorted.sort(new CollectionUtils.SortEntitiesByDistance(origin, true));
    return sorted.get(0);
}

ShipAPI enemy = nearest(AIUtils.getNearbyEnemies(player, SCAN_RANGE), origin);
Vector2f intercept = enemy == null ? null : AIUtils.getBestInterceptPoint(
        origin, INTERCEPT_SPEED, enemy.getLocation(), enemy.getVelocity());
```

大型舰船的中心可能比护卫舰更远，但舰体边缘已经进入作用范围。计入碰撞半径的排序会优先选择真正更靠近效果边缘的实体。单参数构造器同样计入碰撞半径；只比较中心坐标时必须使用 `new SortEntitiesByDistance(origin, false)`。

排序只决定候选顺序。敌我关系、相位状态、碰撞类别和业务优先级仍应在排序前完成筛选，否则一个更近但不可处理的实体会占据首位。

## 计算移动目标的拦截点

直接瞄准目标当前位置会在目标横向移动时产生稳定的落后量。`getBestInterceptPoint(point, speed, targetLoc, targetVel)` 解算匀速运动下的会合位置：`point` 是追击物体的起点，`speed` 是追击速度，后两个参数分别是目标当前位置和速度。

距离排序得到 `enemy` 后，代码把玩家舰船位置、800 su/s 追击速度、敌舰位置和敌舰速度交给 `getBestInterceptPoint(...)`。紫色粒子会随敌舰速度和方向移动。目标速度过高、追击速度不足或几何关系没有会合解时，方法返回 `null`；此时调用方可以直瞄当前位置、保留旧目标或停止开火。该计算采用目标当前速度，不预测后续加速和转向。

## 在所属回调中执行动作

查询结果可以被武器 AI、导弹 AI、舰船系统 AI、系统效果或每帧插件使用。执行动作的方法需要留在拥有对应生命周期的角色中，避免只读扫描器意外改变战斗。

### 判断主舰船系统能否使用

舰船系统 AI 的 `advance(...)` 可以先调用 `canUseSystemThisFrame(ship)`，再检查当前战术条件：

```java
private ShipAPI ship;

@Override
public void init(
        ShipAPI ship,
        ShipSystemAPI system,
        ShipwideAIFlags flags,
        CombatEngineAPI engine) {
    this.ship = ship;
}

@Override
public void advance(
        float amount,
        Vector2f missileDangerDir,
        Vector2f collisionDangerDir,
        ShipAPI target) {
    if (!AIUtils.canUseSystemThisFrame(ship)) {
        return;
    }
    if (!AIUtils.getNearbyEnemyMissiles(ship, 600f).isEmpty()) {
        ship.useSystem();
    }
}
```

这里的 `ship` 由 `ShipSystemAIScript.init(...)` 保存。方法检查 `ship.getSystem()` 是否存在，并综合过载或散逸、弹药、冷却、激活状态与可用幅能；返回 `true` 表示本帧发出主系统指令能够产生作用，系统已经开启时也可能返回 `true`。

`canUseSystemThisFrame(ship)` 只检查主舰船系统。右键系统走另一条控制入口，相位斗篷通过 `ship.getPhaseCloak()` 取得；右键防御系统及相位斗篷的可用性必须根据该对象的状态和规格判断。

### 向实体施加力

舰船系统效果取得范围候选后，可以使用方向向量或角度重载推动实体：

```java
@Override
public void apply(
        MutableShipStatsAPI stats,
        String id,
        State state,
        float effectLevel) {
    ShipAPI ship = (ShipAPI) stats.getEntity();
    for (MissileAPI missile : CombatUtils.getMissilesWithinRange(
            ship.getLocation(), 600f)) {
        if (missile.getOwner() == ship.getOwner()) {
            continue;
        }
        Vector2f direction = Vector2f.sub(
                missile.getLocation(), ship.getLocation(), null);
        CombatUtils.applyForce(missile, direction, 120f * effectLevel);
    }

    CombatUtils.applyForce(ship, ship.getFacing(), 40f * effectLevel);
}
```

向量重载会自动归一化 `direction`，角度重载按游戏角度施力。`force` 的单位考虑实体质量：同样的数值作用于高质量舰船时速度变化更小。持续系统通常让力随 `effectLevel` 缩放，避免进入和退出阶段突然跳变。

## 补充武器与防御查询

### 使用 WeaponUtils 约束武器目标

空间查询以任意世界坐标为中心，武器查询还要考虑炮塔位置、射程和射界。自动开火 AI 可以先取得弧内敌方导弹，再按瞄准时间选择目标：

```java
List<MissileAPI> missiles = WeaponUtils.getEnemyMissilesInArc(weapon);
MissileAPI nearest = WeaponUtils.getNearestEnemyMissileInArc(weapon);
if (nearest != null && WeaponUtils.isWithinArc(nearest, weapon)) {
    float aimTime = WeaponUtils.getTimeToAim(weapon, nearest.getLocation());
    WeaponUtils.aimTowardsPoint(weapon, nearest.getLocation(), amount);
}
```

`getEnemiesInArc(...)` 与 `getAlliesInArc(...)` 返回射程和射界内的舰船；`getNearestEnemyInArc(...)` 与 `getNearestAllyInArc(...)` 直接返回最近对象。`isWithinArc(...)` 同时检查射程和射界，`getTimeToAim(...)` 给出炮塔转到指定坐标所需时间，`aimTowardsPoint(...)` 按本帧 `amount` 手动推进炮塔朝向。

这些方法适合 `AutofireAIPlugin` 或武器每帧效果。调用手动瞄准后，目标保持、切换阈值和是否开火仍由武器角色负责。

### 使用 DefenseUtils 读取受损位置

`DefenseUtils.getArmorValue(ship, location)` 把世界坐标映射到装甲格并返回当前装甲值；`getArmorDamage(...)` 返回该处累计损失，`getArmorLevel(...)` 返回装甲比例。坐标落在装甲网格之外时，这三个方法返回 `DefenseUtils.NOT_IN_GRID`。

`getDefenseAtPoint(...)` 进一步判断世界坐标命中护盾、装甲、船体或落空，并把舰体实际边界纳入判断。`hasArmorDamage(...)` 与 `hasHullDamage(...)` 适合快速决定维修或防御行为，`getMostDamagedArmorCell(...)` 返回损伤最重的装甲格坐标；舰船装甲完整时返回 `null`。

防御查询提供目标评分所需的数据，不负责选择目标。维修无人机可以从友军列表中选择已经受损的舰船，攻击 AI 则可以把薄弱装甲位置转换为世界坐标后作为瞄准依据。

## 常用方法速查表

| 类 | 方法组 | 返回或作用 |
|---|---|---|
| `CombatUtils` | `getShipsWithinRange`、`getMissilesWithinRange`、`getProjectilesWithinRange`、`getAsteroidsWithinRange` | 按世界坐标和半径返回指定类型的空间候选 |
| `CombatUtils` | `getObjectivesWithinRange`、`getEntitiesWithinRange` | 返回目标点或综合战斗实体 |
| `CombatUtils` | `isVisibleToSide` | 检查一个实体对指定战斗方是否可见 |
| `CombatUtils` | `getFleetMember` | 取得战斗舰船对应的生涯舰队成员 |
| `CombatUtils` | `applyForce` | 按方向向量或游戏角度向实体施力 |
| `CombatUtils` | `toWorldCoordinates`、`toScreenCoordinates`、`centerViewport` | 转换屏幕/世界坐标或移动视口 |
| `CombatUtils` | `spawnShipOrWingDirectly` | 在当前战斗中直接生成舰船或战机联队 |
| `AIUtils` | `getNearestEnemy`、`getNearestAlly`、`getNearestShip` | 按关系和可见性取得最近舰船 |
| `AIUtils` | `getNearestEnemyMissile`、`getNearestMissile` | 取得最近敌方导弹或任意可见导弹 |
| `AIUtils` | `getNearestObjective` | 取得最近战斗目标点 |
| `AIUtils` | `getNearbyEnemies`、`getNearbyAllies`、`getNearbyEnemyMissiles` | 按查询实体和半径返回关系集合 |
| `AIUtils` | `getEnemiesOnMap`、`getAlliesOnMap`、`getEnemyMissilesOnMap` | 返回整张战场上的关系集合 |
| `AIUtils` | `getBestInterceptPoint` | 解算匀速目标的拦截坐标，无解时返回 `null` |
| `AIUtils` | `canUseSystemThisFrame` | 判断本帧能否操作 `ship.getSystem()` |
| `CollectionUtils` | `SortEntitiesByDistance` | 按中心距离或实体边缘距离排序战斗实体 |

## 注册战斗扫描器

每帧战斗插件通过 `data/config/settings.json` 的 `plugins` 对象登记。注册 key 在合并后的配置中保持唯一，值由 Java 包名和类名组成：

```json
{
    "plugins": {
        "gearInCombatScanner": "data.scripts.gearin.lib.GearInCombatScanner"
    }
}
```

游戏为每场战斗创建一个扫描器实例。插件每 0.20 秒执行一次查询，1600 su 范围能够覆盖玩家周围的常见交战距离；较短间隔让粒子位置连续，查询频率也只有每秒 5 次。粒子持续 0.25 秒，相邻两次结果会短暂重叠，目标移动时形成连续标记。

## 完整最小例

`GearInCombatScanner` 只读取当前战斗状态并生成诊断粒子。空间查询与关系查询各自保留结果，最终敌舰和拦截点使用单独颜色显示。

::: details 完整成品：GearInCombatScanner

```java
package data.scripts.gearin.lib;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.lazywizard.lazylib.CollectionUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * Scans the area around the player ship and marks representative query results.
 * The plugin only reads combat state and creates diagnostic particles.
 */
public final class GearInCombatScanner extends BaseEveryFrameCombatPlugin {
    private static final float SCAN_RANGE = 1600f;
    private static final float SCAN_INTERVAL = 0.20f;
    private static final float INTERCEPT_SPEED = 800f;
    private static final float PARTICLE_DURATION = 0.25f;

    private static final Color SHIP_COLOR = new Color(80, 160, 255, 220);
    private static final Color MISSILE_COLOR = new Color(255, 150, 40, 220);
    private static final Color PROJECTILE_COLOR = new Color(255, 225, 70, 220);
    private static final Color ASTEROID_COLOR = new Color(170, 170, 170, 220);
    private static final Color ENEMY_COLOR = new Color(255, 70, 70, 240);
    private static final Color INTERCEPT_COLOR = new Color(190, 90, 255, 240);

    private float elapsed;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) {
            return;
        }

        elapsed += amount;
        if (elapsed < SCAN_INTERVAL) {
            return;
        }
        elapsed = 0f;

        ShipAPI player = engine.getPlayerShip();
        if (player == null || !player.isAlive() || !engine.isEntityInPlay(player)) {
            return;
        }

        Vector2f origin = player.getLocation();
        List<ShipAPI> ships = CombatUtils.getShipsWithinRange(origin, SCAN_RANGE);
        ships.remove(player);

        mark(engine, nearest(ships, origin), 18f, SHIP_COLOR);
        mark(engine, nearest(CombatUtils.getMissilesWithinRange(origin, SCAN_RANGE), origin),
                14f, MISSILE_COLOR);
        mark(engine, nearest(CombatUtils.getProjectilesWithinRange(origin, SCAN_RANGE), origin),
                10f, PROJECTILE_COLOR);
        mark(engine, nearest(CombatUtils.getAsteroidsWithinRange(origin, SCAN_RANGE), origin),
                18f, ASTEROID_COLOR);

        ShipAPI enemy = nearest(AIUtils.getNearbyEnemies(player, SCAN_RANGE), origin);
        mark(engine, enemy, 28f, ENEMY_COLOR);
        if (enemy != null) {
            Vector2f intercept = AIUtils.getBestInterceptPoint(
                    origin, INTERCEPT_SPEED, enemy.getLocation(), enemy.getVelocity());
            mark(engine, intercept, 22f, INTERCEPT_COLOR);
        }
    }

    private static <T extends CombatEntityAPI> T nearest(List<T> entities, Vector2f origin) {
        if (entities.isEmpty()) {
            return null;
        }
        List<T> sorted = new ArrayList<T>(entities);
        sorted.sort(new CollectionUtils.SortEntitiesByDistance(origin, true));
        return sorted.get(0);
    }

    private static void mark(
            CombatEngineAPI engine,
            CombatEntityAPI entity,
            float size,
            Color color) {
        if (entity != null) {
            mark(engine, entity.getLocation(), size, color);
        }
    }

    private static void mark(
            CombatEngineAPI engine,
            Vector2f location,
            float size,
            Color color) {
        if (location != null) {
            engine.addSmoothParticle(
                    location, new Vector2f(), size, 1f, PARTICLE_DURATION, color);
        }
    }
}
```

:::

## 验证方案

前置数据：`GearInCombatScanner` 已登记到 `data/config/settings.json`，并准备一场包含玩家舰船、敌舰、导弹、弹丸与小行星的模拟战。

1. 执行 `gradle jar`，把包含扫描器的 JAR 放入 MOD 并启动游戏。
2. 进入模拟战，观察玩家舰船周围的蓝色、橙色、黄色和灰色粒子。
3. 暂停战斗 3 秒，确认粒子停止刷新；恢复战斗后继续观察。
4. 把两艘碰撞半径不同的舰船放在相近位置，确认蓝色粒子标记边缘距离更近者。
5. 让一艘可见敌舰进入 1600 su，确认它出现红色粒子，同时在前方出现紫色拦截点。
6. 让敌舰横向加速和转向，观察紫色粒子随当前速度改变位置。
7. 让敌舰离开视野，比较红色目标标记与蓝色空间候选标记。
8. 把全部对象移到 1600 su 外，确认对应类型的粒子在 0.25 秒后消失。
9. 在独立主系统 AI 中运行 `canUseSystemThisFrame` 片段，分别观察冷却、过载和可用状态。
10. 在相位舰上读取 `getPhaseCloak()`，确认右键系统状态由该对象提供。

预期结果：

- 四类空间候选分别使用蓝色、橙色、黄色和灰色粒子，每类只标记边缘距离最近的对象；
- 暂停期间扫描计时冻结，恢复后重新按 0.20 秒间隔刷新；
- 红色粒子只标记玩家一方当前可见的最近敌舰；
- 蓝色空间候选可以标记仍在范围内但尚未进入关系筛选结果的舰船；
- 紫色粒子随目标当前位置和速度变化，无拦截解时不生成；
- 1600 su 外的对象不会进入扫描结果，旧粒子在 0.25 秒后结束；
- 主系统冷却、过载或资源不足时，`canUseSystemThisFrame` 返回 `false`；
- 相位斗篷状态来自 `getPhaseCloak()`，不由主系统判断代替；
- `starsector.log` 没有未处理异常。

## 故障分析

- **战斗中没有任何标记粒子**：`settings.json` 缺少插件登记、注册 key 被覆盖或完整类名与包声明不一致。
    - **解决方案**：核对 `data/config/settings.json` 的 `plugins` 条目和 JAR 内的 `data.scripts.gearin.lib.GearInCombatScanner`。

- **暂停时粒子仍不断增加**：计时和查询发生在 `engine.isPaused()` 判断之前。
    - **解决方案**：把 `elapsed` 累加和全部查询放在暂停守卫之后。

- **范围内出现友军或不可见敌军**：`CombatUtils` 的空间查询只检查类型和位置。
    - **解决方案**：按 `owner` 和 `isVisibleToSide(...)` 继续筛选，或改用对应的 `AIUtils` 关系查询。

- **对 AIUtils 结果重复执行可见性循环**：敌方关系方法已经过滤战争迷雾，第二次判断只增加工作量。
    - **解决方案**：直接使用 `getNearbyEnemies(...)`、`getEnemiesOnMap(...)` 或敌方导弹方法的返回集合。

- **大规模战斗出现周期性卡顿**：高频回调反复使用全图查询并处理全部实体。
    - **解决方案**：局部行为改用 nearby/range 方法，并用固定间隔限制刷新频率。

- **大型舰船看起来更近却没有排在首位**：排序使用了实体中心距离，画面观察使用的是舰体边缘。
    - **解决方案**：构造 `SortEntitiesByDistance(origin, true)`，让比较包含碰撞半径。

- **拦截点代码产生空引用异常**：当前速度关系没有会合解时，`getBestInterceptPoint(...)` 返回 `null`。
    - **解决方案**：使用拦截坐标前检查返回值，并为无解状态选择直瞄或放弃目标。

- **相位斗篷判断始终与实际状态不一致**：`canUseSystemThisFrame` 读取的是 `ship.getSystem()`。
    - **解决方案**：从 `ship.getPhaseCloak()` 取得右键系统，并检查该对象的状态与规格。

## 总结

`CombatUtils` 从坐标和半径建立原始候选，`AIUtils` 再根据查询实体的所属方和战争迷雾建立战术目标。距离排序与拦截点计算把集合收敛为一个可执行结果，武器、系统和效果角色再决定如何使用它。

扫描器用每秒 5 次的只读查询把这些差异直接显示在战场上。空间候选、可见敌舰和预测坐标使用独立颜色，您可以从画面判断查询链停在了哪一层。

## 建议参考项

- `org.lazywizard.lazylib.combat.CombatUtils`：战场范围查询、可见性、坐标转换、实体生成与施力；
- `org.lazywizard.lazylib.combat.AIUtils`：敌我关系查询、战争迷雾、最近目标、拦截点与主系统可用性；
- `org.lazywizard.lazylib.CollectionUtils.SortEntitiesByDistance`：按指定中心排序战斗实体；
- `org.lazywizard.lazylib.combat.WeaponUtils`：武器射界、弧内目标、瞄准时间和炮塔朝向；
- `org.lazywizard.lazylib.combat.DefenseUtils`：装甲格、防御类型和舰船受损状态。
