# 制作一艘模块船

本节制作窑级模块巡洋舰及其第一个模块。主体和模块分别按舰船登记，再在主体装配中把模块装配连接到指定槽位。船体定义决定挂载位置，装配决定具体使用哪个模块，两者需要对应。

_本节预期阅读时间：\~10 分钟。_

_本节预期操作时间：\~25 分钟。_

## 学习目标

完成后，您将能够：

- 设置 STATION_MODULE 槽位与挂载几何；
- 登记主体和模块的专用 hints；
- 用 modules 连接槽位与模块装配；
- 查询模块的槽位、父子对象和运行位置；
- 按加载、部署与战斗表现定位模块问题。

## 前置知识

- [C1-S2] 的船体贴图、几何与数据登记；
- [C1-S5] 的武器和 `GearIn_durin`；
- [C4-S1] 的装配导出与正式 ID。

## 分别制作主体和模块

主体与模块本身都是舰船，因此先准备两张贴图。主体采用狮鹫贴图修改，ID 为 `GearIn_kiln`：

![窑级主体舰船贴图](./GearIn_kiln.png 'GearIn_kiln.png')

模块采用苍鹭贴图修改，ID 为 `GearIn_kiln_module`：

![窑级舰体模块贴图](./GearIn_kiln_module.png 'GearIn_kiln_module.png')

两张图沿用第一章的尺寸与中心点约定。当前主体先连接一个模块，便于核对位置与朝向。

在舰船编辑器里分别创建主体和模块船体，主体继续设置质量中心、碰撞、护盾、武器槽和引擎：

![编辑器中的窑级主体几何与槽位](./main.png 'main.png')

模块也设置自己的碰撞与武器槽：

![编辑器中的模块船体几何与槽位](./sub.png 'sub.png')

模块的位置跟随主体，当前可以不配置引擎。**无盾模块同样保留护盾中心与半径字段**，它们属于船体读取所需的几何，实际护盾类型在数据中填 NONE。

两份船体分别保存并登记，再进行装配连接。各自的几何与数据仍由对应文件提供，可以在连接之前分别核对。

## 为主体增加模块槽位

在主体添加 STATION_MODULE 类型槽位，编辑器显示为 Station Module：

![主体船体上的模块槽位](./main_slot_1.png 'main_slot_1.png')

将该槽位命名为 `MODULE_0`，设置位置和朝向：

![MODULE_0的模块槽位参数](./main_slot_2.png 'main_slot_2.png')

`locations` 是主体坐标系中的挂载位置，`angle` 是基准朝向，`arc` 是允许的朝向范围。位置与朝向由船体定义，后面的装配则通过槽位 ID 指定模块，因此 modules 映射中的大小写也要一致。

模块可以用 `moduleAnchor` 选择自身连接到槽位的锚点，API 的 `getModuleAnchor()` 返回该设置。连接后贴图偏移时，可以按挂载点和模块锚点分别核对。

## 登记主体与模块的数据

在 `ship_data.csv` 添加两个船体行。主体使用 SHIP_WITH_MODULES，模块使用 MODULE、UNBOARDABLE 和 HIDE_IN_CODEX。

**可驾驶主体的 SHIP_WITH_MODULES 决定部署时生成模块。** 空间站主体使用 STATION，并采用空间站行为。模块的 UNBOARDABLE 控制打捞，HIDE_IN_CODEX 控制数据库显示，MODULE 标记模块身份。

模块后勤数据尽量填写为 0，designation 可以留空，tags 保持空值。模块通过主体装配获得，蓝图与市场标签因此按这个用途安排。当前模块的装配点为 60，便于在装配界面选择并修改。

## 在测试战役里制作两份装配

重启后进入 Gear 测试，分别给主体和模块装上装备。模板会收集所有非战机装配，因此两者都能作为独立设计进行修改。

观察导出的两个任务文件：

![测试战役导出的主体和模块装配](./variant_make.png 'variant_make.png')

读取 hullId 后分别移入 `data/variants/`，改好文件名和内部 variantId：

![主体与模块的正式装配文件](./variant_rename.png 'variant_rename.png')

主体使用 `GearIn_kiln_combat`，模块使用 `GearIn_kiln_module_combat`。这两个名称都是装配 ID，模块装配结构与普通装配相同，内部的 hullId 再指向模块船体。

## 把模块装配连接到槽位

打开主体装配，加入局部字段：

```json
"modules": {
    "MODULE_0": "GearIn_kiln_module_combat"
}
```

