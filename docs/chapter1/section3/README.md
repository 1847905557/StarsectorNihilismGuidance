# 制作一艘航母与战机

航母的船体与 [C1-S2] 没有本质差别，多出的只是甲板。

但战机可就要复杂多了。首先，它本身也是一艘船；然后，它还要另外登记装配；最后才是组装成联队。编者把航母和战机放在同一节，单纯只是因为缺了任何一方，另一方都无从测试，如果真按顺序讲解，那航母流玩家在前三章大概都只能对着数据想象了。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C1-S2] 的舰船编辑器流程，航母与战机的船体都按它制作；
- [C1-S2] 的 `ship_data.csv` 列与复制原版行的做法；
- [C1-S2] 的 Console Commands 与 `Addship`，本节用它们测试；
- JSON 的基本写法：花括号、方括号、键值对与逗号，以及 [C1-S1] 提到的宽松 JSON，装配文件按它手工修改。

## 准备贴图

获取贴图的部分已经无需赘述，下面两张贴图的制作时长共计不到 1 分钟：

![看炉人级航母贴图](./GearIn_furnaceman.png 'GearIn_furnaceman.png')

![炉渣级战机贴图](./GearIn_slag.png 'GearIn_slag.png')

航母命名为 `看炉人-级 (Furnaceman)`，ID 为 `GearIn_furnaceman`，贴图放入 `graphics/ships/`。战机命名为 `炉渣-级 (Slag)`，ID 为 `GearIn_slag`，贴图放入 `graphics/ships/fighter/`。战机单独放一个文件夹并非强制，但战机一多，您会庆幸自己这么做了。

![航母与战机贴图在 graphics/ships 目录中的位置](./storage.png 'storage.png')

## 制作航母

### 设置甲板

按 [C1-S2] 的流程制作看炉人的舰船文件，只多一步：设置完引擎后，按 `L` 设置甲板。

![舰船编辑器中的看炉人级航母](./carrier_make.png 'carrier_make.png')

每个甲板由若干个港口 (Port) 组成，战机从港口起飞和降落。**图标右下角的数字表示它属于第几个甲板**，同一数字的图标数量即为这个甲板的港口数。看炉人有 2 个甲板，所以右下角的数字最大为 2。

每个甲板至少设置 2 个港口，编者建议设置 4 个。港口过少时战机出入异常缓慢；港口数为 0 时，战机直接从舰船中心生成，看上去像是从船体里长出来的。

![舰船编辑器中的航母甲板港口设置](./carrier_port.png 'carrier_port.png')

其余属性设置完毕后，按 `V` 保存。

### 填写航母数据

打开原版 `ship_data.csv`，复制 `秃鹰-级 (Condor)` 航母的一行到 MOD 的 `ship_data.csv`，再逐项修改：

```csv
工匠,GearIn_artizan,驱逐舰,未知,ammofeed,10,5000,500,3800,4167,200,95,,80,60,40,30,60,400,FRONT,,300,0.4,0.8,,,50,100,100,60,2,30,9,40000,,5,12,360,0.25,10,10,,,,,,,,,,GearIn_bp,,,,0.5,2,3,,300
看炉人,GearIn_furnaceman,轻型航母,未知,targetingfeed,11,4500,400,3000,2813,225,75,2,75,50,30,25,25,450,FRONT,,300,0.4,0.8,,,90,150,40,40,2,20,9,52000,,5,15,360,0.25,13,13,,,,,,,,,CARRIER,GearIn_bp,,,,0.5,2,3,,318
```

此处只列出数据行，列名行与 [C1-S2] 相同。

看炉人换上了原版 `苍鹭-级` 航母的战术系统 `目标馈送系统` (`targetingfeed`)：激活后，航母向自己的战机实时传送目标数据，短时间内提高它们的伤害。驾驶者由此获得一种在关键时刻 "下令出击" 的操作感。它的槽位大多换成了防御用的小型实弹，作为补偿，结构更高、部署点更低。

航母与普通舰船的区别只在两列：`fighter bays` 填甲板数，此处为 2，须与 `.ship` 中的甲板数一致；`hints` 填 `CARRIER`，让战斗 AI 按航母行动。

## 制作战机

### 制作战机的船体

"战机的舰船文件" 听上去很奇怪，但战机确实是一种舰船。按 [C1-S2] 的流程制作炉渣的舰船文件，`hullSize` 选 `FIGHTER`。

![舰船编辑器中的炉渣级战机](./fighter_make.png 'fighter_make.png')

::: caution 战机的船体约束

`hullSize` 必须是 `FIGHTER`。游戏以此判定战机身份，选错时它不会获得战机 AI，也不会作为联队成员起飞、返航和补充。

