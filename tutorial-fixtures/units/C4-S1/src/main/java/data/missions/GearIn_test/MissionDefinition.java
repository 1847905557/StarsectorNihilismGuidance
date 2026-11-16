package data.missions.GearIn_test;

// shared by Nihilism, modified from AnyIDElse under license

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;

/** 自动列出本 MOD 的全部非战机装配，用于测试与导出。 */
public class MissionDefinition implements MissionDefinitionPlugin {

    // 船体 ID 的前缀，换成您自己的前缀名
    public static final String PREFIX = "GearIn_";

    @Override
    public void defineMission(MissionDefinitionAPI api) {
        // 双方舰队：舰船名前缀、作战目标、是否由 AI 控制、初始指挥点
        api.initFleet(FleetSide.PLAYER, "SIM", FleetGoal.ATTACK, false, 5);
        api.initFleet(FleetSide.ENEMY, "SIM", FleetGoal.ATTACK, true, 5);

        api.setFleetTagline(FleetSide.PLAYER, "TEST");
        api.setFleetTagline(FleetSide.ENEMY, "TEST");

        api.addBriefingItem("TEST");

        // 找出船体 ID 以前缀开头的全部装配，跳过战机和测试战役自己导出的装配
        List<String> variants = new ArrayList<String>();
        for (String id : Global.getSettings().getAllVariantIds()) {
            ShipVariantAPI variant = Global.getSettings().getVariant(id);
            if (variant.getHullSpec().getHullId().startsWith(PREFIX)
                    && variant.getHullSpec().getHullSize() != ShipAPI.HullSize.FIGHTER
                    && !id.startsWith("mission_")) {
                variants.add(id);
            }
        }
        Collections.sort(variants);

        // 全部加入玩家舰队，第一艘为旗舰
        boolean first = true;
        for (String variant : variants) {
            api.addToFleet(FleetSide.PLAYER, variant, FleetMemberType.SHIP, first);
            first = false;
        }

        // 敌方：原版余辉中枢
        api.addToFleet(FleetSide.ENEMY, "remnant_station2_Standard", FleetMemberType.SHIP, false);

        // 地图：大小、星云、传感干扰器、通讯中继站、导航浮标与小行星带
        float width = 10000f;
        float height = 10000f;
        api.initMap(-width / 2f, width / 2f, -height / 2f, height / 2f);

        float minX = -width / 2;
        float minY = -height / 2;

        api.addNebula(minX + width * 0.66f, minY + height * 0.5f, 2000);
        api.addNebula(minX + width * 0.25f, minY + height * 0.6f, 1000);
        api.addNebula(minX + width * 0.25f, minY + height * 0.4f, 1000);

        for (int i = 0; i < 5; i++) {
            float x = (float) Math.random() * width - width / 2;
            float y = (float) Math.random() * height - height / 2;
            float radius = 100f + (float) Math.random() * 400f;
            api.addNebula(x, y, radius);
        }

        api.addObjective(minX + width * 0.25f + 2000f, minY + height * 0.5f, "sensor_array");
        api.addObjective(minX + width * 0.75f - 2000f, minY + height * 0.5f, "comm_relay");
        api.addObjective(minX + width * 0.33f + 2000f, minY + height * 0.4f, "nav_buoy");
        api.addObjective(minX + width * 0.66f - 2000f, minY + height * 0.6f, "nav_buoy");

        api.addAsteroidField(-(minY + height), minY + height, -45, 2000f, 20f, 70f, 100);
    }
}
