# 补完描述文本

本节系统讲解 `descriptions.csv` 的描述类型、字段用途和对象绑定关系。您将沿用已有的描述文件，为齿轮工业势力与 GearIn Second 补上说明，再从各类界面确认对象如何读取这些文本。

*本节预期阅读时间：\~20 分钟。*

*本节预期操作时间：\~25 分钟。*

## 学习目标

- 按对象选择 `Description.Type`、查找键和文本字段；
- 解释 `text1` 至 `text5` 的默认值与各类型的显示用途；
- 使用船体描述键、行星描述键和实体自定义描述键连接对象与文本；
- 为既有战术系统、势力和行星编写能够被读取的描述行；
- 从界面显示位置定位描述绑定与字段使用问题。

## 前置知识

- [C1-S7] 的 CSV 转义、描述注册、`id + type` 与基础对象描述；
- [C2-S3] 的战术系统 ID、舰船挂载与效果参数；
- [C3-S1] 的 `gear_industry` 势力 ID；
- [C3-S4] 的星系生成器、行星对象和新档生成入口；
- 理解数据库 (codex)、货舱、行星详情与战斗指挥界面的用途。

## 从描述行取得文本

[C1-S7] 建立的 `data/strings/descriptions.csv` 继续作为描述入口。表头、空列、引号、注释和编码按 [C1-S7] 的规则书写；扩展对象时，在同一个文件中追加对应行。

`id + type` 共同标识描述。类型相同而 ID 不同的行属于不同对象；ID 相同而类型不同的行也属于不同描述。`Description.getUID()` 将这两个值组合为 `id + "_" + type.name()`。

游戏在启动阶段读取原版与已启用 MOD 的同路径描述文件。同一文件中的重复组合会中止加载，跨文件同键行按读取顺序保留先处理的行。自有描述必须保持键唯一；严禁把重复登记既有键当作修改描述的方式。

需要整体替换数据文件时，`mod_info.json` 的 `replace` 数组可以声明对应路径。这种配置改变的是整份文件的读取范围，替换文件必须承担该路径所需的全部描述。新增对象的文本直接追加自有行。

运行时通过 `SettingsAPI.getDescription(id, type)` 查询描述对象。下面的局部片段取出 [C1-S7] 已注册的工匠船体描述：

```java
Description description = Global.getSettings().getDescription(
        "GearIn_artizan", Description.Type.SHIP);
String firstParagraph = description.getText1FirstPara();
```

`id` 为 `GearIn_artizan`，`type` 为 `SHIP`，`getText1FirstPara()` 读取 `text1` 的第一段。界面根据自己的用途选择全文、段落或其他文本列。

### 描述字段默认值速查表

| 字段 | 默认值 | 读取方式 |
|---|---|---|
| `text1` | `No description... yet` | `getText1()`、`getText1FirstPara()`、`getText1Paras()` |
| `text2` | `No description... yet` | `getText2()`、`getText2Paras()` |
| `text3` | `No description... yet` | `getText3()`、`getText3Paras()` |
| `text4` | `null` | `getText4()`、`getText4Paras()` |
| `text5` | `null` | `getText5()`、`getText5Paras()` |
| `notes` | 空 | CSV 中的维护备注 |

`hasText1()`、`hasText2()`、`hasText3()` 用于判断字段是否包含实际内容，占位字符串也会被判为空；`hasText4()`、`hasText5()` 判断对应字段是否为非空字符串。段落读取方法按换行拆分文本，去掉段落首尾空白并跳过空段落。

同一列在不同类型中承担不同职责。`text2` 可以是舰船引言、系统分类、武器署名、图库正文或实体标题，填写前先确认读取它的对象类型。

## 按对象选择描述类型

### SHIP：舰船与战机船体

`SHIP` 的查找键取船体的 `descriptionId`，基础船体使用 `hullId`。[C1-S7] 已为工匠、守炉人、锻件和炉渣登记各自的船体描述，战机联队通过装配读取对应战机船体的同一行。

