package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;

/** 齿轮工业的界面文字：集中从 strings.json 的 GearIn_campaign 分类里取。 */
public final class GearInI18n {

    public static final String CATEGORY = "GearIn_campaign";

    private GearInI18n() {
    }

    public static String get(String id) {
        return Global.getSettings().getString(CATEGORY, id);
    }

    /** 文字里的 %s 依次替换为参数。 */
    public static String format(String id, Object... args) {
        return String.format(get(id), args);
    }
}
