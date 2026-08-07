package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

/**
 * 完整最小例：Sector 级生涯事件监听器(CampaignEventListener 角色)。
 * 注册入口：onGameLoad 门控块(页面调用点片段，REGISTER_MARK 一次性门控)。
 * 注册与自停由配对脚本 GearInListenerLifecycleScript 管理 —— 脚本到期后显式
 * removeListener：脚本结束不等于监听器移除(硬规则)。BaseCampaignEventListener
 * 提供全接口 21 方法的空实现，本类只覆写关心的事件边界(市场/贸易/战斗/舰队/声望)。
 */
public class GearInCampaignListener extends BaseCampaignEventListener {

    /** 存档持久化标记：本监听器及其生命周期脚本是否已在该存档注册过(随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_CampaignListener";

    private static final Logger LOG = Global.getLogger(GearInCampaignListener.class);

    /** 不在构造器自注册；注册由调用点在 onGameLoad 门控块内显式执行。 */
    public GearInCampaignListener() {
        super(false);
    }

    // ---- 事件边界：市场 ----

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        LOG.info(String.format("GearIn listener: market opened: %s (size %d)",
                market.getName(), market.getSize()));
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        LOG.info("GearIn listener: market closed: " + market.getName());
    }

    // ---- 事件边界：贸易 ----

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        LOG.info(String.format("GearIn listener: trade at %s, credit value %.0f",
                transaction.getMarket().getName(), transaction.getCreditValue()));
    }

    @Override
    public void reportEncounterLootGenerated(FleetEncounterContextPlugin plugin, CargoAPI loot) {
        LOG.info("GearIn listener: encounter loot generated, " + loot.getStacksCopy().size() + " stacks");
    }

    // ---- 事件边界：战斗 ----

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        LOG.info(String.format("GearIn listener: battle occurred, player involved=%s, winner=%s",
                battle.isPlayerInvolved(), winnerName(primaryWinner)));
    }

    @Override
    public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        LOG.info(String.format("GearIn listener: battle finished, player involved=%s, winner=%s",
                battle.isPlayerInvolved(), winnerName(primaryWinner)));
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        LOG.info("GearIn listener: player engagement resolved, didPlayerWin=" + result.didPlayerWin());
    }

    // ---- 事件边界：舰队 ----

    @Override
    public void reportFleetSpawned(CampaignFleetAPI fleet) {
        LOG.info("GearIn listener: fleet spawned: " + fleet.getName() + " (" + fleet.getFaction().getId() + ")");
    }

    @Override
    public void reportFleetDespawned(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        LOG.info("GearIn listener: fleet despawned: " + fleet.getName() + ", reason=" + reason);
    }

    // ---- 事件边界：声望 ----

    @Override
    public void reportPlayerReputationChange(String faction, float delta) {
        LOG.info(String.format("GearIn listener: reputation change vs faction %s by %+.2f", faction, delta));
    }

    @Override
    public void reportPlayerReputationChange(PersonAPI person, float delta) {
        LOG.info(String.format("GearIn listener: reputation change vs %s by %+.2f",
                person.getName().getFullName(), delta));
    }

    private static String winnerName(CampaignFleetAPI fleet) {
        if (fleet == null) {
            return "none";
        }
        return fleet.getName() + " (" + fleet.getFaction().getId() + ")";
    }
}
