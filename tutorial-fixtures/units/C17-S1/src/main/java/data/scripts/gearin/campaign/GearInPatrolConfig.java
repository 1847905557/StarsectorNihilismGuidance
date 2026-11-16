package data.scripts.gearin.campaign;

import org.json.JSONObject;

import com.fs.starfarer.api.Global;

/** 巡逻队的配置表：启动时从 MOD 自己的 data/config/gearin/patrol.json 读一次，巡逻管理器生成舰队时取用。 */
public final class GearInPatrolConfig {

    public static final String MOD_ID = "GearIndustry";

    public static final String PATH = "data/config/gearin/patrol.json";

    private static float combatPoints;
    private static int commanderLevel;
    private static int officerLevel;

    private GearInPatrolConfig() {
    }

    public static void load() throws Exception {
        JSONObject json = Global.getSettings().loadJSON(PATH, MOD_ID);
        combatPoints = (float) json.getDouble("combatPoints");
        commanderLevel = json.getInt("commanderLevel");
        officerLevel = json.getInt("officerLevel");
    }

    public static float getCombatPoints() {
        return combatPoints;
    }

    public static int getCommanderLevel() {
        return commanderLevel;
    }

    public static int getOfficerLevel() {
        return officerLevel;
    }
}
