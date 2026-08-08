package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

/**
 * 完整最小例 (势力段)：FactionAPI 常用入口演示脚本。
 *
 * 注册入口：onGameLoad(调用点片段见页面正文)，persistent data 键 REGISTER_MARK 一次性门控。
 *
 * 行为：读取玩家势力身份/颜色与对霸主/海盗/独立势力的关系 (数值、等级、敌对判定、getRelToPlayer
 * 包装)；对霸主 adjustRelationship(+0.05) 后立即 setRelationship 精确回滚 ("写入后必须回滚"
 * 基线写法)；读取 tariff/toll/fine、非法商品、已知资源集合规模、isShowInIntelTab。不遗留持久状态。
 */
public class GearInFactionServicesScript extends GearInOneShotScript {

    /** 存档持久化标记：本脚本是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_FactionServices";

    private static final Logger LOG = Global.getLogger(GearInFactionServicesScript.class);

    /** 关系数值统一格式：保留两位小数。 */
    private static String fmt(float value) {
        return String.format("%.2f", value);
    }

    @Override
    protected void runDemo() {
        SectorAPI sector = Global.getSector();
        FactionAPI playerFaction = sector.getPlayerFaction();
        FactionAPI hegemony = sector.getFaction(Factions.HEGEMONY);
        FactionAPI pirates = sector.getFaction(Factions.PIRATES);
        FactionAPI independent = sector.getFaction(Factions.INDEPENDENT);

        LOG.info(String.format(
                "GearIn campaign services faction: player=%s(%s) baseColor=%s darkColor=%s | hegemony=%s pirates=%s",
                playerFaction.getId(), playerFaction.getDisplayNameWithArticle(),
                playerFaction.getBaseUIColor(), playerFaction.getDarkUIColor(),
                hegemony.getDisplayName(), pirates.getDisplayName()));

        LOG.info(String.format(
                "GearIn campaign services faction relations: hegemony=%s(%s) hostile=%s | pirates=%s(%s) hostile=%s | independent=%s(%s) hostile=%s",
                fmt(playerFaction.getRelationship(Factions.HEGEMONY)),
                playerFaction.getRelationshipLevel(Factions.HEGEMONY).getDisplayName(),
                playerFaction.isHostileTo(Factions.HEGEMONY),
                fmt(playerFaction.getRelationship(Factions.PIRATES)),
                playerFaction.getRelationshipLevel(Factions.PIRATES).getDisplayName(),
                playerFaction.isHostileTo(Factions.PIRATES),
                fmt(playerFaction.getRelationship(Factions.INDEPENDENT)),
                playerFaction.getRelationshipLevel(Factions.INDEPENDENT).getDisplayName(),
                playerFaction.isHostileTo(Factions.INDEPENDENT)));

        // 关系写入并精确回滚：adjustRelationship 直接加、setRelationship 直写原值 (势力管理器钳位 [-1,1])
        float relBefore = hegemony.getRelationship(Factions.PLAYER);
        hegemony.adjustRelationship(Factions.PLAYER, 0.05f);
        float relDuring = hegemony.getRelationship(Factions.PLAYER);
        hegemony.setRelationship(Factions.PLAYER, relBefore);
        float relAfter = hegemony.getRelationship(Factions.PLAYER);
        LOG.info(String.format(
                "GearIn campaign services faction rel write-rollback: %s -> %s -> %s (rolled back)",
                fmt(relBefore), fmt(relDuring), fmt(relAfter)));
        LOG.info(String.format(
                "GearIn campaign services faction misc: relToPlayer=%s(%s) tariff=%.3f toll=%.3f fine=%.3f drugsIllegal=%s illegalCount=%d knownShips=%d knownWeapons=%d knownFighters=%d showInIntelTab=%s",
                fmt(playerFaction.getRelToPlayer().getRel()),
                playerFaction.getRelToPlayer().getLevel().getDisplayName(),
                playerFaction.getTariffFraction(), playerFaction.getTollFraction(), playerFaction.getFineFraction(),
                playerFaction.isIllegal(Commodities.DRUGS), playerFaction.getIllegalCommodities().size(),
                playerFaction.getKnownShips().size(),
                playerFaction.getKnownWeapons().size(),
                playerFaction.getKnownFighters().size(),
                playerFaction.isShowInIntelTab()));
    }
}
