package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

import data.scripts.gearin.GearInModPlugin;

/** 玩家击败齿轮工业的巡逻队后，势力记下这笔账：累计次数、限时敌意标记，并降低关系。 */
public class GearInPatrolGrudgeListener extends BaseCampaignEventListener {

    /** 全局 Memory：玩家累计击败的齿轮工业巡逻队数量。 */
    public static final String LOST_KEY = "$GearIn_patrols_lost";

    /** 全局 Memory：最近击败过齿轮工业的巡逻队，60 天后自动消失。 */
    public static final String GRUDGE_KEY = "$GearIn_patrol_grudge";

    private static final float GRUDGE_DAYS = 60f;

    /** 每击败一支巡逻队，关系额外下降的数值。 */
    private static final float RELATION_PENALTY = 0.05f;

    public GearInPatrolGrudgeListener() {
        super(false);
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (!battle.isPlayerInvolved()) {
            return;
        }
        int defeated = 0;
        for (CampaignFleetAPI fleet : battle.getNonPlayerSideSnapshot()) {
            if (!GearInModPlugin.FACTION_ID.equals(fleet.getFaction().getId())) {
                continue;
            }
            if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) {
                continue;
            }
            if (battle.wasFleetDefeated(fleet, primaryWinner)) {
                defeated++;
            }
        }
        if (defeated == 0) {
            return;
        }

        SectorAPI sector = Global.getSector();
        MemoryAPI memory = sector.getMemoryWithoutUpdate();
        memory.set(LOST_KEY, memory.getFloat(LOST_KEY) + defeated);
        memory.set(GRUDGE_KEY, true, GRUDGE_DAYS);
        sector.getFaction(GearInModPlugin.FACTION_ID)
                .adjustRelationship(sector.getPlayerFaction().getId(), -RELATION_PENALTY * defeated);
        sector.getCampaignUI().addMessage("齿轮工业记下了被击败的巡逻队。");
    }
}
