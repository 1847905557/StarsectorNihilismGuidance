package data.scripts;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;

import data.scripts.world.systems.Nipher;

public class GearIn_NormalGenerate implements SectorGeneratorPlugin {

	@Override
	public void generate(SectorAPI sector) {
		// 在此准备生成星系
		new Nipher().generate(sector);

		relationAdj(sector);
	}

	protected void relationAdj(SectorAPI sector) {
		FactionAPI faction = sector.getFaction("gear_industry");

		faction.setRelationship("player", 0f);
		faction.setRelationship("pirates", RepLevel.HOSTILE);
		faction.setRelationship("hegemony", RepLevel.FRIENDLY);
		faction.setRelationship("tritachyon", RepLevel.HOSTILE);

		faction.setRelationship("luddic_path", RepLevel.HOSTILE);
		faction.setRelationship("derelict", RepLevel.HOSTILE);
		faction.setRelationship("remnant", RepLevel.HOSTILE);

		faction.setRelationship("cabal", RepLevel.VENGEFUL);
	}
}