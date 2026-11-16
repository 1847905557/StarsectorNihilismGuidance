package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/** 齿轮工业舰船幅能过高时在舰体上方提示，玩家座舰另有警报声与状态栏图标。 */
public class GearInFluxWarningPlugin extends BaseEveryFrameCombatPlugin {

    private static final String HULL_PREFIX = "GearIn_";
    private static final float WARNING_LEVEL = 0.8f;
    private static final float TEXT_INTERVAL = 2f;
    private static final Color TEXT_COLOR = new Color(255, 140, 60, 255);
    private static final String STATUS_KEY = "GearIn_flux_warning";

    /** 每艘舰船下一次允许显示提示的战斗时间，只在本场战斗内有效。 */
    private final Map<ShipAPI, Float> nextTextTime = new HashMap<>();

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused() || Global.getCurrentState() == GameState.TITLE) {
            return;
        }
        float now = engine.getTotalElapsedTime(false);
        for (ShipAPI ship : engine.getShips()) {
            if (!isWatched(ship) || ship.getFluxLevel() < WARNING_LEVEL) {
                continue;
            }
            Float next = nextTextTime.get(ship);
            if (next == null || now >= next) {
                engine.addFloatingText(ship.getLocation(), "Flux critical", 24f, TEXT_COLOR, ship, 0.5f, 1f);
                nextTextTime.put(ship, now + TEXT_INTERVAL);
            }
            if (ship == engine.getPlayerShip()) {
                warnPlayer(engine, ship);
            }
        }
        nextTextTime.keySet().removeIf(ship -> !engine.isEntityInPlay(ship));
    }

    private static boolean isWatched(ShipAPI ship) {
        return ship.isAlive()
                && !ship.isHulk()
                && !ship.isFighter()
                && !ship.isStationModule()
                && ship.getOwner() == 0
                && ship.getHullSpec().getHullId().startsWith(HULL_PREFIX);
    }

    private static void warnPlayer(CombatEngineAPI engine, ShipAPI ship) {
        Global.getSoundPlayer().playUILoop("GearIn_flux_alarm", 1f, 1f);
        String icon = Global.getSettings().getSpriteName("ui", "icon_tactical_overloaded");
        int percent = (int) (ship.getFluxLevel() * 100f);
        engine.maintainStatusForPlayerShip(STATUS_KEY, icon, "Flux warning", percent + "% flux", true);
    }
}
