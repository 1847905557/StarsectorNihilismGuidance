package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例：生涯舰队工厂 (FleetParamsV3 -> FleetFactoryV3.createFleet -> inflate -> spawn)。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，persistent data 键 REGISTER_MARK 一次性门控
 * (沿用既有模式：每个注册项用各自的键，不挂进插件的 CLASS_MARK 门内)。
 *
 * 行为：挂载后第一次 advance() 执行一次演示序列并自停 (isDone()=true，引擎下一帧自动移除本脚本)：
 *   1. 基线：选定演示市场 (优先 jangala)，记录地点/锚点与市场质量；
 *   2. FleetParamsV3(market 构造器：source/quality/timestamp 一次就位)，qualityOverride=0.6
 *      压质量 (平均 D 插约 2 个/船，可观察)，flagshipVariantId 指定旗舰变体；
 *   3. FleetFactoryV3.createFleet：记录舰队名/势力/类型/成员数/战机数/FP；
 *   4. 旗舰与指挥官：flagship hull 与变体、舰长是否就是舰队指挥官、指挥官等级/personality、军官数；
 *   5. 膨胀前状态：isInflated=false、inflater 已挂载、inflater.getQuality()==0.6；
 *   6. inflateIfNeeded 后：isInflated=true、inflater 保留 (persistent=true)、
 *      成员变体来源 REFIT、D 插总数 (质量 0.6 -> 平均 2 个/船)；
 *   7. 内存键：$fleetType / $sourceMarket / $salvageSeed / $spawnFPMult；
 *   8. 投放：location.spawnFleet(anchor, 0, 0, fleet) -> 入容器、坐标=锚点坐标、isAlive=true；
 *   9. 对照：FleetFactory.createPatrol(V1 固定模板) 生成快速巡逻队并投放；
 *  10. 清理：同帧 removeEntity 移除两支舰队，不留任何持久副作用。
 */
