# 读取并修改舰船装配

装配是一份被共享的数据。同一个装配 ID 被几艘船用着，改一处，它们全变。

所以改装配永远从一个副本开始，改完再写回去。漏了写回那一步，改动不会报错，也永远不会生效。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C11-S1] 的巡逻舰队管理器，改装在 `spawnFleet()` 中调用；
- [C11-S2] 的旗舰与指挥官，改装需要指挥官的技能参与 OP 计算；
- [C8-S2] 的过热冶炉与 [C8-S4] 的破甲校准，本节的改装把它们配合起来。

## 装配保存了什么

成员的 `getVariant()` 返回 `ShipVariantAPI`。这上面是这艘船的装配：武器装在哪个槽、装了哪些船插、幅能散热器与电容各几个、机库放哪个联队、模块挂什么。

船体本身不在装配里。槽位有哪些、OP 上限多少，来自 `getHullSpec()`。

```java
ShipVariantAPI variant = member.getVariant();
ShipHullSpecAPI hull = variant.getHullSpec();

variant.getFittedWeaponSlots();     // 已装武器的槽
variant.getWeaponId(slotId);        // 某个槽上装的是什么
variant.getNumFluxVents();          // 幅能散热器数量
variant.getNumFluxCapacitors();     // 幅能电容数量
variant.getHullMods();              // 船插
variant.getFittedWings();           // 机库里的联队
variant.getModuleSlots();           // 模块槽
```

改装配的方法与之对应：`addWeapon(slotId, weaponId)`、`clearSlot(slotId)`、`addMod(modId)`、`removeMod(modId)`、`setNumFluxVents(n)`、`setNumFluxCapacitors(n)`、`setWingId(index, wingId)`。

**`clearSlot(...)` 要先于 `addWeapon(...)`。** 槽位上已有武器时，`addWeapon(...)` 不会替换。

装配只有一份。同一个装配 ID 被几艘船共用时，改一处，它们全变。

## 改之前先复制

`getVariant()` 返回的可能是一份共享的预设。改它，所有用同一预设的成员一起变。正确的顺序是先复制成自己的，改副本，再写回：

```java
ShipVariantAPI variant = member.getVariant().clone();
variant.setSource(VariantSource.REFIT);
// 改 variant
member.setVariant(variant, false, true);
```

`setVariant(...)` 的 `withRefit` 决定是否走一遍整备流程，`withStatsUpdate` 决定是否立刻重算成员数值。**改完之后要马上读到新数值，第二个参数传 `true`。**

`setSource(VariantSource.REFIT)` 把这份装配标成玩家改装产生的，与直接读来的预设区分开。原版有的地方先调 `setSource(...)` 再 `setVariant(...)`，有的地方先 `setVariant(...)` 再对新装配调 `setSource(...)`，两种都在用。不做这一步也不会出错，但装配的来源会被当成原来的预设。

**只 `clone()` 不 `setVariant(...)`，改动不会生效。** 副本是独立对象，成员身上挂的还是原来那一份。

## OP 怎么算

装配点数是装得下装不下的唯一标准，由装配自己算：

```java
int cost = variant.computeOPCost(stats);
int limit = variant.getHullSpec().getOrdnancePoints(stats);
```

`computeOPCost(...)` 与 `getOrdnancePoints(...)` 都要传人物属性 `stats`，因为指挥官的技能会改变 OP 上限与船插开销。**同一个装配，换一个舰长算出来的数不一样。**

想只知道船插占了多少，用 `computeHullModOPCost(stats)`。还剩多少，用 `getUnusedOP(stats)`。

**超限的装配能写进去，也能进战斗。** 装配界面会显示成红字超限，玩家一眼就能看见。程序里自己算一遍再决定写不写，比让玩家发现问题早。

## 模块船的装配

模块船的每个模块是一份独立的装配，挂在主装配的模块槽上：

```java
Map<String, String> modules = variant.getStationModules();
ShipVariantAPI module = variant.getModuleVariant(slotId);
variant.setModuleVariant(slotId, moduleVariant);
```

改模块的装配，等于改另一份 `ShipVariantAPI`。它同样受 "共享预设" 那条规则约束：`clone()` 之后写回，否则所有同型模块一起变。

上面这些是装配层的东西。接下来改装巡逻队的旗舰。

## 改装旗舰

巡逻队的旗舰是 [C11-S2] 加入的工匠，装配为 `GearIn_artizan_combat`。改装做三处修改：

```java
for (String slot : BALLISTIC_SLOTS) {
    variant.clearSlot(slot);
    variant.addWeapon(slot, NEW_WEAPON);
}
variant.addMod(NEW_MOD);
variant.setNumFluxVents(NEW_VENTS);
```

- 小型实弹槽 `WS0005`、`WS0006` 的火神炮换成都林自动炮。两者都是小型实弹武器，OP 同为 4，替换不改变总量；
- 加装破甲校准 `GearIn_breach_calibration`，驱逐舰上占 6 OP；
- 幅能散热器从 14 减到 12，让出 2 OP。

