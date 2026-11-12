# 为舰船加入内置武器、船插与战机

内置内容随船体存在，玩家无法拆卸，也不必为它支付装配点。内置内容的强度和设计一般来说会被直接归类到船体上，而不会被单独审视，因此，它往往比任何数值都更清楚地说明这艘船为何而造。而真正的难处在于克制。

---

_本节预期阅读时间：\~10 分钟。_

_本节预期操作时间：\~10 分钟。_

## 内置武器

### 规划内置槽位

内置武器需要 `BUILT_IN` 类型的槽位。在舰船编辑器中设置槽位时，把类型选为 `BUILT_IN` 即可，所以内置槽位最好在设计舰船时就规划好。

本例把工匠船头的两个小型固定槽位设为内置。若您在 [C1-S2] 中没有这样设置，用舰船编辑器打开工匠修改槽位类型并保存。它们在 `GearIn_artizan.ship` 中是这样的：

```json {4,13}
    {
      "id": "WS0003",
      "size": "SMALL",
      "type": "BUILT_IN",
      "mount": "HARDPOINT",
      "arc": 10,
      "angle": 0,
      "locations": [59, 45]
    },
    {
      "id": "WS0004",
      "size": "SMALL",
      "type": "BUILT_IN",
      "mount": "HARDPOINT",
      "arc": 10,
      "angle": 0,
      "locations": [59, -45]
    },
```

### 填写 builtInWeapons

槽位装什么武器，舰船编辑器管不了，需要手动写进 `.ship`。从原版 `攻势-级 (Onslaught)` 的 `onslaught.ship` 中抄出：

```json
    "builtInWeapons": {
        "WS 016": "tpc",
        "WS 017": "tpc"
    },
```

键是槽位 ID，值是武器 ID。改为工匠的槽位和 [C1-S5] 的都林自动炮，插入 `GearIn_artizan.ship` 的顶层，例如 `weaponSlots` 之前：

```json {2,3}
    "builtInWeapons": {
        "WS0003": "GearIn_durin",
        "WS0004": "GearIn_durin"
    },
```

![工匠级船体文件中的内置武器配置](./built_weapon.png 'built_weapon.png')

::: warning 槽位 ID 必须逐字符一致

`builtInWeapons` 的键必须与 `weaponSlots` 中的槽位 `id` 完全一致。原版写作 `WS 016`，舰船编辑器生成的是 `WS0003`，照抄原版的键是最常见的错误。

:::

内置槽位的武器只在 `.ship` 中填写。`.variant` 里写给内置槽位的武器不会生效，留着只会误导后来的自己。

内置武器通常不希望出现在市场和打捞中。为它单独准备一门武器时，清空 `weapon_data.csv` 行的 `tags`，需要从数据库中隐藏时，再在 `hints` 中加入 `SYSTEM`。本例借用的都林是一门正常出售的武器，所以保持原样。

### 确认内置武器

1. 在控制台执行 `Addship GearIn_artizan`。
2. 在装配界面查看工匠：两个小型固定槽位已装上都林，无法拆卸或替换，装配点与未安装时相同。
3. 进入一场战斗，两门都林应当正常开火。

## 装饰武器

装饰武器的写法与内置武器完全相同，只是槽位类型改为 `DECORATIVE`，武器 `.wpn` 的 `type` 也是 `DECORATIVE`。它们只负责好看：

- 不进入武器组，AI 不会用它们开火；
- 不计入装配界面的武器幅能统计；
- 战斗中与生涯界面中照常显示。

原版的例子是 `水牛-级 (Buffalo)` 船身上的红绿信号灯，`buffalo.ship` 的 `builtInWeapons` 中挂的就是 `blinker_red` 与 `blinker_green`。天线、雷达、旋转部件之类不参与战斗的结构，都可以用装饰武器实现。

::: caution 战机不要使用内置武器

战机的武器只能由 `.variant` 的 `weaponGroups` 安装。战机不会自动生成武器组，内置槽位上的武器不属于任何武器组，AI 永远不会给它分配目标：它只会停在槽位朝向，不转动，不追踪。

:::

## 内置船插

`builtInMods` 是 `.ship` 顶层的船插 ID 数组。本例为 [C1-S4] 的锻件加上相位舰通用的 `相位场` (`phasefield`)。从原版 `阴影-级 (Shade)` 的 `shade.ship` 中抄出：

```json
    "builtInMods": [
        "phasefield",
        "delicate"
    ],
```

