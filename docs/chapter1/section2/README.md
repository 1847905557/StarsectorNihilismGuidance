# 制作一艘基本舰船

从一张贴图出发，经过舰船编辑器，再到数据表里的一行数值。于是一艘能开进战场的船就这么被变了出来。

虽然过程可能看似繁琐，但请务必掌握，因为这很可能是您接下来几个小时里最常做的流程。写起来倒是轻松，毕竟这里不教数值设计。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C1-S1] 的 MOD 文件夹与 `mod_info.json`，工匠放在这个 MOD 里；
- [C1-S1] 的 `GearIn_` 前缀规则，舰船 ID 按它命名；
- [C1-S1] 的 "只写新增的行"，`ship_data.csv` 只写工匠这一行；
- 一款能拼接图片并导出 PNG 的图像软件，贴图用它准备。

## 获取贴图

制作舰船首先需要一张贴图。

**编者并不擅长画图，但刚好也能展示获取贴图对于绝大多数人来说都不是一件困难的事情**。

这里以 `锤头` 与 `斗士` 的简单拼接为例。在 `Photoshop` 中拼接之后，做了一些简单的画笔涂抹和颜色修正，耗时共计 16 秒，最终效果已经勉强能看。

![由锤头与斗士贴图拼接出的舰船草图](./ship_making.png 'ship_making.png')

完成品如下。若您需要 (连斗士的引擎都没擦，也许您看不上)，可以随意取用：

![完成简单涂抹和颜色修正后的工匠级贴图](./GearIn_artizan.png 'GearIn_artizan.png')

::: caution 贴图尺寸

舰船贴图的宽和高都应当是偶数。奇数尺寸会让贴图中心位于半个像素处，武器和引擎随之偏移半个像素，画面发糊。工匠的贴图为 108 × 164。

贴图四周只保留边缘抗锯齿所需的透明像素，不要无意义地扩大画布。

:::

## 命名并放入贴图

为舰船命名时，**请有意避开已经存在的舰船名**，以便于您 MOD 的交流和推广。若您打算制作一个完整的势力 MOD，建议提前定下一整套命名风格。一味追求高端、玄奥、霸气、神秘的名字，只会令其他用户感到不适。

舰船需要中文名和英文名。本例的舰船命名为 `工匠-级 (Artizan)`，ID 按 [C1-S1] 的规则写作 `GearIn_artizan`。

贴图文件与 ID 同名，即 `GearIn_artizan.png`，放入 `graphics/ships/`：

![GearIn_artizan 贴图在 graphics/ships 目录中的位置](./rename_into.png 'rename_into.png')

## 制作舰船文件

`.ship` 文件记录贴图上的一切几何信息：碰撞范围、护盾、武器槽和引擎。手写它不现实，所以我们使用舰船编辑器，即首页 "准备开始" 中列出的 `sf-ship-ed`。它是社区制作的独立程序，不随游戏附带。

打开舰船编辑器，选择游戏目录：

![舰船编辑器中的游戏目录选择界面](./select_game.png 'select_game.png')

导入贴图后，流程十分明确：**从按键 `C` 开始一路向下**。制作过程中**记得多保存**，编辑器并不以稳定著称。

![舰船编辑器中的船体制作流程按键](./progress.png 'progress.png')

### 设置碰撞圈

按 `C` 设置质量中心和碰撞圈 (俗称 `黄圈`)。除非有特殊要求，**质量中心设在贴图中心**。

::: caution 黄圈必须包住整张贴图

黄圈之外的部分没有碰撞判定，炮弹会直接穿过去。

黄圈也不要比贴图大太多：碰撞会在离船体很远时就开始判定，浪费性能。

:::

![舰船编辑器中的质量中心和碰撞圈设置](./mass.png 'mass.png')

### 设置护盾范围

按 `S` 设置护盾中心和护盾半径 (俗称 `蓝圈`)。除非有特殊要求，**护盾中心与质量中心重合**。

蓝圈与黄圈相互独立，护盾判定只看护盾自身的半径与朝向。编者非常建议让蓝圈覆盖整张贴图：露在护盾外的部分会被炮弹直接命中，而这显然不是一个好的设计。

![舰船编辑器中的护盾中心和护盾范围设置](./shield.png 'shield.png')

### 设置碰撞箱

按 `B` 设置碰撞箱，按空格键切换对称设置。碰撞箱是贴图的轮廓多边形，炮弹按它判定是否命中装甲。顶点沿贴图外缘描一圈即可，过密浪费性能，过疏则命中位置失真。

![舰船编辑器中的舰船碰撞箱设置](./bounds.png 'bounds.png')

