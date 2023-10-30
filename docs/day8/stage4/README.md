# 初步支持势力争霸

*本节预期阅读时间：~5 分钟。*

*本节预期操作时间：~10 分钟。*

## 势力配置

势力争霸的势力文件配置集中于`data/config`文件夹内。**下列提及的所有文件夹或文件都不在框架内，请自行创建**。

### data\config\exerelin 文件夹

对于势力配置而言，此文件夹内包含`corvus_spawnpoints.csv`一个 CSV 文件。

::: tip 古早遗留

`corvus_capitals.csv`文件现已无意义，不应保留，应当删除。

:::

`corvus_spawnpoints.csv`用于控制玩家选择该势力时的出生点，它看上去是这样的：

```csv {3}
faction,system,entityID
#machine name,#human name,#machine name
gear_industry,Nipher,GearIn_planet1
```

从左至右分别为势力 ID、星系名、星球 ID。

### data\config\exerelinFactionConfig 文件夹

对于势力配置而言，此文件夹内包含`mod_factions.csv`和一个自定义的 JSON 文件。

`mod_factions.csv`用于注册势力，它看上去是这样的：

```csv {3}
faction
# case sensitive!!!
gear_industry
```

而同目录下则应当新建一个以势力 ID 为文件名的 JSON 文件，参考成品范例或其它 MOD 同目录下的文件修改即可。

### 势力争霸势力文件配置速查表

**不在本表上的数据均为不常用项目，请自行寻找参考。**

| 英文                    | 实际意义                                                     |
| ----------------------- | ------------------------------------------------------------ |
| playableFaction         | 是否可被玩家选择                                             |
| corvusCompatible        | 是否可兼容原版星域。如果不设为 true，则后续无法继续进行兼容  |
| marketSpawnWeight       | 在随机星域模式下的殖民地生成倍率                             |
| colonyExpeditionChance  | 建立新殖民地的频率倍率                                       |
| colonyTargetValuator    | 建立新殖民地的价值考虑，通常填写`exerelin.campaign.colony.IndustrialColonyTargetValuator` |
| startRelationships      | 原版星域模式下的初始关系                                     |
| maxRelationships        | 与某些势力可允许的最大关系                                   |
| minRelationships        | 与某些势力可允许的最小关系                                   |
| diplomacyPositiveChance | 与某些势力的正面事件频率倍率                                 |
| diplomacyNegativeChance | 与某些势力的负面事件频率倍率                                 |
| diplomacyTraits         | 势力特性，参考势力争霸的`data\config\exerelin\factionTraits.json` |
| alignments              | 势力倾向                                                     |
| startShipsSolo          | 单舰开局的舰船装配                                           |
| startShipsCombatSmall   | 小型军事舰队开局的舰船装配                                   |
| startShipsCombatLarge   | 大型军事舰队开局的舰船装配                                   |
| startShipsTradeSmall    | 小型贸易舰队开局的舰船装配                                   |
| startShipsTradeLarge    | 大型贸易舰队开局的舰船装配                                   |
| startShipsCarrierSmall  | 小型航母舰队开局的舰船装配                                   |
| startShipsCarrierLarge  | 大型航母舰队开局的舰船装配                                   |
| startShipsSuper         | 超级单舰开局的舰船装配                                       |
| startSpecialItems       | 开局后提供的物品，通常来说是蓝图包 ID，第二个空留空          |
| defenceStations         | 使用的空间站设施类型                                         |
| industrySeeds           | 殖民地自动发展时建立设施的比率                               |
| bonusSeeds              | 殖民地开局的设施设置                                         |

## 杂项配置

更多杂项配置请参考势力争霸的`data\config\exerelin`文件夹。

**在进行上述配置后，您的势力依然无法在势力争霸下正常运作**。因为距离它完成还需要一些关键的代码工作，这些代码工作将不可避免地涉及封装等进阶操作，因此将在后文介绍。
