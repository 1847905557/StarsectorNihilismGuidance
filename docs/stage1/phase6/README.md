# 制作舰船插件

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~15 分钟。*

## 制作舰船插件数据

舰船插件数据在`data/hullmods`文件夹的`hull_mods.csv`中，框架已经提供了一份相对干净的副本。

### 实用舰船插件数据速查表

| 英文              | 实际意义                                                     |
| ----------------- | ------------------------------------------------------------ |
| name              | 舰船插件的名称                                               |
| id                | 舰船插件 ID                                                  |
| tier              | 科技等级，和前文其它的`tier`意义相同。正常范围是 0~3，大于 3 将导致不会在市场上正常出现。科技等级越大则越稀有且购买所需关系越高。 |
| rarity            | 稀有度，和前文其它的`rarity`意义相同。数字越小越罕见，大于 1 时被视为 1，小于 0 时被视为 0 |
| tech/manufacturer | 科技类型，原版包括诸如 核心纪元、主宰纪元 等，可随意填写     |
| tags              | 进阶内容，可随意编纂，后文详细解释                           |
| uiTags            | 在装配界面的文字分组，直接填写可读的文字。建议参考原版各类插件的`uiTags` |
| base value        | 舰船插件的的成本价                                           |
| unlocked          | 如果设为 TRUE，则玩家默认拥有此插件，否则需要在市场上购买    |
| hidden            | 如果设为 TRUE，则市场上不会刷新此插件，否则可能在妥善设置后刷新 |
| hiddenEverywhere  | 如果设为 TRUE，则此插件将被彻底隐藏，即使被安装在舰船上也不会显示 |
| cost_*            | 护卫舰、驱逐舰、巡洋舰、主力舰的装配点数消耗，不可为空，即使是为内置考虑也应填 0 |
| script            | 舰船插件的代码文件路径                                       |
| desc              | 舰船插件的描述文本，使用 %s 控制高亮标签                     |
| short             | 舰船插件的一句话简短描述                                     |
| sprite            | 舰船插件的贴图文件路径                                       |

对于初学者阶段，插件制作依然建议以简单为主，尽量先熟悉基本结构，再为日后更为复杂的插件制作进行准备。

在本例，编者将制作一个`稳定化推进器`，大致上是`不稳定推进器`的调整版，用幅度更小的加成来取消射程惩罚和甲板惩罚。将`不稳定推进器`的相关内容从对应`hullmods.csv`中复制并修改：

```csv {2}
name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sprite
稳定化推进器,GearIn_stable_injector,0,,,"engines, merc",引擎,2500,TRUE,,,5,10,15,25,data.hullmods.GearIn_StableInjector,"提高舰船最大速度 %s/%s/%s/%s ，不兼容 不稳定推进器。","提高舰船最大速度。",graphics/hullmods/GearIn_stable_injector.png
```

当然别忘了应该放在对应路径下的贴图：

![](./GearIn_stable_injector.png)

然后将`UnstableInjector.java`从原文件中复制到 MOD 文件夹的`data/hullmods`同路径下，文件名修改为`GearIn_StableInjector`，开始进行简单解析：

```java
package data.hullmods; // 路径

import java.util.HashMap; // 类似的 import 结构
import java.util.Map;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class UnstableInjector extends BaseHullMod { // 似曾相识的结构，这里似乎应该改为文件名

	private static Map mag = new HashMap(); // 这是什么？
	static {
		mag.put(HullSize.FRIGATE, 25f); // 似乎是舰船等级和数据的对应
		mag.put(HullSize.DESTROYER, 20f);
		mag.put(HullSize.CRUISER, 15f);
		mag.put(HullSize.CAPITAL_SHIP, 15f);
	}
	
	private static final float RANGE_MULT = 0.85f; // 似乎是射程的倍率
	private static final float FIGHTER_RATE = 25f; // 战机？推测为整备时间
	
	//private static final float ACCELERATION_BONUS = 100f; // 不会生效的注释
	//private static final float EXTRA_DAMAGE = 300f;
	//private static final int BURN_LEVEL_BONUS = 1;
	
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
		stats.getMaxSpeed().modifyFlat(id, (Float) mag.get(hullSize)); // 对最大速度进行加成？
		stats.getBallisticWeaponRangeBonus().modifyMult(id, RANGE_MULT); // 降低实弹武器射程？
		stats.getEnergyWeaponRangeBonus().modifyMult(id, RANGE_MULT); // 降低能量武器射程？
		
		stats.getFighterRefitTimeMult().modifyPercent(id, FIGHTER_RATE); // 降低战机整备速率？
		
		//stats.getAcceleration().modifyPercent(id, ACCELERATION_BONUS); // 不会生效的注释
		//stats.getEngineDamageTakenMult().modifyPercent(id, EXTRA_DAMAGE);
		
		//stats.getMaxBurnLevel().modifyFlat(id, BURN_LEVEL_BONUS);
	}
	
	public String getDescriptionParam(int index, HullSize hullSize) { // 高亮文本相关的内容
		if (index == 0) return "" + ((Float) mag.get(HullSize.FRIGATE)).intValue(); // 似曾相识的 index 0 1 2
		if (index == 1) return "" + ((Float) mag.get(HullSize.DESTROYER)).intValue(); // 也许这里控制高亮
		if (index == 2) return "" + ((Float) mag.get(HullSize.CRUISER)).intValue();
		if (index == 3) return "" + ((Float) mag.get(HullSize.CAPITAL_SHIP)).intValue();
		if (index == 4) return "" + (int) Math.round((1f - RANGE_MULT) * 100f) + "%";
		if (index == 5) return "" + (int) Math.round(FIGHTER_RATE) + "%";
//		if (index == 4) return "" + (int) ACCELERATION_BONUS;
//		//if (index == 5) return "four times";
//		if (index == 5) return "4" + Strings.X;
//		if (index == 6) return "" + BURN_LEVEL_BONUS;
		return null;
	}
	

}
```