### 设置槽位

按 `W` 设置武器槽。`Shift + 鼠标` 拖动位置和朝向，空格键切换对称设置，其余操作见编辑器右下角。全部放好后，按 `T` 逐个设置槽位属性。

::: caution 槽位坐标必须为整数

非隐藏槽位的坐标位于半像素处时，装在上面的武器贴图会发糊。

:::

| mount     | 含义                                                   |
| --------- | ------------------------------------------------------ |
| TURRET    | 炮塔                                                   |
| HARDPOINT | 固定槽位，仍可小幅转动；武器后坐力减半、武器结构值更高 |
| HIDDEN    | 隐藏槽位，不显示武器贴图，安装的武器永远不会下线       |

槽位大小 `size` 只有 `SMALL`、`MEDIUM`、`LARGE` 三档。槽位类型 `type` 决定能装什么：

| type            | 含义                                                 |
| --------------- | ---------------------------------------------------- |
| BALLISTIC       | 实弹                                                 |
| ENERGY          | 能量                                                 |
| MISSILE         | 导弹                                                 |
| HYBRID          | 混合，实弹 + 能量                                    |
| SYNERGY         | 协同，能量 + 导弹                                    |
| COMPOSITE       | 复合，实弹 + 导弹                                    |
| UNIVERSAL       | 通用，全类型                                         |
| BUILT\_IN       | 内置武器专用，见 [C1-S6]                             |
| DECORATIVE      | 装饰武器专用，见 [C1-S6]                             |
| STATION\_MODULE | 模块连接点，见 [C4-S4]                               |
| SYSTEM          | 战术系统使用的位置，当前不必掌握                     |
| LAUNCH\_BAY     | **不要把武器槽设为这个类型**，甲板在专门的步骤中设置 |

槽位的 `angle` 是基础朝向，`arc` 是射界角度。`renderOrderMod` 调整渲染层级：默认大型武器压过中型、中型压过小型，炮塔压过固定槽位，数字越大越靠上层。

![舰船编辑器中的武器槽设置](./weapon.png 'weapon.png')

### 设置引擎

**跳过中间的步骤**，按 `E` 设置引擎，操作方式与槽位相同。全部放好后按 `T` 设置引擎属性。

引擎应当放在贴图像素的最边缘，长度可以适当拉长，过短的尾焰往往很难看。原版引擎风格 `style` 如下，颜色定义在 `data/config/engine_styles.json`：

| style         | 外观                     |
| ------------- | ------------------------ |
| LOW\_TECH     | 偏橘的黄色               |
| MIDLINE       | 亮黄色                   |
| HIGH\_TECH    | 蓝色                     |
| OMEGA         | 紫色，带光带拖尾，不常用 |
| COBRA\_BOMBER | 红色，带大光晕，不常用   |

![舰船编辑器中的引擎位置与风格设置](./engines.png 'engines.png')

工匠不是航母，没有甲板。甲板的设置见 [C1-S3]。

### 设置最终属性

按 `P`，再按 `T`，设置船体的整体属性。

`hullSize` 为舰船分级：`FRIGATE` 护卫舰、`DESTROYER` 驱逐舰、`CRUISER` 巡洋舰、`CAPITAL_SHIP` 主力舰、`FIGHTER` 战机。工匠是驱逐舰。

`style` 为舰船风格，决定护盾颜色等外观：`LOW_TECH` 为红色护盾，`MIDLINE` 与 `HIGH_TECH` 为蓝色护盾，定义在 `data/config/hull_styles.json`。

`hullId` 是船体的主键，**必须与 `ship_data.csv` 的 `id` 列一致**，否则那一行数据不会应用到这艘船上。`hullName` 会被 CSV 覆盖，但编者建议您照样填写。

![工匠级舰船文件的最终编辑器预览](./normal_finale.png 'normal_finale.png')

按 `V` 保存到 `data/hulls/GearIn_artizan.ship`：

![舰船编辑器保存船体文件的第一步](./save_1.png 'save_1.png')

![舰船编辑器保存船体文件的路径选择](./save_2.png 'save_2.png')

制作一艘船最麻烦的部分，到此已经结束了。

## 制作舰船数据

舰船的性能写在 `data/hulls/ship_data.csv`。在 MOD 中新建这个文件，按 [C1-S1] 的规则，只写您新增的行。

CSV 的第一行是列名，从第二行起每行是一条数据，数据之间用英文逗号分隔；数据本身含有英文逗号时，用英文双引号包起来。行首带 `#` 的行会被忽略，可以用来存放参考样本。

