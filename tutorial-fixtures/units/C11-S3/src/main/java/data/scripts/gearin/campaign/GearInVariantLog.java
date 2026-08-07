package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;

/**
 * 包内协作：把变体身份 / 来源 / 装配 / OP 摘要打印到日志。
 * GearInVariantReader 与 GearInVariantRefitter 共用的打印格式，避免两处重复。
 */
final class GearInVariantLog {

    private static final Logger LOG = Global.getLogger(GearInVariantLog.class);

    private GearInVariantLog() {
    }

    static void logVariant(String label, ShipVariantAPI variant, MutableCharacterStatsAPI stats) {
        LOG.info(String.format(
                "GearIn %s: id=%s displayName=%s hull=%s source=%s isStockVariant=%s isEmptyHullVariant=%s "
                        + "weapons=%s mods=%s wings=%s vents=%d caps=%d op=%d unused=%d",
                label, variant.getHullVariantId(), variant.getDisplayName(), variant.getHullSpec().getHullId(),
                variant.getSource(), variant.isStockVariant(), variant.isEmptyHullVariant(),
                variant.getNonBuiltInWeaponSlots(), variant.getHullMods(), variant.getWings(),
                variant.getNumFluxVents(), variant.getNumFluxCapacitors(),
                variant.computeOPCost(stats), variant.getUnusedOP(stats)));
    }
}
