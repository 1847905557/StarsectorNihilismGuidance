package data.scripts.gearin;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;

/**
 * 完整最小例：ModPlugin 生命周期、注册与存档入口。
 * mod_info.json 的 modPlugin 字段指向本类。
 * 每个回调打印一行日志；"首次进入存档" 的一次性初始化用 sector persistent data 门控 (CLASS_MARK 模式)。
 */
public class GearInModPlugin extends BaseModPlugin {

    /** 存档持久化标记：本 MOD 是否已完成首次进入初始化 (随存档保存)。 */
    public static final String CLASS_MARK = "GearInModPlugin";

    @Override
    public void onApplicationLoad() throws Exception {
        String version = Global.getSettings().getGameVersion();
        if (!version.startsWith("0.98a")) {
            throw new IllegalArgumentException("齿轮工业要求游戏版本 0.98a，当前为 [" + version + "]");
        }
        log("onApplicationLoad");
    }

    @Override
    public void onEnabled(boolean wasEnabledBefore) {
        log("onEnabled wasEnabledBefore=" + wasEnabledBefore);
    }

    @Override
    public void onNewGame() {
        log("onNewGame");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        SectorAPI sector = Global.getSector();
        if (!sector.getPersistentData().containsKey(CLASS_MARK)) {
            sector.getPersistentData().put(CLASS_MARK, true);
            SharedData.getData().getPersonBountyEventData().addParticipatingFaction("gear_industry");
            log("first-entry initialization (CLASS_MARK gate opened)");
        }
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

    private static void log(String message) {
        Global.getLogger(GearInModPlugin.class).info("GearIn lifecycle: " + message);
    }
}