**最简单的做法是复制原版**。打开 `starsector-core/data/hulls/ship_data.csv`，复制列名行和 `锤头-级` 驱逐舰那一行，粘贴进来后逐项修改。编者还在文件中留下了几行带 `#` 的原版样本，以供对照：

![ship_data.csv 中的原版样本与工匠数据](./data_copy.png 'data_copy.png')

::: warning 复制样本时注意

粘贴原版行后，删去行首的 `#`，否则整行都不会被读取。

:::

```csv {6}
name,id,designation,tech/manufacturer,system id,fleet pts,hitpoints,armor rating,max flux,8/6/5/4%,flux dissipation,ordnance points,fighter bays,max speed,acceleration,deceleration,max turn rate,turn acceleration,mass,shield type,defense id,shield arc,shield upkeep,shield efficiency,phase cost,phase upkeep,min crew,max crew,cargo,fuel,fuel/ly,range,max burn,base value,parts/dmod,cr %/day,CR to deploy,peak CR sec,CR loss/sec,supplies/rec,supplies/mo,salary,salMult,c/s,c/f,f/s,f/f,crew/s,crew/f,hints,tags,logistics n/a reason,codex variant id,rarity,breakProb,minPieces,maxPieces,travel drive,number
#Lasher,lasher,Frigate,Low Tech,ammofeed,5,1750,300,2100,1750,140,55,,120,110,90,60,90,200,FRONT,,210,0.4,1,,,25,50,40,25,1,25,10,9000,,8,10,240,0.25,4,4,,,,,,,,,,"lowtech_bp, merc, ind",,,,0.4,2,2,,204
#Hammerhead,hammerhead,Destroyer,Midline,ammofeed,10,5000,500,5000,4167,250,95,,90,60,40,30,60,400,FRONT,,300,0.4,0.8,,,50,100,100,60,2,30,9,40000,,5,12,360,0.25,10,10,,,,,,,,,,"midline_bp, merc, ind",,,,0.5,2,3,,300
#Eagle,eagle,Cruiser,Midline,maneuveringjets,14,8000,1000,11000,14000,700,155,,60,40,30,15,20,1200,FRONT,,270,0.4,0.8,,,150,250,150,100,3,33,8,100000,,3,12,480,0.25,20,20,,,,,,,,,,"rare_bp, merc, ind",,,,0.5,2,3,,401
#Conquest,conquest,Battlecruiser,Midline,maneuveringjets,24,12000,1200,20000,30000,1200,315,,55,25,20,6,4,2500,OMNI,,90,0.4,1.4,,,400,500,300,300,10,30,8,300000,,3,15,600,0.25,40,40,,,,,,,,,,"rare_bp, merc",,,,0.5,2,4,,503
工匠,GearIn_artizan,驱逐舰,未知,ammofeed,10,5000,500,3800,4167,200,95,,80,60,40,30,60,400,FRONT,,300,0.4,0.8,,,50,100,100,60,2,30,9,40000,,5,12,360,0.25,10,10,,,,,,,,,,GearIn_bp,,,,0.5,2,3,,300
```

**一艘船最重要的特征是要有趣**。

工匠把锤头的中型实弹槽换成了中型通用槽，可以安装强力的导弹。编者对原版中型导弹 `歼灭者火箭炮` 装在驱逐舰上的表现十分感兴趣，因此把它的幅能容量、幅能耗散和速度都设得略低于锤头，让这艘船更依赖它的导弹优势。

`tags` 中以 `_bp` 结尾的是蓝图标签。生涯中的蓝图包和势力按这类标签决定自己掌握哪些舰船：原版锤头带有 `midline_bp`，因此会出现在中线科技的蓝图包里，也会被使用中线科技的势力生产。工匠改用齿轮工业自己的 `GearIn_bp`，不会混入任何原版来源；后续章节的蓝图包与势力都靠这个标签找到它。若您不希望这艘船在生涯中大量出现，可以清空 `tags`。

下表只列出制作舰船时常改的列。表中没有的列，要么是游戏不读取的说明列 (如 `8/6/5/4%`、`c/s`、`number`)，要么是当前阶段用不到的进阶列，照抄原版样本即可。

