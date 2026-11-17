# 使用 LazyLib 查询战场

离爆炸点最近的那艘船，和武器真正能锁定的目标，本来就不是同一套筛选条件。`CombatUtils` 按纯空间半径找出周边一切实体，`AIUtils` 则会同时滤掉友军、残骸和战争迷雾另一侧的敌舰。

下面在玩家舰周围同时挂起空间候选、可见敌舰和追击拦截点三组粒子标记，把这几组长相相近的战场查询拆开对照。

---

_本节预期阅读时间：\~25 分钟。_

_本节预期操作时间：\~30 分钟。_

## 前置知识

- [C7-S1] 的入口插件与构建后的加载；
- [C8-S1] 的战斗引擎、每帧战斗插件、暂停判断和 `settings.json` 注册；
- [C8-S3] 的战斗实体 (`CombatEntityAPI`)、导弹与弹丸 (`DamagingProjectileAPI`)；
- [C18-S2] 的坐标、距离、向量与碰撞点。

## 注册战斗扫描器

先将本节完整的 `GearInCombatScanner` 保存到 `src/data/scripts/gearin/lib/GearInCombatScanner.java`，再向既有 `data/config/settings.json` 的 `plugins` 对象追加：

```json
{
    "plugins": {
        "GearIn_combatScanner": "data.scripts.gearin.lib.GearInCombatScanner"
    }
}
```

游戏为每场战斗创建一个扫描器实例。插件每 0.20 秒执行一次查询，1600 su 范围能够覆盖玩家周围的常见交战距离；较短间隔让粒子位置连续，查询频率也只有每秒 5 次。粒子持续 0.25 秒，相邻两次结果会短暂重叠，目标移动时形成连续标记。

## 区分空间查询与关系查询

**空间查询按类型与位置取得候选，关系查询进一步使用观察者的所属方。** `CombatUtils` 接收坐标和半径，`AIUtils` 接收一艘舰船、导弹或其他战斗实体，从它的 `owner` 判断关系。敌舰查询还会处理战争迷雾；导弹的集合与 nearest 入口各有筛选条件，后面分别说明。

例如，爆炸效果需要影响半径内的一切舰船，应从 `CombatUtils.getShipsWithinRange(...)` 开始；舰船系统 AI 只筛选当前处于可见状态的敌舰，则直接使用 `AIUtils.getNearbyEnemies(...)`。先确定空间候选，再决定是否需要敌我和可见性筛选，可以避免把两层规则混在一次循环中。

本页的扫描器以玩家舰船为中心，同时展示两层结果。蓝色、橙色、黄色和灰色粒子分别标记空间查询取得的最近舰船、导弹、弹丸和小行星；红色粒子标记 `AIUtils` 选出的可见敌舰；紫色粒子标记以 800 su/s 追向该敌舰时的拦截点。

## 使用 CombatUtils 取得战场实体

### 按实体类型查询范围

范围查询接收世界坐标 `location` 和半径 `range`。舰船、小行星按实体边缘到坐标的距离判断，导弹和弹丸按中心坐标判断；对照同一半径时，也要对照实际重载使用的对象。下面的局部代码可以放入每帧战斗插件、舰船系统效果或武器效果的回调中：

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

这些范围入口只检查实体类型和空间位置。列表中可以同时出现友军、敌军和中立对象，也可以出现当前观察方尚未发现的敌方实体。范围伤害、冲击波和区域视觉通常需要这种原始候选；战术 AI 还要继续检查 `owner` 或交给 `AIUtils` 筛选。

### 查询目标点和综合实体

`getObjectivesWithinRange(...)` 返回范围内的战斗目标点，适合判断导航浮标、传感器阵列等目标是否靠近当前单位。`getEntitiesWithinRange(...)` 把舰船、导弹、弹丸和小行星合并为 `CombatEntityAPI` 列表，适合射线、传送落点和统一排斥区域。

综合列表便于在同一次循环中处理不同实体，再用 `instanceof` 或碰撞类别选择行为。只处理导弹时，使用导弹列表就能限定后续对象。综合查询按实体重载计算距离，弹丸与导弹的边界也计入碰撞半径，与它们各自的专用范围入口有所区别。

### 判断另一方能否看见实体

`isVisibleToSide(entity, side)` 以战斗阵营编号检查战争迷雾。`side` 通常来自观察者的 `getOwner()`；友方和中立实体始终可见，敌方实体则取决于当前一方的视野。

`CombatUtils` 的范围方法没有自动调用这项检查。需要从空间候选建立可见目标时，在循环中使用 `CombatUtils.isVisibleToSide(candidate, ship.getOwner())`。`AIUtils` 的敌舰查询和 `getNearestEnemyMissile(...)` 已应用可见性规则；导弹集合入口仍要由使用方决定是否补充检查。

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

