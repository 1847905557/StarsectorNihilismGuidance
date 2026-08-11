# 为舰船加入内置武器、船插与战机

本节将进一步讲解定义在 `.ship` 文件里的内置组件，包括武器、船插、战机。它们将随船体而存在。

*本节预期阅读时间：~12 分钟。*

*本节预期操作时间：~15 分钟。*

## 学习目标

完成后，您将能够：

- 在 `.ship` 文件中声明 `builtInWeapons`、`builtInMods`、`builtInWings` 三组键，并说出读取并应用它们的使用方；
- 说出内置内容与普通装配内容的区别 (不占装配点、不可拆卸、内置战机占据前几个甲板)；
- 解释为什么战机不应该设置内置武器，并给出可观察结果；
- 从日志报错和战斗表现反推三组键的常见错误。

## 前置知识

- [C1-S2] 的 `.ship` 结构、武器槽位、`ship_data.csv` 登记与 `Addship` 使用方法；
- [C1-S3] 的航母甲板与战机 hull、variant、wing 三层结构；
- [C1-S4] 的相位舰船体与防御数据；
- [C1-S5] 的 `weapon_data.csv`、武器 ID 与 `.wpn` 文件结构。

## 文件与 API 链路

三种内置内容都写在 `.ship` 文件**顶层**，与 `weaponSlots`、`engineSlots`、`center` 同级。

### 内置组件键速查表

原版 `onslaught.ship` 中，`builtInMods` 与 `builtInWeapons` 位于文件前部；`weaponSlots` 是文件最后的数组：

| 键 | JSON 类型 | 含义 | 原版参考 |
|---|---|---|---|
| `builtInWeapons` | 对象 (槽位 ID → 武器 ID)| 随船安装的武器，不占装配点、不可拆卸 | `onslaught.ship`：`"WS 016": "tpc"` |
| `builtInMods` | 数组 (船插 ID)| 随船安装的船插，不占装配点、不可移除 | `shade.ship`：`["phasefield", "delicate"]` |
| `builtInWings` | 数组 (战机 ID)| 占据前几个甲板的战机联队，不可更换、不占装配点 | `tempest.ship`：`["terminator_wing"]` |

使用这三组键的完整链路为：

1. `data/hulls/<hullId>.ship`：声明三组键 (数据入口)。
2. 加载期把 `.ship` 的声明读入 `ShipHullSpecAPI` 的 `builtInWeapons`/`builtInMods`/`builtInWings` 三个字段。
3. 装配构建时把三组内置内容应用到**每一个**装配 —— 包括游戏按船体自动生成的 `<hullId>_Hull` 空装配。
4. 使用方：装配点计算 (`computeWeaponOPCost`/`computeHullModOPCost`)、战斗部署 (舰船生成时)、装配界面 (refit 的武器槽/船插列表/甲板列表)。

公开 API 面 (`com.fs.starfarer.api.*`) 中，只读访问入口在 `com.fs.starfarer.api.combat.ShipHullSpecAPI`：

- `getBuiltInWeapons()`、`getBuiltInMods()`、`getBuiltInWings()`；
- `isBuiltIn(String slotId)`、`isBuiltInMod(String modId)`、`isBuiltInWing(int index)`；
- 槽位类型枚举 `com.fs.starfarer.api.combat.WeaponAPI.WeaponType` 含 `BUILT_IN` 与 `DECORATIVE`。
- 槽位访问入口为 `com.fs.starfarer.api.loading.WeaponSlotAPI.getWeaponType()`。

::: info 为什么要在设计舰船时就把内置槽位规划好

**内置槽位应该在设计舰船的时候就提前设计、设置好**。舰船编辑器 (前置章节的按键流程) 可以设置槽位类型，但 `builtInWeapons` 等三组键位于编辑器流程之外，后期手动修改会略显麻烦。

:::

## 内置武器 (builtInWeapons)

