# 制作 MOD 入口类的最基础适配

势力文件提供身份与舰队配置，初始关系则在创建生涯时设置。本节建立齿轮工业的 MOD 入口，把关系代码放进新档回调。下一节会通过同一个入口生成星系，当前先确认类能加载、回调能执行。

*本节预期阅读时间：\~8 分钟。*

*本节预期操作时间：\~10 分钟。*

## 学习目标

完成后，您将能够：

- 在 `mod_info.json` 中声明 MOD 入口类；
- 创建采用 `BaseModPlugin` 的入口文件；
- 在新档回调中设置自定义势力的初始关系；
- 根据加载日志与新档结果定位入口问题。

## 前置知识

- [C1-S1] 的 MOD 目录、`mod_info.json` 与类名规则；
- [C2-S1] 的效果类、参数和游戏回调；
- [C3-S1] 的 `gear_industry` 势力。

## 创建入口文件

在 MOD 中创建 `data/scripts/gearin/GearInModPlugin.java`。入口类继承 `BaseModPlugin`，写法与此前船插继承基类的结构相似。

这次需要的回调是 `onNewGame()`：游戏创建新生涯时调用它。下面是入口中的局部片段：

```java
@Override
public void onNewGame() {
    seedRelations(Global.getSector());
}
```

`Global.getSector()` 取得正在生成的生涯星区；`seedRelations(...)` 是本例定义的关系设置方法，把星区交给它后再进行设置。

**方法名与调用位置共同决定代码何时生效。** 关系设置放在新档回调，已有存档会继续保留自己的关系。修改代码后，需要创建新档观察初始值。

## 设置齿轮工业的初始关系

在同一个类中加入 `seedRelations(...)`，局部片段如下：

```java
private void seedRelations(SectorAPI sector) {
    FactionAPI gear = sector.getFaction("gear_industry");
    gear.setRelationship("player", 0f);
    gear.setRelationship("pirates", RepLevel.HOSTILE);
    gear.setRelationship("hegemony", RepLevel.FRIENDLY);
    gear.setRelationship("tritachyon", RepLevel.HOSTILE);
}
```

`sector.getFaction("gear_industry")` 按本章定义的 ID 取得势力，`gear` 保存这个对象。后面每行都调用 `setRelationship`，第一项填写对方势力 ID，第二项填写关系。

`0f` 为中立，`RepLevel.HOSTILE` 为敌对，`RepLevel.FRIENDLY` 为友好。等级写法由游戏选择对应关系值，并同步双方的关系。后续生涯事件仍然可以继续改变它们。

本例让齿轮工业与玩家中立、与霸主友好、与海盗和速子科技敌对。编者建议您根据势力设定安排关系：舰队构成、市场归属和外交行为最终都会在同一个星区中发生，关系也应当服务于这个设计。

## 在 mod_info.json 中声明入口

保存文件后，在 MOD 的 `mod_info.json` 顶层对象中加入下面的局部字段：

```json
"modPlugin": "data.scripts.gearin.GearInModPlugin"
```

按相邻字段补齐逗号，并保留已有的 MOD 信息。**`modPlugin` 的值由入口文件的包名和类名组成**，与声明逐字符一致。游戏加载 MOD 时据此找到入口。

## 完整最小例

将完整文件保存为 `data/scripts/gearin/GearInModPlugin.java`，入口声明采用上面的完整类名。

::: details 完整成品：GearInModPlugin

```java
package data.scripts.gearin;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;

/** 在新档生成时设置齿轮工业的初始关系。 */
public class GearInModPlugin extends BaseModPlugin {

    @Override
    public void onNewGame() {
        seedRelations(Global.getSector());
    }

    private void seedRelations(SectorAPI sector) {
        FactionAPI gear = sector.getFaction("gear_industry");
        gear.setRelationship("player", 0f);
        gear.setRelationship("pirates", RepLevel.HOSTILE);
        gear.setRelationship("hegemony", RepLevel.FRIENDLY);
        gear.setRelationship("tritachyon", RepLevel.HOSTILE);
    }
}
```

:::

## 验证方案

1. 保存入口源码与 `mod_info.json`，重启游戏，检查主菜单加载和日志中的类加载信息。
2. 创建新生涯，按 `F` 打开情报并进入势力列表。
3. 选择齿轮工业，核对玩家、海盗、霸主与速子科技的关系。
4. 修改其中一项初始关系，保存并重启，再创建测试生涯比较该项结果。

预期结果：

- MOD 入口类加载完成，游戏进入主菜单。
- 齿轮工业与玩家中立，与霸主友好，与海盗和速子科技敌对。
- 新建生涯采用保存后的初始关系设置；已有生涯保留当前关系。

## 故障分析

- **启动时报 Error loading [data.scripts.gearin.GearInModPlugin]**：入口值与源码包名、类名或目录不一致。
    - **解决方案**：按完整类名逐段核对文件路径和声明，保存后重启。

- **新档的初始关系没有设置**：入口尚未登记，或 `onNewGame()` 未调用关系方法。
    - **解决方案**：核对 `modPlugin` 字段、回调与 `seedRelations` 调用，重新创建生涯。

- **修改后已有存档关系保持原样**：新档回调在创建生涯时执行，当前存档保留自己的状态。
    - **解决方案**：创建测试生涯核对初始关系。

## 总结

MOD 入口通过 `mod_info.json` 登记，`BaseModPlugin` 提供回调结构，`onNewGame()` 把关系设置放在创建生涯的时机。势力文件定义的 ID 在这里用于取得运行中的势力对象。

下一节继续使用这个入口调用星系生成器。启动、保存、读档与持久化的完整处理，由 [C7-S1] 结合工程化后的代码讲解。

## 建议参考项

- `mod_info.json`：`modPlugin` 入口声明。
- `com.fs.starfarer.api.BaseModPlugin`：提供默认回调实现的基类。
- `com.fs.starfarer.api.ModPlugin`：`onNewGame()` 新档回调。
- `com.fs.starfarer.api.Global`：`getSector()` 取得生涯星区。
- `com.fs.starfarer.api.campaign.FactionAPI`：`setRelationship` 设置势力关系。
- `com.fs.starfarer.api.campaign.RepLevel`：关系等级。