- `text1`：船体详细说明，供数据库详情、装配、舰队与蓝图等界面读取；
- `text2`：舰船简略引言；有实际内容且 `text3` 为空时，舰队 tooltip 使用它，其余情况采用 `text1` 第一段；
- `text3`：受限船体详情使用的未知条目文本。

装配带 `ship_limited_tooltip` tag 时，数据库船体详情采用 `text3`；该列缺少实际内容时显示 `UNKNOWN ENTRY`。船体的 `limited_tooltip_if_locked` 用于锁定状态下的受限显示，两项标记分别作用于装配和船体。

### WEAPON：武器

查找键为 `weapon_data.csv` 的武器 ID。`text1` 是详细说明，货舱武器 tooltip 与数据库武器详情读取它；`text2` 可以作为署名。

`text2` 以 `-` 开头时，武器 tooltip 与数据库详情把 `text1` 第一段渲染为斜体，并附加署名行。普通说明可以保留正常正文。

装配武器列表的副标题优先使用 `weapon_data.csv` 的 `primaryRoleStr`，描述行的 `text2` 是备用值。[C1-S5] 的三门武器已设置自己的 `primaryRoleStr`，修改详细描述和修改武器定位文字分别作用于对应位置。

### SHIP_SYSTEM：战术系统

查找键为 `.system` 与 `ship_systems.csv` 共用的系统 ID，舰船通过 `ship_data.csv` 的 `system id` 或 `defense id` 使用这个系统。

- `text1`：系统详细说明；
- `text2`：系统类型标签，数据库按它分类，缺少实际内容时使用 `Special`；
- `text3`：装配界面的简略说明，缺少实际内容时显示 `text1`；
- `text4`：装配说明中需要高亮的文本，多个片段必须用 `|` 分隔；
- `text5`：数据库系统详情的 `Effects` 文本，`{{内容}}` 标记需要高亮的部分。

原版 `maneuveringjets` 行同时展示系统分类、短说明和效果文本：

```csv
maneuveringjets,SHIP_SYSTEM,Engages extra thrusters to greatly improve the ship's maneuverability. Also provides a slight boost to the ship's top speed.,Movement,Provides a brief boost to maneuverability and top speed.,,The ship's top speed is increased by {{50}}.,
```

`Movement` 写在 `text2`，简略说明写在 `text3`，带 `{{50}}` 的效果行写在 `text5`。这条原版行用于对照字段；本节为 [C2-S3] 已注册的 `GearIn_burstdrive` 和 `GearIn_resistfield` 编写各自的系统说明。

### RESOURCE：商品

查找键为 `data/campaign/commodities.csv` 的商品 ID，例如 `supplies`、`fuel`。货舱与交易界面的商品说明读取 `RESOURCE` 行的 `text1`，正文按段落显示。

商品的默认显示文本通过 `RESOURCE` 描述注册。观察原版补给时，可以把货舱 tooltip 与 `supplies,RESOURCE` 行对应起来。

### PLANET：行星类型

查找键由 `data/config/planets.json` 中的行星类型决定。类型对象可以填写 `descriptionId`，缺省使用类型键；多个类型也可以共用一个描述键。行星悬停 tooltip 与详情面板读取对应 `PLANET` 行的 `text1`。

`PlanetAPI.setDescriptionIdOverride()` 可以为一颗行星指定 `PLANET` 描述键，`setCustomDescriptionId()` 则连接 `CUSTOM` 描述。具有自定义描述的行星优先使用 `CUSTOM` 正文。

[C3-S4] 的 GearIn Prime 使用原版 `terran` 类型，可以直接观察该类型的说明。[C4-S5] 的类型定义通过 `planets.json` 的 `descriptionId` 连接 `GearIn_ember_world` 描述行。

### ASTEROID：小行星

默认查找键固定为 `asteroid`。小行星悬停 tooltip 读取 `asteroid,ASTEROID` 的 `text1`，标题使用对象名称或默认的 `Asteroid`。

默认小行星说明由这一行共用。观察时使用已有小行星对象，把悬停正文与原版描述行对应起来。

### FACTION：势力