| 列                  | 含义                                                                                |
| ------------------- | ----------------------------------------------------------------------------------- |
| `name`              | 舰船名称，显示时后接 `designation`                                                  |
| `id`                | 舰船 ID，与 `.ship` 的 `hullId` 一致                                                |
| `designation`       | 舰船类型说明，显示在名称之后，可随意填写                                            |
| `tech/manufacturer` | 科技类型，原版有 `Low Tech`、`Midline`、`High Tech` 等，可随意填写                  |
| `system id`         | 战术系统 ID，原版取值见 `data/shipsystems/ship_systems.csv`                         |
| `fleet pts`         | 舰队生成和自动战斗使用的战斗力点数                                                  |
| `hitpoints`         | 结构                                                                                |
| `armor rating`      | 装甲                                                                                |
| `max flux`          | 幅能容量                                                                            |
| `flux dissipation`  | 幅能耗散                                                                            |
| `ordnance points`   | 装配点数                                                                            |
| `fighter bays`      | 甲板数量                                                                            |
| `max speed`         | 战斗中最大速度                                                                      |
| `acceleration` 等   | `acceleration`、`deceleration`、`max turn rate`、`turn acceleration` 共同决定机动性 |
| `mass`              | 质量，影响撞击和受推力时的表现                                                      |
| `shield type`       | `NONE` 无盾、`FRONT` 前盾、`OMNI` 全向盾、`PHASE` 右键战术系统                      |
| `defense id`        | `shield type` 为 `PHASE` 时的右键战术系统 ID                                        |
| `shield arc`        | 护盾角度                                                                            |
| `shield upkeep`     | 护盾维持倍率；填 0.4 时，开盾每秒产生 40% 基础耗散的软幅能                          |
| `shield efficiency` | 护盾效率；填 0.8 时，护盾每承受 1 点伤害产生 0.8 硬幅能                             |
| `phase cost`        | 右键战术系统的激活幅能，按幅能容量的比例计算                                        |
| `phase upkeep`      | 右键战术系统的每秒维持幅能，按幅能容量的比例计算                                    |
| `min crew`          | 最低船员需求                                                                        |
| `max crew`          | 最大载员                                                                            |
| `cargo`             | 货舱容量                                                                            |
| `fuel`              | 燃料容量                                                                            |
| `fuel/ly`           | 超空间中每航行 1 光年消耗的燃料                                                     |
| `max burn`          | 大地图最大航速                                                                      |
| `base value`        | 基础价格                                                                            |
| `cr %/day`          | 补给充足时每日恢复的 CR 百分比                                                      |
| `CR to deploy`      | 每次部署消耗的 CR 百分比                                                            |
| `peak CR sec`       | 峰值时间，单位为秒                                                                  |
| `CR loss/sec`       | 峰值时间耗尽后每秒下降的 CR                                                         |
| `supplies/rec`      | 部署点数，同时也是每次部署后恢复所需的补给                                          |
| `supplies/mo`       | 每月维护消耗的补给                                                                  |
| `hints`             | AI 与生涯行为提示，见下文                                                           |
| `tags`              | 标签，用于蓝图、势力和规则筛选                                                      |
| `rarity`            | 生涯中蓝图抽选的权重，不影响战斗与装配                                              |
| `breakProb`         | 被击毁时碎成残骸的概率                                                              |
| `minPieces`         | 碎裂时的最少块数                                                                    |
| `maxPieces`         | 碎裂时的最多块数                                                                    |

`hints` 的常用取值：

| hint                        | 作用                                                   |
| --------------------------- | ------------------------------------------------------ |
| `CARRIER`                   | 战斗 AI 按航母行动                                     |
| `PHASE`                     | 右键系统不是原版相位时，仍在势力学说与生产中归入相位舰 |
| `CIVILIAN`                  | 视为民用船，战斗 AI 按民用船行动                       |
| `COMBAT`                    | 与 `CARRIER` 或 `CIVILIAN` 并用，让 AI 更接近普通战舰  |
| `FREIGHTER`                 | 视为货船                                               |
| `TANKER`                    | 视为油轮                                               |
| `LINER`                     | 视为客船                                               |
| `NO_AUTO_ESCORT`            | 与 `CARRIER` 并用，不主动派战机护航友舰                |
| `UNBOARDABLE`               | 不可打捞                                               |
| `SHIP_WITH_MODULES`         | 模块船本体，见 [C4-S4]                                 |
| `HIDE_IN_CODEX`             | 不在数据库中显示                                       |
| `RENDER_ENGINES_BELOW_HULL` | 引擎渲染在贴图下方                                     |

### 零件费用与船员月薪

舰船的维修费用和船员工资同样影响玩家是否愿意使用它。

`parts/dmod` 是修复一项 D 类损伤所需的零件数，填整数。留空时，游戏用基础价格除以 15000 并四舍五入，再与舰级下限取较大值；护卫舰、驱逐舰、巡洋舰和主力舰的下限分别是 1、2、4、8。工匠的基础价格为 40000，修复一项 D 类损伤需要 3 个零件。

