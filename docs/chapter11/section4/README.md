# 读取并修改舰船装配

`member.getVariant()` 拿回来的往往是整片星域共用一份的 `.variant` 原型，直接对着它改武器、加船插，就会惊喜地发现全星域所有用着同一个预设 ID 的同型船全都跟着换了装备。

动手前先复制一份 `REFIT` 副本，改完核对过 OP 再 `setVariant(...)` 写回，这才是只改眼前一艘船的做法。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C8-S2] 的过热冶炉与 [C8-S4] 的破甲校准船插；
- [C11-S1] 的巡逻舰队管理器；
- [C11-S2] 的 `GearInPatrolRoster` 旗舰与指挥官配置。

## ShipVariantAPI 保存了哪些数据

调用 `member.getVariant()` 会返回这艘舰船当前的 `ShipVariantAPI`。一艘船的船体底子 (有哪些武器槽、基础装甲多少、最大装配点数 OP 是多少) 固定在 `ShipHullSpecAPI` 里，而所有允许在装配界面改动的内容都保存在 `ShipVariantAPI` 上：

```java
ShipVariantAPI variant = member.getVariant();
ShipHullSpecAPI hull = variant.getHullSpec();

Collection<String> fittedSlots = variant.getFittedWeaponSlots();
String weaponId = variant.getWeaponId(slotId);
int vents = variant.getNumFluxVents();
int capacitors = variant.getNumFluxCapacitors();
Collection<String> hullMods = variant.getHullMods();
List<String> wings = variant.getFittedWings();
```

与之对应的修改方法同样直观：

- **替换武器**：`variant.addWeapon(slotId, weaponId)` 会直接覆写非内置槽位上的武器；如果需要把某门武器彻底拆空，调 `variant.clearSlot(slotId)` 移除即可 (但内置 `BUILT_IN` 武器槽无论是否先清槽都无法通过 `addWeapon` 改写)；
- **增删船插**：`variant.addMod(hullModId)` 与 `variant.removeMod(hullModId)`；
- **调整幅能耗散通道与幅能寄存器**：`variant.setNumFluxVents(n)` 与 `variant.setNumFluxCapacitors(n)`；
- **更换战机联队**：`variant.setWingId(index, wingId)`。

## 修改原则：先做副本，改完再写回

刚刚由工厂或 `createFleetMember(...)` 创建出来的舰队成员，它的 `getVariant()` 指向的往往是 `Global.getSettings().getVariant(id)` 里的那份全局预设原型。如果直接在这份对象上删武器、加船插，相当于把游戏内存里的 `.variant` 预设文件给改了 —— 此后全星域任何势力刷出使用该预设的舰船，都会带着您的改动出场。

因此在运行期修改单艘舰船的装配，要按 "复制副本 → 标记来源 → 修改 → 写回" 四步来写：

```java
ShipVariantAPI variant = member.getVariant().clone();
variant.setSource(VariantSource.REFIT);
// 在此修改 variant 副本...
member.setVariant(variant, false, true);
```

1. `member.getVariant().clone()` 复制出一份只属于当前这艘船的独立装配对象；
2. `variant.setSource(VariantSource.REFIT)` 将装配来源标记为独立改装 (`REFIT`)，通知引擎在保存存档时把这份装配的具体内容序列化进存档，而不是只存一个预设 ID；
3. **修改完成后必须调用 `member.setVariant(variant, false, true)` 写回成员** (第一个布尔参数 `withRefit` 传 `false`，第二个布尔参数 `withStatsUpdate` 传 `true` 以立即重算该船属性)。如果只 `clone()` 改完却忘了调用 `setVariant(...)`，改过的副本在方法结束时就会被垃圾回收，船上挂着的依然是原来的旧装配。

对于 [C4-S4] 窑级那样带有子模块的舰船，每个模块槽位 (`variant.getStationModules()`) 上挂载的也是一份独立的 `ShipVariantAPI`。若要修改子模块的装配，同样需要先取出 `variant.getModuleVariant(slotId)`、调用 `.clone()` 并设好 `VariantSource.REFIT`，改完后再通过 `variant.setModuleVariant(slotId, moduleVariant)` 写回主装配。

## 结合舰长技能核对装配点数 (OP)

用代码直接往 `ShipVariantAPI` 里塞武器和船插时，引擎**不会**替您检查总装配点数是否超限 —— 哪怕塞进去的装备超了 50 点 OP，代码照样能跑通、舰船照样能进战场，只是玩家一旦在星图或战后点开舰队面板，就会看到刺眼的红字超限。

因此在调用 `member.setVariant(...)` 写回之前，应当先结合该船舰长的 `MutableCharacterStatsAPI` 核算一次 OP：

