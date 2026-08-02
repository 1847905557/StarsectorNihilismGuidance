package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 环节二（个人）：舰长技能 -> 成员 stats 交接（applyPersonalToStats）。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），persistent data 键
 * REGISTER_MARK 一次性门控（每个注册项用各自的键）。
 *
 * 行为：挂载后第一次 advance() 执行一次演示并自停（isDone()=true，引擎下一帧自动移除）：
 *   1. 未入队换舰长：setCaptain 触发 updateStats，但舰长个人技能不应用
 *      （updateStats 的舰队条件：getFleetData() 为 null 时跳过 applyPersonalToStats）；
 *   2. 入队 + 显式 updateStats：个人技能（gunnery_implants L1）开始应用，
 *      后坐力倍率 1.0 -> 0.75、自动瞄准 0.0 -> 1.0、实弹/能量射程 +15%；
 *   3. 入队换舰长：setCaptain 立即重算，换回无技能人物后数值恢复。
 *
 * 演示全程同帧回滚（只留下 REGISTER_MARK 键与演示成员的增删），不遗留任何持久状态。
 */
public class GearInCaptainToMember implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_CaptainToMember";

    private static final Logger LOG = Global.getLogger(GearInCaptainToMember.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInCaptainToMember() {
        // 空构造：注册由调用方执行（onGameLoad 的 persistent data 门内，见页面调用点片段）
    }

    @Override
    public boolean isDone() {
        return executed;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (executed) {
            return;
        }
        executed = true;
        runDemo();
    }

    private void runDemo() {
        SectorAPI sector = Global.getSector();
        FleetDataAPI fleetData = sector.getPlayerFleet().getFleetData();
        int initialCount = fleetData.getNumMembers();
        LOG.info(String.format("GearIn captainToMember start: members=%d", initialCount));

        // --- 1. 未入队换舰长：setCaptain 触发 updateStats，但个人技能不应用 ---
        FleetMemberAPI wolf = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        PersonAPI gunner = Global.getFactory().createPerson();
        gunner.getStats().setSkillLevel("gunnery_implants", 1f);
        wolf.setCaptain(gunner);
        LOG.info(String.format(
                "GearIn captainToMember preJoin: recoil=%.3f aim=%.3f (skills not applied, member not in fleet)",
                wolf.getStats().getMaxRecoilMult().getModifiedValue(),
                wolf.getStats().getAutofireAimAccuracy().getModifiedValue()));

        // --- 2. 入队 + 显式 updateStats：个人技能开始应用（getFleetData() 已非 null） ---
        fleetData.addFleetMember(wolf);
        wolf.updateStats();
        LOG.info(String.format(
                "GearIn captainToMember joined: count=%d recoil=%.3f aim=%.3f ballisticRange=%.1f energyRange=%.1f",
                fleetData.getNumMembers(),
                wolf.getStats().getMaxRecoilMult().getModifiedValue(),
                wolf.getStats().getAutofireAimAccuracy().getModifiedValue(),
                wolf.getStats().getBallisticWeaponRangeBonus().getPercentMod(),
                wolf.getStats().getEnergyWeaponRangeBonus().getPercentMod()));

        // --- 3. 入队换舰长：立即重算；换回无技能人物后恢复 ---
        float recoilWithGunner = wolf.getStats().getMaxRecoilMult().getModifiedValue();
        PersonAPI plain = Global.getFactory().createPerson();
        wolf.setCaptain(plain);
        float recoilRestored = wolf.getStats().getMaxRecoilMult().getModifiedValue();
        LOG.info(String.format("GearIn captainToMember swap: recoil %.3f -> %.3f (restored)",
                recoilWithGunner, recoilRestored));

        // --- 4. 清理：移除演示成员 ---
        fleetData.removeFleetMember(wolf);
        LOG.info(String.format("GearIn captainToMember done: members=%d (initial %d)",
                fleetData.getNumMembers(), initialCount));
    }
}