然后大概就知道怎么改了：

```java {10,11,13,23,24,33,34,35,48,49,57-78}
package data.hullmods;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import com.fs.starfarer.api.Global; // 新的 import
import com.fs.starfarer.api.combat.ShipAPI; // 新的 import

public class GearIn_StableInjector extends BaseHullMod {

	private static Map mag = new HashMap();
	static {
		mag.put(HullSize.FRIGATE, 15f);
		mag.put(HullSize.DESTROYER, 10f);
		mag.put(HullSize.CRUISER, 5f);
		mag.put(HullSize.CAPITAL_SHIP, 5f);
	}
	


	
	//private static final float ACCELERATION_BONUS = 100f;
	//private static final float EXTRA_DAMAGE = 300f;
	//private static final int BURN_LEVEL_BONUS = 1;
	
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
		stats.getMaxSpeed().modifyFlat(id, (Float) mag.get(hullSize));


		

		
		//stats.getAcceleration().modifyPercent(id, ACCELERATION_BONUS);
		//stats.getEngineDamageTakenMult().modifyPercent(id, EXTRA_DAMAGE);
		
		//stats.getMaxBurnLevel().modifyFlat(id, BURN_LEVEL_BONUS);
	}
	
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + ((Float) mag.get(HullSize.FRIGATE)).intValue();
		if (index == 1) return "" + ((Float) mag.get(HullSize.DESTROYER)).intValue();
		if (index == 2) return "" + ((Float) mag.get(HullSize.CRUISER)).intValue();
		if (index == 3) return "" + ((Float) mag.get(HullSize.CAPITAL_SHIP)).intValue();


//		if (index == 4) return "" + (int) ACCELERATION_BONUS;
//		//if (index == 5) return "four times";
//		if (index == 5) return "4" + Strings.X;
//		if (index == 6) return "" + BURN_LEVEL_BONUS;
		return null;
	}
    
 	@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) { // 抄来的不兼容
		if (ship.getVariant().getHullMods().contains("unstable_injector")) {
			Global.getSoundPlayer().playUISound("cr_allied_warning", 1f, 1f);
			ship.getVariant().removeMod("GearIn_stable_injector"); // 改成本插件的 id
		}
	}
	
    @Override
	public boolean isApplicableToShip(ShipAPI ship) { // 从黄定位里抄来的不兼容
		return !ship.getVariant().getHullMods().contains("unstable_injector");
	}
	
	
	public String getUnapplicableReason(ShipAPI ship) { // 从黄定位里抄来的不兼容

		if (ship.getVariant().getHullMods().contains("unstable_injector")) {
			return "不兼容 不稳定推进器";
		}
		
		return null;
	}

}
```

修改类名，调整数据，删去了需要删去的内容。

## 插件实装

![](./hullmod_play.png)

## 实用舰船插件 tags 速查表

**不在本表上的数据均为与舰船插件本身性能无关的 tags(如 merc)，关于这些 tags 详见后文。**

| 英文                  | 实际意义                                                     |
| --------------------- | ------------------------------------------------------------ |
| offensive             | 当进行自动装配时，AI 将认为这是对综合进攻能力有直接增益的插件 |
| defensive             | 当进行自动装配时，AI 将认为这是对综合防御能力有直接增益的插件 |
| shields               | 当进行自动装配时，AI 将认为这是对护盾有直接增益的插件        |
| engines               | 当进行自动装配时，AI 将认为这是对移动能力有直接增益的插件    |
| phase                 | 当进行自动装配时，AI 将认为这是对相位能力有直接增益的插件    |
| non_phase             | 当进行自动装配时，AI 将认为这是不建议安装在相位舰船上的插件  |
| special               | 当进行自动装配时，AI 倾向于不随意移除或安装它                |
| req_spaceport         | 必须在空间站或殖民地才可安装，不可随地拔插                   |
| no_build_in           | 不可使用故事点内置                                           |
|                       | **下列 tags 是 D 插独有的**                                  |
| dmod                  | 这是 D 插                                                    |
| damage                | 这是通常型 D 插(实际上意义不大，只需要设置`dmod`即可)        |
| damageStruct          | 这个 D 插出现的概率异常地高                                  |
| destroyedDamageAlways | 如果这艘船在战斗中粉碎，则极大概率得到本 D 插                |
| peak_time             | 这个 D 插只会出现在峰值不为无限的舰船上                      |
| reqShields            | 这个 D 插只会出现在带有护盾的舰船上                          |
| civOnly               | 这个 D 插只会出现在民用船上                                  |
| fighterBayDamage      | 这个 D 插只会出现在本身自带甲板的舰船上                      |
| phaseDamage           | 这个 D 插只会出现在相位船上                                  |
| notPhase              | 这个 D 插不会出现在相位船上                                  |
| notAuto               | 这个 D 插不会出现在无人舰船上                                |