`clearSlot(...)` 必须先于 `addWeapon(...)`：槽位上已有武器时直接添加，不会替换。

这套改装让旗舰成为一艘典型的齿轮工业舰船：它已装有过热冶炉，再配上都林与破甲校准，靠承受压力换取输出。代价是散热器少了两个，幅能耗散下降，更容易被打到过载。

## 核对 OP

工匠的 OP 上限是 95，原装配占用 91。改装后的总量由装配自己计算：

```java
int cost = variant.computeOPCost(captain.getStats());
int limit = variant.getHullSpec().getOrdnancePoints(captain.getStats());
if (cost > limit) {
    LOG.warn("GearIn patrol refit skipped: OP " + cost + "/" + limit);
    return;
}
```

`computeOPCost(...)` 与 `getOrdnancePoints(...)` 都要传入人物的属性，因为指挥官的技能可以改变 OP 上限与船插开销。**超出上限时放弃改装并记录警告，不要强行写回。** 超限的装配照样能进入战斗，玩家在舰队界面看到的却是一艘红字超限的舰船。

最后用 `member.setVariant(variant, false, true)` 写回：`withRefit` 传 `false`，不触发整备流程；`withStatsUpdate` 传 `true`，立即更新成员属性。

::: warning 先改装，再配军官

改装发生在 `setFlagship(...)` 之后、`setCaptain(...)` 之前，所以这里传入的是创建好的指挥官人物，而不是已经派驻的舰长。顺序见 [C11-S1] 的完整类。

:::

## 完整的改装类

`src/data/scripts/gearin/campaign/GearInPatrolRefitter.java`：

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.VariantSource;

/** 巡逻队旗舰的改装：火神炮换成都林，加装破甲校准，用散热器让出 OP；超出 OP 上限则放弃。 */
public final class GearInPatrolRefitter {

    private static final Logger LOG = Global.getLogger(GearInPatrolRefitter.class);

    private static final String[] BALLISTIC_SLOTS = {"WS0005", "WS0006"};
    private static final String NEW_WEAPON = "GearIn_durin";
    private static final String NEW_MOD = "GearIn_breach_calibration";
    private static final int NEW_VENTS = 12;

    private GearInPatrolRefitter() {
    }

    /** 对成员的装配做一份副本并改装，通过 OP 核对后写回成员。 */
    public static void refit(FleetMemberAPI member, PersonAPI captain) {
        ShipVariantAPI variant = member.getVariant().clone();
        variant.setSource(VariantSource.REFIT);

        for (String slot : BALLISTIC_SLOTS) {
            variant.clearSlot(slot);
            variant.addWeapon(slot, NEW_WEAPON);
        }
        variant.addMod(NEW_MOD);
        variant.setNumFluxVents(NEW_VENTS);

        int cost = variant.computeOPCost(captain.getStats());
        int limit = variant.getHullSpec().getOrdnancePoints(captain.getStats());
        if (cost > limit) {
            LOG.warn("GearIn patrol refit skipped: OP " + cost + "/" + limit);
            return;
        }
        member.setVariant(variant, false, true);
    }
}
```

在 [C11-S1] 管理器的 `spawnFleet()` 中，紧接在创建指挥官之后调用改装：

```java
PersonAPI commander = createCommander(random);
GearInPatrolRefitter.refit(flagship, commander);
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，用控制台命令 `jump Nipher` 到巡逻队附近，选中舰队查看旗舰。

- 旗舰是一艘工匠，小型实弹槽上装的是都林自动炮，不再是火神炮；
- 旗舰带有破甲校准，幅能散热器为 12；
- 旗舰的 OP 不超过 95；
- 保存并重新读档，改装结果没有变化。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **改装之后所有同款舰船都变了**：直接修改了成员的原装配，没有先 `clone()`。
    - **解决方案**：先复制副本，再改副本，最后用 `setVariant(...)` 写回。

- **槽位上的武器没有被替换**：没有先调用 `clearSlot(...)`。
    - **解决方案**：先清空槽位，再 `addWeapon(...)`。

- **改装没有生效，旗舰仍是原装配**：OP 超出上限，改装被放弃。
    - **解决方案**：查看日志中的 `GearIn patrol refit skipped`，减少散热器或换用更便宜的船插。

- **舰队界面里旗舰显示红字超限**：强行写回了超出 OP 上限的装配。
    - **解决方案**：写回之前用 `computeOPCost(...)` 与 `getOrdnancePoints(...)` 核对。

- **OP 核对结果与装配界面不一致**：传入的人物属性不同，指挥官的技能改变了 OP 上限或船插开销。
    - **解决方案**：核对时传入最终派驻的指挥官的属性。

## 建议参考项

- `com.fs.starfarer.api.combat.ShipVariantAPI`：装配的全部读写方法，包括 `clone()`、`computeOPCost(...)` 与模块映射。
- `com.fs.starfarer.api.loading.VariantSource`：装配来源的枚举。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：`setVariant(...)` 的两个开关与成员的装配读写。
- `com.fs.starfarer.api.impl.campaign.econ.impl.LionsGuardHQ`：原版对成员装配做副本再写回的写法。
