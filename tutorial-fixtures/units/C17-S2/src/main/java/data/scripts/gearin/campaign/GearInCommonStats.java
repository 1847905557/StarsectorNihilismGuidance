package data.scripts.gearin.campaign;

import org.json.JSONObject;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;

/** 跨存档的战绩：所有存档里累计击败的齿轮工业巡逻队，保存在 common 目录的一个 JSON 文件里。 */
public final class GearInCommonStats {

    public static final String FILE = "GearIndustry_stats.json";

    private static final String DEFEATED = "patrolsDefeated";

    private GearInCommonStats() {
    }

    /** 读取累计数，文件不存在或损坏时返回 0。 */
    public static int getPatrolsDefeated() {
        try {
            return readStats().optInt(DEFEATED, 0);
        } catch (Exception e) {
            Global.getLogger(GearInCommonStats.class).warn("读取 " + FILE + " 失败", e);
            return 0;
        }
    }

    /** 读出、加上本次的数量、整份写回。 */
    public static void addPatrolsDefeated(int count) {
        try {
            JSONObject stats = readStats();
            stats.put(DEFEATED, stats.optInt(DEFEATED, 0) + count);
            Global.getSettings().writeTextFileToCommon(FILE, stats.toString(4));
        } catch (Exception e) {
            Global.getLogger(GearInCommonStats.class).warn("写入 " + FILE + " 失败", e);
        }
    }

    private static JSONObject readStats() throws Exception {
        SettingsAPI settings = Global.getSettings();
        if (!settings.fileExistsInCommon(FILE)) {
            return new JSONObject();
        }
        return new JSONObject(settings.readTextFileFromCommon(FILE));
    }
}
