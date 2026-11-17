package data.scripts.gearin.campaign;

import java.awt.Color;
import java.util.Random;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.util.Misc;

import data.scripts.gearin.GearInModPlugin;

/** 创建没有恒星的 Ashen Verge：一处可以进入并打捞的遗迹地点。 */
public class GearInAshenVergeGenerator {

    public static final String SYSTEM_NAME = "Ashen Verge";

    public static final String BEACON_ID = "GearIn_ashen_beacon";

    public void generate(SectorAPI sector) {
        StarSystemAPI system = sector.createStarSystem(SYSTEM_NAME);
        system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
        system.getLocation().set(-13500f, -4500f);

        // 没有恒星时，用空的中心点作为轨道焦点，光照颜色需要手动给定
        SectorEntityToken center = system.initNonStarCenter();
        system.setLightColor(new Color(120, 110, 100));
        center.addTag(Tags.AMBIENT_LS);

        // 遗迹主体：原版的勘测船残骸，自带交互对话与打捞
        SectorEntityToken ship = system.addCustomEntity(
                "GearIn_ashen_wreck", "灰烬残骸", Entities.DERELICT_SURVEY_SHIP, Factions.NEUTRAL);
        ship.setCircularOrbitPointingDown(center, 40f, 900f, 80f);

        SectorEntityToken cache = system.addCustomEntity(
                "GearIn_ashen_cache", "封存的补给箱", Entities.SUPPLY_CACHE, Factions.NEUTRAL);
        cache.setCircularOrbit(center, 220f, 1400f, 120f);

        // 信标：原版导航浮标，归属齿轮工业，用稳定 ID 供后续脚本查找
        SectorEntityToken beacon = system.addCustomEntity(
                BEACON_ID, "灰烬信标", Entities.NAV_BUOY, GearInModPlugin.FACTION_ID);
        beacon.setCircularOrbitPointingDown(center, 130f, 1100f, 90f);
        beacon.setInteractionImage("illustrations", "comm_relay");

        // 残骸场围绕勘测船，提示玩家这里发生过战斗
        DebrisFieldParams params = new DebrisFieldParams(300f, -1f, 10000000f, 0f);
        params.source = DebrisFieldSource.MIXED;
        SectorEntityToken debris = Misc.addDebrisField(system, params, new Random(20261114L));
        debris.setCircularOrbit(ship, 0f, 0f, 100f);

        // 只生成通往超空间的跳跃点：第一个参数为气态巨行星入口，这里不需要
        system.autogenerateHyperspaceJumpPoints(false, true);
    }
}