战机的武器槽不要设为 `BUILT_IN`。战机的武器由装配文件安装，内置槽位会与之冲突。

:::

战机的碰撞箱**越简单越好**，甚至可以为此牺牲一些精度。战场上同时存在几十架战机，每一个多余的顶点都要乘上这个数。

### 填写战机的船体数据

"战机舰船的数据" 听上去更奇怪了。复制原版 `阔剑-级 (Broadsword)` 的一行，修改后写入 `ship_data.csv`：

```csv
炉渣,GearIn_slag,,未知,flarelauncher_fighter,,750,100,200,,100,0,,180,400,350,90,180,30,NONE,,0,0,0,,,1,1,,,,,,,,,,,,,,,,,,,,,,,,,,,0,,,,104
```

炉渣的幅能容量与耗散由阔剑的 80 / 10 提高到 200 / 100，用来支撑轻型双管自动炮；作为代价，最大速度降低了 20。战机的装配点数、价格和部署相关的列都留空，这些由联队数据决定。

### 制作战机的装配

装配 (variant) 记录一艘船装了什么武器和船插。舰船编辑器的确能制作装配，但编者**极度不推荐使用它**：它不利于建立对文件的直觉，对后期调整也没有任何帮助。

打开原版 `data/variants/fighters/`，把 `broadsword_Fighter.variant` 复制到 MOD 的 `data/variants/fighter/`，重命名为 `GearIn_slag_normal.variant`。装配文件夹的名字可以自取，游戏按 `variantId` 识别装配，不看所在目录。

![炉渣级战机装配文件的命名与存放位置](./fighter_rename.png 'fighter_rename.png')

修改后的内容如下：

```json {3-5,15,16}
{
	"quality":0.5,
	"displayName":"重型战机",
	"hullId":"GearIn_slag",
	"variantId":"GearIn_slag_normal",
	"fluxVents":0,
	"fluxCapacitors":0,
	"hullMods":[], # array of strings

	# mode is either LINKED or ALTERNATING
	# slot ids (WS ***) must match what's in the .ship file
	"weaponGroups":[
		{"mode":"LINKED",
		 "weapons":{
		 			"WS0001":"lightdualac",
		 			"WS0002":"lightdualac",
		 		   },
		},
	],
}
```

这是一份原版风格的宽松 JSON，`#` 后的内容是注释。需要修改的只有：

| 字段           | 含义                                             |
| -------------- | ------------------------------------------------ |
| `displayName`  | 装配名，显示在战机的数据页面                     |
| `hullId`       | 使用的船体，即 `GearIn_slag`                     |
| `variantId`    | 装配 ID，与文件名相同，即 `GearIn_slag_normal`   |
| `hullMods`     | 预装的船插                                       |
| `weaponGroups` | 武器组；`weapons` 中左侧是槽位 ID，右侧是武器 ID |

武器 ID 可以在原版 `data/weapons/weapon_data.csv` 中查到，`lightdualac` 是轻型双管自动炮。

::: warning 槽位 ID 必须存在于船体文件中

原版槽位 ID 的习惯写法是 `WS 001`，舰船编辑器生成的是 `WS0001`。复制原版装配后，务必把槽位 ID 改成您 `.ship` 文件里实际存在的那些，否则游戏报错。

:::

### 登记联队

"战机的战机数据" 听上去比前两个还要奇怪。联队 (wing) 是航母实际装载的单位：几架同样的战机，加上队形、角色和整备时间。

打开原版 `data/hulls/wing_data.csv`，复制列名行与 `broadsword_wing` 一行，写入 MOD 的 `data/hulls/wing_data.csv` 后修改：

```csv {2}
id,variant,tags,tier,rarity,fleet pts,op cost,formation,range,attackRunRange,attackPositionOffset,num,role,role desc,refit,base value,,,,,,,,,,,,,number
GearIn_slag_wing,GearIn_slag_normal,"fighter4, fighter, low, GearIn_bp",0,,6,8,V,4000,500,,3,FIGHTER,重型战机,12,6000,,,,,,,,,,,,,1
```

`tags` 中的蓝图标签换成了 `GearIn_bp`，并删去了原版的 `lowtech_bp, merc, ind`，这些标签会让炉渣出现在原版势力的舰队里。

::: caution 联队 ID 绝对不能与船体 ID 相同

联队 ID 按惯例以 `_wing` 结尾。

联队 ID 与船体 ID 相同时，部分按 ID 生成联队的方法会生成错误的对象。

如果您真的这么干了，虽然您不会在正常测试中感觉到任何问题，但一旦您后期有类似需求，或您的 MOD 和其它有类似功能的 MOD 一同运行，那么您会迅速理解为什么这一则警告是**红色**的。
:::