查找键为势力文件的 `id`。势力信息面板读取 `FACTION` 行的 `text1`，按段落显示说明。

[C3-S1] 已注册 `gear_industry`，因此描述行直接使用 `gear_industry,FACTION`。势力文件确定身份，描述文件提供正文，两个文件通过相同的势力 ID 对应。

### TERRAIN：生涯地形

查找键为地形插件的 `getTerrainId()`。星云、小行星带、磁场、日冕和事件视界等地形在悬停 tooltip 中显示对应 `TERRAIN` 行的 `text1`。

超空间地形根据区域状态查询 `terrainId + "_abyssal"`、`"_normal"`、`"_deep"` 或 `"_storm"` 等变体键。自定义地形的描述键必须与插件实际查询的键一致，才能在对应区域显示正文。

### ACTION_TOOLTIP：战斗命令

查找键为战斗指挥命令的小写名称，例如 `avoid`、`assault`、`escort`。战斗中悬停对应 C2 命令时，tooltip 读取 `text1`，命令的短标签可以保存在 `text2`。

原版 `avoid` 行包含分段正文和 `Avoid` 标签。观察时在战斗指挥界面选择目标并悬停命令，核对对应描述内容。

### GALLERY：图库插图

查找键为 graphics 配置中 `illustrations` 分类的贴图键。`CodexDataV2.populateGallery()` 遍历 `getSpriteKeys("illustrations")`，逐个查询同键的 `GALLERY` 描述。

- `text1`：图库条目标题，`hasText1()` 为真时条目进入列表；
- `text2`：详情正文，按段落显示；成对双引号包住的段落渲染为斜体，其后的 `-` 开头段落作为灰色署名；
- `text3`：显示与解锁标记；转为小写后含 `hide_in_codex` 时隐藏，含 `unlocked` 时默认解锁，其余条目随玩家见过该插图而解锁。

描述行的键必须与 `illustrations` 分类的贴图键逐字符一致。图库条目由贴图键和有效标题共同建立，填写描述时要同时确认这两个输入。

### CUSTOM：实体自定义描述

描述键由创建对象的代码或实体配置提供。`SectorEntityToken.setCustomDescriptionId(id)` 把一个描述键关联到行星、空间站、跳跃点或其他生涯实体；实体配置也可以使用 `customDescriptionId`。

- `text1`：实体 tooltip 和详情正文；
- `text2`：实体 tooltip 使用的自定义标题，实际采用它的界面优先于对象名称；
- `text3`：行星接触后显示的文本；
- `text4`、`text5`：可由 Rules 的 `PrintDescription` 命令按字段编号读取。

`PrintDescription` 的参数 `1` 至 `5` 分别选择对应文本列。目标为行星且 CUSTOM 各列都没有实际内容时，该命令改用行星描述覆盖键或类型键查询 `PLANET` 行。

行星的 CUSTOM 描述属于具体行星，PLANET 描述属于行星类型。本节使用 GearIn Second 连接自定义行星文本，GearIn Prime 继续使用类型描述。

### 其他对象描述来源速查表

| 对象 | 描述来源 | 使用方式 |
|---|---|---|
| 战机联队 | 对应战机船体的 `SHIP` 行 | 联队通过装配读取船体说明 |
| 船插 | `data/hullmods/hull_mods.csv` 的 `desc` | `HullModSpecAPI.getDescription(HullSize)` 按船体等级代换 `%s` |
| 市场条件 | `data/campaign/market_conditions.csv` 的 `desc` | 市场条件 tooltip 读取说明 |
| 特殊物品 | `data/campaign/special_items.csv` 的 `desc` | `BaseSpecialItemPlugin.createTooltip` 读取物品规格 |
| 商品 | `descriptions.csv` 的 `RESOURCE` 行 | 货舱和交易界面读取商品说明 |

各 CSV 描述列的字段转义采用 [C1-S7] 的写法，显示规则由对应对象决定。

### Description.Type 完整速查表

