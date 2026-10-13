package data.scripts.gearin.modcompat;

import exerelin.campaign.intel.groundbattle.GBDataManager;
import exerelin.campaign.intel.groundbattle.GroundUnitDef;

/** 查询地面战产业、单位与通用插件的实际登记。 */
public final class GearInGroundBattleProbe {
    public boolean hasIndustry(String industryId) {
        return industryId.equals(GBDataManager.getIndustryDef(industryId).industryId);
    }

    public String describeIndustry(String industryId) {
        GBDataManager.IndustryDef def = GBDataManager.getIndustryDef(industryId);
        return "industry=" + def.industryId + " strengthMult=" + def.strengthMult
                + " troops=" + def.troopCounts + " plugin=" + def.plugin;
    }

    public String findPlugin(String pluginId) {
        return GBDataManager.getPluginsById().get(pluginId);
    }

    public boolean hasUnit(String unitId) {
        return GroundUnitDef.getUnitDef(unitId) != null;
    }
}
