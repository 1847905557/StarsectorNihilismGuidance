package data.scripts.gearin;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;

/** 齿轮工业的可调参数，在应用加载时从 settings.json 读取一次。 */
public final class GearInSettings {

    private static final Logger LOG = Global.getLogger(GearInSettings.class);

    private static float assaultFeedRofBonus;
    private static float assaultFeedFluxReduction;
    private static float assaultFeedSpeedBonus;
    private static float assaultFeedManeuverBonus;

    private GearInSettings() {
    }

    public static void load() {
        SettingsAPI settings = Global.getSettings();
        assaultFeedRofBonus = settings.getFloat("GearIn_assaultFeedRofBonus");
        assaultFeedFluxReduction = settings.getFloat("GearIn_assaultFeedFluxReduction");
        assaultFeedSpeedBonus = settings.getFloat("GearIn_assaultFeedSpeedBonus");
        assaultFeedManeuverBonus = settings.getFloat("GearIn_assaultFeedManeuverBonus");
        LOG.info("Assault feed: rof +" + assaultFeedRofBonus + ", flux -" + assaultFeedFluxReduction
                + "%, speed +" + assaultFeedSpeedBonus + ", maneuver +" + assaultFeedManeuverBonus + "%");
    }

    public static float getAssaultFeedRofBonus() {
        return assaultFeedRofBonus;
    }

    public static float getAssaultFeedFluxReduction() {
        return assaultFeedFluxReduction;
    }

    public static float getAssaultFeedSpeedBonus() {
        return assaultFeedSpeedBonus;
    }

    public static float getAssaultFeedManeuverBonus() {
        return assaultFeedManeuverBonus;
    }
}
