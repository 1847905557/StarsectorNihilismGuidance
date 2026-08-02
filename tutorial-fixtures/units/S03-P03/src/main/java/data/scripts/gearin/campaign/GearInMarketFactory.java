package data.scripts.gearin.campaign;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;

/**
 * 本页示例：代码路径建市场的载体类（角色：市场工厂）。
 * 由 GearInSystemGenerator.generate 调用：每个实体一行 createMarket(...)，
 * 即可完成条件/产业/子市场填充与注册三步收尾（economy.addMarket / setMarket / setFaction）。
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
