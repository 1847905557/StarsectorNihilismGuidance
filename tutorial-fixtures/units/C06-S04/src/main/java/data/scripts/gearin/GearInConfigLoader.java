package data.scripts.gearin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;

/**
 * 配置文件读取：单文件 loadJSON 与合并 CSV（getMergedSpreadsheetDataForMod）。
 *
 * 路径一律用 "/"：禁止 ".."、反斜杠、":" 及首字符 "/"（违反在调用点抛 RuntimeException）。
 */
public class GearInConfigLoader {

    public static final String CONFIG_PATH = "data/config/gear_in/services.json";
    public static final String ENTRIES_PATH = "data/config/gear_in/service_entries.csv";

    private GearInConfigLoader() {
        // 工具类：不实例化
    }

    /** 单文件读取：loadJSON 解析 MOD 自己的配置文件（# 注释与尾逗号会被剥离）。 */
    public static JSONObject loadServiceConfig() throws IOException, JSONException {
        return Global.getSettings().loadJSON(CONFIG_PATH);
    }

    /** 合并读取：以本 MOD 的 CSV 为基底，其他 MOD 可按 id 列替换行（主行被覆盖）。 */
    public static List<JSONObject> loadServiceEntries() throws IOException, JSONException {
        SettingsAPI settings = Global.getSettings();
        JSONArray rows = settings.getMergedSpreadsheetDataForMod("id", ENTRIES_PATH, "gear_in");
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            result.add(rows.getJSONObject(i));
        }
        return result;
    }
}
