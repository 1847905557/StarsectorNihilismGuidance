# 注册一个势力

本节为 GearIn 注册一个能够被生涯星区读取的势力身份。页面只处理注册文件、显示名称、旗帜、人物名称和头像；舰船池、舰队学说与势力关系由下一节继续配置。

*本节预期阅读时间：~12 分钟。*

*本节预期操作时间：~10 分钟。*

## 学习目标

完成后，您将能够：

- 用 `data/world/factions/factions.csv` 注册一个 faction 文件；
- 配置势力 ID、显示名称、旗帜、名称来源和头像；
- 根据启动日志和情报界面确认势力身份已经加载；
- 从路径、必填字段和贴图资源三处定位加载故障。

## 前置知识

- [C1-S1] 的 MOD 目录、数据文件合并和全局 ID 规则；
- [C1-S2] 的资源路径和贴图加载规则；
- 能够打开生涯情报界面并读取 `starsector.log`。

## 确定势力身份

势力注册由两个文件共同完成：`factions.csv` 提供 faction 文件路径，faction 文件内部的 `id` 才是势力的注册键。文件名可以与 ID 不同，但教程统一使用 `gear_industry.faction` 和 `gear_industry`，让路径与身份保持容易核对的对应关系。

运行时通过 `Global.getSector().getFaction("gear_industry")` 取得该势力。市场、舰队和星系实体随后都使用同一个 ID 表示归属。

## 注册势力文件

在 `data/world/factions/factions.csv` 中写入表头和一行路径：

```csv
faction
data/world/factions/gear_industry.faction
```

这个 CSV 在所有启用来源之间按行合并。MOD 只提交自己的路径；行首使用 `#` 可以暂时停止加载该行。

完成信号：启动日志开始读取 `gear_industry.faction`，并打印 `Loading gear_industry faction`。

## 配置身份字段

在 `data/world/factions/gear_industry.faction` 中写入能够建立身份的最小数据：

```json
{
	"id":"gear_industry",
	"displayName":"齿轮工业",
	"displayNameWithArticle":"齿轮工业",
	"logo":"graphics/factions/gear_industry.png",
	"crest":"graphics/factions/crest_gear_industry.png",
	"color":[180,120,60,255],
	"shipNamePrefix":"GIS",
	"names":{
		"modern":2,
		"world":1,
	},
	"portraits":{
		"standard_male":[
			"graphics/portraits/portrait_hegemony01.png",
		],
		"standard_female":[
			"graphics/portraits/portrait_hegemony03.png",
		],
	},
}
```

`id`、`logo`、`displayName`、`displayNameWithArticle`、`names` 和 `portraits` 是必填字段。缺少其中任意一个，加载阶段会在读取对应键时中断。

`crest`、`color` 与 `shipNamePrefix` 用于本页的可观察身份：小旗、界面配色和舰船名前缀。头像条目使用已经存在的原版资源，先确认字段关系成立；制作自己的头像资产时只需替换路径。

### 身份字段速查表

| 字段 | 页面用途 | 错误表现 |
|---|---|---|
| `id` | 势力注册键和后续对象归属 | 重复 ID 或字段缺失会中断加载 |
| `displayName` | 普通势力名称 | 字段缺失会中断加载 |
| `displayNameWithArticle` | 带冠词语境中的名称 | 字段缺失会中断加载 |
| `logo` | 情报界面大旗 | 字段或贴图缺失会中断加载 |
| `crest` | 市场、收入、勘测、百科等界面的小旗 | 路径错误会在资源加载阶段报错 |
| `names` | 随机人物名称来源 | 对象缺失会中断加载 |
| `portraits` | 随机人物头像池 | 对象缺失会中断加载，错误贴图路径会报告资源错误 |

这些字段用于核对一个势力是否已经具备可被其它生涯对象引用的身份。舰船池和学说字段具有另一组加载条件，在 [C3-S2] 单独处理。

## 验证方案

前置数据：GearIn MOD 已启用，两个旗帜路径和示例头像路径能够读取。

1. 启动游戏并等待主菜单完成加载。
2. 在 `starsector.log` 中查找 `Loading gear_industry faction`。
3. 新建或读取生涯，打开情报界面的势力列表。
4. 选择齿轮工业，核对显示名称、颜色和 logo。

预期结果：

- 日志确认 `gear_industry` 势力已经加载，随后没有 faction JSON 或贴图异常；
- 情报界面出现齿轮工业；
- 势力名称、主色和大旗与 faction 文件一致；
- `Global.getSector().getFaction("gear_industry")` 能够取得势力对象。

## 故障分析

- **势力没有出现在情报界面**：`factions.csv` 路径错误、表头不是 `faction` 或注册行以 `#` 开头时，游戏不会读取目标 faction 文件。

    - **解决方案**：核对表头和相对 MOD 根目录的完整路径，重启。

- **加载阶段报告缺少 JSON 字段**：faction 文件缺少六个必填字段之一。

    - **解决方案**：根据异常中的键名补齐字段，重启。

- **加载阶段报告重复 spec**：另一个来源已经注册相同 `id`。

    - **解决方案**：为 GearIn 选择全局唯一的势力 ID，并同步后续页面的引用，重启。

- **旗帜或头像无法加载**：资源路径指向不存在的文件，TEXTURE 加载会报告目标路径。

    - **解决方案**：以 MOD 根目录为基准核对文件名和大小写，重启。

## 总结

势力身份由 `factions.csv` 的路径和 faction 文件内部的 `id` 共同建立。六个必填字段让游戏能够创建势力规格，旗帜、颜色、名称与头像提供可以在情报界面核对的显示结果。

后续页面只需引用 `gear_industry`，就能把星系实体、市场和舰队归到同一个势力。下一节会在这份身份文件上加入舰船池、舰队学说和初始关系。

## 建议参考项

- `data/world/factions/factions.csv`：势力文件注册表；
- `data/world/factions/gear_industry.faction`：GearIn 势力身份文件；
- `data/world/factions/hegemony.faction`：原版完整 faction 文件参考；
- `FactionSpecAPI`：加载后的势力规格；
- `SectorAPI.getFaction(id)`：运行时势力查询入口。
