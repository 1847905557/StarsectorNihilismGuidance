# 制作一件商品

本节把一行 `commodities.csv` 变成一个能出现在货舱里的 GearIn 商品，再把同一个 ID 接进势力的违禁品规则。读者最后用控制台加货，不需要编写新的 Java 类。

*本节预期阅读时间：~12 分钟。*

*本节预期操作时间：~10 分钟。*

## 学习目标

完成后，您将能够：

* 在 `commodities.csv` 注册一个唯一商品 ID；
* 根据需求类别、价格、堆叠和货舱占用选择字段；
* 让势力文件引用该商品并在交易界面观察违禁标记；
* 区分商品注册成功、商品描述挂接和经济流入三件事。

## 前置知识

* [C1-S1] 的 MOD 目录、CSV 合并与 UTF-8 无 BOM 规则；
* [C3-S3] 的势力文件路径与 `illegalCommodities` 字段；
* 能够启用 Console Commands 并进入生涯市场。

## 确定商品身份

GearIn 的熔炼厂产出一种 "回火合金"。它是货舱里的普通商品，使用 `heavy_machinery` 需求类别，图标先复用原版材料图标。该页只完成注册和观察；产业供需会在后续经济主题中处理。

## 注册商品数据

文件位置：`data/campaign/commodities.csv`。MOD 只提交自己的行，表头和原版行保持一致。

### 锁定唯一 ID

```csv
name,id,demand class,base price,export value,price variability,utility,origin,tags,stack size,cargo space,icon,sound id,sound id drop,order,economyTier,econUnitOrigUnused,econUnit,baseRaidDanger,eU * base value,iconWidthMult,plugin,desc
Tempered Alloy,GearIn_tempered_alloy,heavy_machinery,180,700,3,1,,"industrial, expensive",1000,1,graphics/icons/cargo/materials.png,ui_cargo_supplies,ui_cargo_supplies_drop,2.9,2.2,200,600,,60000,1,,
```

完成信号：日志加载 `GearIn_tempered_alloy`，且没有重复 ID 错误。

### 配置价格来源

`heavy_machinery` 是需求类别，不代表商品会自动出现在某个产业库存。非主商品的基础价格会按需求类别和 `utility` 计算，因此先选已有类别，避免凭空创建经济分类。

```csv
Tempered Alloy,GearIn_tempered_alloy,heavy_machinery,180,700,3,1,,"industrial, expensive",1000,1,graphics/icons/cargo/materials.png,ui_cargo_supplies,ui_cargo_supplies_drop,2.9,2.2,200,600,,60000,1,,
```

完成信号：货舱单位体积为 1，堆叠上限为 1000，价格面板能读出商品的基础值。

### 接入势力规则

在 `data/world/factions/gear_industry.faction` 的 `illegalCommodities` 数组追加同一个 ID。

```json
"illegalCommodities":[
    "drugs",
    "organs",
    "GearIn_tempered_alloy"
],
```

完成信号：进入 GearIn 市场后，商品行显示违禁状态；拼写错误会在加载期报 `Invalid illegal commodity id`。

### 验证货舱对象

使用 Console Commands 的 `addcommodity` 给玩家舰队添加 10 单位，然后在货舱和交易面板观察同一个 ID。

```text
addcommodity GearIn_tempered_alloy 10
```

完成信号：货舱出现 "Tempered Alloy ×10"，图标来自 `graphics/icons/cargo/materials.png`。

## 排查商品加载故障

下面是**局部故障片段**：

```csv
Tempered Alloy,GearIn_tempered_alloy,unknown_class,180,700,3,1,,"industrial, expensive",1000,1,graphics/icons/cargo/materials.png,ui_cargo_supplies,ui_cargo_supplies_drop,2.9,2.2,200,600,,60000,1,,
```

实际症状：启动阶段加载商品失败，GearIn 不能进入主菜单。

原因：`demand class` 被用来查找商品经济类别，`unknown_class` 没有对应注册商品。

下面是**局部修正片段**：

```csv
Tempered Alloy,GearIn_tempered_alloy,heavy_machinery,180,700,3,1,,"industrial, expensive",1000,1,graphics/icons/cargo/materials.png,ui_cargo_supplies,ui_cargo_supplies_drop,2.9,2.2,200,600,,60000,1,,
```

复验：重启后确认加载日志出现商品 ID，再执行 `addcommodity GearIn_tempered_alloy 10`。

## 验证方案

1. 启动游戏，观察 `starsector.log` 中 `commodities.csv` 的加载记录。
2. 进入 GearIn 市场，确认 `GearIn_tempered_alloy` 被势力规则识别为违禁品。
3. 执行 `addcommodity GearIn_tempered_alloy 10`，打开货舱和交易界面。

预期结果：

* 商品名称、ID、图标、堆叠和货舱占用均可观察；
* 交易界面显示 GearIn 商品的违禁状态；
* 日志没有 `Invalid illegal commodity id`、CSV 列错位或图标加载错误。

## 故障分析

- **货舱里没有商品**：先核对 `commodities.csv` 路径、商品 ID 和控制台拼写。商品注册只建立 spec，市场库存仍需要控制台、产业或经济生成链提供数量。修正后再次执行 `addcommodity GearIn_tempered_alloy 10`，货舱应增加 10 单位商品。
- **商品名称异常**：检查 `commodities.csv` 的 `name` 列及 CSV 列位。修正后重启游戏并重新添加商品，货舱应显示 `Tempered Alloy`。
- **商品描述缺失**：检查 [C3-S8] 的 `RESOURCE` 行是否使用相同商品 ID。`commodities.csv` 的 `desc` 列不承担货舱描述。修正后重新打开货舱详情，描述文本应正常显示。
- **势力文件加载失败**：检查 `illegalCommodities` 中的 ID 是否逐字等于商品注册 ID，并确认商品行已经成功注册。修正后重启游戏，日志不应再出现 `Invalid illegal commodity id`，交易界面应标记该商品为违禁品。

## 总结

商品页先解决 "它是什么、怎么显示、谁禁止交易"，再把经济生产留给市场和产业主题。基础篇只需要稳定的注册 ID、有效的需求类别和一条可观察的货舱验证。

## 建议参考项

* `data/campaign/commodities.csv`：商品注册表；
* `data/world/factions/gear_industry.faction`：势力违禁商品入口；
* `data/strings/descriptions.csv` 的 `RESOURCE`：商品货舱描述；
* Console Commands 的 `addcommodity`：快速创建货舱验证状态。

::: details 完整成品：商品注册与势力规则

```csv
name,id,demand class,base price,export value,price variability,utility,origin,tags,stack size,cargo space,icon,sound id,sound id drop,order,economyTier,econUnitOrigUnused,econUnit,baseRaidDanger,eU * base value,iconWidthMult,plugin,desc
Tempered Alloy,GearIn_tempered_alloy,heavy_machinery,180,700,3,1,,"industrial, expensive",1000,1,graphics/icons/cargo/materials.png,ui_cargo_supplies,ui_cargo_supplies_drop,2.9,2.2,200,600,,60000,1,,
```

```json
"illegalCommodities":[
    "drugs",
    "organs",
    "GearIn_tempered_alloy"
],
```

:::
