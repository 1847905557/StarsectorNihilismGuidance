package data.scripts.gearin.campaign;

import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.impl.combat.BattleCreationPluginImpl;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;

/**
 * 沿用原版战场创建流程，并为匹配的战斗强制生成战术目标。
 */
public class GearInObjectiveBattleCreationPlugin extends BattleCreationPluginImpl {

    @Override
    public void initBattle(BattleCreationContext context, MissionDefinitionAPI api) {
        context.forceObjectivesOnMap = true;
        super.initBattle(context, api);
    }
}
