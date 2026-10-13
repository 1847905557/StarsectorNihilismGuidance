package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;

public final class GearInI18n {

    public static final String CATEGORY_UI = "GearIn_ui";
    public static final String CATEGORY_DIALOG = "GearIn_dialog";

    private GearInI18n() {
    }

    public static String get(String category, String id) {
        return Global.getSettings().getString(category, id);
    }

    public static String ui(String id) {
        return get(CATEGORY_UI, id);
    }

    public static String dialog(String id) {
        return get(CATEGORY_DIALOG, id);
    }

    public static String format(String category, String id, Object... args) {
        String template = get(category, id);
        return String.format(template, args);
    }

    public static String replaceToken(String text, String token, String value) {
        return text.replace("$" + token, value);
    }
}
