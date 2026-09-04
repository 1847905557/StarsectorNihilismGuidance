package data.scripts.gearin.lib;

import org.apache.log4j.Logger;
import org.magiclib.util.MagicCampaign;
import org.magiclib.util.MagicVariables;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;

/** 生涯工具 (MagicCampaign)：查询面与写侧的参考消费姿势。
 * 生涯工具以 Global.getSector() 为宿主，只能在生涯上下文调用，调用前先判空；
 * logCampaignInfo 是查询面演示 (战役场景输出 "not in campaign")，
 * spawnDerelictExample 是写侧参考姿势 (调用方是生涯脚本，战斗插件不调用)。 */
public class GearInCampaignTools {

    private static final Logger LOG = Global.getLogger(GearInCampaignTools.class);

    private GearInCampaignTools() {
        // 工具类：不实例化
    }

    /** 生涯上下文查询演示：只在 Global.getSector() 非 null(生涯启动/读档后) 时输出统计。 */
    public static void logCampaignInfo() {
        SectorAPI sector = Global.getSector();
        if (sector == null) {
            LOG.info("GearIn magic campaign: no sector (not in campaign)");
            return;
        }
        LOG.info("GearIn magic campaign: sectorSize=" + MagicVariables.getSectorSize()
                + " systems=" + sector.getStarSystems().size()
                + " markets=" + sector.getEconomy().getMarketsCopy().size());
    }

    /** 生涯写侧参考姿势：在生涯脚本 (如 C3-S4 的 GearInSystemGenerator 末尾) 调用，
     * 把一艘残骸舰放到轨道上。只有生涯上下文有效 (orbitCenter 必须已加入星区)；
     * 场景错误时写 warn 而不是抛错。 */
    public static void spawnDerelictExample(SectorEntityToken orbitCenter, String variantId) {
        if (Global.getSector() == null) {
            LOG.warn("GearIn magic campaign: createDerelict called outside campaign, skipped");
            return;
        }
        SectorEntityToken wreck = MagicCampaign.createDerelict(
                variantId,
                ShipRecoverySpecial.ShipCondition.AVERAGE,
                true,  // discoverable：发现时奖励探索 XP
                null,  // discoveryXp：null 使用默认
                true,  // recoverable：写入打捞 special(可回收舰船)
                orbitCenter,
                0f,    // orbitStartAngle
                200f,  // orbitRadius
                30f);  // orbitDays
        LOG.info("GearIn magic campaign: derelict " + wreck.getId()
                + " orbiting " + orbitCenter.getId());
    }
}
