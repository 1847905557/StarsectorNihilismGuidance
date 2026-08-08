# 制作一个生涯市场

本节从 [C3-S6] 的星系对象中挑出 GearIn Prime，给它接上市场、势力、条件、产业和子市场。市场逻辑沿用现有 `GearInMarketFactory`，正文把注意力放在五个注册 ID 与三个绑定动作上。

*本节预期阅读时间：~12 分钟。*

*本节预期操作时间：~12 分钟。*

## 学习目标

完成后，您将能够：

- 说出市场从实体到经济集合的绑定顺序；
- 选择已注册的条件、产业和子市场 ID；
- 区分商品注册与市场经济流；
- 在星球详情、交易面板和日志中验证市场存在。

## 前置知识

- [C3-S3] 的 GearIn 势力 ID；
- [C3-S6] 的 GearIn Prime 实体；
- `market_conditions.csv`、`industries.csv` 和 `submarkets.csv` 中的原版 ID。

## 先锁定市场的五条关系

`market.json → market entity → faction → submarkets → conditions/industries`

市场是行星实体上的经济节点。商品 `GearIn_tempered_alloy` 已经注册，但它只有在市场库存或产业供需链提供数量时才会进入自然经济流。

## 逐步接通市场

### 创建市场实体

```java
MarketAPI market = Global.getFactory().createMarket(
        entity.getId(), entity.getName(), 4);
```

完成信号：市场拥有与行星实体一致的 ID 和规模 4。

### 绑定势力与主实体

```java
market.setFactionId("gear_industry");
market.setPrimaryEntity(entity);
market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
```

完成信号：行星详情显示 GearIn 势力颜色，市场面板能打开。

### 配置注册对象

```java
market.addCondition("population_4");
market.addIndustry("spaceport");
market.addSubmarket("open_market");
market.addSubmarket("storage");
```

完成信号：行星详情出现人口与空间港，交易面板出现公开市场和仓储入口。

### 配置经济关系

```java
Global.getSector().getEconomy().addMarket(market, true);
entity.setMarket(market);
entity.setFaction("gear_industry");
```

完成信号：经济集合、行星实体和势力三处都能读到同一个市场。

### 调用市场工厂

插入位置：[C3-S6] 的 `GearInSystemGenerator.generate()` 末尾，跳跃点生成完成之后；同时在文件顶部导入 `java.util.List`。

```java
GearInMarketFactory marketFactory = new GearInMarketFactory();
marketFactory.createMarket(prime, "gear_industry", 0.35f,
		List.of("population_4", "thin_atmosphere", "hot", "ore_rich", "rare_ore_abundant"),
		List.of("population", "spaceport", "mining", "refining", "heavybatteries", "orbitalworks"),
		List.of("open_market", "generic_military", "black_market", "storage"));
```

完成信号：创建新档后，GearIn Prime 同时拥有 [C3-S6] 的行星实体和本节创建的市场。这个调用只组合两个已经完成的角色类，不增加新的 Java 类型。

## 排查市场接入故障

下面是**局部故障片段**：

```java
MarketAPI market = Global.getFactory().createMarket(entity.getId(), entity.getName(), 4);
market.setFactionId("gear_industry");
market.addIndustry("missing_industry");
entity.setMarket(market);
```

实际症状：市场创建流程在产业 ID 处中断，行星可能已经有部分临时状态，但经济集合没有完整市场。

原因：`addIndustry` 通过注册表查找产业 spec，未知 ID 无法实例化；市场收尾也尚未完成。

下面是**局部修正片段**：

```java
market.addIndustry("spaceport");
Global.getSector().getEconomy().addMarket(market, true);
entity.setMarket(market);
entity.setFaction("gear_industry");
```

复验：新档进入 GearIn Prime，确认市场面板、公开市场和行星势力全部存在。

::: details 完整成品：GearInMarketFactory

```java
package data.scripts.gearin.campaign;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;

/**
 * 本节示例：代码路径建市场的载体类 (角色：市场工厂)。
 * 由 GearInSystemGenerator.generate 调用：每个实体一行 createMarket(...)，
 * 即可完成条件/产业/子市场填充与注册三步收尾 (economy.addMarket / setMarket / setFaction)。
 */
public class GearInMarketFactory {

	public MarketAPI createMarket(SectorEntityToken entity, String factionId, float tariff,
									List<String> conditions, List<String> industries, List<String> submarkets) {
		// 市场规模来自 population_ 条件的数字，与 market.json 数据路径的 size 字段同理
		int size = 0;
		for (String condition : conditions) {
			if (condition.startsWith("population_")) {
				size = Integer.parseInt(condition.replace("population_", ""));
			}
		}

		MarketAPI market = Global.getFactory().createMarket(entity.getId(), entity.getName(), size);
		market.setFactionId(factionId);
		market.setPrimaryEntity(entity);
		market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
		market.getTariff().modifyFlat("generator", tariff);
		for (String condition : conditions) {
			market.addCondition(condition);
		}
		for (MarketConditionAPI condition : market.getConditions()) {
			condition.setSurveyed(true);
		}
		for (String industry : industries) {
			market.addIndustry(industry);
		}
		for (String submarket : submarkets) {
			market.addSubmarket(submarket);
		}

		// 三步缺一不可：注册进经济集合 -> 绑定到实体 -> 实体的势力归属
		Global.getSector().getEconomy().addMarket(market, true);
		entity.setMarket(market);
		entity.setFaction(factionId);
		return market;
	}
}
```

:::

## 验证方案

1. 按 "调用市场工厂" 的实现把市场创建接入 [C3-S6] 的生成器，并导入 `java.util.List`。
2. 创建包含 [C3-S6] 星系的全新生涯。
3. 进入 GearIn Prime，打开行星详情和交易面板。
4. 分别打开公开市场、仓储，观察势力颜色、税率、条件和产业。

预期结果：

- GearIn Prime 的市场规模为 4；
- 公开市场和仓储入口可用，市场势力为 `gear_industry`；
- `population_4`、`spaceport` 等注册对象显示在详情中；
- 日志没有 `Industry [...] not found`、`Market condition [...] not found` 或空实体绑定。

## 故障分析

- **行星有市场图标但交易面板打不开**：检查 `Global.getSector().getEconomy().addMarket`、`entity.setMarket` 和 `entity.setFaction` 是否全部执行。三处分别负责经济集合、实体关系和势力归属。补齐调用后创建新档，点击行星应能打开交易面板。
- **市场条件或产业没有出现**：逐字核对注册表 ID。`population_4`、`spaceport` 和 `open_market` 是不同文件里的 spec，拼写错误会在添加阶段暴露。修正后创建新档，市场详情应列出对应条件、产业和子市场。
- **商品注册成功但市场没有库存**：商品注册只建立商品 spec；市场库存需要经济迭代、产业供需或控制台添加。先用 `additem` 确认商品可进入货舱，再核对产业供需来源；经济迭代后，交易界面应出现相应库存。

## 总结

市场接入的关键是绑定顺序：先创建市场，再填入已注册的对象，最后同时写入经济集合、实体和势力。商品、市场和产业是三条相互连接但生命周期不同的数据链。

## 建议参考项

- `MarketAPI`：市场运行时对象；
- `GearInMarketFactory`：本页完整模板；
- `data/campaign/market_conditions.csv`：条件注册表；
- `data/campaign/industries.csv`：产业注册表；
- `data/campaign/submarkets.csv`：子市场注册表。