| 类型 | 描述键来源 | 主要显示位置 |
|---|---|---|
| `SHIP` | 船体 `descriptionId`，基础船体使用 `hullId` | 舰船与战机详情、舰队、装配和蓝图 |
| `WEAPON` | 武器 ID | 武器 tooltip、装配列表和数据库 |
| `SHIP_SYSTEM` | 系统 ID | 装配系统行和数据库系统详情 |
| `RESOURCE` | 商品 ID | 货舱和交易界面 |
| `PLANET` | 行星类型、类型 `descriptionId` 或运行时覆盖键 | 行星悬停与详情 |
| `ASTEROID` | `asteroid` | 小行星悬停 |
| `FACTION` | 势力 ID | 势力信息面板 |
| `TERRAIN` | 地形插件实际查询的 ID | 地形悬停 |
| `ACTION_TOOLTIP` | 战斗命令的小写名称 | 战斗指挥命令 |
| `GALLERY` | `illustrations` 分类的贴图键 | 图库标题、正文和解锁状态 |
| `CUSTOM` | 实体的 `customDescriptionId` | 实体说明、行星接触与 Rules 文本 |

## 扩展船体描述键与前置文字

`.ship` 与 `.skin` 可以通过 `descriptionId` 指向 `SHIP` 描述行。字段改变的是描述查找键，船体注册仍使用原来的船体 ID；皮肤可以共享已有正文，也可以通过独立键使用自己的说明。

`descriptionPrefix` 保存正文之前的文字。`ShipBlueprintItemPlugin` 读取船体的前置文字，用 `前缀 + "\n\n" + text1 第一段` 组成蓝图说明。描述键负责选正文，前置文字负责补充这份正文。

编者在基础船体上保留默认描述键，在皮肤需要独立说明时使用这两个字段。这样对象的注册 ID 与常用描述行可以直接对应，皮肤差异也有明确位置。

## 把描述连接到既有对象

### 为势力补上正文

在已有 `descriptions.csv` 的表头后追加下面的局部片段：

```csv
gear_industry,FACTION,以重型制造、耐用结构和严格工序闻名的工业联合体。,,,,,
```

`gear_industry` 与 [C3-S1] 的势力文件 `id` 相同。势力信息面板查询这个 ID 时，读取 `text1` 作为正文。

### 给 GearIn Second 设置自定义描述

在同一个文件中追加行星的局部描述片段：

```csv
GearIn_description_second,CUSTOM,齿轮工业外围的一颗荒芜行星，表面留有早期勘探设施的遗迹。,GearIn Second,您的舰队接近了 GearIn Second，地表勘探设施的信号仍在重复播放。,,,
```

`text1` 是行星正文，`text2` 是供相应 tooltip 使用的标题，`text3` 是接触行星后显示的文本。`GearIn_description_second` 由本节定义，实体 ID 继续使用 [C3-S4] 的 `GearIn_planet2`。

打开 [C3-S4] 的 `GearInSystemGenerator.generate()`，在创建第二颗行星的语句之后加入绑定调用。局部片段如下，其中 `second` 是生成器刚创建的行星：

```java
PlanetAPI second = system.addPlanet(
        "GearIn_planet2", star, "GearIn Second",
        "barren", 200, 90, 5200, 200);
second.setCustomDescriptionId("GearIn_description_second");
```

`setCustomDescriptionId` 的参数必须与 CUSTOM 行的 `id` 一致。生成器在创建新档时执行这段代码，新档中的 GearIn Second 随后使用自定义描述；描述文件中的文本修改在重启后加载。

### 给战术系统补上各列说明

[C2-S3] 的燃烧驱动与抵抗力场已经注册并装到工匠上。本节以这两个系统 ID 登记 `SHIP_SYSTEM` 行，分别提供详细说明、分类、装配短说明、高亮片段和效果文本。

燃烧驱动的效果脚本把速度与加速度加成设为 `200`，描述的 `text5` 使用 `{{200}}` 显示该数值。抵抗力场的描述对应最高高爆与 EMP 减伤 `75%`、动能与能量减伤 `50%`。这些文本随系统 ID 被读取，数值对应各自的效果脚本。