为舰船加入内置武器需要先有对应的内置槽位。内置槽位在 `.ship` 文件中看上去大概是这样的 (本例为 `GearIn_artizan.ship` 的两门内置武器槽位，来自前置章节制作的工匠-级)：

```json {3,4,5,6}
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

槽位与普通槽位一样有三个关键维度：`mount`(形态：TURRET/HARDPOINT/HIDDEN)、`type`(类型：`BUILT_IN` 表示内置槽位)、`size`(大小，与所装武器一致)。

`mount` 与 `type` 相互独立：原版攻势的两门内置 TPC 槽位是 `"mount": "HARDPOINT"`、`"arc": 5`(武器固定朝向)，监视者的内置槽位则是 `"mount": "TURRET"`。

装饰槽位同理，例如原版 `buffalo.ship` 是 `"mount": "TURRET"` 配 `"type": "DECORATIVE"`。

::: caution 槽位类型

`type` 写成 `BUILT_IN` 的槽位**不能**在装配界面手动安装武器 ——`isWeaponSlot()` 只认 `BALLISTIC`、`ENERGY`、`MISSILE`、`UNIVERSAL`、`HYBRID`、`SYNERGY`、`COMPOSITE` 七种类型 (`WeaponSlotAPI.isWeaponSlot()`)。

内置武器只能由 `builtInWeapons` 提供。

:::

::: tip 操作方式

增加内置武器建议手动打开 `.ship` 文件进行修改，但**能复制粘贴修改就不手写**。

:::

本例将对 `GearIn_artizan.ship` 加入两门内置武器。

从原版攻势的 `onslaught.ship` 文件中抄出如下内容：

```json
    "builtInWeapons": {
        "WS 016": "tpc",
        "WS 017": "tpc"
    },
```

修改为所需武器 (`GearIn_durin` 是前置章节制作的都林自动炮)。

::: warning 槽位 ID 必须逐字符一致

**反复确定槽位 ID 的正确** ——`builtInWeapons` 的键必须与 `.ship` 中 `weaponSlots` 数组里的槽位 `id` 完全一致。

:::

然后插入到 `.ship` 文件内的对应位置：

```json {2,3}
    "builtInWeapons": {
        "WS0003": "GearIn_durin",
        "WS0004": "GearIn_durin"
    },