`getNearestEnemy(...)` 和 `getNearestEnemyMissile(...)` 同时限定所属方差异与可见性。`getNearestAlly(...)` 选择友舰，`getNearestShip(...)` 与 `getNearestMissile(...)` 接受任意可见阵营，`getNearestObjective(...)` 则寻找最近的战斗目标点。舰船查询还会排除废船与 shuttle pod，友舰查询排除观察者自身。

最近对象方法在没有候选时返回 `null`。舰船的 nearest 方法比较候选碰撞圆到观察者坐标的距离，导弹的 nearest 方法比较中心坐标距离；nearby 集合则按两实体碰撞圆之间的间隔限定半径。取得列表后，调用方继续按船体等级、相位状态、碰撞类别、载荷或业务分数筛选。

### 对比局部列表和全图列表

`getEnemiesOnMap(...)` 与 `getAlliesOnMap(...)` 取得整张战场的敌舰、友舰集合；敌舰继续遵守战争迷雾，友舰排除观察者，双方都排除废船与 shuttle pod。它们适合编队协调、全局目标分配和低频战术评估。

`getEnemyMissilesOnMap(...)` 的实际条件不同：它纳入所属方不同的导弹，也纳入本方已熄火的导弹，查询过程没有检查战争迷雾。`getNearbyEnemyMissiles(...)` 再从这份集合按距离筛选，保留同样的关系条件。

防御 AI 拿到这份导弹集合后，仍要按己方威胁口径自行补齐所属方、可见性与熄火状态判断 —— 单靠方法名里的 `Enemy` 并不能替点防武器做完全部取舍。

nearby 方法限定返回集合的半径，后续行为只处理附近候选，但它们仍从全图关系集合开始筛选。敌舰查询启用 LazyLib 缓存时可以取得缓存集合，其余遍历与后续处理仍有各自的工作量。舰船范围查询遍历舰船列表，导弹、小行星的专用空间查询使用相应网格，因此扫描器仍按间隔执行。

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

大型舰船的中心可能比护卫舰更远，但碰撞圆边缘更接近观察点。计入碰撞半径的排序会优先选择真正更靠近效果边缘的实体。单参数构造器同样计入碰撞半径；只比较中心坐标时必须使用 `new CollectionUtils.SortEntitiesByDistance(origin, false)`。

排序只决定候选顺序。敌我关系、相位状态、碰撞类别和业务优先级仍应在排序前完成筛选，这样排在首位的对象才符合当前动作的条件。

## 计算移动目标的拦截点

直接瞄准目标当前位置会在目标横向移动时产生稳定的落后量。`getBestInterceptPoint(point, speed, targetLoc, targetVel)` 解算匀速运动下的会合位置：`point` 是追击物体的起点，`speed` 是追击速度，后两个参数分别是目标当前位置和速度。

距离排序得到 `enemy` 后，代码把玩家舰船位置、800 su/s 追击速度、敌舰位置和敌舰速度交给 `getBestInterceptPoint(...)`。紫色粒子会随敌舰速度和方向移动。目标速度过高、追击速度不足或几何关系没有会合解时，方法返回 `null`；此时调用方可以直瞄当前位置、保留旧目标或停止开火。该计算采用目标当前速度，不预测后续加速和转向。

## 在所属回调中执行动作

扫描结果已经能显示出来。需要据此开火、启动系统或施力时，把下列局部片段放到相应角色的回调，沿用该角色已经取得的舰船、武器与时间参数。**谁负责动作，谁接收查询结果。**扫描器继续承担观察。

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

这里的 `ship` 由 `ShipSystemAIScript.init(...)` 保存。方法检查主系统是否存在，并判断过载或散逸、弹药、冷却、激活状态与可用幅能。`true` 表示这些通用条件通过，已开启且可以切换的系统也可能通过；特定系统效果的可用条件仍由游戏侧处理。