锻件不需要 `精密机械` (`delicate`)，删去后插入 `GearIn_forging.ship` 的顶层：

```json {2}
    "builtInMods": [
        "phasefield"
    ],
```

![锻件级船体文件中的内置相位船插配置](./built_hullmod.png 'built_hullmod.png')

数组的元素是船插 ID，取值来自 `data/hullmods/hull_mods.csv` 的 `id` 列。自制船插见 [C9-S1]。

内置船插不占装配点，也无法移除。它看上去是一种不花代价的加强手段，因此也最容易被滥用。一艘船挂上五六个内置船插，玩家打开装配界面看到的只是一面船插墙，而不是一艘有特点的船。**能用数据表达的特点，就不要写成内置船插。**

### 确认内置船插

1. 在控制台执行 `Addship GearIn_forging`。
2. 在装配界面查看锻件：船插列表中出现 `相位场`，并标为内置，无法移除。
3. 回到生涯地图，舰队的探测范围应当比加入锻件之前略低。

## 内置战机

`builtInWings` 是 `.ship` 顶层的联队 ID 数组，按顺序占据航母最前面的甲板。本例让 [C1-S3] 的看炉人第一个甲板固定搭载炉渣。从原版 `暴雨-级 (Tempest)` 的 `tempest.ship` 中抄出：

```json
    "builtInWings": [
        "terminator_wing"
    ],
```

改为炉渣的联队 ID 后插入 `GearIn_furnaceman.ship` 的顶层：

```json {2}
    "builtInWings": [
        "GearIn_slag_wing"
    ],
```

![看炉人级船体文件中的内置炉渣联队配置](./built_wing.png 'built_wing.png')

这里填的是**联队 ID**，不是战机的船体 ID 或装配 ID。内置战机占用的甲板仍然计入 `ship_data.csv` 的 `fighter bays`：看炉人有 2 个甲板，第一个被炉渣占据，第二个仍可在装配界面自由安装。

### 确认内置战机

1. 在控制台执行 `Addship GearIn_furnaceman`。
2. 在装配界面查看看炉人：第一个甲板固定为炉渣，无法替换；第二个甲板可以正常安装联队。
3. 进入一场战斗并部署看炉人，炉渣应当从甲板起飞，被击落后正常补充。

## 常见问题

- **报错 `Weapon spec [...] not found in weapon_data.csv`**：`builtInWeapons` 中的武器 ID 不存在，或大小写不一致。
    - **解决方案**：对照 `weapon_data.csv` 的 `id` 列改正后重启。

- **部署时报 `slot id [...] not found for weapon [...]`**：`builtInWeapons` 的键不是 `.ship` 中实际存在的槽位 ID。
    - **解决方案**：把键改成与 `weaponSlots` 中的 `id` 逐字符一致。

- **生成舰船时崩溃，异常堆栈中出现船插 ID**：`builtInMods` 中的船插 ID 不存在于 `hull_mods.csv`。加载阶段不检查这一项，错误要到舰船生成时才出现。
    - **解决方案**：对照 `hull_mods.csv` 的 `id` 列改正后重启。

- **内置甲板为空**：`builtInWings` 中的联队 ID 不存在，或填成了船体 ID、装配 ID。
    - **解决方案**：填写 `wing_data.csv` 中的联队 ID，即 `GearIn_slag_wing`。

- **内置内容毫无反应，日志也没有报错**：键名拼错，例如 `builtInWeapon` 少了 `s`。游戏不认识的键会被静默忽略。
    - **解决方案**：对照原版文件逐字符核对键名。

- **装饰武器被 AI 开火，或计入幅能统计**：槽位类型或 `.wpn` 的 `type` 有一处不是 `DECORATIVE`。
    - **解决方案**：两处都改为 `DECORATIVE` 后重启。

## 建议参考项

- `data/hulls/onslaught.ship`：`builtInWeapons` 与 `BUILT_IN` 槽位的样本。
- `data/hulls/buffalo.ship`：装饰槽位与信号灯的样本。
- `data/weapons/blinker_red.wpn`：装饰武器的 `.wpn` 样本。
- `data/hulls/shade.ship`：`builtInMods` 的样本。
- `data/hulls/tempest.ship`：`builtInWings` 的样本。
- `com.fs.starfarer.api.combat.ShipHullSpecAPI`：`getBuiltInWeapons()`、`getBuiltInMods()`、`getBuiltInWings()` 等查询方法。
