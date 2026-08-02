package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;

/**
 * 税率探针：对市场税率（MutableStat）做瞬时 modifyFlat 并当帧 unmodifyFlat 回滚，
 * 记录生效值 before/during/after，演示"修改高频参数观察结果变化"的最小任务链。
 * 由巡检脚本 GearInMarketInspector 选定目标市场后调用，无持久残留。
 */
public class GearInTariffProbe {

    private static final Logger LOG = Global.getLogger(GearInTariffProbe.class);

    private static final String TARIFF_MOD_ID = "GearIn_inspect_tariff";

    private GearInTariffProbe() {
        // 工具类：不实例化
    }

    public static void log(MarketAPI market) {
        MutableStat tariff = market.getTariff();
        float before = tariff.getModifiedValue();
        tariff.modifyFlat(TARIFF_MOD_ID, 0.05f);
        float during = tariff.getModifiedValue();
        tariff.unmodifyFlat(TARIFF_MOD_ID);
        float after = tariff.getModifiedValue();
        LOG.info(String.format(
                "GearIn tariff probe: before=%.3f during=%.3f after=%.3f",
                before, during, after));
    }
}
