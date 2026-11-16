package data.scripts.gearin;

import java.util.Map;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;

import data.scripts.gearin.campaign.GearInSystemGenerator;

/** MOD 入口：新档生成星系与关系，进入生涯时为存档补上一次性初始化。 */
public class GearInModPlugin extends BaseModPlugin {

    public static final String FACTION_ID = "gear_industry";

    /** 存档公共数据中记录 "已加入人物赏金名单" 的键。 */
    private static final String BOUNTY_JOINED_KEY = "GearIn_bounty_joined";

    @Override
    public void onNewGame() {
        SectorAPI sector = Global.getSector();
        new GearInSystemGenerator().generate(sector);
        seedRelations(sector);
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Map<String, Object> data = Global.getSector().getPersistentData();
        if (!data.containsKey(BOUNTY_JOINED_KEY)) {
            SharedData.getData().getPersonBountyEventData().addParticipatingFaction(FACTION_ID);
            data.put(BOUNTY_JOINED_KEY, true);
        }
    }

    private void seedRelations(SectorAPI sector) {
        FactionAPI gear = sector.getFaction(FACTION_ID);
        gear.setRelationship("player", 0f);
        gear.setRelationship("pirates", RepLevel.HOSTILE);
        gear.setRelationship("hegemony", RepLevel.FRIENDLY);
        gear.setRelationship("tritachyon", RepLevel.HOSTILE);
    }
}
