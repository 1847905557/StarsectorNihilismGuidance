package data.scripts.gearin.campaign;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 本节示例：最小形态的 ModPlugin，本节的生成入口类。
 * mod_info.json 的 modPlugin 字段指向本类；游戏在新建生涯时调用 onNewGame()。
 * ModPlugin 各回调的完整生命周期由 C6-S2 讲解(其所有权示例类 GearInModPlugin
 * 位于 data.scripts.gearin 包)，本节只保留 onNewGame() 这一个生成入口。
 */
public class GearInSystemGenPlugin extends BaseModPlugin {

	@Override
	public void onNewGame() {
		// 生成入口：新档时序里，所有 MOD 的 onNewGame 在原版 SectorGen、
		// generators.csv 插件之后、程序化生成(SectorProcGen)与经济加载(economy.json)之前调用，
		// 因此这里生成的星系与实体能被后续步骤消费(市场数据路径、程序化生成)。
		new GearInSystemGenerator().generate(Global.getSector());

		// 关系播种的挂载点：faction 文件不定义势力间关系，只能由代码设置(见势力单元的关系小节)
		seedRelations(Global.getSector());
	}

	private void seedRelations(SectorAPI sector) {
		FactionAPI gear = sector.getFaction("gear_industry");
		gear.setRelationship("player", 0f);
		gear.setRelationship("pirates", RepLevel.HOSTILE);
		gear.setRelationship("hegemony", RepLevel.FRIENDLY);
		gear.setRelationship("tritachyon", RepLevel.HOSTILE);
	}
}
