package data.scripts.gearin.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

/**
 * 教程第一个最小船插：给舰船一个固定百分比的最大速度加成。
 * 整份文件只有两处是参数 ——SPEED_PERCENT 的数字与日志文字；其余全部按模板使用。
 */
public class GearInSpeedBooster extends BaseHullMod {

    /** 速度加成百分比(参数：改这个数字，其余不动)。 */
    private static final float SPEED_PERCENT = 10f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().modifyPercent(id, SPEED_PERCENT);
        Global.getLogger(GearInSpeedBooster.class).info("GearIn_speed_booster applied");
    }
}
