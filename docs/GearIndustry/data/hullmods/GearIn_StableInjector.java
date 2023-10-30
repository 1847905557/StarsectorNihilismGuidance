package data.hullmods;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;

public class GearIn_StableInjector extends BaseHullMod {

	private static Map mag = new HashMap();
	static {
		mag.put(HullSize.FRIGATE, 15f);
		mag.put(HullSize.DESTROYER, 10f);
		mag.put(HullSize.CRUISER, 5f);
		mag.put(HullSize.CAPITAL_SHIP, 5f);
	}
	


	
	//private static final float ACCELERATION_BONUS = 100f;
	//private static final float EXTRA_DAMAGE = 300f;
	//private static final int BURN_LEVEL_BONUS = 1;
	
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
		stats.getMaxSpeed().modifyFlat(id, (Float) mag.get(hullSize));


		

		
		//stats.getAcceleration().modifyPercent(id, ACCELERATION_BONUS);
		//stats.getEngineDamageTakenMult().modifyPercent(id, EXTRA_DAMAGE);
		
		//stats.getMaxBurnLevel().modifyFlat(id, BURN_LEVEL_BONUS);
	}
	
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + ((Float) mag.get(HullSize.FRIGATE)).intValue();
		if (index == 1) return "" + ((Float) mag.get(HullSize.DESTROYER)).intValue();
		if (index == 2) return "" + ((Float) mag.get(HullSize.CRUISER)).intValue();
		if (index == 3) return "" + ((Float) mag.get(HullSize.CAPITAL_SHIP)).intValue();


//		if (index == 4) return "" + (int) ACCELERATION_BONUS;
//		//if (index == 5) return "four times";
//		if (index == 5) return "4" + Strings.X;
//		if (index == 6) return "" + BURN_LEVEL_BONUS;
		return null;
	}

	@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
		if (ship.getVariant().getHullMods().contains("unstable_injector")) {
			Global.getSoundPlayer().playUISound("cr_allied_warning", 1f, 1f);
			ship.getVariant().removeMod("GearIn_stable_injector");
		}
	}
	
	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
		return !ship.getVariant().getHullMods().contains("unstable_injector");
	}
	
	
	public String getUnapplicableReason(ShipAPI ship) {

		if (ship.getVariant().getHullMods().contains("unstable_injector")) {
			return "不兼容 不稳定推进器";
		}
		
		return null;
	}

}
