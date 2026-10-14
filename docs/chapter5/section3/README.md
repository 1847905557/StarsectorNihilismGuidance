# 为游戏对象加入音频

声音已经登记，本节将它们用于都林自动炮、突击填弹器、GEAR_TECH 舰船风格、进入生涯的界面反馈与齿轮工业市场。各入口使用同一组已登记 ID，分别观察触发、持续与停止时的结果。

*本节预期阅读时间：\~15 分钟。*

*本节预期操作时间：\~20 分钟。*

## 学习目标

完成后，您将能够：

- 在武器和系统定义中引用声音；
- 在舰船风格中选择分档声音集；
- 在回调中播放 UI 提示并保持循环；
- 通过音乐插件选择市场曲目；
- 按资源、登记与调用位置定位播放问题。

## 前置知识

- [C5-S1] 的四个 OGG 文件；
- [C5-S2] 的音效、分档集与音乐登记；
- [C1-S5] 的都林武器定义；
- [C2-S3] 的突击填弹器效果；
- [C3-S3] 的 GearInModPlugin 入口与回调；
- [C3-S4] 的 Prime 市场；
- [C4-S3] 的 GEAR_TECH 风格。

## 为都林设置开火声音

打开 `data/weapons/GearIn_durin.wpn`，把两种安装形式的开火字段设置为本节音效，局部片段如下：

```json
"fireSoundOne": "GearIn_weapon_fire",
"fireSoundTwo": "GearIn_weapon_fire"
```

武器开火时按这些 ID 取得声音，实际资源由上一节的注册条目提供。文件仍使用第一章的武器 ID 与弹体，当前只更换播放资源。

命中音另由武器与冲击设置决定。需要关闭默认冲击音时配置 noImpactSounds；自定义位置播放还可以用 `Global.getSoundPlayer().playSound(...)`，完整方法与位置参数由 [C7-S4] 讲解。

## 为突击填弹器设置三段声音

在 `GearIn_assaultfeed.system` 中添加或替换：

```json
"useSound": "GearIn_weapon_fire",
"loopSound": "GearIn_system_loop",
"deactivateSound": "GearIn_weapon_fire"
```

useSound 在激活时播放，deactivateSound 在停用时播放，loopSound 用于持续阶段。三个字段对应系统的三个播放时机，当前先使用已登记资源，最终设计可以换成自己的系统素材。

**循环播放需要持续续期。** 定义字段由系统控制播放；由效果脚本控制时，持续调用放在每帧 apply 中，通过 playLoop 传入稳定的舰船对象和当前坐标、速度。

下面是效果类 apply 末尾的局部片段，添加在原来的属性修改之后：

```java
ShipAPI ship = (ShipAPI) stats.getEntity();
if (ship != null && effectLevel > 0f) {
    Global.getSoundPlayer().playLoop("GearIn_system_loop", ship, 1f,
            effectLevel, ship.getLocation(), ship.getVelocity());
}
```

创建前的数值对象可能没有战斗舰船，实际播放使用可用的舰船实体，因此这段代码在取得舰船后才调用播放器。effectLevel 同时作为音量，让声音随系统进入和退出变化。同一个声音 ID 与同一实体标记对应持续声源，后续调用延续这个声源，停止调用后自然淡出。

## 给舰船风格选择声音集

在 `hull_styles.json` 的 GEAR_TECH 中修改两个字段：

```json
{
    "GEAR_TECH": {
        "engineLoopSet": "GearIn_engine_loop",
        "engineAccelerateSet": "GearIn_engine_accelerate"
    }
}
```

这是局部合并示意，当前风格的颜色与其他声音字段保留。两个字段分别引用上一节的引擎循环与加速声音集，再按船体等级选择资源和音量。其他护盾、排散等声音仍使用当前已有的原版 ID。

**一个风格引用一组明确的声音集**，比逐艘船重复填写素材更容易维护。新增船体继续引用 GEAR_TECH 时，也会沿用它的声音配置，替换该声音集中的资源便可统一调整。

## 在进入生涯时播放 UI 反馈

在既有 `data/scripts/gearin/GearInModPlugin.java` 的方法区添加下面的局部回调：

```java
@Override
public void onGameLoad(boolean newGame) {
    Global.getSoundPlayer().playUISound("GearIn_ui_feedback", 1f, 1f);
}
```

Global 已由前文入口类导入。onGameLoad 在进入生涯时调用，这条播放语句随回调执行一次；以后给按钮或选项配声音，同样放到对应动作回调中。

playUISound 接收 ID、音高、音量，使用上一节独立准备的双声道资源。界面提示按调用触发，因此这里提供播放参数即可，不需要实体位置。

## 为齿轮工业市场选择音乐

创建 `data/scripts/gearin/campaign/GearInMusicPlayerPlugin.java`，使用后面的完整类。它继承原版音乐选择逻辑，只为所属势力为 gear_industry 的市场返回 GearIn_market_music，其他市场交由基类继续选择。

在 `data/config/settings.json` 的 plugins 对象中登记：

```json
{
    "plugins": {
        "musicPlugin": "data.scripts.gearin.campaign.GearInMusicPlayerPlugin"
    }
}
```

musicPlugin 是原版音乐插件入口键，游戏通过它选择当前配置的音乐插件。**这个入口服务整个音乐选择流程**，本例因此保留基类的其他行为，只调整目标市场。项目中多个功能需要修改音乐选择时，在同一个插件中组织对应条件。

原版市场选择方法也读取势力 music 映射和市场状态，本节通过类返回已登记的音乐 ID。返回值确定曲目，sounds.json 的 music 分类提供资源，source 与 file 再组合为实际文件路径。

## 完整最小例