| 列                     | 含义                                                              |
| ---------------------- | ----------------------------------------------------------------- |
| `id`                   | 联队 ID                                                           |
| `variant`              | 战机使用的装配 ID                                                 |
| `tags`                 | 自动装配优先级、分类与蓝图标签，见下文                            |
| `tier`                 | 科技等级；常规市场只刷新 `tier` 不大于 3 的联队                   |
| `rarity`               | 稀有度，影响市场与掉落权重，照抄原版即可                          |
| `op cost`              | 装配点数                                                          |
| `formation`            | 队形：`V`、`CLAW`、`BOX`、`DIAMOND`                               |
| `range`                | 联队离开母舰的最大交战距离                                        |
| `attackRunRange`       | 攻击时的盘旋距离                                                  |
| `attackPositionOffset` | 攻击位置的偏移                                                    |
| `num`                  | 每个联队的战机数                                                  |
| `role`                 | 战机 AI：`FIGHTER`、`INTERCEPTOR`、`BOMBER`、`SUPPORT`、`ASSAULT` |
| `role desc`            | 战机类型的显示文字                                                |
| `refit`                | 整备一架战机所需的秒数                                            |
| `base value`           | 每个联队的价格                                                    |

`role` 决定 AI 怎么飞：`INTERCEPTOR` 优先追击范围内最近的敌方战机，`BOMBER` 对舰船进行攻击航线，`SUPPORT` 留在母舰附近不主动离开。

`tags` 的常用取值：

| tag                      | 作用                                                                        |
| ------------------------ | --------------------------------------------------------------------------- |
| `fighter1`–`fighter5` 等 | 自动装配时的优先级，数字越大越优先；`interceptor`、`bomber`、`support` 同理 |
| `fighter` 等             | 分类标签，与 `role` 对应；分类与优先级标签各写一个                          |
| `auto_fighter`           | 只有带此标签的联队能被无人舰船使用                                          |
| `rapid_reform`           | 联队倾向于保持严格队形                                                      |
| `leader_no_swarm`        | 长机接敌后拉开距离，不一拥而上                                              |
| `wingmen_no_swarm`       | 僚机接敌后拉开距离，不一拥而上                                              |
| `match_leader_facing`    | 僚机与长机保持相同朝向                                                      |
| `attack_at_an_angle`     | 倾向于以侧面武器角度对敌                                                    |
| `independent_of_carrier` | 母舰被击毁后仍正常作战，不会溃散                                            |

## 战机升空

1. 启用齿轮工业和 Console Commands，进入任意生涯存档。
2. 在控制台执行 `Addship GearIn_furnaceman` 与 `Addwing GearIn_slag_wing`。
3. 在舰队界面为看炉人的甲板装上炉渣。炉渣应当出现在战斗机一栏，类型显示为 `重型战机`。
4. 进入一场战斗并部署看炉人。

战机应当从甲板的港口起飞，而不是从船体中心冒出来；被击落的战机会在整备后补充，返航时降落到港口。

![看炉人级航母在战斗中投放战机](./carrier_play.png 'carrier_play.png')

![炉渣级战机联队在战斗中的表现](./fighter_play.png 'fighter_play.png')

## 常见问题

- **战机从舰船中心生成**：对应甲板没有设置港口。
    - **解决方案**：在舰船编辑器中按 `L` 为每个甲板补齐港口并保存。

- **控制台提示 `No LPC found with id [...]`**：联队没有被读取，或输入的是船体 ID、装配 ID 而非联队 ID。
    - **解决方案**：检查 `wing_data.csv` 的行是否加载，使用 `GearIn_slag_wing`。

- **战机起飞后没有武器**：装配中的槽位 ID 与 `.ship` 不一致。
    - **解决方案**：把装配中的槽位 ID 改为 `.ship` 中实际存在的 ID。

- **战机不返航、不补充**：炉渣的 `hullSize` 不是 `FIGHTER`，或联队、装配、船体三层 ID 没有依次指向现有对象。
    - **解决方案**：按联队 `variant` → 装配 `hullId` → 船体 `hullId` 的顺序逐一核对。

## 建议参考项

- `data/hulls/wing_data.csv`：原版联队数据，各类战机的参考样本。
- `data/variants/fighters/broadsword_Fighter.variant`：本节战机装配的复制来源。
- `data/hulls/condor.ship`：原版轻型航母，可对照甲板设置。
- `com.fs.starfarer.api.loading.WingRole`：`role` 列的全部取值。
- `com.fs.starfarer.api.loading.FormationType`：`formation` 列的全部取值。
