# 制作一种星体类型

原版的类地、荒芜、气态巨行星都是登记在一份文件里的星体类型。新的类型同样登记一项、引用几张贴图就能做出来，不需要写代码。

Nipher Prime 现在是一颗普通的类地行星。齿轮工业的首都，应该长得像个工业世界。

---

_本节预期阅读时间：\~8 分钟。_

_本节预期操作时间：\~10 分钟。_

## 前置知识

- [C3-S3] 的 `GearInSystemGenerator.java`，Nipher Prime 的类型要在其中改写；
- [C3-S4] 的 `descriptions.csv` 追加方法，类型描述写在那里；
- JSON 的基本写法与 [C1-S1] 提到的宽松 JSON，星体类型登记按它手工修改。

## 登记余烬世界

星体类型写在 `data/config/planets.json` 里，从原版挑一个相近的类型复制过来改就行。余烬世界由原版的 `terran` 改来，地表贴图与云层贴图都沿用原版，只改颜色、大气与旋转。在 MOD 中创建 `data/config/planets.json`：

```json
{
	"GearIn_ember_world":{ # 星体类型 ID，必改
		"name":"余烬世界", # 类型名称，显示在悬停与数据库中
		"descriptionId":"GearIn_ember_world", # 类型描述的 ID，不填则使用类型 ID
		"tilt":20, # 左右倾斜角度，大于 0 向左倾
		"pitch":30, # 前后倾斜角度，大于 0 朝向观察者
		"rotation":-3, # 地表自转速度，度/秒
		"planetColor":[255,255,255,255], # 地表贴图的着色，白色即不着色
		"atmosphereThickness":0.18, # 大气厚度，为半径的倍数，0 为无大气
		"atmosphereThicknessMin":35, # 大气最小厚度，像素
		"atmosphereColor":[190,95,55,180], # 大气颜色
		"texture":"graphics/planets/terran.jpg", # 地表贴图
		"icon":"graphics/warroom/icon_planet.png", # 星图图标
		"iconColor":[255,150,90,255], # 星图图标的颜色
		"isStar":false, # 是否为恒星
		"lightPosition":[-2,1,8], # 光源相对行星的位置，以行星半径为单位
		"cloudTexture":"graphics/planets/clouds_banded01.png", # 云层贴图，不填则没有云层
		"cloudColor":[255,205,180,180], # 云层颜色
		"cloudRotation":-8, # 云层自转速度，度/秒
	},
}
```

和原版 `terran` 比，这里只改了大气、云层和星图图标的颜色；星图上的行星本来就只有一小块，换掉这几处颜色，视觉上就已经够区分，地表贴图沿用原版即可。

**`isStar` 决定这是行星还是恒星。** 恒星类型由 `initStar` 使用，行星类型由 `addPlanet` 使用，两者不能混用。

然后在 [C3-S4] 的 `descriptions.csv` 中为类型补上描述：

```csv
GearIn_ember_world,PLANET,"被炼厂烟尘和赤红云层包围的工业世界，地表昼夜都映着熔炉的光。",,,,,
```

**类型描述写的是所有余烬世界的共性**，与 [C3-S4] 为 Nipher Prime 写的 CUSTOM 描述不同。Nipher Prime 有自己的 CUSTOM 描述，悬停时显示的仍是那一段；类型描述出现在数据库的星体条目里，以及以后每一颗没有 CUSTOM 描述的余烬世界上。

## 让 Nipher Prime 使用新类型

打开 [C3-S3] 的 `GearInSystemGenerator.java`，把 Nipher Prime 的类型从 `terran` 改为 `GearIn_ember_world`：

```java
PlanetAPI prime = system.addPlanet(
        "GearIn_planet1", star, "Nipher Prime", "GearIn_ember_world", 55, 150, 3200, 120);
```

行星 ID、名称与轨道都不变，市场和描述的绑定也不受影响。

**新类型只出现在您亲手放置的地方。** 原版随机生成星系时，只从 `data/campaign/procgen/planet_gen_data.csv` 里登记过的类型中抽取，余烬世界不在其中，星区里自然不会凭空冒出别的同类行星。

星体类型只管外观与类型描述。宜居度、资源这些由行星上的市场条件决定，留到 [C12-S1]。

### 进游戏核对

保存所有文件，重启游戏，创建一个新生涯，进入 Nipher：

- Nipher Prime 的大气为棕红色，云层带着橙色，转得比地表快；
- 星图上 Nipher Prime 的图标为橙色；
- 在数据库的星体条目中找到余烬世界，描述为上面的类型描述。

## 常见问题

- **加载时报 JSON 错误或贴图找不到**：颜色数组不足四项、漏了逗号，或贴图路径与实际文件不一致。
    - **解决方案**：按日志定位字段，核对路径，保存后重启。

- **Nipher Prime 仍是类地行星**：使用的是改代码之前的存档，或生成器里的类型 ID 没有改。
    - **解决方案**：核对 `addPlanet` 的类型参数，创建新生涯。

- **创建新生涯时报错**：`addPlanet` 使用了恒星类型，或 `initStar` 使用了行星类型。
    - **解决方案**：核对 `isStar`，让两者各用各的类型。

- **数据库里的余烬世界没有描述**：`descriptionId` 与 PLANET 描述行的 `id` 不一致。
    - **解决方案**：核对两处 ID，保存后重启。

## 建议参考项

- `data/config/planets.json`：原版全部星体类型，恒星与行星的写法都能在这里找到。
- `graphics/planets/`：原版地表与云层贴图，可以自由组合。
- `data/campaign/procgen/planet_gen_data.csv`：原版行星生成表，类型 ID 在这里与生成规则对应。
- `data/strings/descriptions.csv`：行星类型描述的追加位置。