public class GearInFleetFactory implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_FleetFactory";

    private static final Logger LOG = Global.getLogger(GearInFleetFactory.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInFleetFactory() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内，见页面调用点片段)
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
        try {
            runOnce();
        } catch (Throwable t) {
            LOG.error("GearIn fleet factory failed", t);
        }
    }

    private void runOnce() {
        MarketAPI market = pickMarket();
        if (market == null) {
            LOG.error("GearIn fleet factory: no market found, aborting");
            return;
        }
        LocationAPI location = market.getContainingLocation();
        SectorEntityToken anchor = market.getPrimaryEntity();
        if (location == null || anchor == null) {
            LOG.error("GearIn fleet factory: market has no containing location or primary entity, aborting");
            return;
        }
        LOG.info(String.format(
                "GearIn fleet factory start: market=%s size=%d location=%s quality=%.2f",
                market.getId(), market.getSize(), location.getId(),
                Misc.getShipQuality(market, market.getFactionId())));

        // --- 1. FleetParamsV3：market 构造器 (source + quality + timestamp 一次就位)---
        // combatPts=40, freighterPts=12, tankerPts=6；总点数 58 -> getMinPreferredMarketSize=5
        FleetParamsV3 params = new FleetParamsV3(market, FleetTypes.PATROL_MEDIUM,
                40f, 12f, 6f, 0f, 0f, 0f, 0f);
        params.qualityOverride = 0.6f;          // 覆盖市场质量：quality=0.6 -> 平均 D 插 (1-0.6)/0.2 = 2 个/船
        params.flagshipVariantId = "tempest_Attack"; // 旗舰装配覆盖 (换 hull 为 tempest)
        params.officerLevelBonus = 1;           // 军官/指挥官等级 +1
        LOG.info(String.format(
                "GearIn fleet factory params: faction=%s fleetType=%s marketQuality=%.2f qualityOverride=%.2f totalPts=%.0f",
                params.factionId, params.fleetType, params.quality, params.qualityOverride, params.getTotalPts()));

        // --- 2. FleetFactoryV3.createFleet：完整生成链 ---
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
        LOG.info(String.format(
                "GearIn fleet factory created: name=%s faction=%s fleetType=%s members=%d fighters=%d fp=%.0f",
                fleet.getName(), fleet.getFaction().getId(),
                fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_FLEET_TYPE),
                members.size(), fleet.getNumFighters(), fleet.getFleetPoints()));
        if (members.isEmpty()) {
            LOG.error("GearIn fleet factory: generated fleet is empty, aborting");
            return;
        }

        // --- 3. 旗舰与指挥官 ---
        FleetMemberAPI flagship = fleet.getFlagship();
        String flagshipInfo = flagship == null ? "none" : String.format(
                "hull=%s variant=%s captainIsCommander=%s",
                flagship.getHullId(), flagship.getVariant().getHullVariantId(),
                flagship.getCaptain() == fleet.getCommander());
        LOG.info("GearIn fleet factory flagship: " + flagshipInfo);
        LOG.info(String.format(
                "GearIn fleet factory commander: name=%s level=%d personality=%s officers=%d",
                fleet.getCommander().getNameString(), fleet.getCommander().getStats().getLevel(),
                fleet.getCommander().getPersonalityAPI().getId(),
                fleet.getFleetData().getOfficersCopy().size()));

        // --- 4. 膨胀前：inflater 已挂载但未膨胀 ---
        LOG.info(String.format(
                "GearIn fleet factory pre-inflate: isInflated=%s inflaterAttached=%s inflaterQuality=%s",
                fleet.isInflated(), fleet.getInflater() != null,
                fleet.getInflater() == null ? "n/a" : String.format("%.2f", fleet.getInflater().getQuality())));

        // --- 5. 惰性膨胀：inflateIfNeeded 一次性补全装配 ---
        fleet.inflateIfNeeded();
        int dmodTotal = 0;
        int refitVariants = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            dmodTotal += DModManager.getNumDMods(member.getVariant());
            if (member.getVariant().getSource() == VariantSource.REFIT) {
                refitVariants++;
            }
        }
        LOG.info(String.format(
                "GearIn fleet factory inflated: isInflated=%s inflaterAttached=%s refitVariants=%d dmods=%d",
                fleet.isInflated(), fleet.getInflater() != null, refitVariants, dmodTotal));

        // --- 6. 工厂写入的舰队内存键 ---
        LOG.info(String.format(
                "GearIn fleet factory memory: fleetType=%s sourceMarket=%s salvageSeed=%d spawnFPMult=%.3f",
                fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_FLEET_TYPE),
                fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_SOURCE_MARKET),
                fleet.getMemoryWithoutUpdate().getLong(MemFlags.SALVAGE_SEED),
                Misc.getSpawnFPMult(fleet)));

        // --- 7. 投放：入容器 + 定位 (锚点坐标 + 偏移)+ setNoEngaging(3s) ---
        float anchorX = anchor.getLocation().x;
        float anchorY = anchor.getLocation().y;
        location.spawnFleet(anchor, 0f, 0f, fleet);
        LOG.info(String.format(
                "GearIn fleet factory spawned: inLocation=%s at=(%.0f, %.0f) anchor=(%.0f, %.0f) alive=%s",
                fleet.getContainingLocation() == location,
                fleet.getLocation().x, fleet.getLocation().y, anchorX, anchorY, fleet.isAlive()));

        // --- 8. 对照：FleetFactory(V1) 固定模板 createPatrol ---
        CampaignFleetAPI patrol = FleetFactory.createPatrol(market, FleetFactory.PatrolType.FAST);
        List<FleetMemberAPI> patrolMembers = patrol.getFleetData().getMembersListCopy();
        LOG.info(String.format(
                "GearIn fleet factory patrol: name=%s members=%d fp=%.0f flagship=%s",
                patrol.getName(), patrolMembers.size(), patrol.getFleetPoints(),
                patrol.getFlagship() == null ? "none" : patrol.getFlagship().getHullId()));
        location.spawnFleet(anchor, 200f, 200f, patrol);

        // --- 9. 清理：同帧移除，不留任何持久副作用 ---
        location.removeEntity(fleet);
        location.removeEntity(patrol);
        LOG.info(String.format(
                "GearIn fleet factory done: fleetAlive=%s patrolAlive=%s",
                fleet.isAlive(), patrol.isAlive()));
    }

    /** 演示市场：优先 jangala(hegemony 核心市场)，否则第一个有 open 子市场的市场。 */
    private MarketAPI pickMarket() {
        MarketAPI market = Global.getSector().getEconomy().getMarket("jangala");
        if (market != null) {
            return market;
        }
        for (MarketAPI candidate : Global.getSector().getEconomy().getMarketsCopy()) {
            if (candidate.hasSubmarket(Submarkets.SUBMARKET_OPEN)) {
                return candidate;
            }
        }
        return null;
    }
}
