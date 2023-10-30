package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;

public class GearIn_ModPlugin extends BaseModPlugin {

	@Override
	public void onGameLoad(boolean newGame) {

	}

	@Override
	public void beforeGameSave() {

	}

	@Override
	public void onApplicationLoad() {

	}
	
	@Override
	public void onNewGame() {

		if (NEX()) {

		} else {
			// 在此准备生成星系

			new GearIn_NormalGenerate().generate(Global.getSector());
		}

		SharedData.getData().getPersonBountyEventData().addParticipatingFaction("gear_industry");
	}

	public static boolean NEX() {
		return Global.getSettings().getModManager().isModEnabled("nexerelin");
	}
}