```java
int cost = variant.computeOPCost(captain.getStats());
int limit = variant.getHullSpec().getOrdnancePoints(captain.getStats());
if (cost > limit) {
    LOG.warn("GearIn patrol refit skipped: OP " + cost + "/" + limit);
    return;
}
```

之所以要把 `captain.getStats()` 传给 `computeOPCost(...)` 与 `getOrdnancePoints(...)`，是因为 `MutableCharacterStatsAPI` 上挂着舰体 OP 加成 (`getShipOrdnancePointBonus()`) 与各规格武器 OP 修饰等入口 (供 MOD 自定义技能或早期遗留技能修改)，传入实际执掌该船的舰长属性才不会漏算这类加成 (无人物修饰时传 `null` 亦可)；此外 [C8-S4] 提到的 `WeaponOPCostModifier` 等监听器也会在装配计算时一并参与。

## 定制巡逻队旗舰装配：GearInPatrolRefitter

[C11-S2] 的 `GearInPatrolRoster` 为巡逻队配好了工匠级旗舰 (预设装配 `GearIn_artizan_combat`，占用 `91 / 95` 点 OP，已装有 [C8-S2] 的过热冶炉) 与指挥官 `Brann Hale`。本节我们编写 `GearInPatrolRefitter`，将这艘旗舰改装为齿轮工业特色的近战破甲配置：

- 将两个小型实弹槽 (`WS0005`、`WS0006`) 上的原版火神炮替换为齿轮工业的都林自动炮 (`GearIn_durin`，同为 4 点 OP，点数不变)；
- 加装 [C8-S4] 制作的 "破甲校准" 船插 (`GearIn_breach_calibration`，驱逐舰消耗 6 点 OP)，使都林对船体与装甲伤害提高 15%；
- 将幅能耗散通道从 `14` 个减为 `12` 个，腾出 2 点 OP，使总 OP 刚好卡在 `95 / 95` 上限以内。

### 完整改装类

`src/data/scripts/gearin/campaign/GearInPatrolRefitter.java`：

```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.VariantSource;

/** 巡逻队旗舰的改装：火神炮换成都林，加装破甲校准，用耗散通道让出 OP；超出 OP 上限则放弃。 */
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

### 接入巡逻队管理器

在 [C11-S1] 管理器的 `spawnFleet()` 中，紧接在 [C11-S2] 的 `GearInPatrolRoster.configure(...)` 之后调用改装：

```java
FleetMemberAPI flagship = GearInPatrolRoster.configure(fleet, random);
GearInPatrolRefitter.refit(flagship, fleet.getCommander());
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，执行 `jump Nipher` 查看轨道上的巡逻队旗舰：

- 巡逻队旗舰工匠级的两个小型实弹槽已换装都林自动炮，船插栏中同时装有过热冶炉与破甲校准，幅能耗散通道为 12；
- 在模拟战或生涯市场的舰船列表中查看其它未改装的 `GearIn_artizan_combat` 工匠级，它们的小型槽依然是原版的火神炮，未受旗舰改装波及；
- 保存并重新读档，巡逻队旗舰的改装内容完整保留，不会退回初始预设。

## 常见问题

- **改装巡逻队旗舰后，全星域所有同型号工匠级舰船全都变成了改装后的配置**：直接修改了 `member.getVariant()` 返回的共享预设，漏掉了 `.clone()`。
    - **解决方案**：任何针对单船装配的运行期修改，都必须先调用 `member.getVariant().clone()` 复制副本，改完再通过 `member.setVariant(variant, false, true)` 写回。

- **保存并重新读档后，改装过的武器和船插全部还原成了初始预设**：复制修改后漏掉了 `variant.setSource(VariantSource.REFIT)`。
    - **解决方案**：生成副本后将其来源设为 `VariantSource.REFIT`，使存档保存完整的改装数据而非仅记录预设 ID。

- **调用 `addWeapon(slotId, weaponId)` 后武器没有变化**：目标槽位属于船体自带的 `BUILT_IN` 内置槽位 (如工匠级上的 `WS0003`、`WS0004`)，引擎默认拒绝通过普通 `addWeapon(...)` 覆写内置武器。
    - **解决方案**：只对普通可拆装槽位 (`WS0005`、`WS0006`) 调用 `addWeapon(...)`。

## 建议参考项

- `com.fs.starfarer.api.combat.ShipVariantAPI`：装配复制 (`clone()`)、槽位武器读写、船插增删与 `computeOPCost(...)`。
- `com.fs.starfarer.api.loading.VariantSource`：装配来源枚举 (`STOCK`、`REFIT` 等)。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：`getVariant()` 与 `setVariant(variant, withRefit, withStatsUpdate)`。
- `com.fs.starfarer.api.impl.campaign.econ.impl.LionsGuardHQ`：原版狮卫总部复制并定制舰船装配的参考实现。
