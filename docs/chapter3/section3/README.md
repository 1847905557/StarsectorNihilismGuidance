# 制作 MOD 入口类的最基础适配

本节为 GearIn 建立一个最小 MOD 入口类，并在新建生涯时设置势力关系。这里只需要理解入口声明、`BaseModPlugin` 和 `onNewGame()`；核心入口生命周期与存档初始化由 [C7-S1] 继续讲解。

*本节预期阅读时间：\~8 分钟。*

*本节预期操作时间：\~10 分钟。*

## 学习目标

完成后，您将能够：

- 用 `mod_info.json` 的 `modPlugin` 声明 MOD 入口类；
- 创建继承 `BaseModPlugin` 的 MOD 生命周期插件；
- 在 `onNewGame()` 中设置自定义势力与其他势力的初始关系；
- 根据启动日志和新档关系定位入口类故障。

## 前置知识

- [C1-S1] 的 MOD 目录、`mod_info.json` 与完整类名；
- [C2-S1] 的 Java 类、继承和回调；
- [C3-S1] 的 `gear_industry` 势力 ID。

## 声明入口类

`modPlugin` 告诉游戏哪个类负责接收 MOD 回调。在 `mod_info.json` 中加入这个字段：

```json
"modPlugin": "data.scripts.gearin.GearInModPlugin"
```

记得注意每一行的结尾是否应该加上逗号。

字段值必须与 Java 文件的 `package` 和类名组成的完整类名一致。拼写不一致时，游戏会在启动阶段报告 `Error loading [data.scripts.gearin.GearInModPlugin]`。

## 创建最小入口

在 MOD 根目录创建 `data/scripts/gearin/GearInModPlugin.java`。入口类继承 `BaseModPlugin`，因此只需要覆写当前使用的回调。新档生成时，游戏会调用 `onNewGame()`；本节就在这里取得 GearIn 势力并设置初始关系。

```java
@Override
public void onNewGame() {
    seedRelations(Global.getSector());
}
```

`onNewGame()` 只在创建新生涯时执行。修改关系代码后必须创建新档，读取旧档不会重新设置这些初始值。

## 设置势力关系

势力文件负责注册势力和配置舰队，势力关系则是新档中的运行时状态。`setRelationship` 可以直接写入关系数值，也可以使用 `RepLevel` 设置关系等级：

```java
private void seedRelations(SectorAPI sector) {
    FactionAPI gear = sector.getFaction("gear_industry");
    gear.setRelationship("player", 0f);
    gear.setRelationship("pirates", RepLevel.HOSTILE);
    gear.setRelationship("hegemony", RepLevel.FRIENDLY);
    gear.setRelationship("tritachyon", RepLevel.HOSTILE);
}
```

`0f` 表示中立。`HOSTILE` 和 `FRIENDLY` 让游戏选择对应等级的关系值，并同步该势力对另一方的关系。这里设置的是新档初始状态，后续生涯事件仍可继续改变关系。

## 完整最小例

::: details 完整成品：GearInModPlugin

```java
package data.scripts.gearin;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * GearIn 的 MOD 入口类。
 * mod_info.json 的 modPlugin 字段指向本类；游戏在新建生涯时调用 onNewGame()。
 */
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

前置数据：[C3-S1] 注册的 `gear_industry` 势力、本节的 `GearInModPlugin.java`，以及指向该类的 `modPlugin` 字段。

1. 启动游戏并等待主菜单完成加载，检查 `starsector.log`。
2. 创建新生涯，按 `F` 打开情报界面的势力列表。
3. 选择齿轮工业，核对它与玩家、海盗、霸主和速子科技的关系。

预期结果：

- 游戏进入主菜单，日志没有 `Error loading [data.scripts.gearin.GearInModPlugin]`；
- 齿轮工业与玩家保持中立；
- 齿轮工业与海盗、速子科技敌对；
- 齿轮工业与霸主友好。

## 故障分析

- **启动阶段报告 `Error loading [data.scripts.gearin.GearInModPlugin]`**：`modPlugin`、`package`、文件路径或类名没有指向同一个类。

    - **解决方案**：逐段核对 `data.scripts.gearin.GearInModPlugin` 与文件中的 `package`、类名和目录，修正后重启。

- **新档中的关系全部保持中立**：`onNewGame()` 没有调用 `seedRelations`，或入口类没有通过 `modPlugin` 注册。

    - **解决方案**：核对入口声明和 `onNewGame()` 的调用，再创建新档。

- **修改关系后旧档没有变化**：`onNewGame()` 只在创建新生涯时执行，已有存档保留自己的运行时关系。

    - **解决方案**：创建新档并重新核对四组关系。

## 总结

`mod_info.json` 用完整类名声明 `GearInModPlugin`，`BaseModPlugin` 允许入口类只覆写需要的回调，`onNewGame()` 则把关系设置放进新档生成过程。这个入口会在下一节继续调用星系生成器；启动、读档、保存和存档初始化的核心规则由 [C7-S1] 负责。

## 建议参考项

- `mod_info.json`：`modPlugin` 入口声明；
- `com.fs.starfarer.api.BaseModPlugin`：提供默认回调实现的基础类；
- `com.fs.starfarer.api.ModPlugin`：`onNewGame` 新档生成回调；
- `com.fs.starfarer.api.campaign.FactionAPI`：`setRelationship` 写入势力关系；
- `com.fs.starfarer.api.campaign.RepLevel`：关系等级枚举。
