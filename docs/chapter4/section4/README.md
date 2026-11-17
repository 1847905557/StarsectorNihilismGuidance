# 制作一艘模块船

模块船的每个模块都有自己的装甲、幅能和武器，被打掉以后主体照样能打。普通舰船做不出这种设计。

游戏对模块船的支持却相当别扭。编者建议您先做一艘只有一个模块的船，了解清楚了再加。

---

_本节预期阅读时间：\~10 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C1-S2] 的舰船编辑器流程与 `ship_data.csv` 列，模块与主体都是舰船；
- [C4-S1] 的装配制作，模块船要为主体与模块各写装配；
- JSON 的基本写法与 [C1-S1] 提到的宽松 JSON，装配文件按它手工修改。

## 制作模块部件

一艘模块船至少包含主体和 1 个模块。这些部件本身都是舰船，因此需要先准备好它们的贴图：

![窑级主体舰船贴图](./GearIn_kiln.png 'GearIn_kiln.png')![窑级舰体模块贴图](./GearIn_kiln_module.png 'GearIn_kiln_module.png')

这艘 `窑-级 (Kiln)` 巡洋舰的主体和模块分别由 `狮鹫` 与 `苍鹭` 简单修改而来，耗时不超过 20 秒。为它们分别分配 `GearIn_kiln` 和 `GearIn_kiln_module` 的舰船 ID。

然后按正常流程做出它们的船体文件：

![编辑器中的窑级主体几何与槽位](./main.png 'main.png')

![编辑器中的模块船体几何与槽位](./sub.png 'sub.png')

::: tip 模块设置

如果情况合适，则模块可以不设置引擎。

但它依然必须设置护盾范围，哪怕它设计上无盾。删掉 `shieldCenter` 或 `shieldRadius`，船体文件会直接加载失败。

:::

注意主体要带有 `STATION_MODULE` 型槽位，编辑器中显示为 Station Module，这是后续安装模块所需的槽位：

![主体船体上的模块槽位](./main_slot_1.png 'main_slot_1.png')

![MODULE_0 的模块槽位参数](./main_slot_2.png 'main_slot_2.png')

本例把它命名为 `MODULE_0`。槽位的位置就是模块挂上去的位置，槽位的朝向就是模块默认的朝向。

然后为本例中的部件制作数据。下面只列出窑级的两行，表头沿用 [C1-S2] 的 `ship_data.csv`：

```csv
name,id,designation,tech/manufacturer,system id,fleet pts,hitpoints,armor rating,max flux,8/6/5/4%,flux dissipation,ordnance points,fighter bays,max speed,acceleration,deceleration,max turn rate,turn acceleration,mass,shield type,defense id,shield arc,shield upkeep,shield efficiency,phase cost,phase upkeep,min crew,max crew,cargo,fuel,fuel/ly,range,max burn,base value,parts/dmod,cr %/day,CR to deploy,peak CR sec,CR loss/sec,supplies/rec,supplies/mo,salary,salMult,c/s,c/f,f/s,f/f,crew/s,crew/f,hints,tags,logistics n/a reason,codex variant id,rarity,breakProb,minPieces,maxPieces,travel drive,number
窑,GearIn_kiln,模块巡洋舰,Midline,GearIn_assaultfeed,15,10000,1250,10000,11000,500,165,,60,40,30,15,20,1500,FRONT,,210,0.4,0.8,,,200,300,150,150,4,38,8,125000,,4,12,480,0.25,25,25,,,,,,,,,SHIP_WITH_MODULES,"GearIn_bp",,,,0.5,2,4,,401.5
,GearIn_kiln_module,模块,Midline,ammofeed,10,5000,500,3000,4167,250,60,,0,0,0,10,10,300,NONE,,0,0,0,,,0,0,0,0,1,,,40000,,,1,360,0.25,0,0,,,,,,,,,"UNBOARDABLE, HIDE_IN_CODEX, MODULE",,,,,0.5,2,3,,300
```

::: tip 模块的数据

模块的舰级名可以不填。

模块的后勤数据应当尽可能接近于 "无"，且带有 `UNBOARDABLE`、`HIDE_IN_CODEX` 与 `MODULE` 以避免问题。前两个分别让它不能被打捞、不在数据库里出现，`MODULE` 标明它的模块身份，原版的每一个模块都带着它。

**模块船的 `hints` 必须带有 `SHIP_WITH_MODULES`，否则将在某些场合出现模块丢失**。最常见的场合就是战斗：装配界面里模块好好地挂着，部署出去却只有主体。

除此之外，它的 `tags` 不应该包含任何诸如 `x_bp` 或 `merc`。模块只随主体出现，单独进入势力的蓝图或市场毫无意义。

:::

## 组装模块船

此时进入测试战役，会发现主体没有模块，也无法在游戏内使用任何正常方法为它设置模块。**因为远行星号的模块舰制作极其别扭，必须要有一个 `带模块的主体装配`**，这艘船才会默认带有模块。

使用测试战役分别为主体和模块制作一个装配：

![测试战役导出的主体和模块装配](./variant_make.png 'variant_make.png')

然后放入您 MOD 的 `data/variants` 文件夹中，修改文件名和内部的 variantId 等参数，主体为 `GearIn_kiln_combat`，模块为 `GearIn_kiln_module_combat`：

