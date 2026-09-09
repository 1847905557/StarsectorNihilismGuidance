package data.scripts.gearin.campaign;

import java.util.List;
import org.apache.log4j.Logger;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.MutableValue;

public class GearInCargoServicesScript implements EveryFrameScript {
    private static final Logger LOG = Global.getLogger(GearInCargoServicesScript.class);
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

        CargoAPI cargo = Global.getFactory().createCargo(true);
        cargo.addSupplies(120f);
        cargo.addFuel(80f);
        cargo.addCrew(40);
        cargo.addMarines(12);
        cargo.addCommodity(Commodities.HEAVY_MACHINERY, 25f);

        MutableValue credits = cargo.getCredits();
        credits.add(2500f);

        CargoAPI snapshot = cargo.createCopy();
        LOG.info(String.format(
                "GearIn cargo created: supplies=%.0f fuel=%.0f crew=%d marines=%d personnel=%d credits=%.0f used=%.1f left=%.1f",
                cargo.getSupplies(), cargo.getFuel(), cargo.getCrew(), cargo.getMarines(),
                cargo.getTotalPersonnel(),
                credits.get(), cargo.getSpaceUsed(), cargo.getSpaceLeft()));

        List<CargoStackAPI> stacks = cargo.getStacksCopy();
        int nullStacks = 0;
        for (CargoStackAPI stack : stacks) {
            if (stack.isNull()) {
                nullStacks++;
            }
            LOG.info(String.format("GearIn cargo stack: type=%s name=%s size=%.1f space=%.1f",
                    stack.getType(), stack.getDisplayName(), stack.getSize(), stack.getCargoSpace()));
        }

        cargo.removeCommodity(Commodities.HEAVY_MACHINERY, 5f);
        LOG.info(String.format("GearIn cargo changed: machinery=%.0f snapshotMachinery=%.0f nullStacks=%d",
                cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY),
                snapshot.getCommodityQuantity(Commodities.HEAVY_MACHINERY), nullStacks));
        done = true;
    }
}