系统类替换现有 `data/scripts/gearin/shipsystems/GearInAssaultFeedSystem.java`，音乐类保存到上面的 campaign 目录。入口 UI 回调、武器和风格的局部修改按前文接入。

::: details 完整成品：GearInAssaultFeedSystem

```java
package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class GearInAssaultFeedSystem extends BaseShipSystemScript {

    public static final float SPEED_BONUS = 50f;
    public static final float MANEUVER_BONUS = 200f;
    public static final float ROF_BONUS = 1f;
    public static final float FLUX_REDUCTION = 50f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        stats.getBallisticRoFMult().modifyMult(id, 1f + ROF_BONUS * effectLevel);
        stats.getBallisticWeaponFluxCostMod().modifyMult(id,
                1f - FLUX_REDUCTION * 0.01f * effectLevel);
        stats.getMaxSpeed().modifyFlat(id, SPEED_BONUS * effectLevel);
        stats.getAcceleration().modifyPercent(id, MANEUVER_BONUS * effectLevel);
        stats.getDeceleration().modifyPercent(id, MANEUVER_BONUS * effectLevel);
        stats.getTurnAcceleration().modifyFlat(id, 30f * effectLevel);
        stats.getTurnAcceleration().modifyPercent(id, MANEUVER_BONUS * effectLevel);
        stats.getMaxTurnRate().modifyFlat(id, 15f * effectLevel);
        stats.getMaxTurnRate().modifyPercent(id, 100f * effectLevel);

        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship != null && effectLevel > 0f) {
            Global.getSoundPlayer().playLoop("GearIn_system_loop", ship, 1f,
                    effectLevel, ship.getLocation(), ship.getVelocity());
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getBallisticRoFMult().unmodify(id);
        stats.getBallisticWeaponFluxCostMod().unmodify(id);
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("+" + (int) (SPEED_BONUS * effectLevel) + " top speed", false);
        }
        if (index == 1) {
            return new StatusData("improved maneuverability", false);
        }
        if (index == 2) {
            return new StatusData("ballistic flux use -" + (int) (FLUX_REDUCTION * effectLevel) + "%", false);
        }
        if (index == 3) {
            return new StatusData("ballistic rate of fire +" + (int) (ROF_BONUS * effectLevel * 100f) + "%", false);
        }
        return null;
    }
}
```

:::

::: details 完整成品：GearInMusicPlayerPlugin

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;

public class GearInMusicPlayerPlugin extends MusicPlayerPluginImpl {

    @Override
    protected String getMarketMusicSetId(Object param) {
        MarketAPI market = (MarketAPI) param;
        if ("gear_industry".equals(market.getFactionId())) {
            return "GearIn_market_music";
        }
        return super.getMarketMusicSetId(param);
    }
}
```

:::

## 验证方案

1. 保存声音资源、登记与对象引用，重启游戏，检查音频加载。
2. 进入生涯，观察 UI 提示，再给工匠安装都林并进入模拟战。
3. 开火、移动与进入零幅能加速，分别比较武器、引擎循环和加速声音。
4. 激活突击填弹器，观察激活、持续和停用声音，结束后确认循环淡出。
5. 进入 Prime 市场，听取市场曲目；再进入其他势力市场，核对原版选择继续生效。
6. 调整一个条目的 volume，重启后以同一场景比较，并核对相关日志。

预期结果：

- 进入生涯时播放 UI 反馈，武器开火采用 GearIn_weapon_fire。
- GEAR_TECH 移动与加速采用两个自有声音集，分档与当前船体对应。
- 系统持续阶段保持循环，结束后停止续期。
- 齿轮工业市场播放 GearIn_market_music，其他市场沿用原版选择。
- 声音参数修改后实际听感随新值变化。
- 日志可以定位无效 ID、分类与资源路径错误。

## 故障分析

- **开火没有声音**：武器字段 ID 未登记或不同。
    - **解决方案**：核对 GearIn_durin.wpn 字段与 sounds.json 自有键。

- **循环只响一下**：脚本未持续调用或声源对象每次不同。
    - **解决方案**：在效果回调中用同一舰船对象持续调用 playLoop。

- **引擎声音不符**：GEAR_TECH 引用了不同或未登记的声音集。
    - **解决方案**：核对 engineLoopSet、engineAccelerateSet 及分档结构。

- **UI 声音空间表现不符**：播放入口或资源声道不同。
    - **解决方案**：使用 GearIn_ui_feedback 双声道与 playUISound。

- **市场音乐未选择**：musicPlugin 未登记、类加载错误或市场所属势力不同。
    - **解决方案**：核对插件完整类名与 gear_industry 归属，再进入市场。

- **音乐资源未播放**：声音 ID 未处于 music 分类，或 source／file 组合错误。
    - **解决方案**：核对注册结构和当前资源路径，重启后比较。

## 总结

本节将已登记声音用于实际对象和回调。武器和系统读取定义字段，舰船风格选择分档集，UI 反馈由动作回调播放，市场音乐由选择插件返回曲目 ID。

资源声道、注册分类与调用时机共同决定播放结果。测试时分别触发每个入口，记录声音何时开始、何时保持、何时结束，再调整素材和参数。

## 建议参考项

- `data/config/sounds.json`：普通音效、分档集与 music 分类。
- `data/config/settings.json`：plugins.musicPlugin 入口。
- `data/config/hull_styles.json`：风格声音集字段。
- `data/weapons/`：武器开火与冲击声音字段。
- `data/shipsystems/`：系统激活、持续和停用声音字段。
- `com.fs.starfarer.api.SoundPlayerAPI`：UI、位置和循环播放。
- `com.fs.starfarer.api.BaseModPlugin`：onGameLoad 回调。
- `com.fs.starfarer.api.impl.MusicPlayerPluginImpl`：市场音乐选择与原版行为。
