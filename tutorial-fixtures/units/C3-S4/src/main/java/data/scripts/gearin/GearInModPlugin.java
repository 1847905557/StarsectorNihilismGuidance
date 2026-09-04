package data.scripts.gearin;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import data.scripts.gearin.campaign.GearInSystemGenerator;

/**
 * GearIn 的 MOD 入口类。
 * mod_info.json 的 modPlugin 字段指向本类；游戏在新建生涯时调用 onNewGame()。
 */
public class GearInModPlugin extends BaseModPlugin {

    @Override
    public void onNewGame() {
        new GearInSystemGenerator().generate(Global.getSector());
        seedRelations(Global.getSector());
    }

    private void seedRelations(SectorAPI sector) {
        FactionAPI gear = sector.getFaction("gear_industry");
        gear.setRelationship("player", 0f);
        gear.setRelationship("pirates", RepLevel.HOSTILE);
        gear.setRelationship("hegemony", RepLevel.FRIENDLY);
        gear.setRelationship("tritachyon", RepLevel.HOSTILE);
    }
}
