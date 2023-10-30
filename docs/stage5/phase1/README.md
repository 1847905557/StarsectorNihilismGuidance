# ModPlugin 精确聚焦

*本节预期阅读时间：~5 分钟。*

*本节预期操作时间：~5 分钟。*

## 在一切之前...

**再次强调**，编者默认您已经在[此处](https://www.runoob.com/java/java-tutorial.html)或类似的地方学习了`基本 Java 语法`并安装了一种叫`IDEA`的工具，且包括编译、封装等基础操作。编者将默认您对于各类 Java 基础概念均拥有一定程度上的理解，并不再提及诸如类名调整等简单修改。如果您并不了解上述内容，请去学习它。

## 重新审视 ModPlugin

ModPlugin 是目前接触地较为频繁的类，也是一个关键类。ModPlugin 应当继承了 BaseModPlugin，它的主要作用是**为生涯模式的新建、存档、读档相关步骤插入新的自定义步骤，以及用于设置各类舰船、导弹、武器开火的默认 AI**。通常来说，ModPlugin 不会频繁修改。

```java
    // 几个必须解析且常用的方法

	// 在游戏保存时调用
	public void beforeGameSave() {
		
	}

	// 在游戏读档完毕时调用
	public void onGameLoad(boolean newGame) {
		
	}

	// 在远行星号这个程序刚启动时调用
	public void onApplicationLoad() throws Exception {
		
	}

	// 在新建一局生涯时调用
	public void onNewGame() {
		
	}

	// 在此自定义舰船默认 AI，返回 null 时则为原版舰船 AI。注意传入的是 FleetMemberAPI
	public PluginPick<ShipAIPlugin> pickShipAI(FleetMemberAPI member, ShipAPI ship) {
		return null;
	}

	// 在此自定义武器开火默认 AI，返回 null 时则为原版武器开火 AI
	public PluginPick<AutofireAIPlugin> pickWeaponAutofireAI(WeaponAPI weapon) {
		return null;
	}

	// 在此自定义导弹默认 AI，返回 null 时则为原版导弹 AI
	public PluginPick<MissileAIPlugin> pickMissileAI(MissileAPI missile,
			ShipAPI launchingShip) {
		return null;
	}

	// 其余方法请自行 ctrl+左键 检查
```

现在详细回顾`齿轮工业`的 ModPlugin 到底做了什么事情：

```java
package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;

public class GearIn_ModPlugin extends BaseModPlugin {

	@Override
	public void onGameLoad(boolean newGame) {

	}

	@Override
	public void beforeGameSave() {

	}

	@Override
	public void onApplicationLoad() {

	}
	
	@Override
	public void onNewGame() {

		if (NEX()) {

		} else {
			// 在此准备生成星系

			new GearIn_NormalGenerate().generate(Global.getSector());
		}

		SharedData.getData().getPersonBountyEventData().addParticipatingFaction("gear_industry");
	}

	public static boolean NEX() {
		return Global.getSettings().getModManager().isModEnabled("nexerelin");
	}
}
```

很显然，它在 onNewGame 处添加了检测，当没有开启势力争霸时生成星系。然后用 SharedData 将势力注册到支持赏金机制的列表中。这是一个非常原始且简单的例子，但随着后续的开发，ModPlugin 将变得越来越复杂。

## 完全支持势力争霸

不过既然都以势力争霸作为例子了，那么就直接在此做它的完全支持。在前文 前缀名\_NormalGenerate 的同目录下新建 前缀名\_NEXGenerate，然后导入：

```java
package data.scripts;

import com.fs.starfarer.api.campaign.SectorAPI;
import exerelin.campaign.SectorManager;

public class GearIn_NEXGenerate extends PLSP_NormalGenerate {
	
	@Override
	public void generate(SectorAPI sector) {
		if (SectorManager.getManager().isCorvusMode()) { // 检测 nex 是否为原版地图模式
			super.generate(sector);
		}
	}
}
```

并修改 ModPlugin：

```java
			if (NEX()) {
				new GearIn_NEXGenerate().generate(Global.getSector());
			} else {
				new GearIn_NormalGenerate().generate(Global.getSector());
			}
```

即可完成完整的势力争霸支持。

