# 制作一个自定义实体

星图上除了行星和空间站，还有通讯中继站、导航浮标、废弃探测器这类小物件。它们都是自定义实体，登记一个类型，再在生成器里放到某个位置。

做出一个能被看见、能被发现的实体不需要代码。让它真的做点什么就需要写插件了，那是很后面的事。

---

_本节预期阅读时间：\~6 分钟。_

_本节预期操作时间：\~10 分钟。_

## 前置知识

- [C3-S3] 的 `GearInSystemGenerator.java`，实体要在其中放置；
- [C3-S4] 的 `descriptions.csv` 追加方法，实体描述写在那里；
- JSON 的基本写法与 [C1-S1] 提到的宽松 JSON，实体类型登记按它手工修改。

## 登记导航锚

自定义实体位于 `data/config` 文件夹内的 `custom_entities.json`。同理，尽可能复制粘贴修改原版的实体。导航锚由原版的 `comm_relay` 改来，在 MOD 中创建 `data/config/custom_entities.json`：

```json
{
	"GearIn_navigation_anchor":{ # 实体类型 ID，必改
		"baseId":"base_campaign_objective", # 继承的基础类型，地图显示、名称颜色、渲染层等由它提供
		"defaultName":"导航锚", # 默认名称，创建时没有传入名称才使用
		"nameInText":"导航锚", # 在文本中提及时的名称
		"shortName":"导航锚", # 简称
		"icon":"graphics/icons/relay.png", # 星图图标
		"iconWidth":24,
		"iconHeight":24,
		"sprite":"graphics/ships/com_relay.png", # 实体贴图
		"spriteWidth":89,
		"spriteHeight":48,
		"customDescriptionId":"GearIn_navigation_anchor", # CUSTOM 描述的 ID
		"detectionRange":1800, # 发现距离，必须大于 0，下面两项才生效
		"discoverable":true, # 是否需要玩家发现
		"discoveryXP":150, # 发现时获得的经验
	},
}
```

图标和贴图都沿用原版中继站，只换了名字和描述。

原版 `comm_relay` 还有 `tags`、`pluginClass` 与 `interactionImage` 三项，编者都删掉了。**中继站的功能来自它的标签与插件，不来自它的贴图。** 带着 `comm_relay` 与 `objective` 标签，它会被当作一座可以夺取的中继站，给持有者提供情报；`pluginClass` 则负责它的运行状态。删掉这几项，剩下的就只有外观。要是只改了名字就把它们原样留着，玩家看到的是导航锚，游戏却当它是一座中继站。

`detectionRange` 与 `discoverable` 让导航锚在被发现之前只是一个身份不明的信号，玩家驶入 1800 的距离内才会发现它，并得到 150 经验。原版的废弃探测器就是这样被发现的。

然后在 [C3-S4] 的 `descriptions.csv` 中补上描述：

```csv
GearIn_navigation_anchor,CUSTOM,"一座用于校准本地航路的齿轮工业导航锚。",,,,,
```

## 放到 Nipher

打开 [C3-S3] 的 `GearInSystemGenerator.java`，在通讯中继站之后加入：

```java
SectorEntityToken anchor = system.addCustomEntity(
        "GearIn_anchor", "Nipher 导航锚", "GearIn_navigation_anchor", "gear_industry");
anchor.setCircularOrbitPointingDown(star, 270, 2200, 100);
```

参数依次为实体 ID、名称、实体类型与所属势力，与通讯中继站的写法相同，只是类型换成了刚才登记的 `GearIn_navigation_anchor`。导航锚与中继站在同一条轨道上，位于恒星的另一侧。

编者没有用导航锚替换通讯中继站，而是另放一个。中继站是一个真正有用的东西，换掉它，Nipher 就收不到情报了。**新实体应当补充星系，不该挤掉已有的功能。**

### 进游戏核对

保存所有文件，重启游戏，创建一个新生涯，进入 Nipher：

- 驶向通讯中继站轨道的对侧，靠近后出现发现提示，并获得经验；
- 发现之后，星图上显示导航锚的图标和名称；
- 悬停导航锚，显示上面的描述。

## 常见问题

- **加载时报 `baseId` 相关错误**：`baseId` 拼错，或继承的类型不存在。
    - **解决方案**：改为原版存在的 `base_campaign_objective`，保存后重启。

- **创建新生涯时报错，指向 `GearIn_navigation_anchor`**：`addCustomEntity` 的类型参数与 `custom_entities.json` 中的 ID 不一致。
    - **解决方案**：让两处 ID 逐字一致，保存后重启并创建新生涯。

- **靠近导航锚时没有发现提示**：`detectionRange` 为 0 或没有填写，`discoverable` 因此不生效。
    - **解决方案**：填写大于 0 的 `detectionRange`，创建新生涯。

- **导航锚表现得像一座中继站**：复制时保留了原版的 `tags` 或 `pluginClass`。
    - **解决方案**：删掉这两项，保存后重启并创建新生涯。

- **悬停时没有描述**：`customDescriptionId` 与 CUSTOM 描述行的 `id` 不一致。
    - **解决方案**：核对两处 ID，保存后重启。

## 建议参考项

- `data/config/custom_entities.json`：原版全部自定义实体，中继站、浮标、探测器、残骸都在这里。
- `com.fs.starfarer.api.impl.campaign.ids.Entities`：原版自定义实体类型的 ID 常量。
- `com.fs.starfarer.api.impl.campaign.ids.Tags`：实体标签常量，交互与可打捞等行为由标签决定。
