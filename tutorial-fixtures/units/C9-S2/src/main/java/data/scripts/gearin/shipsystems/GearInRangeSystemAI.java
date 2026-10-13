package data.scripts.gearin.shipsystems;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.IntervalUtil;

public class GearInRangeSystemAI implements ShipSystemAIScript {

    private static final Logger LOG = Global.getLogger(GearInRangeSystemAI.class);
    private ShipAPI ship;
    private ShipSystemAPI system;
    private CombatEngineAPI engine;
    private final IntervalUtil interval = new IntervalUtil(0.2f, 0.3f);

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system,
            ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
        this.engine = engine;
        interval.randomize();
        LOG.info("GearIn range AI init: " + ship.getHullSpec().getHullId());
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir,
            Vector2f collisionDangerDir, ShipAPI target) {
        if (engine.isPaused()) {
            return;
        }
        interval.advance(amount);
        if (!interval.intervalElapsed() || target == null
                || !target.isAlive() || system.isActive()
                || system.isCoolingDown()) {
            return;
        }
        if (ship.getFluxTracker().isOverloadedOrVenting()) {
            return;
        }
        ship.useSystem();
        LOG.info("GearIn range AI activated");
    }
}