片段用范围内导弹集合非空作为启动条件。按前面的集合规则，本方已熄火导弹也可能使条件成立；实际防御 AI 再根据自己的威胁定义筛选。

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
    CombatEngineAPI engine = Global.getCombatEngine();
    if (engine.isPaused()) {
        return;
    }
    ShipAPI ship = (ShipAPI) stats.getEntity();
    float amount = engine.getElapsedInLastFrame();
    for (MissileAPI missile : CombatUtils.getMissilesWithinRange(
            ship.getLocation(), 600f)) {
        if (missile.getOwner() == ship.getOwner()) {
            continue;
        }
        Vector2f direction = Vector2f.sub(
                missile.getLocation(), ship.getLocation(), null);
        CombatUtils.applyForce(missile, direction, 120f * effectLevel * amount);
    }

    CombatUtils.applyForce(ship, ship.getFacing(), 40f * effectLevel * amount);
}
```

向量重载会自动归一化 `direction`，角度重载按游戏角度施力。**`applyForce(...)` 每次直接改变速度。**同样的参数作用于高质量实体时，速度变化更小。持续回调把参数乘以本帧 `amount`，让每秒效果随实际经过的时间累计，再用 `effectLevel` 控制进入和退出的变化。`getElapsedInLastFrame()` 在暂停时仍返回当前帧时间，先按 `engine.isPaused()` 结束这一帧，力才会随战斗暂停。

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

`getEnemiesInArc(...)` 与 `getAlliesInArc(...)` 从相应舰船集合按射程和射界筛选；nearest 入口直接遍历全场舰船，**但只检查所属方差异、射界与射程**，既不管可见性也不排除残骸和逃生舱，因此仍要按当前 AI 需要补齐筛选。导弹集合入口沿用 `AIUtils.getEnemyMissilesOnMap(...)`，nearest 导弹入口则自行排除同方导弹，并按中心距离与射界选择。

`isWithinArc(...)` 判断射程和射界，还接受碰撞圆与射界边线相交的对象。`getTimeToAim(...)` 用坐标到射界的角差除以转速，坐标已在射界内时返回 0；`aimTowardsPoint(...)` 才按 `amount` 直接推进炮塔当前朝向。两者的计算用途要分别对照。

这些方法适合 `AutofireAIPlugin` 或武器每帧效果。调用手动瞄准后，目标保持、切换阈值和是否开火仍由武器角色负责。

### 使用 DefenseUtils 读取受损位置

`DefenseUtils.getArmorValue(ship, location)` 把世界坐标映射到装甲格并返回当前装甲值；`getArmorDamage(...)` 返回该处累计损失，`getArmorLevel(...)` 返回装甲比例。坐标位于装甲网格之外时，这三个方法返回 `DefenseUtils.NOT_IN_GRID`。

`getDefenseAtPoint(...)` 先判断相位斗篷与舰体边界，再判断该坐标是否由开启的护盾覆盖，最后按装甲值区分装甲与船体。`hasArmorDamage(...)` 与 `hasHullDamage(...)` 用于判断维修需要，`getMostDamagedArmorCell(...)` 返回装甲最少的网格坐标；装甲完整时返回 `null`。

防御查询提供目标评分所需的数据，不负责选择目标。维修无人机可以从友军列表中选择已经受损的舰船，攻击 AI 则可以把薄弱装甲位置转换为世界坐标后作为瞄准依据。

### 取得持久成员、生成对象与调整视口

战斗舰船使用 `ShipAPI`，对应的舰队成员使用 `FleetMemberAPI`。`getFleetMember(ship)` 从战斗舰队管理器寻找已部署或预备的成员，缺少对应关系时返回 `null`。模拟战与临时对象也按是否具有这个成员关系判断；取得对象以后，再读取设计、船员或舰队数据。

`spawnShipOrWingDirectly(...)` 根据 variant 或 wing ID、成员类型、战斗方、战备值、位置与朝向直接生成舰船或联队，会立即改变战场，适合任务战和脚本化增援。

`toWorldCoordinates(...)` 将屏幕坐标转换成世界坐标，`toScreenCoordinates(...)` 做相反转换，`centerViewport(...)` 把视口中心移到指定坐标。它们用于输入、HUD 与镜头，接收的是当前用途所需的位置。

## 常用方法速查表

| 类                | 方法组                                                                                                  | 返回或作用                                |
| ----------------- | ------------------------------------------------------------------------------------------------------- | ----------------------------------------- |
| `CombatUtils`     | `getShipsWithinRange`、`getMissilesWithinRange`、`getProjectilesWithinRange`、`getAsteroidsWithinRange` | 按世界坐标和半径返回指定类型的空间候选    |
| `CombatUtils`     | `getObjectivesWithinRange`、`getEntitiesWithinRange`                                                    | 返回目标点或综合战斗实体                  |
| `CombatUtils`     | `isVisibleToSide`                                                                                       | 检查一个实体对指定战斗方是否可见          |
| `CombatUtils`     | `getFleetMember`                                                                                        | 取得战斗舰船对应的生涯舰队成员            |
| `CombatUtils`     | `applyForce`                                                                                            | 按方向向量或游戏角度向实体施力            |
| `CombatUtils`     | `toWorldCoordinates`、`toScreenCoordinates`、`centerViewport`                                           | 转换屏幕/世界坐标或移动视口               |
| `CombatUtils`     | `spawnShipOrWingDirectly`                                                                               | 在当前战斗中直接生成舰船或战机联队        |
| `AIUtils`         | `getNearestEnemy`、`getNearestAlly`、`getNearestShip`                                                   | 按关系和可见性取得最近舰船                |
| `AIUtils`         | `getNearestEnemyMissile`、`getNearestMissile`                                                           | 取得最近敌方导弹或任意可见导弹            |
| `AIUtils`         | `getNearestObjective`                                                                                   | 取得最近战斗目标点                        |
| `AIUtils`         | `getNearbyEnemies`、`getNearbyAllies`、`getNearbyEnemyMissiles`                                         | 按查询实体和半径返回关系集合              |
| `AIUtils`         | `getEnemiesOnMap`、`getAlliesOnMap`、`getEnemyMissilesOnMap`                                            | 返回整张战场上的关系集合                  |
| `AIUtils`         | `getBestInterceptPoint`                                                                                 | 解算匀速目标的拦截坐标，无解时返回 `null` |
| `AIUtils`         | `canUseSystemThisFrame`                                                                                 | 判断本帧能否操作 `ship.getSystem()`       |
| `CollectionUtils` | `SortEntitiesByDistance`                                                                                | 按中心距离或实体边缘距离排序战斗实体      |

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

import com.fs.starfarer.api.GameState;
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
        if (engine == null || engine.isPaused() || Global.getCurrentState() == GameState.TITLE) {
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

### 进游戏核对

执行 `gradle jar`，确认 `data/config/settings.json` 已登记 `GearIn_combatScanner`，进入一场包含敌舰、导弹、弹丸与小行星的模拟战：

- 玩家舰船周围出现蓝、橙、黄、灰四色粒子，各自标出对应类型中边缘距离最近的对象 (两艘碰撞半径不同的船靠近时，边缘更近者会被选中)；
- 暂停战斗期间粒子停止刷新，恢复后重新以 0.20 秒周期更新；
- 范围内存在当前玩家方可见敌舰时出现红色标记，速度关系有解时前方同步出现随速度变化的紫色拦截点；敌舰退出视野后红色与紫色标记消失，空间查询的蓝色标记仍留在范围内；
- 将各类对象移至对应距离口径的 1600 su 外，或运行主系统/相位斗篷可用性片段验证 `canUseSystemThisFrame` 与 `getPhaseCloak()` 边界，`starsector.log` 无异常。

## 常见问题

- **战斗中没有任何标记粒子**：`settings.json` 缺少插件登记、注册 key 被覆盖或完整类名与包声明不一致。
    - **解决方案**：核对 `data/config/settings.json` 的 `plugins` 条目和 JAR 内的 `data.scripts.gearin.lib.GearInCombatScanner`。

- **暂停时粒子仍不断增加**：计时和查询发生在 `engine.isPaused()` 判断之前。
    - **解决方案**：把 `elapsed` 累加和全部查询放在暂停守卫之后。

- **范围内出现友军或不可见敌军**：`CombatUtils` 的空间查询只检查类型和位置。
    - **解决方案**：按 `owner` 和 `isVisibleToSide(...)` 继续筛选，或改用对应的 `AIUtils` 关系查询。

- **导弹集合触发了不符合预期的防御动作**：集合入口包含本方已熄火导弹，也没有自动处理战争迷雾。
    - **解决方案**：在 `getEnemyMissilesOnMap(...)` 或 `getNearbyEnemyMissiles(...)` 返回集合上，按当前威胁条件筛选所属方、可见性和熄火状态。

- **大规模战斗出现周期性卡顿**：高频回调反复取得关系集合，并处理大量候选。
    - **解决方案**：按实际半径选择 nearby/range 方法，减少后续处理对象，并用固定间隔限制查询频率。

- **大型舰船看起来更近却没有排在首位**：排序使用了实体中心距离，画面观察使用的是舰体边缘。
    - **解决方案**：构造 `CollectionUtils.SortEntitiesByDistance(origin, true)`，让比较包含碰撞半径。

- **拦截点代码产生空引用异常**：当前速度关系没有会合解时，`getBestInterceptPoint(...)` 返回 `null`。
    - **解决方案**：使用拦截坐标前检查返回值，并为无解状态选择直瞄或放弃目标。

- **相位斗篷判断始终与实际状态不一致**：`canUseSystemThisFrame` 读取的是 `ship.getSystem()`。
    - **解决方案**：从 `ship.getPhaseCloak()` 取得右键系统，并检查该对象的状态与规格。

## 建议参考项

- `org.lazywizard.lazylib.combat.CombatUtils`：战场范围查询、可见性、坐标转换、实体生成与施力；
- `org.lazywizard.lazylib.combat.AIUtils`：敌我关系查询、战争迷雾、最近目标、拦截点与主系统可用性；
- `org.lazywizard.lazylib.CollectionUtils.SortEntitiesByDistance`：按指定中心排序战斗实体；
- `org.lazywizard.lazylib.combat.WeaponUtils`：武器射界、弧内目标、瞄准时间和炮塔朝向；
- `org.lazywizard.lazylib.combat.DefenseUtils`：装甲格、防御类型和舰船受损状态。
