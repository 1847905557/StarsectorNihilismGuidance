package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;

/**
 * 市场结构日志器：输出目标市场的身份/势力/实体绑定/survey/开放状态/条件与产业 id 列表，
 * 以及市场人物数量与同经济组市场数。由巡检脚本 GearInMarketInspector 选定目标市场后调用。
 */
public class GearInMarketStructureLog {

    private static final Logger LOG = Global.getLogger(GearInMarketStructureLog.class);

    private GearInMarketStructureLog() {
        // 工具类：不实例化
    }

    public static void log(MarketAPI market, EconomyAPI economy) {
        LOG.info(String.format(
                "GearIn market structure: id=%s name=%s size=%d faction=%s inEconomy=%s survey=%s playerOwned=%s freePort=%s hidden=%s onOrAt=%s primaryEntity=%s",
                market.getId(), market.getName(), market.getSize(), market.getFactionId(),
                market.isInEconomy(), market.getSurveyLevel(), market.isPlayerOwned(),
                market.isFreePort(), market.isHidden(), market.getOnOrAt(),
                market.getPrimaryEntity() == null ? "null" : market.getPrimaryEntity().getName()));

        List<MarketConditionAPI> conditions = market.getConditions();
        StringBuilder conditionIds = new StringBuilder();
        for (MarketConditionAPI condition : conditions) {
            if (conditionIds.length() > 0) {
                conditionIds.append(", ");
            }
            conditionIds.append(condition.getId());
        }
        LOG.info(String.format(
                "GearIn market conditions: count=%d ids=[%s]",
                conditions.size(), conditionIds));

        List<Industry> industries = market.getIndustries();
        StringBuilder industryIds = new StringBuilder();
        for (Industry industry : industries) {
            if (industryIds.length() > 0) {
                industryIds.append(", ");
            }
            industryIds.append(industry.getId());
        }
        LOG.info(String.format(
                "GearIn market industries: count=%d ids=[%s]",
                industries.size(), industryIds));

        LOG.info(String.format(
                "GearIn market people: count=%d",
                market.getPeopleCopy().size()));
        LOG.info(String.format(
                "GearIn market econGroup: group=%s sameGroupMarkets=%d",
                market.getEconGroup(), economy.getMarketsWithSameGroup(market).size()));
    }
}
