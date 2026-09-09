package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.loading.WeaponGroupSpec;

/**
 * 克隆一份装配，修改武器、武器组、船插和幅能后应用到演示成员。
 */
public class GearInVariantRefitter implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_VariantRefitter";

    private static final Logger LOG = Global.getLogger(GearInVariantRefitter.class);

    private static final String SLOT_ID = "WS 001";
    private static final String VARIANT_ID = "wolf_Assault";

    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }
        done = true;

        FleetMemberAPI member = Global.getFactory().createFleetMember(
                FleetMemberType.SHIP,
                VARIANT_ID);
        ShipVariantAPI original = member.getVariant();
        ShipVariantAPI refit = original.clone();

        String weaponBefore = refit.getWeaponId(SLOT_ID);
        int ventsBefore = refit.getNumFluxVents();
        int capacitorsBefore = refit.getNumFluxCapacitors();

        refit.addWeapon(
                SLOT_ID,
                "pdlaser");
        refit.autoGenerateWeaponGroups();

        if (!refit.getWeaponGroups().isEmpty()) {
            WeaponGroupSpec firstGroup = refit.getWeaponGroups().get(0);
            firstGroup.setAutofireOnByDefault(
                    !firstGroup.isAutofireOnByDefault());
        }

        refit.removeMod("fluxdistributor");
        refit.addMod("fluxdistributor");
        refit.setNumFluxVents(ventsBefore + 1);
        refit.setNumFluxCapacitors(capacitorsBefore + 1);

        member.setVariant(
                refit,
                false,
                true);
        member.updateStats();

        LOG.info("GearIn variant refitter: id=" + member.getId()
                + " hull=" + member.getHullId()
                + " source=" + refit.getSource()
                + " weapon=" + weaponBefore + "->" + refit.getWeaponId(SLOT_ID)
                + " vents=" + ventsBefore + "->" + refit.getNumFluxVents()
                + " capacitors=" + capacitorsBefore + "->"
                + refit.getNumFluxCapacitors()
                + " groups=" + refit.getWeaponGroups().size()
                + " op=" + refit.computeOPCost(null)
                + " deployable=" + member.canBeDeployedForCombat());
    }
}
