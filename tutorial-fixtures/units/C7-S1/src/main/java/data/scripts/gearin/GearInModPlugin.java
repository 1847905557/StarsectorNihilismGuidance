package data.scripts.gearin;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;

import data.scripts.gearin.campaign.GearInSystemGenerator;

/** 接收MOD生命周期回调，生成新档内容并记录存档初始化与保存。 */
public class GearInModPlugin extends BaseModPlugin {

    public static final String CLASS_MARK = "GearInModPlugin";

    @Override
    public void onApplicationLoad() throws Exception {
        log("onApplicationLoad version=" + Global.getSettings().getGameVersion());
    }

    @Override
    public void onEnabled(boolean wasEnabledBefore) {
        log("onEnabled wasEnabledBefore=" + wasEnabledBefore);
    }

    @Override
    public void onNewGame() {
        new GearInSystemGenerator().generate(Global.getSector());
        seedRelations(Global.getSector());
        log("onNewGame");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        SectorAPI sector = Global.getSector();
        if (!sector.getPersistentData().containsKey(CLASS_MARK)) {
            SharedData.getData().getPersonBountyEventData().addParticipatingFaction("gear_industry");
            sector.getPersistentData().put(CLASS_MARK, true);
            log("first-entry initialization (CLASS_MARK gate opened)");
        }
        Global.getSoundPlayer().playUISound("GearIn_ui_feedback", 1f, 1f);
        log("onGameLoad newGame=" + newGame);
    }

    @Override
    public void beforeGameSave() {
        log("beforeGameSave");
    }

    @Override
    public void afterGameSave() {
        log("afterGameSave");
    }

    @Override
    public void onGameSaveFailed() {
        log("onGameSaveFailed");
    }

    private void seedRelations(SectorAPI sector) {
        FactionAPI gear = sector.getFaction("gear_industry");
        gear.setRelationship("player", 0f);
        gear.setRelationship("pirates", RepLevel.HOSTILE);
        gear.setRelationship("hegemony", RepLevel.FRIENDLY);
        gear.setRelationship("tritachyon", RepLevel.HOSTILE);
    }

    private static void log(String message) {
        Global.getLogger(GearInModPlugin.class).info("GearIn lifecycle: " + message);
    }
}
