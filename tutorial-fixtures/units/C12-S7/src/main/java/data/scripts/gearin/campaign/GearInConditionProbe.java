package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class GearInConditionProbe {
    private static final Logger LOG = Global.getLogger(GearInConditionProbe.class);

    private GearInConditionProbe() {
    }

    public static void log(MarketAPI market) {
        String token = market.addCondition(GearInWarfrontCondition.CONDITION_ID, 1.0f);
        MarketConditionAPI condition = market.getSpecificCondition(token);
        if (condition == null) {
            LOG.info("Warfront condition was not created");
            return;
        }
        MarketConditionSpecAPI spec = condition.getSpec();
        MarketConditionPlugin plugin = condition.getPlugin();
        LOG.info(String.format("Warfront condition: id=%s token=%s name=%s script=%s surveyed=%s requiresSurveying=%s planetary=%s",
                condition.getId(), condition.getIdForPluginModifications(), condition.getName(),
                spec.getScriptClass(), condition.isSurveyed(), condition.requiresSurveying(), condition.isPlanetary()));
        LOG.info(String.format("Warfront values: hazard=%.2f accessibility=%.2f fleetQuality=%.2f plugin=%s",
                market.getHazard().getModifiedValue(), market.getAccessibilityMod().computeEffective(0f),
                market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).computeEffective(0f),
                plugin.getClass().getSimpleName()));
        LOG.info("Warfront market survey level=" + market.getSurveyLevel());

        market.suppressCondition(GearInWarfrontCondition.CONDITION_ID);
        float beforeReapply = market.getHazard().getModifiedValue();
        market.reapplyConditions();
        LOG.info(String.format("Warfront suppressed: beforeReapplyHazard=%.2f afterReapplyHazard=%.2f",
                beforeReapply, market.getHazard().getModifiedValue()));
        market.unsuppressCondition(GearInWarfrontCondition.CONDITION_ID);
        market.reapplyConditions();
        LOG.info(String.format("Warfront restored: hazard=%.2f accessibility=%.2f fleetQuality=%.2f",
                market.getHazard().getModifiedValue(), market.getAccessibilityMod().computeEffective(0f),
                market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).computeEffective(0f)));

        condition.setSurveyed(false);
        LOG.info("Warfront condition survey toggled: surveyed=" + condition.isSurveyed());
        condition.setSurveyed(true);
        market.removeSpecificCondition(token);
        LOG.info("Warfront condition removed: present=" + market.hasSpecificCondition(token));
    }
}