## 行星描述在界面中的显示位置

在行星悬停界面中，观察标题和说明正文：

![行星描述在界面中的显示位置](./desc_planet.png "desc_planet.png")

PLANET 行提供类型说明，CUSTOM 行通过具体实体的描述键提供正文与接触文字。行星接触文本显示在交互面板中。

## 完整最小例

本节在 `gear_in` 的 `data/strings/descriptions.csv` 中新增的记录如下。文件保留 [C1-S7] 的表头和基础对象描述，每个 `id + type` 必须只登记一次。这里各系统 ID 来自 [C2-S3]，势力 ID 来自 [C3-S1]：

```csv
GearIn_burstdrive,SHIP_SYSTEM,启动额外推进器，提高舰船的速度与加速度。,机动,提高速度与加速度。,速度|加速度,"速度最多提高 {{200}}。
加速度最多提高 {{200}}。",燃烧驱动
GearIn_resistfield,SHIP_SYSTEM,启动抵抗力场，降低舰船受到的伤害。,防御,降低高爆、EMP、动能与能量伤害。,高爆|EMP|动能|能量,"高爆与 EMP 伤害最多降低 {{75%}}。
动能与能量伤害最多降低 {{50%}}。",抵抗力场
gear_industry,FACTION,以重型制造、耐用结构和严格工序闻名的工业联合体。,,,,,
GearIn_description_second,CUSTOM,齿轮工业外围的一颗荒芜行星，表面留有早期勘探设施的遗迹。,GearIn Second,您的舰队接近了 GearIn Second，地表勘探设施的信号仍在重复播放。,,,
```

## 验证方案

前置数据：已启用 Console Commands 和包含 [C1-S7] 基础描述、[C2-S3] 战术系统与 [C3-S4] 星系生成器的 GearIn MOD，生成器已加入 Second 的描述绑定调用。

1. 保存描述文件与生成器，重启游戏，在 `starsector.log` 中观察 `Loading descriptions` 和描述文件加载结果。
2. 创建新生涯并进入 GearIn 星系，悬停 GearIn Second、打开行星详情，再接触它，分别核对自定义正文和接触文字。
3. 悬停 GearIn Prime 并打开详情，核对原版 `terran,PLANET` 的正文。
4. 打开势力信息面板，选择齿轮工业，核对 `gear_industry,FACTION` 的正文。
5. 使用控制台执行 `Addship GearIn_artizan`，打开工匠的装配界面，再打开数据库中的燃烧驱动与抵抗力场详情，核对短说明、分类、高亮片段与 `Effects` 文本。
6. 在货舱悬停补给，核对 `supplies,RESOURCE` 的正文；在数据库中检查原版 text2 以 `-` 开头的武器，观察署名与斜体正文。
7. 在数据库中查看工匠、守炉人、锻件、炉渣及三门 GearIn 武器，核对基础描述；查看受限船体条目，观察未知条目文本。
8. 在已有小行星带悬停小行星，再悬停带区地形，分别核对 `asteroid,ASTEROID` 和该地形查询的 `TERRAIN` 行。
9. 进入战斗指挥界面，选中目标并悬停规避、攻击和护航命令，核对 `avoid`、`assault`、`escort` 对应说明。
10. 打开数据库图库，查看已解锁插图，核对 `GALLERY` 标题、详情段落和署名。
11. 修改 `GearIn_description_second` 的 `text1` 后保存并重启，读取刚创建的生涯，核对 Second 正文。

预期结果：

- 描述文件加载完成，本节新增记录没有 CSV 或类型解析异常；
- GearIn Second 的正文为 CUSTOM 行 `text1`，接触文本为 `text3`，重启后正文采用保存的新文字；
- GearIn Prime 显示 `terran` 类型的 PLANET 正文；
- 齿轮工业势力信息显示本节 FACTION 正文；
- 燃烧驱动与抵抗力场分别显示 `机动`、`防御` 分类、短说明及带高亮数值的效果文本；
- 补给说明来自 `RESOURCE` 行，署名武器正文的第一段以斜体显示并附加署名；
- 基础船体、战机和武器详情继续读取各自描述，受限船体按 `text3` 或 `UNKNOWN ENTRY` 显示；
- 小行星与地形 tooltip 分别读取各自类型的正文；
- 战斗命令 tooltip 与命令键对应；
- 图库条目的标题来自 `text1`，详情来自 `text2`，引文与署名按各自格式显示。

