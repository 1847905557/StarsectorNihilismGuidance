package data.scripts.gearin;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/** 齿轮工业的全局设置与预加载资源入口，在应用加载时初始化一次。 */
public final class GearInSettings {

    public static final String DESIGN_TYPE = "齿轮工业";
    public static final String FX_CATEGORY = "fx";
    public static final String HEAT_GLOW_KEY = "GearIn_heat_glow";
    public static final String INTEL_CATEGORY = "intel";
    public static final String FLEET_ICON_KEY = "GearIn_fleet_icon";
    public static final String SALVAGE_BONUS_XP_KEY = "GearIn_salvage";

    private static Color designTypeColor;
    private static String fleetIconName;
    private static float salvageBonusXp;
    private static int economyIterPerMonth;
    private static int maxShipsInFleet;

    private GearInSettings() {
    }

    public static void load() {
        SettingsAPI settings = Global.getSettings();
        designTypeColor = settings.getDesignTypeColor(DESIGN_TYPE);
        fleetIconName = settings.getSpriteName(INTEL_CATEGORY, FLEET_ICON_KEY);
        salvageBonusXp = settings.getBonusXP(SALVAGE_BONUS_XP_KEY);
        economyIterPerMonth = (int) settings.getFloat("economyIterPerMonth");
        maxShipsInFleet = settings.getInt("maxShipsInFleet");
        createHeatGlowSprite();
    }

    public static SpriteAPI createHeatGlowSprite() {
        return Global.getSettings().getSprite(FX_CATEGORY, HEAT_GLOW_KEY);
    }

    public static Color getDesignTypeColor() {
        return designTypeColor;
    }

    public static String getFleetIconName() {
        return fleetIconName;
    }

    public static float getSalvageBonusXp() {
        return salvageBonusXp;
    }

    public static int getEconomyIterPerMonth() {
        return economyIterPerMonth;
    }

    public static int getMaxShipsInFleet() {
        return maxShipsInFleet;
    }
}