```

![工匠级船体文件中的内置武器配置](./built_weapon.png "built_weapon.png")

### 内置武器的行为

- **不占装配点**：`computeWeaponOPCost` 跳过内置槽位 (`!hullSpec.isBuiltIn(slotId)`)，装配界面显示 OP 只按非内置武器计算。
- **不可拆卸**：装配界面的 "清空武器/返还货舱" 只处理 `getNonBuiltInWeaponSlots()`，内置武器永远留在装配里。
- `.variant` 文件也无法覆盖内置槽位 (`addWeapon(..., false)` 对内置槽位直接拒绝写入)。
- **自动开火**：普通舰船会自动生成武器组 (`autoGenerateWeaponGroupsV2`)。
- 内置武器只要不是装饰槽就会并入武器组，随 AI 自动开火。
- **武器 ID 必须真实存在**：`setHullSpec` 按武器 ID 查询武器注册表，未知 ID 会在装配构建时抛 `Weapon spec [<id>] not found!`(见故障分析)。
- **建议清空掉落标签**：内置武器通常不希望出现在市场和打捞中。
- 此前武器数据章节给出的提示同样适用：清空或精简 `weapon_data.csv` 行的 `tags`。
- 需要彻底隐藏时可用 `SYSTEM` hint(`SYSTEM` 单独使用时条目不出现在数据库；原版 `tpc` 是 `SYSTEM, SHOW_IN_CODEX`，`SHOW_IN_CODEX` 使其仍出现在数据库、不掉落)。

### 验证方案

前置数据：`gear_in` MOD[C1-S1]+ 本处 `.ship` 修改 + 前置章节已注册的船体 `GearIn_artizan`[C1-S2] 与武器 `GearIn_durin`[C1-S5]。

1. 用 dev 控制台 `Addship GearIn_artizan` 生成测试舰船。
2. 在装配界面确认两门内置都林自动炮已安装。
3. 进入任意战斗，观察两门内置武器是否随 AI 自动开火。

预期结果：

- 装配点数与未安装时相同 (不占 OP)。
- 两门内置武器无法拆卸或替换。
- 战斗中自动开火正常。
- `starsector.log` 中 `GearIn_artizan` 无 `ERROR`。

## 内置装饰性武器

装饰性武器的安装方式和通常的内置武器几乎完全一样，只是需要将槽位类型改为 `DECORATIVE` 即可。装饰武器**不会**出现在武器组或舰船数据列表中：

- **不进武器组**：`autoGenerateWeaponGroupsV2` 与 `hasUnassignedWeapons` 都跳过装饰槽，AI 不会把它们纳入自动开火判定。
- **不计入舰船数据面板**：装配界面的舰船数据面板统计 "武器幅能消耗/秒" 时跳过装饰武器。
- **不被 AI 瞄准开火**：进攻机动、侧舷压制和各战术系统 AI 的武器选择都跳过装饰武器。
- **不会被永久损坏**：装饰槽武器不会因损坏而失效。
- **仍然渲染**：战斗中和生涯舰船界面都会显示装饰武器。

装饰武器适合纯视觉内容。原版例子：`buffalo.ship` 的红色/绿色信号灯 ——`builtInWeapons` 里装 `blinker_red`/`blinker_green` (`data/weapons/blinker_red.wpn`、`blinker_green.wpn`，`.wpn` 的 `"type": "DECORATIVE"`)，信号灯槽位为 `"type": "DECORATIVE"` 的 TURRET 槽。

## 战机的内置武器？

战机**不应该**设置内置武器，而应该设置正常的槽位、在装配文件里设置武器 (前置章节已建立的战机装配流程)。

::: caution 有什么不好？

战机设置内置武器可能导致武器无法追踪目标、导弹类内置武器转速归零。这是数据路径决定的确定性行为，不是偶发问题：

- 战机**不会**自动生成或补全武器组。
- 舰船生成时只为非战机执行 `autoGenerateWeaponGroups`/`assignUnassignedWeapons`。
- 战机只使用 `.variant` 装配文件里显式写出的 `weaponGroups`。
- 舰船 AI 只给 "当前选中武器组" 内的武器设置瞄准目标。
- 内置武器的槽位如果没有写进装配文件的 `weaponGroups`，它就不属于任何武器组，永远得不到瞄准目标，只能停在槽位朝向，不追踪、不转动。
- 更直接的情况：内置槽位若按常见做法用 `HARDPOINT` 挂载，导弹武器装在固定槽位上时最大转速被直接设为 0(除非船体带 `MISSILE_HARDPOINTS_ROTATE` 提示且槽位角小于 90 度)。
- 无论 `mount` 是哪种，未进武器组的内置武器都拿不到瞄准目标。
- 可观察结果：战机内置武器只朝固定方向开火、无法追踪目标；`HARDPOINT` 挂载的导弹类内置武器完全不转动。

因此战机请保持正常槽位 + `.variant` 装配文件的 `weaponGroups` 方案。内置三件套是为舰船准备的。

:::

## 内置船插 (builtInMods)

增加内置船插也建议手动打开 `.ship` 文件进行修改，但**能复制粘贴修改就不手写**。本例将对 `GearIn_forging.ship`(前置章节的锻件-级相位护卫舰) 加入相位对应内置船插。

从原版阴影的 `shade.ship` 文件中抄出如下内容：

```json
    "builtInMods": [
        "phasefield",
        "delicate"
    ],