**左侧是主体槽位 ID，右侧是模块装配 ID。** 原版空间站也采用这个关系，例如 station_small_Standard：

```json
    "modules": {
        "WS 001": "platform1_Standard",
        "WS 002": "platform1_Standard",
        "WS 003": "platform1_Standard",
        "WS 004": "platform1_Standard"
    }
```

解析还接受数组写法，例如 `[{"WS 024":"remnant_armour3_Standard"}]`；原版 remnant_station2_Standard 使用这类结构，数据中也可保留井号注释。当前使用单个对象，目标关系比较直接。

有模块槽位而暂时不安装模块时，可以使用 `"modules":[]`。要默认带上模块，则主体装配中需要有实际映射。游戏内普通装配操作保持这份映射，不会自动选出模块设计。编者认为模块制作最别扭的部分就在这里：船体已经有了连接点，具体连接仍要落实到装配文件。

观察连接后的装配界面：

![装配界面中连接到主体的模块](./module_done.png 'module_done.png')

模块贴图在主体槽位处显示，当前模块有装配点，可以选择并配置。战斗中是否实际生成，还要核对主体 hints 和部署结果。

## 完整最小例

以下数据包含既有船体示例和本节的主体、模块行，已有文件保留其表头与记录，只追加当前定义的两行。

```csv
name,id,designation,tech/manufacturer,system id,fleet pts,hitpoints,armor rating,max flux,8/6/5/4%,flux dissipation,ordnance points,fighter bays,max speed,acceleration,deceleration,max turn rate,turn acceleration,mass,shield type,defense id,shield arc,shield upkeep,shield efficiency,phase cost,phase upkeep,min crew,max crew,cargo,fuel,fuel/ly,range,max burn,base value,parts/dmod,cr %/day,CR to deploy,peak CR sec,CR loss/sec,supplies/rec,supplies/mo,salary,salMult,c/s,c/f,f/s,f/f,crew/s,crew/f,hints,tags,logistics n/a reason,codex variant id,rarity,breakProb,minPieces,maxPieces,travel drive,number
#Lasher,lasher,Frigate,Low Tech,ammofeed,5,1750,300,2100,1750,140,55,,120,110,90,60,90,200,FRONT,,210,0.4,1,,,25,50,40,25,1,25,10,9000,,8,10,240,0.25,4,4,,,,,,,,,,"lowtech_bp, merc, ind",,,,0.4,2,2,,204
#Hammerhead,hammerhead,Destroyer,Midline,ammofeed,10,5000,500,5000,4167,250,95,,90,60,40,30,60,400,FRONT,,300,0.4,0.8,,,50,100,100,60,2,30,9,40000,,5,12,360,0.25,10,10,,,,,,,,,,"midline_bp, merc, ind",,,,0.5,2,3,,300
#Eagle,eagle,Cruiser,Midline,maneuveringjets,14,8000,1000,11000,14000,700,155,,60,40,30,15,20,1200,FRONT,,270,0.4,0.8,,,150,250,150,100,3,33,8,100000,,3,12,480,0.25,20,20,,,,,,,,,,"rare_bp, merc, ind",,,,0.5,2,3,,401
#Conquest,conquest,Battlecruiser,Midline,maneuveringjets,24,12000,1200,20000,30000,1200,315,,55,25,20,6,4,2500,OMNI,,90,0.4,1.4,,,400,500,300,300,10,30,8,300000,,3,15,600,0.25,40,40,,,,,,,,,,"rare_bp, merc",,,,0.5,2,4,,503
炉渣,GearIn_slag,,未知,flarelauncher_fighter,,750,100,200,,100,0,,180,400,350,90,180,30,NONE,,0,0,0,,,1,1,,,,,,,,,,,,,,,,,,,,,,,,,,,0,,,,104
锻件,GearIn_forging,相位护卫舰,未知,ammofeed,6,2000,350,1200,2000,100,35,,90,120,90,45,90,200,PHASE,phasecloak,90,,,0.075,0.075,4,10,10,20,1,20,9,26000,,10,20,180,0.25,6,6,,,,,,,,,PHASE,GearIn_bp,,,,0.5,2,2,,228
工匠,GearIn_artizan,驱逐舰,未知,GearIn_assaultfeed,10,5000,500,3800,4167,200,95,,80,60,40,30,60,400,PHASE,GearIn_resistfield,,,,0.1,0,50,100,100,60,2,30,9,40000,,5,12,360,0.25,10,10,,,,,,,,,,GearIn_bp,,,,0.5,2,3,,300
守炉人,GearIn_furnaceman,轻型航母,未知,targetingfeed,11,4500,400,3000,2813,225,75,2,75,50,30,25,25,450,FRONT,,300,0.4,0.8,,,90,150,40,40,2,20,9,52000,,5,15,360,0.25,11,13,,,,,,,,,CARRIER,"GearIn_bp",,,,0.5,2,3,,318
窑,GearIn_kiln,模块巡洋舰,Midline,maneuveringjets,15,10000,1250,10000,,500,165,,60,40,30,15,20,1500,FRONT,,210,0.4,0.8,,,200,300,150,150,4,38,8,125000,,4,12,480,0.25,25,25,,,,,,,,,SHIP_WITH_MODULES,"GearIn_bp",,,,0.5,2,4,,401.5
,GearIn_kiln_module,模块,Midline,,10,5000,500,3000,,250,60,,0,0,0,10,10,300,NONE,,0,0,0,,,0,0,0,0,1,0,,40000,,,1,,,0,0,,,,,,,,,"UNBOARDABLE, HIDE_IN_CODEX, MODULE",,,,,0.5,2,3,,300
```