`salary` 是整艘船的基础船员月薪，单位为星币。留空时按 `舰级工资系数 × supplies/mo × salMult` 计算并四舍五入，四种舰级的工资系数分别是 50、50、75、150，`salMult` 默认为 1。工匠每月消耗 10 补给，基础月薪为 `50 × 10 × 1 = 500` 星币。

上述除数、零件下限和工资系数分别由原版 `settings.json` 的 `defaultDModRepairPartsBaseValueDivisor`、`minDModRepairCostInParts` 和 `shipSalaryBySize` 设定。

::: details 进阶：用船体皮肤制作后勤型

`.skin` 文件可以在原船基础上制作不同型号，继承船体和槽位，只覆盖部分属性。将下面的内容保存为 `data/hulls/skins/GearIn_artizan_logistics.skin`：

```json
{
    "baseHullId": "GearIn_artizan",
    "skinHullId": "GearIn_artizan_logistics",
    "hullName": "工匠 (后勤型)",
    "parts/dmod": 5,
    "salary": 300,
    "salMult": 0.75
}
```

`baseHullId` 指向原船，`skinHullId` 是后勤型的 ID。这个型号修复一项 D 类损伤需要 5 个零件，基础月薪为 300 星币。同时填写 `salary` 与 `salMult` 时，固定的 `salary` 优先；删去 `salary` 后，月薪按 `50 × 10 × 0.75 = 375` 星币重新计算。

皮肤也可以继承另一张皮肤。工资优先取当前皮肤的 `salary`；当前皮肤只改了 `suppliesPerMonth` 或 `salMult` 时重新计算，其余情况继承上一级。

:::

## 首舰下水

数据填写完毕后，这艘船就能被游戏读取了。它目前还没有获取途径，只能用 `控制台` 生成。控制台来自 Console Commands MOD，可在 FOSSIC 论坛下载。

1. 启用齿轮工业和 Console Commands，进入任意生涯存档。
2. 按 `Ctrl + Backspace` 打开控制台，输入 `Addship GearIn_artizan`。
3. 在舰队界面查看工匠：幅能容量 3800、耗散 200、最大速度 80，与 CSV 一致。
4. 进入一场战斗，观察贴图、护盾范围、武器槽与引擎尾焰是否与编辑器中的设定一致。

`Addship` 实际接收的是装配 ID。游戏加载船体时会自动生成一个空装配 `GearIn_artizan_Hull`，控制台在您输入舰船 ID 时会自动找到它。若制作了后勤型，用 `Addship GearIn_artizan_logistics` 生成它，核对名称、修复零件数和月薪。

**在继续之前，强烈推荐您亲自驾驶一下您制作的舰船**。

![工匠级驱逐舰在战斗中的实际表现](./battle.png 'battle.png')

这艘工匠的表现相当不错，可以用源源不断的火箭弹进行近距离压制。不过一旦被同等重火力盯上就会陷入僵局，战术系统也难以发挥。它也许还可以试试 `安全协议超驰` 船插配合离子脉冲的配置。作为一艘注重手感的驱逐舰，总体还算成功，但再加一些微调会更好。

## 常见问题

- **控制台提示找不到舰船**：CSV 行没有被读取 (例如行首仍带 `#`)，或 `.ship` 的 `hullId` 与 CSV 的 `id` 不一致。
    - **解决方案**：将两处 ID 统一为 `GearIn_artizan`，保存并重启游戏。

- **舰船显示为白块或缺图**：`.ship` 的 `spriteName` 没有指向实际的 PNG。
    - **解决方案**：核对 `graphics/ships/GearIn_artizan.png` 的路径、大小写和扩展名。

- **武器或引擎偏移半个像素**：贴图尺寸为奇数，或槽位坐标不是整数。
    - **解决方案**：把贴图改为偶数尺寸，在编辑器中重新对齐槽位。

- **游戏启动时报错并指向 `ship_data.csv`**：某一行的列数与列名行不一致，常见于数据里含有未加引号的逗号。
    - **解决方案**：从日志中第一个报错开始，检查对应行的逗号与引号。

## 建议参考项

- `data/hulls/ship_data.csv`：原版舰船数据，复制样本的来源。
- `data/hulls/hammerhead.ship`：锤头的船体文件，可与工匠对照。
- `data/config/engine_styles.json`：原版引擎风格的颜色定义。
- `data/config/hull_styles.json`：原版舰船风格与护盾颜色定义。
- `data/shipsystems/ship_systems.csv`：战术系统 ID，`system id` 列的取值来源。