```

由于无需精密机械，因此简单修改后插入到 `.ship` 文件内的对应位置：

```json {2}
    "builtInMods": [
        "phasefield"
    ],
```

![锻件级船体文件中的内置相位船插配置](./built_hullmod.png "built_hullmod.png")

`builtInMods` 数组里的每个元素是**船插 ID**，取值来自 `hull_mods.csv` 的 `id` 列。

例如 `phasefield` 是原版 "Phase Field" 船插，`delicate` 是 "Delicate Machinery"。

### 内置船插的行为

- **不占装配点**：`computeHullModOPCost` 跳过内置船插 (`!hullSpec.isBuiltInMod(id)`)。
- **不可移除**：`removeMod` 对内置船插直接返回；船插选择器把内置船插排除在可装卸列表之外；清空装配时 `clear(false)` 会把内置船插重新加回。
- **船插 ID 必须真实存在**：装配构建阶段不校验，但舰船部署时 `getAllMods()` 会解析每个船插 ID，未知 ID 抛 `Hullmod <id> not found!`(见故障分析)。
- 本节只涉及 "内置" 这一安装方式；effect 脚本和 S-mod 见 [C8-S1]。

### 验证方案

前置数据：`gear_in` MOD[C1-S1]+ 本处 `.ship` 修改 + 前置章节已注册的船体 `GearIn_forging`[C1-S4]。

1. 用 dev 控制台 `Addship GearIn_forging` 生成测试舰船。
2. 在装配界面确认船插列表出现 `phasefield`(原版 Phase Field)。
3. 进入任意战斗，观察相位立场效果。

预期结果：

- `phasefield` 无法移除。
- 其余装配不受影响。
- 相位立场正常降低探测范围。
- `starsector.log` 中 `GearIn_forging` 无 `ERROR`。

## 内置战机 (builtInWings)

增加内置战机也建议手动打开 `.ship` 文件进行修改，但**能复制粘贴修改就不手写**。本例将对 `GearIn_furnaceman.ship`(前置章节的守炉人-级航母，2 个甲板) 加入一甲板对应内置战机。

从原版暴雨的 `tempest.ship` 文件中抄出如下内容：

```json
"builtInWings": [
    "terminator_wing"
],
```

修改为所需的战机 ID 后插入到 `.ship` 文件内的对应位置：

```json {2}
"builtInWings": [
    "GearIn_slag_wing"
],
```

![守炉人级船体文件中的内置炉渣联队配置](./built_wing.png "built_wing.png")

### 内置战机行为

- **占据前几个甲板**：`setHullSpec` 按顺序把内置战机填入甲板 0、1、2...(`setWingId(i, ...)` 对内置甲板索引强制使用内置战机)。
- 本例中 `GearIn_slag_wing` 占据守炉人的第一个甲板，第二个甲板仍可在装配界面正常装配。
- **不可更换**：`setWingId` 对内置甲板索引强制覆盖；装配界面的 "清空战机槽" 按 `isBuiltInWing` 判断、跳过内置甲板。
- **不占装配点**：`computeWeaponOPCost` 的战机循环跳过内置战机；舰队成员的造价计算也使用 `getNonBuiltInWings()` 排除内置战机。
- **战机 ID 必须真实存在** (`wing_data.csv` 的 `id` 列)：未知 ID 不会报错，而是该甲板**静默不生成联队** (见故障分析)。

### 验证方案

前置数据：`gear_in` MOD[C1-S1]+ 本处 `.ship` 修改 + 前置章节已注册的船体 `GearIn_furnaceman` 与战机 `GearIn_slag_wing`[C1-S3]。

1. 用 dev 控制台 `Addship GearIn_furnaceman` 生成测试舰船。
2. 在装配界面确认第一个甲板固定为 `GearIn_slag_wing`(炉渣联队)。
3. 进入任意战斗，观察联队出动、返航与补充。

预期结果：

- 内置武器正常开火 (普通舰船)；
- 内置船插效果生效 (相位立场降低生涯探测范围)；
- 内置战机从对应甲板出动、返航与补充正常。

## 故障分析

- **游戏加载时报 `Weapon spec [<id>] not found!`**：`builtInWeapons` 中的武器 ID 不在 `weapon_data.csv` 注册表，或拼写、大小写不一致。装配构建会立即解析该 ID。

    - **解决方案**：统一 CSV 的 `id` 列后重启。

- **部署时报 `slot id [<槽位>] not found for weapon [<武器>]`**：`builtInWeapons` 的键不是真实槽位 ID，或槽位片段与该键不一致。舰船生成时会检查每个已装配槽位。

    - **解决方案**：把两处 ID 改成逐字符一致后重新部署。

- **部署时抛 `Hullmod <id> not found!`**：`builtInMods` 的船插 ID 不存在于 `hull_mods.csv`。装配构建阶段不会校验，错误会在舰船生成时由 `getAllMods()` 抛出。

    - **解决方案**：补齐或改正 `id` 后重启。

- **内置战机甲板静默为空**：`builtInWings` 中的战机 ID 不存在于 `wing_data.csv`，解析失败时甲板不会生成联队。

    - **解决方案**：核对 wing ID 并重新部署航母。

- **内置插件出现在可拆卸列表或 OP 计算中**：`.ship` 键名拼错 (例如写成 `builtInWeapon` 而不是 `builtInWeapons`) 时，内置内容会被静默忽略。

    - **解决方案**：改正键名后重启。

- **战机内置武器不转动或不追踪**：战机内置槽位不会提供正常武器的装配行为。

    - **解决方案**：取消内置槽位，改为正常槽位并在 `.variant` 中装配，再重新生成联队。

- **装饰武器仍被 AI 开火或计入面板**：槽位 `type` 不是 `DECORATIVE`，或武器 `.wpn` 的 `"type": "DECORATIVE"` 不一致。

    - **解决方案**：两处都改为装饰类型后重新载入。

## 总结

本节在普通舰船链的基础上强化了船体对装配的强制约束：`builtInWeapons` 按槽位挂武器、`builtInMods` 按 ID 挂船插、`builtInWings` 按顺序填甲板。

不过，虽然和技术本身无关，但编者必须指出的是，滥用内置插件 (是的，单独点名内置插件，因为这种例子已经太多了) 可能并不会显得您的舰船如何牛逼，而更只可能令人觉得 "不过是又一个插件列表堆成墙的东西"。

## 建议参考项

- `com.fs.starfarer.api.combat.WeaponAPI`：`WeaponType` 枚举，含 `BUILT_IN` 与 `DECORATIVE` 两种类型。
- `data/hulls/onslaught.ship`：原版 `builtInMods`/`builtInWeapons` 与 BUILT_IN 槽位示例。
- `data/hulls/shade.ship`：原版 `builtInMods` 示例 (`phasefield`/`delicate`)。
- `data/hulls/tempest.ship`：原版 `builtInWings` 示例 (`terminator_wing`)。
- `data/hulls/buffalo.ship`：装饰槽位与信号灯 `builtInWeapons` 示例。
- `data/weapons/blinker_red.wpn`、`data/weapons/blinker_green.wpn`：装饰武器 `.wpn` 示例 (`"type": "DECORATIVE"`)。
- `data/hullmods/hull_mods.csv`：船插 ID 注册表 (`phasefield`/`delicate`)。
- `data/weapons/weapon_data.csv`：武器 ID 注册表 (`tpc` 行、`tags`/`hints` 列)。
- `data/hulls/wing_data.csv`：战机 ID 注册表 (`terminator_wing`)。
- `data/hulls/GearIn_artizan.ship`、`data/hulls/GearIn_forging.ship`、`data/hulls/GearIn_furnaceman.ship`：本节三个例子修改的文件。
- 以上均为 MOD 内相对路径。
