package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.VariantSource;

/** 巡逻队旗舰的改装：火神炮换成都林，加装破甲校准，用耗散通道让出 OP；超出 OP 上限则放弃。 */
public final class GearInPatrolRefitter {

    private static final Logger LOG = Global.getLogger(GearInPatrolRefitter.class);

    private static final String[] BALLISTIC_SLOTS = {"WS0005", "WS0006"};
    private static final String NEW_WEAPON = "GearIn_durin";
    private static final String NEW_MOD = "GearIn_breach_calibration";
    private static final int NEW_VENTS = 12;

    private GearInPatrolRefitter() {
    }

    /** 对成员的装配做一份副本并改装，通过 OP 核对后写回成员。 */
    public static void refit(FleetMemberAPI member, PersonAPI captain) {
        ShipVariantAPI variant = member.getVariant().clone();
        variant.setSource(VariantSource.REFIT);

        for (String slot : BALLISTIC_SLOTS) {
            variant.clearSlot(slot);
            variant.addWeapon(slot, NEW_WEAPON);
        }
        variant.addMod(NEW_MOD);
        variant.setNumFluxVents(NEW_VENTS);

        int cost = variant.computeOPCost(captain.getStats());
        int limit = variant.getHullSpec().getOrdnancePoints(captain.getStats());
        if (cost > limit) {
            LOG.warn("GearIn patrol refit skipped: OP " + cost + "/" + limit);
            return;
        }
        member.setVariant(variant, false, true);
    }
}