主体装配保存到 `data/variants/GearIn_kiln_combat.variant`：

```json
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
        # 注意：左边是槽位 ID，右边是模块的装配 ID
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

模块装配保存到 `data/variants/GearIn_kiln_module_combat.variant`：

```json
{
    "displayName": "Combat",
    "fluxCapacitors": 0,
    "fluxVents": 0,
    "hullId": "GearIn_kiln_module",
    "hullMods": [],
    "permaMods": [],
    "sMods": [],
    "variantId": "GearIn_kiln_module_combat",
    "weaponGroups": []
}
```

## 理解加载、部署与跟随关系

加载装配后，`ShipVariantAPI.getStationModules()` 返回映射，`getModuleSlots()` 返回模块槽位，`getModuleVariant(slotId)` 取得模块装配；运行时修改可以用 `setModuleVariant`。

部署主体时，游戏按刚才的映射取得 STATION_MODULE 槽位和模块装配，再创建模块的 `FleetMemberAPI` 与 `ShipAPI`。槽位的 `computePosition()` 和 `computeMidArcAngle()` 决定位置与朝向，装配则提供模块的具体设计。

模块保存父子关系，`getParentStation()`、`getStationSlot()` 和主体的 `getChildModulesCopy()` 可以查询它们。模块仍有自己的装甲、幅能、武器、系统与 AI。

### 位置、朝向与状态

主体移动时，模块位置每帧按槽位更新，跟随主体平移和旋转。默认朝向使用槽位中间角；模块带 INDEPENDENT_ROTATION 时保留独立朝向，位置继续跟随。

`moduleAnchor` 决定模块自身的挂载锚点，缺省按槽位位置连接。预览用 `getSizeWithModules()` 把模块计入整体尺寸。

::: warning 预览与部署的条件不同

缺少 SHIP_WITH_MODULES 的主体仍可能在列表中显示模块。**预览显示和战斗生成要分别确认。**

:::

模块的战斗损伤按槽位记录在主体 `FleetMemberStatusAPI` 中，下次部署按记录恢复；摧毁模块后，主体也可以继续作战。

装配界面默认按 `getOrdnancePoints() > 0` 决定模块能否选择。装配点为 0 时通常不可选，当前提供 60 点供测试。

### 模块行为 tag 速查表

模块的下列行为 tag 放在模块行的 `tags` 列 (会被加载器复制到船体 tag 上)：

| 英文                   | 实际意义                     | 使用位置                        |
| ---------------------- | ---------------------------- | ------------------------------- |
| `module_unselectable`  | 模块无法被选中               | 装配界面的装配面板 (强制不可选) |
| `module_refit_bright`  | 模块在装配界面时不会默认暗下 | 装配界面的装配面板              |
| `module_no_status_bar` | 模块在战斗中不显示状态属性条 | 战斗瞄准环渲染器                |
| `module_hull_bar_only` | 模块在战斗中不显示幅能属性条 | 战斗瞄准环渲染器                |

## 验证方案

1. 保存两份船体、数据行与两份装配，重启游戏，核对 variant 加载记录。
2. 进入测试战役，查看主体预览，再部署窑级。
3. 移动和转向主体，观察模块的相对位置与朝向。
4. 回到装配界面选择模块，修改武器与船插，并核对主体自动装配目标。
5. 在战斗中击毁模块，退出后观察主体记录的模块状态，再验证后续部署。

预期结果：

- 主体与模块装配加载完成，预览包含模块。
- 部署时生成独立模块，挂载在 MODULE_0，位置跟随主体。
- 模块有自己的武器和状态条，当前 60 装配点允许选择修改。
- goalVariant 使主体设计进入对应自动装配候选。
- 模块损伤按槽位记录，主体在失去模块后仍可继续作战。

## 故障分析

- **战斗中模块完全不生成**：主体 CSV 行的 hints 缺少或拼错 `SHIP_WITH_MODULES` 时，部署分支不会进入模块船生成流程；舰队列表和预览仍可能显示模块。
    - **解决方案**：给主体 hints 加上 `SHIP_WITH_MODULES`，重新部署。

- **模块槽位被静默跳过**：`modules` 的键能找到真实槽位，但该槽位不是 `STATION_MODULE` 时，部署流程会跳过它。
    - **解决方案**：让键逐字对应 `.ship` 中的 `STATION_MODULE` 槽位 ID，重新部署。

- **部署模块时抛出空引用**：`modules` 的槽位 ID 在 hull 上不存在时，`getHullSpec().getWeaponSlot(id)` 返回 null，模块生成处会在部署期失败。
    - **解决方案**：用编辑器确认槽位 ID 并同步映射键，再次部署。

- **装配加载正常但进入战斗时报错**：`modules` 只保存字符串映射，加载期不会校验模块装配 ID；部署时 `getModuleVariant` 返回 null 才会抛出空引用或 variant 错误。
    - **解决方案**：确认模块装配已注册且 `variantId` 逐字一致，重新进入战斗。

- **模块装配在加载阶段直接失败**：模块 variant 的 `hullId` 与注册船体不一致时，`Loading variant [...]` 后会抛出 `Ship hull spec [...] not found!`。
    - **解决方案**：让 `hullId` 等于模块 `.ship` 和 `ship_data.csv` 的 ID，重启。

- **模块在装配界面无法选中**：模块船体的 `ordnance points` 为 0，或 tags 包含 `module_unselectable` 时，模块按钮不会接受点击。
    - **解决方案**：需要改装时提供装配点并移除该 tag。

- **无盾模块的船体文件加载失败**：删除 `shieldCenter` 或 `shieldRadius` 会让 `.ship` 加载器的 `getVector2f`、`getInt` 抛出 JSONException。
    - **解决方案**：保留这两个字段，只把 `shield type` 设为 `NONE`，重启。

## 总结

模块制作仍然建立在船体和装配上：主体槽位提供位置，modules 映射选择模块装配，部署逻辑创建子对象并维护相对关系。贴图、几何、数据和两份装配分别保存，按实际连接核对。

先确认加载的 ID，再确认部署的槽位与装配，最后观察跟随和损伤记录。模块既是独立舰船，又受主体关系约束，这两部分决定了它的实际表现。

## 建议参考项

- `com.fs.starfarer.api.combat.ShipVariantAPI`：`getModuleSlots()`、`getStationModules()`、`getModuleVariant(slotId)` / `setModuleVariant(slotId, variant)`：模块槽位与模块装配映射的读写入口。
- `com.fs.starfarer.api.combat.ShipAPI`：`isShipWithModules()`、`getParentStation()` / `getStationSlot()`、`getChildModulesCopy()`、`getModuleOffset()`、`isStation()` / `isStationModule()`：主体与模块实体的运行态关系。
- `com.fs.starfarer.api.loading.WeaponSlotAPI`：`isStationModule()`、`computePosition()`、`computeMidArcAngle()`：模块槽位的判定与挂载几何。
- `com.fs.starfarer.api.combat.ShipHullSpecAPI`：`getModuleAnchor()` 与 `ShipTypeHints` 枚举 (`STATION`、`SHIP_WITH_MODULES`、`MODULE`、`UNDER_PARENT`、`INDEPENDENT_ROTATION`)。
- `data/hulls/ship_data.csv`：主体行 (`SHIP_WITH_MODULES`) 与模块行 (`UNBOARDABLE, HIDE_IN_CODEX, MODULE`) 的 hints/tags。
- `data/variants/stations/station_small_Standard.variant`：原版 `modules` 对象写法参照；
- `data/variants/stations/remnant_station2_Standard.variant`：原版 `modules` 数组写法参照。
- `Starsector/starsector-core/starsector.log`：加载期与部署期异常的定位入口。