![主体与模块的正式装配文件](./variant_rename.png 'variant_rename.png')

打开原版的 `data/variants/stations/station_small_Standard.variant`，里面有这样一段：

```json
    "modules": {
        "WS 001": "platform1_Standard",
        "WS 002": "platform1_Standard",
        "WS 003": "platform1_Standard",
        "WS 004": "platform1_Standard"
    }
```

这就是模块在装配文件的设置方案。将它略加修改后，插入到本例的 `GearIn_kiln_combat.variant` 文件中：

```json {15-18}
{
    "displayName": "Combat",
    "fluxCapacitors": 23,
    "fluxVents": 30,
    "hullId": "GearIn_kiln",
    "hullMods": [
        "heavyarmor",
        "auxiliarythrusters",
        "targetingunit"
    ],
    "permaMods": [],
    "sMods": [],
    "variantId": "GearIn_kiln_combat",
    "goalVariant": true,
    "modules": {
        # 注意槽位 ID       模块的 装配 ID
        "MODULE_0": "GearIn_kiln_module_combat"
    },
    "weaponGroups": [
        {
            "autofire": false,
            "mode": "LINKED",
            "weapons": {
                "WS0004": "GearIn_durin",
                "WS0005": "GearIn_durin",
                "WS0011": "GearIn_durin"
            }
        },
        {
            "autofire": false,
            "mode": "ALTERNATING",
            "weapons": {
                "WS0006": "sabotpod",
                "WS0007": "sabotpod"
            }
        },
        {
            "autofire": true,
            "mode": "LINKED",
            "weapons": {"WS0001": "heavyac"}
        },
        {
            "autofire": true,
            "mode": "LINKED",
            "weapons": {
                "WS0002": "vulcan",
                "WS0003": "vulcan",
                "WS0008": "vulcan",
                "WS0009": "vulcan",
                "WS0010": "vulcan"
            }
        }
    ]
}
```

**左侧是主体的槽位 ID，右侧是模块的装配 ID**，不是模块的船体 ID。模块装配本身与普通装配没有区别，`hullId` 指向模块船体即可。

`窑-级` 模块巡洋舰现在默认带有模块了。

![装配界面中连接到主体的模块](./module_done.png 'module_done.png')

编者给模块堆了一身防御船插，再挂一门相位长矛，让它当主体侧面的一块装甲。模块被打掉以后主体照样能打，所以**模块最适合承担可以失去的职责**：挡伤害、补火力、扛导弹。把主体的唯一火力或唯一推进放在模块上，模块一掉，整艘船就废了。

### 进游戏核对

保存所有文件并重启游戏，进入测试战役：

- 舰队列表中的窑级带着模块，装配界面里可以点选模块单独改装；
- 部署后模块挂在 `MODULE_0` 槽位上，主体转向时模块跟着转；
- 集火打掉模块，主体继续作战。

### 实用模块 tags 速查表

下列标签填在模块那一行的 `tags` 列：

| 英文                   | 实际意义                     |
| ---------------------- | ---------------------------- |
| `module_unselectable`  | 模块无法被选中               |
| `module_refit_bright`  | 模块在装配界面时不会默认暗下 |
| `module_no_status_bar` | 模块在战斗中不显示状态属性条 |
| `module_hull_bar_only` | 模块在战斗中不显示幅能属性条 |

此外，装配点上限为 0 的模块默认无法在装配界面被选中。本例的模块给了 60 点，所以可以单独改装。

## 常见问题

- **装配界面有模块，战斗中却没有**：主体那一行的 `hints` 缺少 `SHIP_WITH_MODULES`。
    - **解决方案**：补上 `SHIP_WITH_MODULES`，保存后重启。

- **测试战役里的主体没有模块**：玩家舰队里那艘用的是空装配，空装配不带 `modules`。
    - **解决方案**：确认主体装配写了 `modules`，并在测试战役中选用 `GearIn_kiln_combat`。

- **部署时报空指针，或模块没有出现**：`modules` 左侧的 ID 与主体上的槽位 ID 不一致，或那个槽位不是 `STATION_MODULE` 型。
    - **解决方案**：在编辑器中核对槽位 ID 与类型，让两边逐字一致，保存后重启。

- **游戏正常加载，进战斗时才报错**：`modules` 右侧的模块装配 ID 写错。加载时游戏不会检查这一项。
    - **解决方案**：让它与模块装配的 `variantId` 逐字一致，保存后重启。

- **加载时报 `Ship hull spec [...] not found!`**：模块装配的 `hullId` 与模块船体 ID 不一致。
    - **解决方案**：改为 `GearIn_kiln_module`，保存后重启。

- **模块在装配界面点不了**：模块的装配点为 0，或带有 `module_unselectable`。
    - **解决方案**：需要改装时给它装配点并去掉这个标签。

## 建议参考项

- `data/variants/stations/station_small_Standard.variant`：原版 `modules` 写法。
- `data/hulls/ship_data.csv`：原版模块那几行的 `hints` 写法，搜 `MODULE` 即可。
- `data/hulls/station1.ship`：原版空间站船体，模块槽位与连接点的设置样本。