## 故障分析

- **新增对象显示占位描述**：类型合法，但描述行的键与对象实际查询的键不一致。
    - **解决方案**：从对象定义确认船体描述键、武器 ID、系统 ID、商品 ID、势力 ID 或实体描述键，再统一对应行。

- **舰船引言与预期短文不同**：`text2` 缺少实际内容，或 `text3` 已被填写。
    - **解决方案**：简略引言写入 `text2`，普通船体的 `text3` 保持空值。

- **武器列表副标题保持原样**：该武器的 `primaryRoleStr` 优先于描述行的 `text2`。
    - **解决方案**：需要调整定位文字时修改 `weapon_data.csv` 的 `primaryRoleStr`。

- **系统分类显示 `Special` 或短说明显示长文**：对应系统描述的 `text2` 或 `text3` 缺少实际内容。
    - **解决方案**：按系统 ID 补齐分类与装配短说明。

- **系统说明中的文本没有高亮**：`text4` 的片段与 `text3` 实际文本不匹配，或多个片段的分隔符错误。
    - **解决方案**：让高亮片段逐字对应短说明，并用 `|` 分隔。

- **Second 仍显示行星类型说明**：当前生涯中的行星没有执行新的自定义描述绑定，或绑定键与描述行不一致。
    - **解决方案**：确认 `second.setCustomDescriptionId("GearIn_description_second")` 位于第二颗行星创建后，重新创建测试生涯。

- **Second 接触界面缺少新增文字**：对应 CUSTOM 行的 `text3` 为空，或多行 CSV 的字段边界错误。
    - **解决方案**：将接触文本写入该行 `text3`，按 [C1-S7] 的规则保存并重启。

- **图库条目缺失或锁定**：贴图键、`text1` 标题和 `text3` 显示标记共同决定条目状态。
    - **解决方案**：核对 `illustrations` 贴图键与描述键，填写有效标题，并按预期设置 `hide_in_codex` 或 `unlocked` 标记。

## 总结

`descriptions.csv` 用对象查询的键和类型确定描述，再由界面选择实际需要的文本列。把对象定义、描述行与显示位置对应起来，才能判断一段文字该写在哪里。

类型说明和实体说明分别服务各自的对象。本节为势力与战术系统提供了对应字段，并把 Second 连接到独立的 CUSTOM 描述；正文、分类、效果与接触文本各自通过明确的读取位置显示。

## 建议参考项

- `com.fs.starfarer.api.loading.Description`：描述类型、字段默认值与段落读取方法；
- `com.fs.starfarer.api.SettingsAPI`：`getDescription(id, type)`；
- `com.fs.starfarer.api.combat.ShipHullSpecAPI`：船体描述键与前置文字；
- `com.fs.starfarer.api.campaign.PlanetAPI`：行星描述覆盖键；
- `com.fs.starfarer.api.campaign.SectorEntityToken`：实体自定义描述键；
- `com.fs.starfarer.api.impl.codex.CodexDataV2`：系统分类与图库条目；
- `com.fs.starfarer.api.impl.campaign.rulecmd.PrintDescription`：按字段编号读取描述；
- `com.fs.starfarer.api.impl.campaign.PlanetInteractionDialogPluginImpl`：行星接触文本；
- `com.fs.starfarer.api.campaign.impl.items.ShipBlueprintItemPlugin`：舰船蓝图的前置文字与正文；
- `com.fs.starfarer.api.impl.campaign.terrain`：地形描述查询；
- `data/strings/descriptions.csv`：对象描述数据；
- `data/config/planets.json`：行星类型与描述键；
- `data/config/settings.json`：`graphics` 中的 `illustrations` 贴图键。
